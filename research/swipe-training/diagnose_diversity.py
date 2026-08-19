#!/usr/bin/env python3
"""
diagnose_diversity.py

Separates two explanations for the same symptom.

During full-scale Stage 1 training, free-running validation loss climbs while
the teacher-forced loss and the corner-kinematics score both improve. Two
readings fit that trace and they call for opposite responses:

  A. Legitimate diversity. Given a word there are many valid swipes. Running
     free, the generator commits to *a* coherent trajectory; scoring it
     pointwise against *one particular* human recording punishes it for not
     being that specific recording, and punishes harder as it grows more
     confident. Nothing is wrong.

  B. Exposure-bias drift. The trajectories are genuinely degrading and the
     corner metric -- a distribution summary -- is too coarse to notice.

NLL against a fixed target cannot tell these apart, because both produce a
rising number. This does, by changing the reference:

    human-to-human distance   how far do two real recordings of the same word
                              sit from each other? This is the irreducible
                              spread of the task -- the floor.

    model-to-nearest-human    how far is a generated trajectory from the
                              closest real recording of that word?

Under (A) the model's distance to its nearest human neighbour lands inside the
human-to-human spread: it is producing something a person plausibly could have
produced, just not the one we held out. Under (B) it sits well outside, and the
gap widens over training.

A random-pairing control is reported alongside, because "distance to nearest of
N" is optimistic by construction and needs a baseline computed the same way.

Usage
-----
  python diagnose_diversity.py --checkpoint models/seq2traj_best.pt
"""

from __future__ import annotations

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

import numpy as np

from swipe_common import (
    SwipeSample,
    is_supported,
    key_sequence,
    load_futo,
    resample_trajectory,
)

Traj = Tuple[np.ndarray, np.ndarray]


def _norm(xy: np.ndarray, t: np.ndarray, n: int) -> np.ndarray:
    """Resample to n points and return positions only, as [n, 2]."""
    rxy, _ = resample_trajectory(np.asarray(xy, np.float32),
                                 np.asarray(t, np.float32), n)
    return np.asarray(rxy, np.float32)


def path_distance(a: np.ndarray, b: np.ndarray) -> float:
    """Mean per-point Euclidean distance between two aligned paths.

    Both are arc-length resampled to the same count first, so point i of each
    sits at the same fraction along its own path. Units are keyboard widths:
    0.1 is one key.
    """
    return float(np.mean(np.linalg.norm(a - b, axis=1)))


def summarize(name: str, values: Sequence[float]) -> dict:
    v = np.asarray(values, dtype=np.float64)
    if v.size == 0:
        return {"name": name, "n": 0}
    return {
        "name": name,
        "n": int(v.size),
        "mean": float(v.mean()),
        "median": float(np.median(v)),
        "p10": float(np.quantile(v, 0.10)),
        "p90": float(np.quantile(v, 0.90)),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="Diversity vs drift diagnostic")
    ap.add_argument("--parquet", default="futo_swipes.parquet")
    ap.add_argument("--checkpoint", default="models/seq2traj_best.pt")
    ap.add_argument("--report", default="diversity_report.json")
    ap.add_argument("--words", type=int, default=200)
    ap.add_argument("--variations", type=int, default=6)
    ap.add_argument("--min-real-per-word", type=int, default=6,
                    help="need several real recordings per word to measure "
                         "the human-to-human spread at all")
    ap.add_argument("--points", type=int, default=64)
    ap.add_argument("--temperature", type=float, default=0.5)
    ap.add_argument("--noise-sigma", type=float, default=2.0)
    ap.add_argument("--max-real-samples", type=int, default=200000)
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--seed", type=int, default=1337)
    args = ap.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)

    real = load_futo(args.parquet, max_samples=args.max_real_samples)
    by_word: Dict[str, List[SwipeSample]] = defaultdict(list)
    for s in real:
        by_word[s.word].append(s)

    eligible = [
        w for w, v in by_word.items()
        if len(v) >= args.min_real_per_word
        and is_supported(w)
        and len(key_sequence(w)) >= 3
    ]
    random.shuffle(eligible)
    words = eligible[: args.words]
    if not words:
        print("ERROR: no eligible words")
        return 2
    print(f"[diag] {len(words)} words with >= {args.min_real_per_word} real recordings each")

    # --- reference spread: how far apart are two humans on the same word? ----
    # Measured leave-one-out and nearest-of-the-rest, exactly the way the model
    # is scored below, so the two numbers are directly comparable.
    human_nearest: List[float] = []
    human_random: List[float] = []
    banks: Dict[str, np.ndarray] = {}

    for w in words:
        paths = np.stack([_norm(s.xy, s.t, args.points)
                          for s in by_word[w][: args.min_real_per_word * 2]])
        banks[w] = paths
        for i in range(len(paths)):
            others = np.delete(paths, i, axis=0)
            d = [path_distance(paths[i], o) for o in others]
            human_nearest.append(min(d))
            human_random.append(random.choice(d))

    # --- model output --------------------------------------------------------
    import torch
    from train_seq2traj import load_generator, synthesize_words

    dev = torch.device(args.device)
    model, state = load_generator(args.checkpoint, dev)
    model.noise_sigma = args.noise_sigma
    samples = synthesize_words(
        model, words, dev,
        variations=args.variations,
        traj_len=state.get("traj_len", 48),
        temperature=args.temperature,
    )

    model_nearest: List[float] = []
    model_random: List[float] = []
    for s in samples:
        bank = banks.get(s.word)
        if bank is None:
            continue
        p = _norm(s.xy, s.t, args.points)
        d = [path_distance(p, o) for o in bank]
        model_nearest.append(min(d))
        model_random.append(random.choice(d))

    rows = [
        summarize("human -> nearest human", human_nearest),
        summarize("model -> nearest human", model_nearest),
        summarize("human -> random human", human_random),
        summarize("model -> random human", model_random),
    ]

    print("\n" + "=" * 74)
    print(f"{'comparison':<24} {'n':>6} {'mean':>9} {'median':>9} {'p10':>8} {'p90':>8}")
    print("-" * 74)
    for r in rows:
        if not r["n"]:
            continue
        print(f"{r['name']:<24} {r['n']:>6} {r['mean']:>9.4f} {r['median']:>9.4f} "
              f"{r['p10']:>8.4f} {r['p90']:>8.4f}")
    print("=" * 74)
    print("distances are mean per-point offset in keyboard widths (0.1 = one key)")

    hn = np.mean(human_nearest) if human_nearest else float("nan")
    mn = np.mean(model_nearest) if model_nearest else float("nan")
    ratio = mn / hn if hn else float("nan")

    print(f"\nmodel/human nearest-neighbour ratio: {ratio:.2f}x")
    if ratio <= 1.5:
        verdict = "diversity"
        print("READING: generated trajectories sit within the human-to-human spread.")
        print("The rising free-running NLL is the cost of committing to a valid")
        print("alternative, not drift. Expected behaviour for a stochastic generator.")
    elif ratio <= 2.5:
        verdict = "marginal"
        print("READING: generated trajectories sit somewhat outside the human spread.")
        print("Not clearly drift, not clearly healthy. Re-run across epochs and watch")
        print("whether the ratio grows -- a stable ratio is diversity, a rising one is drift.")
    else:
        verdict = "drift"
        print("READING: generated trajectories sit well outside the human spread.")
        print("This is exposure-bias drift that the corner metric is too coarse to see.")
        print("Do not feed Stage 2 from this checkpoint.")

    report = {
        "checkpoint": args.checkpoint,
        "words": len(words),
        "variations": args.variations,
        "points": args.points,
        "temperature": args.temperature,
        "rows": rows,
        "nearest_ratio": ratio,
        "verdict": verdict,
    }
    Path(args.report).write_text(json.dumps(report, indent=2))
    print(f"\n[diag] report -> {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
