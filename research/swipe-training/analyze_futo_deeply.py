#!/usr/bin/env python3
"""
Deep analysis of FUTO swipe characteristics.
Profiles kinematics, velocity profiles, time intervals, and point counts.
"""

import sys
from pathlib import Path
from typing import List, Dict
import pyarrow.parquet as pq
import numpy as np

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_PARQUET = REPO_ROOT / "data" / "swipe" / "raw" / "futo" / "swipe-1" / "train" / "0.parquet"


def analyze_futo_sample(sample: dict) -> dict:
    """Deeply analyze a single FUTO swipe sample."""
    x = np.array(sample['x'])
    y = np.array(sample['y'])
    t = np.array(sample['t'])
    
    # Basic stats
    duration_ms = t[-1] - t[0] if len(t) > 1 else 0
    num_points = len(x)
    
    # Point-to-point distances
    dx = np.diff(x)
    dy = np.diff(y)
    distances = np.sqrt(dx**2 + dy**2)
    total_distance = np.sum(distances)
    
    # Time intervals
    dt = np.diff(t)  # In milliseconds
    dt_nonzero = np.maximum(dt, 1)  # Avoid division by zero
    
    # Velocity in normalized units per millisecond
    velocities_per_ms = distances / dt_nonzero
    velocities_per_sec = velocities_per_ms * 1000
    
    # X and Y ranges
    x_range = x.max() - x.min() if len(x) > 0 else 0
    y_range = y.max() - y.min() if len(y) > 0 else 0
    
    return {
        'duration_ms': duration_ms,
        'num_points': num_points,
        'total_distance': total_distance,
        'x_range': x_range,
        'y_range': y_range,
        'avg_velocity_per_sec': float(np.mean(velocities_per_sec)) if len(velocities_per_sec) > 0 else 0.0,
        'max_velocity_per_sec': float(np.max(velocities_per_sec)) if len(velocities_per_sec) > 0 else 0.0,
        'avg_dt_ms': float(np.mean(dt)) if len(dt) > 0 else 0.0,
        'min_dt_ms': float(np.min(dt)) if len(dt) > 0 else 0.0,
        'max_dt_ms': float(np.max(dt)) if len(dt) > 0 else 0.0,
        'avg_point_distance': float(np.mean(distances)) if len(distances) > 0 else 0.0,
    }


def extract_and_analyze_futo(parquet_path: str, words: List[str], max_samples: int = 3):
    """Extract real FUTO samples for specific words and analyze them."""
    print(f"Reading from {parquet_path}...")
    parquet_file = pq.ParquetFile(parquet_path)
    
    found_samples: Dict[str, List[dict]] = {w.lower(): [] for w in words}
    words_to_find = set(found_samples.keys())
    
    for i in range(parquet_file.metadata.num_row_groups):
        table = parquet_file.read_row_group(i)
        df = table.to_pandas()
        
        for _, row in df.iterrows():
            word = str(row['word']).lower()
            if word in words_to_find and len(found_samples[word]) < max_samples:
                pts = row['data']
                if pts is not None and len(pts) > 0:
                    found_samples[word].append({
                        'x': [p['x'] for p in pts],
                        'y': [p['y'] for p in pts],
                        't': [p['t'] for p in pts],
                        'canvas_w': row.get('canvas_width'),
                        'canvas_h': row.get('canvas_height'),
                        'orientation': row.get('orientation'),
                    })
        
        # Check if we have enough
        if all(len(samples) >= max_samples for samples in found_samples.values()):
            break
    
    # Print results
    print("\n" + "=" * 70)
    print("FUTO REAL HUMAN DATA ANALYSIS")
    print("=" * 70)
    
    for word, samples in found_samples.items():
        if not samples:
            print(f"\n'{word}': Not found in sample")
            continue
            
        print(f"\n{'='*70}")
        print(f"Word: '{word}' (real FUTO, {len(samples)} samples)")
        print(f"{'='*70}")
        
        all_stats = []
        for i, sample in enumerate(samples):
            stats = analyze_futo_sample(sample)
            all_stats.append(stats)
            
            if i == 0:
                print(f"\nSample 1 (detailed):")
                print(f"  Duration: {stats['duration_ms']:.0f}ms")
                print(f"  Points: {stats['num_points']}")
                print(f"  Total distance: {stats['total_distance']:.4f}")
                print(f"  X range: {stats['x_range']:.4f}, Y range: {stats['y_range']:.4f}")
                print(f"  Canvas: {sample['canvas_w']} x {sample['canvas_h']} ({sample['orientation']})")
                print(f"  Velocity: avg={stats['avg_velocity_per_sec']:.4f}, max={stats['max_velocity_per_sec']:.4f} units/s")
                print(f"  Time intervals: avg dt={stats['avg_dt_ms']:.1f}ms, min={stats['min_dt_ms']:.0f}ms, max={stats['max_dt_ms']:.0f}ms")
        
        print(f"\n  Aggregated across {len(samples)} samples:")
        print(f"    Avg duration: {np.mean([s['duration_ms'] for s in all_stats]):.0f}ms")
        print(f"    Avg velocity: {np.mean([s['avg_velocity_per_sec'] for s in all_stats]):.4f} units/sec")
        print(f"    Avg point count: {np.mean([s['num_points'] for s in all_stats]):.0f}")
        print(f"    Avg dt between points: {np.mean([s['avg_dt_ms'] for s in all_stats]):.1f}ms")


def main():
    parquet_path = sys.argv[1] if len(sys.argv) > 1 else (
        "futo_swipes.parquet" if Path("futo_swipes.parquet").exists() else str(DEFAULT_PARQUET)
    )
    test_words = ['the', 'ham', 'about', 'you', 'and']
    extract_and_analyze_futo(parquet_path, test_words)


if __name__ == "__main__":
    main()
