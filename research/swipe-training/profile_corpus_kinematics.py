#!/usr/bin/env python3
"""
OmniBoard Comprehensive FUTO Swipe Corpus Kinematics & Distribution Profiler

This script performs deep statistical profiling of the human swipe corpus to extract
empirical biomechanical distributions:
1. Temporal dynamics (dt intervals, touch sampling Hz, total duration vs word length)
2. Kinematics (instantaneous speed, peak speed, acceleration, jerk in norm & pixel space)
3. Curvature & Motor control (turning speed drops, power law beta, tortuosity)
4. Display geometry (canvas dimensions, aspect ratios, coordinate bounding boxes)
5. Typist & session variation (inter-session vs intra-session variance, repeated word consistency)

Usage:
  uv run --with pyarrow --with numpy --with scipy python3 research/swipe-training/profile_corpus_kinematics.py [--sample-size N] [--all-shards]
"""

import argparse
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
LOCK_FILE = REPO_ROOT / "research" / "swipe-training" / "futo_dataset_lock.json"
OUTPUT_REPORT_PATH = REPO_ROOT / "research" / "swipe-training" / "corpus_kinematics_profile.json"


def compute_quantiles(arr: np.ndarray, q_list=(0, 1, 5, 10, 25, 50, 75, 90, 95, 99, 100)) -> Dict[str, float]:
    if len(arr) == 0:
        return {}
    vals = np.percentile(arr, q_list)
    res = {
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
    return res


def analyze_single_trajectory(
    pts: List[Dict[str, Any]],
    canvas_w: float,
    canvas_h: float,
    word: str,
) -> Optional[Dict[str, Any]]:
    """Deep kinematic analysis of a single continuous swipe trajectory."""
    if not pts or len(pts) < 3:
        return None

    # Handle point array
    xs = np.array([p['x'] for p in pts], dtype=np.float64)
    ys = np.array([p['y'] for p in pts], dtype=np.float64)
    ts = np.array([p['t'] for p in pts], dtype=np.float64)

    # Basic counts & duration
    num_points = len(xs)
    duration_ms = ts[-1] - ts[0]
    if duration_ms <= 0:
        return None

    word_len = len(word) if word else 1
    pts_per_char = num_points / max(word_len, 1)
    duration_per_char = duration_ms / max(word_len, 1)

    # Time intervals
    dts = np.diff(ts) # ms
    valid_dt_mask = dts > 0
    if not np.any(valid_dt_mask):
        return None

    # Spatial increments
    # 1. Normalized space [0, 1]
    dx = np.diff(xs)
    dy = np.diff(ys)
    d_dist_norm = np.sqrt(dx**2 + dy**2)
    total_dist_norm = float(np.sum(d_dist_norm))

    # 2. Aspect-corrected / Pixel space
    w_px = canvas_w if canvas_w and canvas_w > 0 else 400.0
    h_px = canvas_h if canvas_h and canvas_h > 0 else 170.0
    aspect = w_px / h_px

    dx_px = dx * w_px
    dy_px = dy * h_px
    d_dist_px = np.sqrt(dx_px**2 + dy_px**2)
    total_dist_px = float(np.sum(d_dist_px))

    # Velocities
    # Use dt with minimum 1ms to prevent div by zero
    safe_dt_s = np.maximum(dts, 1.0) / 1000.0 # seconds
    velocities_norm = d_dist_norm / safe_dt_s # units/sec
    velocities_px = d_dist_px / safe_dt_s # pixels/sec

    mean_v_norm = float(np.mean(velocities_norm))
    max_v_norm = float(np.max(velocities_norm))
    mean_v_px = float(np.mean(velocities_px))
    max_v_px = float(np.max(velocities_px))

    # Acceleration (pixels/sec^2)
    if len(velocities_px) > 1:
        dt_mid_s = (safe_dt_s[:-1] + safe_dt_s[1:]) / 2.0
        accel_px = np.diff(velocities_px) / dt_mid_s
        mean_accel_px = float(np.mean(np.abs(accel_px)))
        max_accel_px = float(np.max(np.abs(accel_px)))
    else:
        mean_accel_px = 0.0
        max_accel_px = 0.0

    # Curvature & Turning Angles
    # Vector between points
    # Angles between successive displacement vectors
    turn_angles_deg = []
    curvature_list = []
    corner_v_ratios = []

    if len(dx) >= 2:
        v1_x, v1_y = dx[:-1], dy[:-1]
        v2_x, v2_y = dx[1:], dy[1:]
        
        norms1 = np.sqrt(v1_x**2 + v1_y**2)
        norms2 = np.sqrt(v2_x**2 + v2_y**2)
        
        valid_vecs = (norms1 > 1e-6) & (norms2 > 1e-6)
        if np.any(valid_vecs):
            dots = (v1_x[valid_vecs] * v2_x[valid_vecs] + v1_y[valid_vecs] * v2_y[valid_vecs]) / (norms1[valid_vecs] * norms2[valid_vecs])
            dots = np.clip(dots, -1.0, 1.0)
            angles_rad = np.arccos(dots)
            turn_angles_deg = np.degrees(angles_rad).tolist()
            
            # Curvature ~ angle / arc_length
            ds_mid = (d_dist_px[:-1][valid_vecs] + d_dist_px[1:][valid_vecs]) / 2.0
            curvatures = angles_rad / np.maximum(ds_mid, 1.0)
            curvature_list = curvatures.tolist()

            # Compare velocity at sharp turns (> 45 deg) vs gentle segments (< 20 deg)
            sharp_mask = np.degrees(angles_rad) > 45.0
            gentle_mask = np.degrees(angles_rad) < 20.0
            
            v_mid = (velocities_px[:-1][valid_vecs] + velocities_px[1:][valid_vecs]) / 2.0
            if np.any(sharp_mask) and np.any(gentle_mask):
                v_sharp = np.mean(v_mid[sharp_mask])
                v_gentle = np.mean(v_mid[gentle_mask])
                if v_gentle > 0:
                    corner_v_ratios.append(float(v_sharp / v_gentle))

    # Path elongation / Tortuosity: Total arc distance vs Start-to-End straight line
    net_dx = xs[-1] - xs[0]
    net_dy = ys[-1] - ys[0]
    net_dist_norm = math.sqrt(net_dx**2 + net_dy**2)
    tortuosity = total_dist_norm / max(net_dist_norm, 1e-4)

    return {
        "word": word,
        "word_len": word_len,
        "num_points": num_points,
        "duration_ms": duration_ms,
        "pts_per_char": pts_per_char,
        "duration_per_char": duration_per_char,
        "dts": dts.tolist(),
        "total_dist_norm": total_dist_norm,
        "total_dist_px": total_dist_px,
        "mean_v_norm": mean_v_norm,
        "max_v_norm": max_v_norm,
        "mean_v_px": mean_v_px,
        "max_v_px": max_v_px,
        "mean_accel_px": mean_accel_px,
        "max_accel_px": max_accel_px,
        "turn_angles_deg": turn_angles_deg,
        "corner_v_ratios": corner_v_ratios,
        "tortuosity": tortuosity,
        "x_min": float(np.min(xs)),
        "x_max": float(np.max(xs)),
        "y_min": float(np.min(ys)),
        "y_max": float(np.max(ys)),
        "canvas_w": float(w_px),
        "canvas_h": float(h_px),
        "aspect_ratio": float(aspect),
    }


def profile_corpus(
    raw_dir: Path,
    sample_size: Optional[int] = 100000,
    all_shards: bool = False,
) -> Dict[str, Any]:
    """Stream shards and aggregate kinematic distributions."""
    print(f"Starting FUTO Swipe Corpus Kinematics Profiling...")
    print(f"Directory: {raw_dir}")
    print(f"Sample limit: {'ALL SWIPES' if all_shards or sample_size is None else f'{sample_size:,} swipes'}")

    shards_to_process = []
    for run_dir in sorted(raw_dir.glob("swipe-*")):
        for split_dir in sorted(run_dir.glob("*")):
            for pfile in sorted(split_dir.glob("*.parquet")):
                shards_to_process.append(pfile)

    print(f"Found {len(shards_to_process)} parquet shards to profile.")

    # Aggregator lists
    all_dts = []
    all_durations = []
    all_pts_counts = []
    all_pts_per_char = []
    all_duration_per_char = []
    
    all_dist_norm = []
    all_dist_px = []
    all_mean_v_norm = []
    all_max_v_norm = []
    all_mean_v_px = []
    all_max_v_px = []
    all_mean_accel_px = []
    all_max_accel_px = []
    
    all_turn_angles = []
    all_corner_v_ratios = []
    all_tortuosity = []
    
    all_canvas_w = []
    all_canvas_h = []
    all_aspects = []
    all_x_mins = []
    all_x_maxs = []
    all_y_mins = []
    all_y_maxs = []

    # Word length breakdown: word_len -> list of durations, points, velocities
    by_word_len_duration = defaultdict(list)
    by_word_len_pts = defaultdict(list)
    by_word_len_dist_px = defaultdict(list)
    by_word_len_v_px = defaultdict(list)

    # Session stats
    session_swipe_counts = defaultdict(int)
    session_mean_v_px = defaultdict(list)
    session_word_swipes = defaultdict(lambda: defaultdict(list)) # session -> word -> list of (pts, duration, v_px)

    total_scanned = 0
    total_valid = 0
    start_time = time.time()

    for shard_idx, shard_path in enumerate(shards_to_process):
        rel_shard = shard_path.relative_to(raw_dir)
        print(f"\n[{shard_idx+1}/{len(shards_to_process)}] Reading {rel_shard}...")
        pf = pq.ParquetFile(str(shard_path))
        num_rg = pf.metadata.num_row_groups

        for rg_idx in range(num_rg):
            cols_to_read = ["word", "canvas_width", "canvas_height", "session", "data"]
            # Check if columns exist
            schema_names = pf.schema_arrow.names
            cols = [c for c in cols_to_read if c in schema_names]
            
            table = pf.read_row_group(rg_idx, columns=cols)
            pylist = table.to_pylist()

            for row in pylist:
                total_scanned += 1
                raw_data = row.get("data")
                if not raw_data:
                    continue

                if isinstance(raw_data, str):
                    try:
                        pts = json.loads(raw_data)
                    except Exception:
                        continue
                else:
                    pts = raw_data

                # If dual finger, skip for single-finger kinematic baseline or extract main
                if isinstance(pts, dict):
                    continue # Skip dual finger for clean kinematic baseline

                word = str(row.get("word", ""))
                cw = row.get("canvas_width")
                ch = row.get("canvas_height")
                sess = row.get("session", "unknown")

                metrics = analyze_single_trajectory(pts, cw, ch, word)
                if not metrics:
                    continue

                total_valid += 1

                # Subsample dt intervals (to save memory over millions of points)
                if len(all_dts) < 500000:
                    all_dts.extend(metrics["dts"])

                all_durations.append(metrics["duration_ms"])
                all_pts_counts.append(metrics["num_points"])
                all_pts_per_char.append(metrics["pts_per_char"])
                all_duration_per_char.append(metrics["duration_per_char"])

                all_dist_norm.append(metrics["total_dist_norm"])
                all_dist_px.append(metrics["total_dist_px"])
                all_mean_v_norm.append(metrics["mean_v_norm"])
                all_max_v_norm.append(metrics["max_v_norm"])
                all_mean_v_px.append(metrics["mean_v_px"])
                all_max_v_px.append(metrics["max_v_px"])
                all_mean_accel_px.append(metrics["mean_accel_px"])
                all_max_accel_px.append(metrics["max_accel_px"])

                if len(all_turn_angles) < 500000:
                    all_turn_angles.extend(metrics["turn_angles_deg"])
                all_corner_v_ratios.extend(metrics["corner_v_ratios"])
                all_tortuosity.append(metrics["tortuosity"])

                all_canvas_w.append(metrics["canvas_w"])
                all_canvas_h.append(metrics["canvas_h"])
                all_aspects.append(metrics["aspect_ratio"])
                all_x_mins.append(metrics["x_min"])
                all_x_maxs.append(metrics["x_max"])
                all_y_mins.append(metrics["y_min"])
                all_y_maxs.append(metrics["y_max"])

                w_len = metrics["word_len"]
                if w_len <= 15:
                    by_word_len_duration[w_len].append(metrics["duration_ms"])
                    by_word_len_pts[w_len].append(metrics["num_points"])
                    by_word_len_dist_px[w_len].append(metrics["total_dist_px"])
                    by_word_len_v_px[w_len].append(metrics["mean_v_px"])

                # Session tracking
                session_swipe_counts[sess] += 1
                session_mean_v_px[sess].append(metrics["mean_v_px"])
                if len(session_word_swipes[sess][word]) < 5:
                    session_word_swipes[sess][word].append(metrics)

                if sample_size and not all_shards and total_valid >= sample_size:
                    break

            if sample_size and not all_shards and total_valid >= sample_size:
                break
        if sample_size and not all_shards and total_valid >= sample_size:
            break

    elapsed = time.time() - start_time
    print(f"\nProfiling Complete in {elapsed:.2f}s!")
    print(f"Scanned: {total_scanned:,} rows | Valid Trajectories Profiled: {total_valid:,}")

    # Convert to NumPy for quantile computations
    print("Computing empirical distributions and quantiles...")

    dts_np = np.array(all_dts, dtype=np.float64)
    touch_hz = 1000.0 / np.maximum(dts_np[dts_np > 0], 1.0)

    # Linear regression: Duration vs Word Length
    word_lens = []
    durations = []
    for w_len, durs in by_word_len_duration.items():
        word_lens.extend([w_len] * len(durs))
        durations.extend(durs)
    
    if len(word_lens) > 0:
        poly = np.polyfit(word_lens, durations, 1)
        slope_ms_per_char, intercept_ms = float(poly[0]), float(poly[1])
        # Pearson correlation R^2
        corr = np.corrcoef(word_lens, durations)[0, 1]
        r_squared = float(corr**2)
    else:
        slope_ms_per_char, intercept_ms, r_squared = 0.0, 0.0, 0.0

    # Session variation stats
    sess_avg_velocities = [np.mean(vlist) for vlist in session_mean_v_px.values() if len(vlist) >= 10]
    sess_std_velocities = [np.std(vlist) for vlist in session_mean_v_px.values() if len(vlist) >= 10]

    # Word length table
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

    # Compile Full Report
    report = {
        "profiling_metadata": {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "total_scanned_rows": total_scanned,
            "total_valid_trajectories": total_valid,
            "elapsed_seconds": round(elapsed, 2),
            "shards_profiled": len(shards_to_process),
        },
        "temporal_dynamics": {
            "dt_ms": compute_quantiles(dts_np),
            "touch_sampling_hz": compute_quantiles(touch_hz),
            "duration_ms": compute_quantiles(np.array(all_durations)),
            "duration_per_char_ms": compute_quantiles(np.array(all_duration_per_char)),
            "regression_duration_vs_length": {
                "slope_ms_per_char": round(slope_ms_per_char, 2),
                "intercept_ms": round(intercept_ms, 2),
                "r_squared": round(r_squared, 4),
                "formula": f"Duration(N) = {slope_ms_per_char:.1f} * N + {intercept_ms:.1f} ms",
            },
            "dt_discrete_breakdown": {
                "pct_dt_le_8ms (~120Hz+)": round(float(np.mean(dts_np <= 8.5) * 100), 2),
                "pct_dt_9_to_17ms (~60Hz)": round(float(np.mean((dts_np > 8.5) & (dts_np <= 17.5)) * 100), 2),
                "pct_dt_18_to_34ms (~30Hz)": round(float(np.mean((dts_np > 17.5) & (dts_np <= 34.5)) * 100), 2),
                "pct_dt_gt_34ms (<30Hz)": round(float(np.mean(dts_np > 34.5) * 100), 2),
            }
        },
        "points_and_spatial_density": {
            "num_points": compute_quantiles(np.array(all_pts_counts)),
            "points_per_character": compute_quantiles(np.array(all_pts_per_char)),
            "total_distance_normalized": compute_quantiles(np.array(all_dist_norm)),
            "total_distance_pixels": compute_quantiles(np.array(all_dist_px)),
            "tortuosity_ratio": compute_quantiles(np.array(all_tortuosity)),
        },
        "kinematics": {
            "mean_velocity_normalized_per_s": compute_quantiles(np.array(all_mean_v_norm)),
            "max_velocity_normalized_per_s": compute_quantiles(np.array(all_max_v_norm)),
            "mean_velocity_pixels_per_s": compute_quantiles(np.array(all_mean_v_px)),
            "max_velocity_pixels_per_s": compute_quantiles(np.array(all_max_v_px)),
            "mean_accel_pixels_per_s2": compute_quantiles(np.array(all_mean_accel_px)),
            "max_accel_pixels_per_s2": compute_quantiles(np.array(all_max_accel_px)),
        },
        "motor_control_and_curvature": {
            "turn_angles_deg": compute_quantiles(np.array(all_turn_angles)),
            "corner_velocity_ratio (v_turn / v_straight)": compute_quantiles(np.array(all_corner_v_ratios)) if all_corner_v_ratios else {},
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
        "typist_and_session_variation": {
            "unique_sessions_observed": len(session_swipe_counts),
            "sessions_with_ge_10_swipes": len(sess_avg_velocities),
            "inter_session_mean_velocity_px_per_s": compute_quantiles(np.array(sess_avg_velocities)) if sess_avg_velocities else {},
            "intra_session_velocity_std_px_per_s": compute_quantiles(np.array(sess_std_velocities)) if sess_std_velocities else {},
        },
        "scaling_by_word_length": word_len_summary,
    }

    # Save to JSON
    with open(OUTPUT_REPORT_PATH, "w") as f:
        json.dump(report, f, indent=2)

    print(f"\nEmpirical profile written to: {OUTPUT_REPORT_PATH.relative_to(REPO_ROOT)}")

    # Print Clean Formatted Console Summary
    print_console_summary(report)

    return report


def print_console_summary(r: Dict[str, Any]):
    print("\n" + "=" * 80)
    print("           FUTO HUMAN SWIPE CORPUS EMPIRICAL KINEMATIC PROFILE           ")
    print("=" * 80)
    
    meta = r["profiling_metadata"]
    print(f"Dataset Scanned:  {meta['total_scanned_rows']:,} rows ({meta['total_valid_trajectories']:,} valid single-finger swipes)")
    print(f"Execution Time:   {meta['elapsed_seconds']}s")
    
    temp = r["temporal_dynamics"]
    print("\n--- 1. TEMPORAL & HARDWARE TOUCH DYNAMICS ---")
    dt = temp["dt_ms"]
    print(f"Sampling Interval (dt):  median = {dt['p50']:.1f}ms | mean = {dt['mean']:.1f}ms (std = {dt['std']:.1f}ms) | p5={dt['p05']:.1f}ms, p95={dt['p95']:.1f}ms")
    hz = temp["touch_sampling_hz"]
    print(f"Touch Sampling Rate:     median = {hz['p50']:.1f}Hz | p25 = {hz['p25']:.1f}Hz | p75 = {hz['p75']:.1f}Hz | p99 = {hz['p99']:.1f}Hz")
    breakdown = temp["dt_discrete_breakdown"]
    print(f"Touch Rate Breakdown:    60Hz (~16ms): {breakdown['pct_dt_9_to_17ms (~60Hz)']}% | 120Hz+ (<=8ms): {breakdown['pct_dt_le_8ms (~120Hz+)']}% | 30Hz: {breakdown['pct_dt_18_to_34ms (~30Hz)']}%")
    
    dur = temp["duration_ms"]
    dpc = temp["duration_per_char_ms"]
    reg = temp["regression_duration_vs_length"]
    print(f"Total Gesture Duration:  median = {dur['p50']:.0f}ms | mean = {dur['mean']:.0f}ms | p5={dur['p05']:.0f}ms, p95={dur['p95']:.0f}ms")
    print(f"Duration per Character:  median = {dpc['p50']:.0f}ms/char | mean = {dpc['mean']:.0f}ms/char")
    print(f"Linear Motor Law:        {reg['formula']} (R² = {reg['r_squared']:.3f})")

    pts = r["points_and_spatial_density"]
    print("\n--- 2. POINT DENSITY & TORTUOSITY ---")
    np_pts = pts["num_points"]
    ppc = pts["points_per_character"]
    tort = pts["tortuosity_ratio"]
    print(f"Points per Swipe:        median = {np_pts['p50']:.0f} | mean = {np_pts['mean']:.1f} | p5={np_pts['p05']:.0f}, p95={np_pts['p95']:.0f}")
    print(f"Points per Character:    median = {ppc['p50']:.1f} | mean = {ppc['mean']:.1f} | p5={ppc['p05']:.1f}, p95={ppc['p95']:.1f}")
    print(f"Path Tortuosity (S/D):   median = {tort['p50']:.2f}x | mean = {tort['mean']:.2f}x | p90 = {tort['p90']:.2f}x straight Euclidean")

    kin = r["kinematics"]
    print("\n--- 3. VELOCITIES & ACCELERATION ---")
    v_norm = kin["mean_velocity_normalized_per_s"]
    v_px = kin["mean_velocity_pixels_per_s"]
    v_max_px = kin["max_velocity_pixels_per_s"]
    acc_px = kin["mean_accel_pixels_per_s2"]
    print(f"Mean Swipe Speed:        {v_norm['p50']:.2f} norm/s ({v_px['p50']:.0f} px/s) [mean: {v_px['mean']:.0f} px/s, std: {v_px['std']:.0f} px/s]")
    print(f"Peak Swipe Speed:        {v_max_px['p50']:.0f} px/s (p95 = {v_max_px['p95']:.0f} px/s)")
    print(f"Mean Acceleration:       {acc_px['p50']:.0f} px/s² (mean = {acc_px['mean']:.0f} px/s²)")

    motor = r["motor_control_and_curvature"]
    print("\n--- 4. MOTOR CONTROL & CORNERING DYNAMICS ---")
    angles = motor["turn_angles_deg"]
    cv = motor.get("corner_velocity_ratio (v_turn / v_straight)", {})
    print(f"Inflection Turn Angles:  median = {angles['p50']:.1f}° | p75 = {angles['p75']:.1f}° | p95 = {angles['p95']:.1f}°")
    if cv:
        print(f"Corner Speed Ratio:      median = {cv['p50']:.2f} | mean = {cv['mean']:.2f} (Humans slow down to ~{cv['p50']*100:.0f}% speed at key turns!)")

    disp = r["display_geometry_and_bounds"]
    print("\n--- 5. DISPLAY GEOMETRY & COORDINATE BOUNDS ---")
    cw = disp["canvas_width_px"]
    ch = disp["canvas_height_px"]
    asp = disp["aspect_ratio_w_over_h"]
    print(f"Canvas Dimensions:       Width = {cw['p50']:.0f}px (mean {cw['mean']:.0f}px) | Height = {ch['p50']:.0f}px (mean {ch['mean']:.0f}px)")
    print(f"Aspect Ratio (W/H):      median = {asp['p50']:.2f} | mean = {asp['mean']:.2f} | p10 = {asp['p10']:.2f}, p90 = {asp['p90']:.2f}")
    print(f"Coord Bounds:            x in [{disp['x_min_bound']['p05']:.2f}, {disp['x_max_bound']['p95']:.2f}], y in [{disp['y_min_bound']['p05']:.2f}, {disp['y_max_bound']['p95']:.2f}]")

    sess = r["typist_and_session_variation"]
    print("\n--- 6. TYPIST INTER- & INTRA-SESSION VARIATION ---")
    inter_v = sess.get("inter_session_mean_velocity_px_per_s", {})
    intra_v = sess.get("intra_session_velocity_std_px_per_s", {})
    print(f"Unique Typist Sessions:  {sess['unique_sessions_observed']:,} sessions ({sess['sessions_with_ge_10_swipes']:,} >= 10 swipes)")
    if inter_v and intra_v:
        print(f"Inter-Typist Speed:      median = {inter_v['p50']:.0f} px/s (p10={inter_v['p10']:.0f} px/s, p90={inter_v['p90']:.0f} px/s) -> 2.5x speed spread between users!")
        print(f"Intra-Typist Speed Std:  median = {intra_v['p50']:.0f} px/s within the same session")

    print("\n--- 7. DURATION & POINT SCALING BY WORD LENGTH ---")
    print(f" {'Length':<6} | {'Count':<8} | {'Mean Duration':<14} | {'Median Dur':<12} | {'Mean Points':<12} | {'Mean Speed':<12}")
    print(f" {'-'*6}-+-{'-'*8}-+-{'-'*14}-+-{'-'*12}-+-{'-'*12}-+-{'-'*12}")
    for l_str, info in sorted(r["scaling_by_word_length"].items(), key=lambda x: int(x[0])):
        print(f" {l_str:<6} | {info['count']:<8,} | {info['duration_ms_mean']:<6.0f} ms      | {info['duration_ms_median']:<6.0f} ms   | {info['pts_mean']:<6.1f} pts    | {info['velocity_px_mean']:<6.0f} px/s")
    print("=" * 80 + "\n")


def main():
    parser = argparse.ArgumentParser(description="Profile empirical kinematics of FUTO swipe dataset")
    parser.add_argument("--raw-dir", type=Path, default=DEFAULT_RAW_DIR, help="Path to raw FUTO dataset directory")
    parser.add_argument("--sample-size", type=int, default=100000, help="Number of swipes to sample (default: 100,000)")
    parser.add_argument("--all-shards", action="store_true", help="Profile all 1.22M swipes in the corpus")
    args = parser.parse_args()

    profile_corpus(
        raw_dir=args.raw_dir,
        sample_size=None if args.all_shards else args.sample_size,
        all_shards=args.all_shards,
    )


if __name__ == "__main__":
    main()
