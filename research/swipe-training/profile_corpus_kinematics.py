#!/usr/bin/env python3
"""
OmniBoard Canonical FUTO Swipe Corpus Characterization & Kinematic Analysis Pass

This script performs the canonical full-corpus characterization over all 1.22M FUTO swipe records:
1. Complete accounting of per-run and per-split volume and explicit exclusion reasons.
2. Robust touch event temporal analysis handling duplicate / nonpositive timestamps.
3. Aspect-correct geometric velocity and local turning-angle characterization.
4. Operationalized sharp-turn inflection velocity ratios (v_sharp / v_gentle).
5. Rigorous matched repeated-word within-session vs between-session motor style analysis (ANOVA / ICC,
   pairwise speed/duration residuals, and resampled spatial trajectory shape distances).

Terminology note: FUTO recordings are grouped by anonymous session UUIDs (`session`), representing
discrete data-collection sessions rather than verified personal typist identities.

Usage:
  uv run --with pyarrow --with numpy --with scipy python3 research/swipe-training/profile_corpus_kinematics.py [--sample-size N] [--all-shards]
"""

import argparse
import hashlib
import json
import math
import os
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple

import numpy as np
import pyarrow.parquet as pq

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_RAW_DIR = REPO_ROOT / "data" / "swipe" / "raw" / "futo"
OUTPUT_REPORT_PATH = REPO_ROOT / "research" / "swipe-training" / "corpus_kinematics_profile.json"
PROFILE_SEED = 20260820
EVENT_RESERVOIR_SIZE = 1_000_000
INSTANCES_PER_WORD_SESSION = 10
PAIRS_PER_WORD = 250


class DeterministicReservoir:
    """Algorithm R reservoir with an isolated, reproducible RNG stream."""

    def __init__(self, capacity: int, seed: int):
        self.capacity = capacity
        self.rng = np.random.default_rng(seed)
        self.values: List[float] = []
        self.seen = 0

    def extend(self, values) -> None:
        for value in values:
            self.seen += 1
            if len(self.values) < self.capacity:
                self.values.append(float(value))
            else:
                index = int(self.rng.integers(0, self.seen))
                if index < self.capacity:
                    self.values[index] = float(value)


def stable_priority(*parts: Any) -> int:
    payload = "\x1f".join(map(str, (PROFILE_SEED,) + parts)).encode()
    return int.from_bytes(hashlib.blake2b(payload, digest_size=8).digest(), "big")


def balanced_take(candidates: List[Tuple[int, Any]], limit: int) -> List[Any]:
    return [value for _, value in sorted(candidates, key=lambda item: item[0])[:limit]]


def compute_quantiles(arr: np.ndarray, q_list=(0, 1, 5, 10, 25, 50, 75, 90, 95, 99, 100)) -> Dict[str, float]:
    if len(arr) == 0:
        return {}
    vals = np.percentile(arr, q_list)
    return {
        "count": int(len(arr)),
        "mean": float(np.mean(arr)),
        "std": float(np.std(arr)),
        "min": float(vals[0]),
        "p01": float(vals[1]),
        "p05": float(vals[2]),
        "p10": float(vals[3]),
        "p25": float(vals[4]),
        "p50": float(vals[5]),
        "p75": float(vals[6]),
        "p90": float(vals[7]),
        "p95": float(vals[8]),
        "p99": float(vals[9]),
        "max": float(vals[10]),
    }


def compute_icc(groups: List[List[float]]) -> Tuple[Optional[float], Optional[float], Optional[float]]:
    """
    Compute One-Way Random Effects Intraclass Correlation Coefficient (ICC(1,1))
    for unbalanced group sizes (Satterthwaite approximation).
    Returns (icc, var_between_sessions, var_within_sessions).
    """
    valid_groups = [g for g in groups if len(g) >= 2]
    if len(valid_groups) < 3:
        return None, None, None
    
    k_list = [len(g) for g in valid_groups]
    N = sum(k_list)
    k = len(valid_groups)
    
    sum_k_sq = sum(s**2 for s in k_list)
    k_0 = (N - (sum_k_sq / N)) / (k - 1)
    if k_0 <= 0:
        return None, None, None
    
    grand_mean = sum(sum(g) for g in valid_groups) / N
    
    # Sum of squares between (SSB)
    ssb = sum(len(g) * (np.mean(g) - grand_mean)**2 for g in valid_groups)
    msb = ssb / (k - 1)
    
    # Sum of squares within (SSW)
    ssw = sum(sum((x - np.mean(g))**2 for x in g) for g in valid_groups)
    msw = ssw / max(N - k, 1)
    
    var_w = float(msw)
    var_b = float((msb - msw) / k_0)
    denominator = msb + (k_0 - 1.0) * msw
    icc = float((msb - msw) / denominator) if denominator > 0 else None
    return icc, var_b, var_w


def resample_trajectory(xs_px: np.ndarray, ys_px: np.ndarray, num_points: int = 32) -> np.ndarray:
    """Uniformly resample trajectory along arc length in aspect-corrected pixel space."""
    dx = np.diff(xs_px)
    dy = np.diff(ys_px)
    dists = np.sqrt(dx**2 + dy**2)
    cum_dist = np.concatenate(([0.0], np.cumsum(dists)))
    total_len = cum_dist[-1]
    if total_len < 1e-4:
        return np.column_stack([np.full(num_points, xs_px[0]), np.full(num_points, ys_px[0])])
    
    target_dists = np.linspace(0.0, total_len, num_points)
    resampled_x = np.interp(target_dists, cum_dist, xs_px)
    resampled_y = np.interp(target_dists, cum_dist, ys_px)
    return np.column_stack([resampled_x, resampled_y])


def analyze_single_trajectory(
    pts: List[Dict[str, Any]],
    canvas_w: float,
    canvas_h: float,
    word: str,
) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    """
    Kinematic analysis of a single continuous swipe trajectory.
    Returns (metrics_dict, exclusion_reason).
    """
    if not pts or len(pts) == 0:
        return None, "empty_data"
    if len(pts) < 3:
        return None, "insufficient_points (< 3)"

    raw_xs = np.array([p['x'] for p in pts], dtype=np.float64)
    raw_ys = np.array([p['y'] for p in pts], dtype=np.float64)
    raw_ts = np.array([p['t'] for p in pts], dtype=np.float64)

    raw_duration_ms = raw_ts[-1] - raw_ts[0]
    if raw_duration_ms <= 0:
        return None, "nonpositive_duration (<= 0ms)"

    # Aspect-corrected pixel dimensions
    w_px = float(canvas_w) if canvas_w and canvas_w > 0 else 406.0
    h_px = float(canvas_h) if canvas_h and canvas_h > 0 else 170.0
    aspect = w_px / h_px

    # Raw touch interval stats
    raw_dts = np.diff(raw_ts)
    zero_dt_count = int(np.sum(raw_dts == 0))
    neg_dt_count = int(np.sum(raw_dts < 0))
    pos_dt_count = int(np.sum(raw_dts > 0))

    # Collapse duplicate/zero-dt timestamp points for continuous physical kinematics
    # Group points by unique increasing timestamps
    clean_ts = []
    clean_xs_norm = []
    clean_ys_norm = []

    curr_t = raw_ts[0]
    curr_xs = [raw_xs[0]]
    curr_ys = [raw_ys[0]]

    for i in range(1, len(raw_ts)):
        t_i = raw_ts[i]
        if t_i == curr_t:
            curr_xs.append(raw_xs[i])
            curr_ys.append(raw_ys[i])
        else:
            clean_ts.append(curr_t)
            clean_xs_norm.append(float(np.mean(curr_xs)))
            clean_ys_norm.append(float(np.mean(curr_ys)))
            curr_t = t_i
            curr_xs = [raw_xs[i]]
            curr_ys = [raw_ys[i]]
    
    clean_ts.append(curr_t)
    clean_xs_norm.append(float(np.mean(curr_xs)))
    clean_ys_norm.append(float(np.mean(curr_ys)))

    clean_ts = np.array(clean_ts, dtype=np.float64)
    clean_xs_norm = np.array(clean_xs_norm, dtype=np.float64)
    clean_ys_norm = np.array(clean_ys_norm, dtype=np.float64)

    if len(clean_ts) < 3:
        return None, "insufficient_unique_timestamps (< 3)"

    clean_dts_ms = np.diff(clean_ts)
    if np.any(clean_dts_ms <= 0):
        return None, "non_monotonic_timestamps"

    # Physical coordinates in aspect-corrected pixel space
    clean_xs_px = clean_xs_norm * w_px
    clean_ys_px = clean_ys_norm * h_px

    # Physical displacement increments
    dx_px = np.diff(clean_xs_px)
    dy_px = np.diff(clean_ys_px)
    d_dist_px = np.sqrt(dx_px**2 + dy_px**2)
    total_dist_px = float(np.sum(d_dist_px))

    if total_dist_px < 2.0:
        return None, "zero_displacement (< 2px)"

    # Normalized displacement increments
    dx_norm = np.diff(clean_xs_norm)
    dy_norm = np.diff(clean_ys_norm)
    d_dist_norm = np.sqrt(dx_norm**2 + dy_norm**2)
    total_dist_norm = float(np.sum(d_dist_norm))

    # Kinematics: instantaneous velocities
    dt_s = clean_dts_ms / 1000.0
    v_px_per_s = d_dist_px / dt_s
    v_norm_per_s = d_dist_norm / dt_s

    mean_v_px = float(np.mean(v_px_per_s))
    max_v_px = float(np.max(v_px_per_s))
    mean_v_norm = float(np.mean(v_norm_per_s))
    max_v_norm = float(np.max(v_norm_per_s))

    # Acceleration in pixel space (px/s^2)
    if len(v_px_per_s) > 1:
        dt_mid_s = (dt_s[:-1] + dt_s[1:]) / 2.0
        accel_px = np.diff(v_px_per_s) / dt_mid_s
        mean_accel_px = float(np.mean(np.abs(accel_px)))
        max_accel_px = float(np.max(np.abs(accel_px)))
    else:
        mean_accel_px = 0.0
        max_accel_px = 0.0

    # Aspect-Correct Physical Curvature and Sharp-Turn Velocity Ratio
    turn_angles_deg = []
    sharp_turn_v_ratios = []

    if len(dx_px) >= 2:
        v1_x, v1_y = dx_px[:-1], dy_px[:-1]
        v2_x, v2_y = dx_px[1:], dy_px[1:]
        
        norms1 = np.sqrt(v1_x**2 + v1_y**2)
        norms2 = np.sqrt(v2_x**2 + v2_y**2)
        
        valid_vecs = (norms1 > 1e-3) & (norms2 > 1e-3)
        if np.any(valid_vecs):
            dots = (v1_x[valid_vecs] * v2_x[valid_vecs] + v1_y[valid_vecs] * v2_y[valid_vecs]) / (norms1[valid_vecs] * norms2[valid_vecs])
            dots = np.clip(dots, -1.0, 1.0)
            angles_rad = np.arccos(dots)
            turn_angles_deg = np.degrees(angles_rad).tolist()

            # Sharp turn (> 45 deg) vs Gentle segment (< 20 deg)
            sharp_mask = np.degrees(angles_rad) > 45.0
            gentle_mask = np.degrees(angles_rad) < 20.0
            
            v_mid = (v_px_per_s[:-1][valid_vecs] + v_px_per_s[1:][valid_vecs]) / 2.0
            if np.any(sharp_mask) and np.any(gentle_mask):
                v_sharp = float(np.mean(v_mid[sharp_mask]))
                v_gentle = float(np.mean(v_mid[gentle_mask]))
                if v_gentle > 0:
                    sharp_turn_v_ratios.append(v_sharp / v_gentle)

    # Path tortuosity is only stable when endpoint displacement is material.
    net_dx_px = clean_xs_px[-1] - clean_xs_px[0]
    net_dy_px = clean_ys_px[-1] - clean_ys_px[0]
    net_dist_px = math.sqrt(net_dx_px**2 + net_dy_px**2)
    endpoint_displacement_norm = math.sqrt(
        (clean_xs_norm[-1] - clean_xs_norm[0])**2
        + (clean_ys_norm[-1] - clean_ys_norm[0])**2
    )
    tortuosity = total_dist_px / net_dist_px if endpoint_displacement_norm >= 0.05 else None

    # Resampled 32-point spatial representation for repeated-word shape matching
    resampled_32 = resample_trajectory(clean_xs_px, clean_ys_px, num_points=32)
    resampled_32_norm = resample_trajectory(clean_xs_norm, clean_ys_norm, num_points=32)

    word_len = len(word) if word else 1

    metrics = {
        "word": word,
        "word_len": word_len,
        "raw_points_count": len(pts),
        "clean_points_count": len(clean_ts),
        "duration_ms": raw_duration_ms,
        "duration_per_char_ms": raw_duration_ms / max(word_len, 1),
        "raw_dts": raw_dts.tolist(),
        "zero_dt_count": zero_dt_count,
        "neg_dt_count": neg_dt_count,
        "pos_dt_count": pos_dt_count,
        "total_dist_norm": total_dist_norm,
        "total_dist_px": total_dist_px,
        "mean_v_norm": mean_v_norm,
        "max_v_norm": max_v_norm,
        "mean_v_px": mean_v_px,
        "max_v_px": max_v_px,
        "mean_accel_px": mean_accel_px,
        "max_accel_px": max_accel_px,
        "turn_angles_deg": turn_angles_deg,
        "sharp_turn_v_ratios": sharp_turn_v_ratios,
        "tortuosity": tortuosity,
        "endpoint_displacement_norm": endpoint_displacement_norm,
        "x_min": float(np.min(clean_xs_norm)),
        "x_max": float(np.max(clean_xs_norm)),
        "y_min": float(np.min(clean_ys_norm)),
        "y_max": float(np.max(clean_ys_norm)),
        "canvas_w": w_px,
        "canvas_h": h_px,
        "aspect_ratio": aspect,
        "resampled_32": resampled_32,
        "resampled_32_norm": resampled_32_norm,
    }
    return metrics, None


def profile_corpus(
    raw_dir: Path,
    sample_size: Optional[int] = None,
    all_shards: bool = True,
) -> Dict[str, Any]:
    """Execute complete characterization across all canonical parquet shards."""
    print("=" * 80)
    print("      OMNIBOARD CANONICAL FUTO SWIPE CORPUS CHARACTERIZATION PASS       ")
    print("=" * 80)
    print(f"Raw Directory: {raw_dir}")
    print(f"Mode:          {'FULL CORPUS (All 1.22M Records)' if all_shards or sample_size is None else f'Sample limit {sample_size:,}'}")

    shards_to_process = []
    for run_dir in sorted(raw_dir.glob("swipe-*")):
        for split_dir in sorted(run_dir.glob("*")):
            for pfile in sorted(split_dir.glob("*.parquet")):
                shards_to_process.append((run_dir.name, split_dir.name, pfile))

    print(f"Discovered {len(shards_to_process)} canonical parquet shards.\n")

    # Accounting structures
    run_split_accounting = {}
    total_scanned = 0
    total_valid = 0
    total_excluded = 0
    exclusion_reasons = defaultdict(int)

    # Event streams use true deterministic reservoirs. Per-swipe arrays remain exact.
    raw_dt_reservoir = DeterministicReservoir(EVENT_RESERVOIR_SIZE, PROFILE_SEED + 1)
    turn_angle_reservoir = DeterministicReservoir(EVENT_RESERVOIR_SIZE, PROFILE_SEED + 2)
    all_durations = []
    all_duration_per_char = []
    all_raw_pts_counts = []
    all_clean_pts_counts = []
    all_pts_per_char = []
    
    all_dist_norm = []
    all_dist_px = []
    all_mean_v_norm = []
    all_max_v_norm = []
    all_mean_v_px = []
    all_max_v_px = []
    
    all_sharp_turn_v_ratios = []
    all_tortuosity = []
    tortuosity_undefined_count = 0
    all_endpoint_displacement_norm = []
    
    all_canvas_w = []
    all_canvas_h = []
    all_aspects = []
    all_x_mins = []
    all_x_maxs = []
    all_y_mins = []
    all_y_maxs = []

    # Timestamp event counters across corpus
    total_touch_intervals = 0
    total_zero_dt_events = 0
    total_neg_dt_events = 0
    total_pos_dt_events = 0

    # Word length breakdown
    by_word_len_duration = defaultdict(list)
    by_word_len_pts = defaultdict(list)
    by_word_len_dist_px = defaultdict(list)
    by_word_len_v_px = defaultdict(list)

    # Session tracking & matched repeated-word indexing
    # We index top frequent words across sessions to perform rigorous repeated-word within/between session analysis
    # word -> session -> list of {duration_ms, mean_v_px, resampled_32}
    repeated_word_index = defaultdict(lambda: defaultdict(list))
    session_swipe_counts = defaultdict(int)
    session_mean_v_px = defaultdict(list)

    start_time = time.time()

    for shard_idx, (run_name, split_name, shard_path) in enumerate(shards_to_process):
        rel_shard = shard_path.relative_to(raw_dir)
        key = f"{run_name}/{split_name}/{shard_path.name}"
        print(f"[{shard_idx+1:2d}/{len(shards_to_process)}] Processing {rel_shard}...")
        
        pf = pq.ParquetFile(str(shard_path))
        num_rg = pf.metadata.num_row_groups
        shard_rows = pf.metadata.num_rows

        shard_valid = 0
        shard_excluded = 0
        shard_reasons = defaultdict(int)

        for rg_idx in range(num_rg):
            cols_to_read = [
                "word", "canvas_width", "canvas_height", "session", "data",
                "orientation", "layout", "language",
            ]
            schema_names = pf.schema_arrow.names
            cols = [c for c in cols_to_read if c in schema_names]
            
            table = pf.read_row_group(rg_idx, columns=cols)
            pylist = table.to_pylist()

            for row in pylist:
                total_scanned += 1
                raw_data = row.get("data")
                
                if raw_data is None:
                    shard_excluded += 1
                    total_excluded += 1
                    exclusion_reasons["null_data"] += 1
                    shard_reasons["null_data"] += 1
                    continue

                if isinstance(raw_data, str):
                    try:
                        pts = json.loads(raw_data)
                    except Exception:
                        shard_excluded += 1
                        total_excluded += 1
                        exclusion_reasons["unparseable_json"] += 1
                        shard_reasons["unparseable_json"] += 1
                        continue
                else:
                    pts = raw_data

                # Check dual finger format (common in swipe-5)
                if isinstance(pts, dict):
                    shard_excluded += 1
                    total_excluded += 1
                    exclusion_reasons["dual_finger_format"] += 1
                    shard_reasons["dual_finger_format"] += 1
                    continue

                word = str(row.get("word", "")).strip().lower()
                cw = row.get("canvas_width")
                ch = row.get("canvas_height")
                sess = row.get("session", "unknown")

                # Preserve raw event timing evidence even when the trajectory is
                # subsequently excluded from physical kinematics.
                if isinstance(pts, list) and len(pts) >= 2 and all("t" in p for p in pts):
                    record_raw_dts = np.diff(np.array([p["t"] for p in pts], dtype=np.float64))
                    total_touch_intervals += len(record_raw_dts)
                    total_zero_dt_events += int(np.sum(record_raw_dts == 0))
                    total_neg_dt_events += int(np.sum(record_raw_dts < 0))
                    total_pos_dt_events += int(np.sum(record_raw_dts > 0))
                    raw_dt_reservoir.extend(record_raw_dts)

                metrics, reason = analyze_single_trajectory(pts, cw, ch, word)
                if reason:
                    shard_excluded += 1
                    total_excluded += 1
                    exclusion_reasons[reason] += 1
                    shard_reasons[reason] += 1
                    continue

                shard_valid += 1
                total_valid += 1

                all_durations.append(metrics["duration_ms"])
                all_duration_per_char.append(metrics["duration_per_char_ms"])
                all_raw_pts_counts.append(metrics["raw_points_count"])
                all_clean_pts_counts.append(metrics["clean_points_count"])
                all_pts_per_char.append(metrics["clean_points_count"] / max(metrics["word_len"], 1))

                all_dist_norm.append(metrics["total_dist_norm"])
                all_dist_px.append(metrics["total_dist_px"])
                all_mean_v_norm.append(metrics["mean_v_norm"])
                all_max_v_norm.append(metrics["max_v_norm"])
                all_mean_v_px.append(metrics["mean_v_px"])
                all_max_v_px.append(metrics["max_v_px"])

                turn_angle_reservoir.extend(metrics["turn_angles_deg"])
                all_sharp_turn_v_ratios.extend(metrics["sharp_turn_v_ratios"])
                all_endpoint_displacement_norm.append(metrics["endpoint_displacement_norm"])
                if metrics["tortuosity"] is None:
                    tortuosity_undefined_count += 1
                else:
                    all_tortuosity.append(metrics["tortuosity"])

                all_canvas_w.append(metrics["canvas_w"])
                all_canvas_h.append(metrics["canvas_h"])
                all_aspects.append(metrics["aspect_ratio"])
                all_x_mins.append(metrics["x_min"])
                all_x_maxs.append(metrics["x_max"])
                all_y_mins.append(metrics["y_min"])
                all_y_maxs.append(metrics["y_max"])

                # Word length table (1 to 15 chars)
                w_len = metrics["word_len"]
                if w_len <= 15:
                    by_word_len_duration[w_len].append(metrics["duration_ms"])
                    by_word_len_pts[w_len].append(metrics["clean_points_count"])
                    by_word_len_dist_px[w_len].append(metrics["total_dist_px"])
                    by_word_len_v_px[w_len].append(metrics["mean_v_px"])

                # Session tracking
                session_swipe_counts[sess] += 1
                session_mean_v_px[sess].append(metrics["mean_v_px"])

                # Repeated word indexing for matched within vs between session analysis
                # Keep index for words that have reasonable frequency (len >= 2)
                if 2 <= len(word) <= 12:
                    instance = {
                        "duration_ms": metrics["duration_ms"],
                        "mean_v_px": metrics["mean_v_px"],
                        "mean_v_norm": metrics["mean_v_norm"],
                        "resampled_32": metrics["resampled_32"],
                        "resampled_32_norm": metrics["resampled_32_norm"],
                        "geometry": (
                            metrics["canvas_w"], metrics["canvas_h"],
                            str(row.get("orientation", "unknown")),
                            str(row.get("layout", "unknown")),
                            str(row.get("language", "unknown")), run_name,
                        ),
                        "priority": stable_priority(word, sess, run_name, split_name, total_scanned),
                    }
                    bucket = repeated_word_index[word][sess]
                    bucket.append(instance)
                    bucket.sort(key=lambda item: item["priority"])
                    del bucket[INSTANCES_PER_WORD_SESSION:]

                if sample_size and not all_shards and total_valid >= sample_size:
                    break
            if sample_size and not all_shards and total_valid >= sample_size:
                break

        run_split_accounting[key] = {
            "run": run_name,
            "split": split_name,
            "filename": shard_path.name,
            "total_rows": shard_rows,
            "valid_swipes": shard_valid,
            "excluded_rows": shard_excluded,
            "exclusion_reasons": dict(shard_reasons),
        }
        print(f"    ✓ Valid: {shard_valid:,} | Excluded: {shard_excluded:,} ({dict(shard_reasons)})")

        if sample_size and not all_shards and total_valid >= sample_size:
            break

    elapsed = time.time() - start_time
    print(f"\nCorpus Scan Complete in {elapsed:.2f}s!")
    print(f"Total Rows Scanned:  {total_scanned:,}")
    print(f"Valid Swipes:        {total_valid:,} ({total_valid/total_scanned*100:.2f}%)")
    print(f"Excluded Rows:       {total_excluded:,} ({total_excluded/total_scanned*100:.2f}%)")
    print(f"Exclusion Breakdown: {dict(exclusion_reasons)}")

    # ---------------------------------------------------------
    # Matched Repeated-Word Within vs Between Session Analysis
    # ---------------------------------------------------------
    print("\nComputing matched repeated-word within-session vs between-session motor style statistics...")
    
    # Words are equal-weight strata. Within each word, sessions and pairs are
    # selected by stable hash priority rather than insertion order.
    matched_words_evaluated = 0
    icc_velocity_list = []
    icc_velocity_norm_list = []
    icc_duration_list = []
    pair_metrics = defaultdict(list)

    for word in sorted(repeated_word_index):
        session_dict = repeated_word_index[word]
        multi_swipe_sessions = sorted(sess for sess, insts in session_dict.items() if len(insts) >= 2)
        if len(multi_swipe_sessions) < 3 or len(session_dict) < 5:
            continue
        matched_words_evaluated += 1
        v_groups = [[item["mean_v_px"] for item in insts] for insts in session_dict.values()]
        icc_v, _, _ = compute_icc(v_groups)
        if icc_v is not None:
            icc_velocity_list.append(icc_v)
        vn_groups = [[item["mean_v_norm"] for item in insts] for insts in session_dict.values()]
        icc_vn, _, _ = compute_icc(vn_groups)
        if icc_vn is not None:
            icc_velocity_norm_list.append(icc_vn)
        dur_groups = [[item["duration_ms"] for item in insts] for insts in session_dict.values()]
        icc_d, _, _ = compute_icc(dur_groups)
        if icc_d is not None:
            icc_duration_list.append(icc_d)

        within_candidates = []
        for sess in multi_swipe_sessions:
            insts = session_dict[sess]
            for i in range(len(insts)):
                for j in range(i + 1, len(insts)):
                    a, b = insts[i], insts[j]
                    values = (
                        abs(a["mean_v_px"] - b["mean_v_px"]),
                        abs(a["mean_v_norm"] - b["mean_v_norm"]),
                        abs(a["duration_ms"] - b["duration_ms"]),
                        float(np.mean(np.linalg.norm(a["resampled_32"] - b["resampled_32"], axis=1))),
                        float(np.mean(np.linalg.norm(a["resampled_32_norm"] - b["resampled_32_norm"], axis=1))),
                        a["geometry"] == b["geometry"],
                    )
                    within_candidates.append((stable_priority(word, "within", sess, i, j), values))

        selected_sessions = sorted(
            session_dict,
            key=lambda sess: stable_priority(word, "session", sess),
        )[:40]
        between_candidates = []
        for s1_idx, s1 in enumerate(selected_sessions):
            for s2 in selected_sessions[s1_idx + 1:]:
                for i, inst1 in enumerate(session_dict[s1]):
                    for j, inst2 in enumerate(session_dict[s2]):
                        values = (
                            abs(inst1["mean_v_px"] - inst2["mean_v_px"]),
                            abs(inst1["mean_v_norm"] - inst2["mean_v_norm"]),
                            abs(inst1["duration_ms"] - inst2["duration_ms"]),
                            float(np.mean(np.linalg.norm(inst1["resampled_32"] - inst2["resampled_32"], axis=1))),
                            float(np.mean(np.linalg.norm(inst1["resampled_32_norm"] - inst2["resampled_32_norm"], axis=1))),
                            inst1["geometry"] == inst2["geometry"],
                        )
                        between_candidates.append((stable_priority(word, "between", s1, s2, i, j), values))

        for label, candidates in (("within", within_candidates), ("between", between_candidates)):
            for speed_px, speed_norm, duration, shape_px, shape_norm, geometry_match in balanced_take(candidates, PAIRS_PER_WORD):
                pair_metrics[f"{label}_speed_px"].append(speed_px)
                pair_metrics[f"{label}_speed_norm"].append(speed_norm)
                pair_metrics[f"{label}_duration"].append(duration)
                pair_metrics[f"{label}_shape_px"].append(shape_px)
                pair_metrics[f"{label}_shape_norm"].append(shape_norm)
                if geometry_match:
                    pair_metrics[f"{label}_speed_px_geometry_matched"].append(speed_px)
                    pair_metrics[f"{label}_shape_px_geometry_matched"].append(shape_px)

    # ---------------------------------------------------------
    # Empirical Distribution Aggregation
    # ---------------------------------------------------------
    print("Computing empirical distributions and quantiles...")

    raw_dts_np = np.array(raw_dt_reservoir.values, dtype=np.float64)
    pos_dts = raw_dts_np[raw_dts_np > 0]
    observed_dispatch_rates = 1000.0 / pos_dts

    # A conservative, distribution-derived analysis flag: three IQRs outside
    # the quartiles in log-duration space. Raw observations remain reported.
    duration_np = np.array(all_durations, dtype=np.float64)
    log_duration = np.log(duration_np)
    log_q1, log_q3 = np.percentile(log_duration, [25, 75])
    log_iqr = log_q3 - log_q1
    duration_lower = float(np.exp(log_q1 - 3.0 * log_iqr))
    duration_upper = float(np.exp(log_q3 + 3.0 * log_iqr))
    duration_analysis_mask = (duration_np >= duration_lower) & (duration_np <= duration_upper)

    # Linear regression: Duration vs Word Length
    word_lens = []
    durations = []
    for w_len, durs in by_word_len_duration.items():
        valid_durs = [d for d in durs if duration_lower <= d <= duration_upper]
        word_lens.extend([w_len] * len(valid_durs))
        durations.extend(valid_durs)
    
    if len(word_lens) > 0:
        poly = np.polyfit(word_lens, durations, 1)
        slope_ms_per_char, intercept_ms = float(poly[0]), float(poly[1])
        corr = np.corrcoef(word_lens, durations)[0, 1]
        r_squared = float(corr**2)
    else:
        slope_ms_per_char, intercept_ms, r_squared = 0.0, 0.0, 0.0

    # Session macro stats
    sess_avg_velocities = [float(np.mean(vlist)) for vlist in session_mean_v_px.values() if len(vlist) >= 10]
    sess_std_velocities = [float(np.std(vlist)) for vlist in session_mean_v_px.values() if len(vlist) >= 10]

    # Word length summary table
    word_len_summary = {}
    for l in sorted(by_word_len_duration.keys()):
        durs = np.array(by_word_len_duration[l])
        pts = np.array(by_word_len_pts[l])
        d_px = np.array(by_word_len_dist_px[l])
        v_px = np.array(by_word_len_v_px[l])
        word_len_summary[str(l)] = {
            "count": int(len(durs)),
            "duration_ms_mean": float(np.mean(durs)),
            "duration_ms_median": float(np.median(durs)),
            "duration_ms_std": float(np.std(durs)),
            "pts_mean": float(np.mean(pts)),
            "pts_median": float(np.median(pts)),
            "dist_px_mean": float(np.mean(d_px)),
            "velocity_px_mean": float(np.mean(v_px)),
        }

    # Compile Final Canonical Report
    report = {
        "characterization_metadata": {
            "report_schema_version": 2,
            "dataset_name": "futo-org/swipe.futo.org",
            "total_scanned_rows": total_scanned,
            "total_valid_single_finger_trajectories": total_valid,
            "total_excluded_rows": total_excluded,
            "exclusion_rate_pct": round((total_excluded / max(total_scanned, 1)) * 100, 2),
            "shards_profiled": len(shards_to_process),
            "profile_seed": PROFILE_SEED,
            "event_reservoir_capacity": EVENT_RESERVOIR_SIZE,
            "event_reservoir_method": "Algorithm R with independent seeded NumPy RNG streams",
            "pair_sampling": f"equal cap of {PAIRS_PER_WORD} stable-hash-priority pairs per target word",
            "terminology_note": "FUTO records are grouped by anonymous session UUIDs representing collection contexts.",
        },
        "per_run_and_split_accounting": run_split_accounting,
        "exclusion_reasons_summary": dict(exclusion_reasons),
        "touch_event_temporal_dynamics": {
            "touch_intervals_count": total_touch_intervals,
            "zero_dt_count": total_zero_dt_events,
            "zero_dt_pct": round((total_zero_dt_events / max(total_touch_intervals, 1)) * 100, 3),
            "negative_dt_count": total_neg_dt_events,
            "positive_dt_count": total_pos_dt_events,
            "positive_dt_pct": round((total_pos_dt_events / max(total_touch_intervals, 1)) * 100, 3),
            "raw_dt_intervals_ms": compute_quantiles(raw_dts_np),
            "observed_event_dispatch_rate_hz": compute_quantiles(observed_dispatch_rates),
            "duration_ms_raw": compute_quantiles(duration_np),
            "duration_ms_analysis_valid": compute_quantiles(duration_np[duration_analysis_mask]),
            "duration_quality_flag": {
                "method": "outside 3 IQRs from Q1/Q3 in log-duration space",
                "lower_bound_ms": duration_lower,
                "upper_bound_ms": duration_upper,
                "flagged_count": int(np.sum(~duration_analysis_mask)),
                "retained_count": int(np.sum(duration_analysis_mask)),
            },
            "duration_per_char_ms": compute_quantiles(np.array(all_duration_per_char)),
            "regression_duration_vs_length": {
                "slope_ms_per_char": round(slope_ms_per_char, 2),
                "intercept_ms": round(intercept_ms, 2),
                "r_squared": round(r_squared, 4),
                "formula": f"Descriptive duration fit: {slope_ms_per_char:.1f} * character_count + {intercept_ms:.1f} ms",
                "analysis_subset": "duration_quality_flag retained rows; character count is descriptive, not physical path geometry",
            },
        },
        "point_density_and_path_geometry": {
            "raw_points_per_swipe": compute_quantiles(np.array(all_raw_pts_counts)),
            "clean_points_per_swipe": compute_quantiles(np.array(all_clean_pts_counts)),
            "clean_points_per_character": compute_quantiles(np.array(all_pts_per_char)),
            "total_distance_normalized": compute_quantiles(np.array(all_dist_norm)),
            "total_distance_pixels": compute_quantiles(np.array(all_dist_px)),
            "path_tortuosity_ratio": compute_quantiles(np.array(all_tortuosity)),
            "tortuosity_definition": "path length / endpoint distance, reported only when normalized endpoint displacement >= 0.05",
            "tortuosity_undefined_close_endpoints": tortuosity_undefined_count,
            "endpoint_displacement_normalized": compute_quantiles(np.array(all_endpoint_displacement_norm)),
        },
        "aspect_correct_kinematics": {
            "mean_velocity_pixels_per_s": compute_quantiles(np.array(all_mean_v_px)),
            "max_velocity_pixels_per_s": compute_quantiles(np.array(all_max_v_px)),
            "mean_velocity_normalized_per_s": compute_quantiles(np.array(all_mean_v_norm)),
            "max_velocity_normalized_per_s": compute_quantiles(np.array(all_max_v_norm)),
            "derivative_caveat": (
                "Acceleration and jerk are intentionally not aggregated: numerical derivatives on "
                "irregular native event intervals are unstable without a declared derived-data "
                "resampling/filtering policy. No 1 ms interval substitution is used."
            ),
        },
        "aspect_correct_curvature_and_inflections": {
            "turning_angle_deg": compute_quantiles(np.array(turn_angle_reservoir.values)),
            "sharp_turn_velocity_ratio": {
                "definition": "Ratio of mean velocity during sharp direction changes (> 45 deg) to gentle transit (< 20 deg) within the same trajectory: v_sharp / v_gentle.",
                "quantiles": compute_quantiles(np.array(all_sharp_turn_v_ratios)) if all_sharp_turn_v_ratios else {},
            },
        },
        "display_geometry_and_bounds": {
            "canvas_width_px": compute_quantiles(np.array(all_canvas_w)),
            "canvas_height_px": compute_quantiles(np.array(all_canvas_h)),
            "aspect_ratio_w_over_h": compute_quantiles(np.array(all_aspects)),
            "x_min_bound": compute_quantiles(np.array(all_x_mins)),
            "x_max_bound": compute_quantiles(np.array(all_x_maxs)),
            "y_min_bound": compute_quantiles(np.array(all_y_mins)),
            "y_max_bound": compute_quantiles(np.array(all_y_maxs)),
        },
        "matched_repeated_word_session_analysis": {
            "matched_words_evaluated": matched_words_evaluated,
            "intraclass_correlation_coefficients": {
                "method": "ICC(1,1), one-way random effects, absolute agreement; unbalanced-group effective size k0",
                "interpretation": "descriptive session clustering for repeated observations of the same word; not human identity evidence",
                "icc_velocity_distribution": compute_quantiles(np.array(icc_velocity_list)) if icc_velocity_list else {},
                "icc_normalized_velocity_distribution": compute_quantiles(np.array(icc_velocity_norm_list)) if icc_velocity_norm_list else {},
                "icc_duration_distribution": compute_quantiles(np.array(icc_duration_list)) if icc_duration_list else {},
            },
            "pairwise_speed_difference_px_per_s": {
                "within_session_same_word": compute_quantiles(np.array(pair_metrics["within_speed_px"])),
                "between_session_same_word": compute_quantiles(np.array(pair_metrics["between_speed_px"])),
                "within_session_same_word_geometry_matched": compute_quantiles(np.array(pair_metrics["within_speed_px_geometry_matched"])),
                "between_session_same_word_geometry_matched": compute_quantiles(np.array(pair_metrics["between_speed_px_geometry_matched"])),
            },
            "pairwise_speed_difference_normalized_per_s": {
                "within_session_same_word": compute_quantiles(np.array(pair_metrics["within_speed_norm"])),
                "between_session_same_word": compute_quantiles(np.array(pair_metrics["between_speed_norm"])),
            },
            "pairwise_duration_difference_ms": {
                "within_session_same_word": compute_quantiles(np.array(pair_metrics["within_duration"])),
                "between_session_same_word": compute_quantiles(np.array(pair_metrics["between_duration"])),
            },
            "pairwise_resampled_shape_distance_px": {
                "within_session_same_word": compute_quantiles(np.array(pair_metrics["within_shape_px"])),
                "between_session_same_word": compute_quantiles(np.array(pair_metrics["between_shape_px"])),
                "within_session_same_word_geometry_matched": compute_quantiles(np.array(pair_metrics["within_shape_px_geometry_matched"])),
                "between_session_same_word_geometry_matched": compute_quantiles(np.array(pair_metrics["between_shape_px_geometry_matched"])),
            },
            "pairwise_resampled_shape_distance_normalized": {
                "within_session_same_word": compute_quantiles(np.array(pair_metrics["within_shape_norm"])),
                "between_session_same_word": compute_quantiles(np.array(pair_metrics["between_shape_norm"])),
            },
            "verdict": (
                "Repeated observations cluster by anonymous session after matching target words. "
                "Normalized-coordinate and exact-geometry comparisons reduce, but cannot eliminate, "
                "environment and collection-context confounding. This does not establish human identity "
                "or require session conditioning in a generator."
            ),
        },
        "session_level_macro_variation": {
            "unique_anonymous_sessions": len(session_swipe_counts),
            "sessions_with_ge_10_swipes": len(sess_avg_velocities),
            "inter_session_mean_velocity_px_per_s": compute_quantiles(np.array(sess_avg_velocities)) if sess_avg_velocities else {},
            "intra_session_velocity_std_px_per_s": compute_quantiles(np.array(sess_std_velocities)) if sess_std_velocities else {},
        },
        "scaling_by_word_length": word_len_summary,
    }

    # Save to canonical JSON location
    with open(OUTPUT_REPORT_PATH, "w") as f:
        json.dump(report, f, indent=2)

    print(f"\nCanonical Characterization JSON written to: {OUTPUT_REPORT_PATH.relative_to(REPO_ROOT)}")

    # Print Clean Formatted Console Summary
    print_console_summary(report)

    return report


def print_console_summary(r: Dict[str, Any]):
    print("\n" + "=" * 80)
    print("      OMNIBOARD CANONICAL FUTO SWIPE CORPUS CHARACTERIZATION SUMMARY     ")
    print("=" * 80)
    
    meta = r["characterization_metadata"]
    print(f"Scanned: {meta['total_scanned_rows']:,} rows | Valid Single-Finger Trajectories: {meta['total_valid_single_finger_trajectories']:,} | Excluded: {meta['total_excluded_rows']:,} ({meta['exclusion_rate_pct']}%)")
    
    print("\n--- 1. EXCLUSION ACCOUNTING ACROSS RUNS & SPLITS ---")
    print(f" {'Run / Split':<30} | {'Total Rows':<11} | {'Valid Swipes':<13} | {'Excluded':<10} | {'Top Reason'}")
    print(f" {'-'*30}-+-{'-'*11}-+-{'-'*13}-+-{'-'*10}-+-{'-'*20}")
    for k, v in sorted(r["per_run_and_split_accounting"].items()):
        top_reason = max(v["exclusion_reasons"].items(), key=lambda x: x[1])[0] if v["exclusion_reasons"] else "none"
        print(f" {k:<30} | {v['total_rows']:<11,} | {v['valid_swipes']:<13,} | {v['excluded_rows']:<10,} | {top_reason}")

    temp = r["touch_event_temporal_dynamics"]
    print("\n--- 2. TOUCH EVENT TEMPORAL DYNAMICS & TIMESTAMPS ---")
    print(f"Total Touch Intervals:   {temp['touch_intervals_count']:,} (Positive: {temp['positive_dt_pct']}%, Duplicate Zero-dt: {temp['zero_dt_pct']}%, Negative: {temp['negative_dt_count']})")
    dt = temp["raw_dt_intervals_ms"]
    hz = temp["observed_event_dispatch_rate_hz"]
    print(f"Raw Touch Event Δt:      median = {dt['p50']:.1f}ms | mean = {dt['mean']:.1f}ms (std = {dt['std']:.1f}ms) | p5={dt['p05']:.1f}ms, p95={dt['p95']:.1f}ms")
    print(f"Observed Dispatch Rate:  median = {hz['p50']:.1f} events/s | p25 = {hz['p25']:.1f} | p75 = {hz['p75']:.1f} events/s")
    
    dur = temp["duration_ms_analysis_valid"]
    dpc = temp["duration_per_char_ms"]
    reg = temp["regression_duration_vs_length"]
    print(f"Gesture Duration:        median = {dur['p50']:.0f}ms | mean = {dur['mean']:.0f}ms | p5={dur['p05']:.0f}ms, p95={dur['p95']:.0f}ms")
    print(f"Duration per Character:  median = {dpc['p50']:.0f}ms/char | mean = {dpc['mean']:.0f}ms/char")
    print(f"Descriptive Length Fit:  {reg['formula']} (R² = {reg['r_squared']:.3f})")

    pts = r["point_density_and_path_geometry"]
    print("\n--- 3. POINT DENSITY & PATH TORTUOSITY ---")
    clean_pts = pts["clean_points_per_swipe"]
    ppc = pts["clean_points_per_character"]
    tort = pts["path_tortuosity_ratio"]
    print(f"Points per Swipe:        median = {clean_pts['p50']:.0f} | mean = {clean_pts['mean']:.1f} | p5={clean_pts['p05']:.0f}, p95={clean_pts['p95']:.0f}")
    print(f"Points per Character:    median = {ppc['p50']:.1f} | mean = {ppc['mean']:.1f} | p5={ppc['p05']:.1f}, p95={ppc['p95']:.1f}")
    print(f"Path Tortuosity (S/D):   median = {tort['p50']:.2f}x | mean = {tort['mean']:.2f}x | p90 = {tort['p90']:.2f}x Euclidean")

    kin = r["aspect_correct_kinematics"]
    print("\n--- 4. ASPECT-CORRECT KINEMATICS & INFLECTION RATIOS ---")
    v_px = kin["mean_velocity_pixels_per_s"]
    v_max = kin["max_velocity_pixels_per_s"]
    print(f"Mean Swipe Speed:        {v_px['p50']:.0f} px/s (mean: {v_px['mean']:.0f} px/s, std: {v_px['std']:.0f} px/s) [p10: {v_px['p10']:.0f} px/s, p90: {v_px['p90']:.0f} px/s]")
    print(f"Peak Swipe Speed:        {v_max['p50']:.0f} px/s (p95: {v_max['p95']:.0f} px/s)")
    
    curv = r["aspect_correct_curvature_and_inflections"]
    angles = curv["turning_angle_deg"]
    stvr = curv["sharp_turn_velocity_ratio"]["quantiles"]
    print(f"True Turning Angles:     median = {angles['p50']:.1f}° | p75 = {angles['p75']:.1f}° | p95 = {angles['p95']:.1f}°")
    if stvr:
        print(f"High-Curvature Speed Ratio: median = {stvr['p50']:.2f} | mean = {stvr['mean']:.2f} (v_high-curvature / v_low-curvature)")

    disp = r["display_geometry_and_bounds"]
    print("\n--- 5. DISPLAY GEOMETRY & ACTIVE COORDINATES ---")
    cw = disp["canvas_width_px"]
    ch = disp["canvas_height_px"]
    asp = disp["aspect_ratio_w_over_h"]
    print(f"Canvas Dimensions:       Width = {cw['p50']:.0f}px (mean {cw['mean']:.0f}px) | Height = {ch['p50']:.0f}px (mean {ch['mean']:.0f}px)")
    print(f"Aspect Ratio (W/H):      median = {asp['p50']:.2f} | mean = {asp['mean']:.2f} | p10 = {asp['p10']:.2f}, p90 = {asp['p90']:.2f}")
    print(f"Coord Bounds:            x in [{disp['x_min_bound']['p05']:.2f}, {disp['x_max_bound']['p95']:.2f}], y in [{disp['y_min_bound']['p05']:.2f}, {disp['y_max_bound']['p95']:.2f}]")

    mat = r["matched_repeated_word_session_analysis"]
    print("\n--- 6. MATCHED REPEATED-WORD WITHIN- VS BETWEEN-SESSION ANALYSIS ---")
    icc_v = mat["intraclass_correlation_coefficients"]["icc_velocity_distribution"]
    icc_d = mat["intraclass_correlation_coefficients"]["icc_duration_distribution"]
    spd_w = mat["pairwise_speed_difference_px_per_s"]["within_session_same_word"]
    spd_b = mat["pairwise_speed_difference_px_per_s"]["between_session_same_word"]
    shp_w = mat["pairwise_resampled_shape_distance_px"]["within_session_same_word"]
    shp_b = mat["pairwise_resampled_shape_distance_px"]["between_session_same_word"]
    print(f"Matched Words Evaluated: {mat['matched_words_evaluated']:,} vocabulary words with repeated multi-session instances")
    if icc_v and icc_d:
        print(f"Velocity ICC (Motor Consistency): median ICC = {icc_v['p50']:.3f} | mean = {icc_v['mean']:.3f} (p75 = {icc_v['p75']:.3f})")
        print(f"Duration ICC (Rhythm Consistency): median ICC = {icc_d['p50']:.3f} | mean = {icc_d['mean']:.3f} (p75 = {icc_d['p75']:.3f})")
    if spd_w and spd_b:
        print(f"Pairwise Speed Δ |v1 - v2|:  Within-Session = {spd_w['p50']:.0f} px/s vs Between-Session = {spd_b['p50']:.0f} px/s ({((spd_b['p50']-spd_w['p50'])/spd_w['p50'])*100:+.1f}% spread between sessions!)")
    if shp_w and shp_b:
        print(f"Pairwise Shape Dist (32-pt): Within-Session = {shp_w['p50']:.1f}px vs Between-Session = {shp_b['p50']:.1f}px ({((shp_b['p50']-shp_w['p50'])/shp_w['p50'])*100:+.1f}% divergence between sessions)")
    print(f"Statistical Verdict:     {mat['verdict']}")

    print("\n--- 7. DURATION & POINT SCALING BY WORD LENGTH ---")
    print(f" {'Length':<6} | {'Count':<8} | {'Mean Duration':<14} | {'Median Dur':<12} | {'Mean Points':<12} | {'Mean Speed':<12}")
    print(f" {'-'*6}-+-{'-'*8}-+-{'-'*14}-+-{'-'*12}-+-{'-'*12}-+-{'-'*12}")
    for l_str, info in sorted(r["scaling_by_word_length"].items(), key=lambda x: int(x[0])):
        print(f" {l_str:<6} | {info['count']:<8,} | {info['duration_ms_mean']:<6.0f} ms      | {info['duration_ms_median']:<6.0f} ms   | {info['pts_mean']:<6.1f} pts    | {info['velocity_px_mean']:<6.0f} px/s")
    print("=" * 80 + "\n")


def main():
    parser = argparse.ArgumentParser(description="Canonical characterization and kinematics analysis of FUTO swipe dataset")
    parser.add_argument("--raw-dir", type=Path, default=DEFAULT_RAW_DIR, help="Path to raw FUTO dataset directory")
    parser.add_argument("--sample-size", type=int, default=None, help="Optional sample limit (default: all shards)")
    parser.add_argument("--all-shards", action="store_true", default=True, help="Profile all shards in the corpus")
    args = parser.parse_args()

    profile_corpus(
        raw_dir=args.raw_dir,
        sample_size=args.sample_size,
        all_shards=args.all_shards,
    )


if __name__ == "__main__":
    main()
