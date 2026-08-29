#!/usr/bin/env python3
"""
validate_seq2traj.py

Stage 1 acceptance gate.

Compares the kinematics of three trajectory sources on the *same* held-out
words:

  1. real      — human swipes from FUTO (ground truth)
  2. seq2traj  — Stage 1 generator output
  3. physics   — the legacy Bezier/spring generator (the known-bad baseline)

The decisive metric is the corner speed ratio: speed at a direction-change
vertex divided by the trajectory's mean speed. Humans brake into corners, so
this sits well below 1.0. Spline-based generators carry speed through the
vertex, which is the +1531% corner-velocity error the earlier validation suite
reported.

Passing this gate is the precondition for spending GPU time on Stage 2. If
Seq2Traj's corner ratio does not land inside the human distribution, Stage 2
would just be training on the same failure in a nicer wrapper.

Usage
-----
  python validate_seq2traj.py \
      --parquet futo_swipes.parquet \
      --checkpoint models/seq2traj_best.pt \
      --report validation_report.json
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

import numpy as np

from swipe_common import (
    CornerStats,
    SwipeSample,
    corner_speed_ratios,
    corner_stats,
    is_supported,
    key_sequence,
    load_futo,
    resample_trajectory,
)

Traj = Tuple[np.ndarray, np.ndarray]


# =============================================================================
# SOURCES
# =============================================================================


def sample_physics(words: Sequence[str], variations: int) -> List[Traj]:
    """Legacy Bezier/spring generator — the baseline we are trying to beat."""
    try:
        from generate_synthetic_swipes import generate_synthetic_swipes
    except ImportError as exc:
        print(f"[physics] unavailable ({exc}), skipping baseline")
        return []

    out: List[Traj] = []
    for w in words:
        try:
            for rec in generate_synthetic_swipes(w, num_variations=variations):
                c = rec["curve"]
                xy = np.stack([np.asarray(c["x"], dtype=np.float32),
                               np.asarray(c["y"], dtype=np.float32)], axis=1)
                t = np.asarray(c["t"], dtype=np.float32)
                # Legacy generator emits milliseconds; normalize to seconds.
                if t.size and t.max() > 50:
                    t = t / 1000.0
                out.append((xy, t))
        except Exception as exc:  # noqa: BLE001 - baseline is best-effort
            print(f"[physics] {w!r} failed: {exc}")
    return out


def sample_seq2traj(checkpoint: str, words: Sequence[str], variations: int,
                    device: str, temperature: float, noise_sigma: float = 2.0) -> List[Traj]:
    import torch

    from train_seq2traj import load_generator, synthesize_words

    dev = torch.device(device)
    model, state = load_generator(checkpoint, dev)
    model.noise_sigma = noise_sigma
    samples = synthesize_words(
        model, words, dev,
        variations=variations,
        traj_len=state.get("traj_len", 48),
        temperature=temperature,
    )
    return [(s.xy, s.t) for s in samples]


# =============================================================================
# COMPARISON
# =============================================================================


def pct_error(candidate: float, reference: float) -> float:
    if not np.isfinite(candidate) or not np.isfinite(reference) or reference == 0:
        return float("nan")
    return 100.0 * (candidate - reference) / abs(reference)


def wasserstein_1d(a: np.ndarray, b: np.ndarray) -> float:
    """1-D Wasserstein distance without a scipy dependency."""
    a, b = np.sort(np.asarray(a, float)), np.sort(np.asarray(b, float))
    if a.size == 0 or b.size == 0:
        return float("nan")
    q = np.linspace(0, 1, 256)
    return float(np.mean(np.abs(np.quantile(a, q) - np.quantile(b, q))))


def match_sampling(trajs: List[Traj], n_points: int) -> List[Traj]:
    """Resample every trajectory to a common point count.

    Corner detection is sensitive to sampling density: the *same* human swipes
    measure a mean corner ratio of 0.161 at their native ~77 points and 0.342
    resampled to 48, because sparser sampling averages away momentary stops.
    Comparing 48-point synthetic output against native-resolution human data
    therefore reports roughly double the true error. Stage 2 resamples every
    source to a single length before the classifier sees it, so the gate has to
    measure on that same footing or it is not describing what we ship.
    """
    out: List[Traj] = []
    for xy, t in trajs:
        if len(xy) < 2:
            continue
        rxy, rt = resample_trajectory(np.asarray(xy, np.float32),
                                      np.asarray(t, np.float32), n_points)
        out.append((rxy, rt))
    return out


def summarize(name: str, trajs: List[Traj]) -> dict:
    stats = corner_stats(trajs)
    ratios = []
    for xy, t in trajs:
        ratios.extend(corner_speed_ratios(xy, t))
    d = stats.as_dict()
    d["_ratios"] = ratios
    d["source"] = name
    return d


def main() -> int:
    ap = argparse.ArgumentParser(description="Stage 1 kinematic acceptance gate")
    ap.add_argument("--parquet", default="futo_swipes.parquet")
    ap.add_argument("--checkpoint", default="models/seq2traj_best.pt")
    ap.add_argument("--report", default="validation_report.json")
    ap.add_argument("--words", type=int, default=300, help="held-out words to compare")
    ap.add_argument("--variations", type=int, default=10)
    ap.add_argument("--min-real-per-word", type=int, default=4)
    ap.add_argument("--temperature", type=float, default=0.7)
    ap.add_argument("--noise-sigma", type=float, default=2.0)
    ap.add_argument("--max-real-samples", type=int, default=200000)
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--seed", type=int, default=1337)
    ap.add_argument("--tolerance", type=float, default=25.0,
                    help="max allowed %% error on mean corner speed ratio")
    ap.add_argument("--skip-physics", action="store_true")
    ap.add_argument("--match-points", type=int, default=64,
                    help="resample all sources to this many points before "
                         "measuring; must match Stage 2's --traj-len")
    args = ap.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)

    # --- real reference -----------------------------------------------------
    real = load_futo(args.parquet, max_samples=args.max_real_samples)
    by_word: Dict[str, List[SwipeSample]] = defaultdict(list)
    for s in real:
        by_word[s.word].append(s)

    eligible = [
        w for w, v in by_word.items()
        if len(v) >= args.min_real_per_word and is_supported(w) and len(key_sequence(w)) >= 3
    ]
    random.shuffle(eligible)
    words = eligible[: args.words]
    if not words:
        print("ERROR: no eligible held-out words", file=sys.stderr)
        return 2
    print(f"[gate] comparing on {len(words)} held-out words")

    real_trajs: List[Traj] = []
    for w in words:
        for s in by_word[w][: args.variations]:
            real_trajs.append((s.xy, s.t))

    n_pts = args.match_points
    print(f"[gate] resampling every source to {n_pts} points "
          f"(Stage 2's trajectory length) before measuring")
    results = [summarize("real", match_sampling(real_trajs, n_pts))]

    # --- seq2traj -----------------------------------------------------------
    if Path(args.checkpoint).exists():
        results.append(summarize("seq2traj", match_sampling(
            sample_seq2traj(args.checkpoint, words, args.variations,
                            args.device, args.temperature, args.noise_sigma),
            n_pts)))
    else:
        print(f"[gate] no checkpoint at {args.checkpoint}, skipping seq2traj")

    # --- physics baseline ---------------------------------------------------
    if not args.skip_physics:
        phys = sample_physics(words, args.variations)
        if phys:
            results.append(summarize("physics", match_sampling(phys, n_pts)))

    # --- report -------------------------------------------------------------
    ref = results[0]
    ref_ratios = np.asarray(ref["_ratios"])

    print("\n" + "=" * 78)
    print(f"{'source':<10} {'corners':>8} {'mean':>8} {'median':>8} "
          f"{'p10':>7} {'p90':>7} {'%err':>9} {'wass':>7}")
    print("-" * 78)

    report = {"words": len(words), "variations": args.variations, "sources": {}}
    verdict_ok = None

    for r in results:
        err = pct_error(r["mean_corner_ratio"], ref["mean_corner_ratio"])
        wass = wasserstein_1d(np.asarray(r["_ratios"]), ref_ratios)
        print(f"{r['source']:<10} {r['n_corners']:>8} {r['mean_corner_ratio']:>8.3f} "
              f"{r['median_corner_ratio']:>8.3f} {r['p10_corner_ratio']:>7.3f} "
              f"{r['p90_corner_ratio']:>7.3f} {err:>8.1f}% {wass:>7.3f}")

        entry = {k: v for k, v in r.items() if not k.startswith("_")}
        entry["corner_ratio_pct_error_vs_real"] = err
        entry["corner_ratio_wasserstein_vs_real"] = wass
        report["sources"][r["source"]] = entry

        if r["source"] == "seq2traj":
            verdict_ok = abs(err) <= args.tolerance

    print("=" * 78)

    if verdict_ok is None:
        print("\nVERDICT: INCOMPLETE — seq2traj was not evaluated.")
        report["verdict"] = "incomplete"
        code = 2
    elif verdict_ok:
        print(f"\nVERDICT: PASS — seq2traj corner kinematics within "
              f"±{args.tolerance:.0f}% of human. Clear to generate the supplement.")
        report["verdict"] = "pass"
        code = 0
    else:
        print(f"\nVERDICT: FAIL — seq2traj corner kinematics outside "
              f"±{args.tolerance:.0f}% of human. Do not feed Stage 2 yet.")
        report["verdict"] = "fail"
        code = 1

    report["tolerance_pct"] = args.tolerance
    report["match_points"] = n_pts
    Path(args.report).write_text(json.dumps(report, indent=2))
    print(f"[gate] report -> {args.report}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
