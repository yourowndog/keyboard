#!/usr/bin/env python3
"""
Deep analysis of FUTO swipe characteristics.
Understand exactly how FUTO data is structured so we can match it precisely.
"""

import pyarrow.parquet as pq
import numpy as np
import json
from typing import List, Dict

# =============================================================================
# FUTO DATA ANALYSIS
# =============================================================================

def analyze_futo_sample(sample: dict) -> dict:
    """Deeply analyze a single FUTO swipe sample."""
    x = np.array(sample['x'])
    y = np.array(sample['y'])
    t = np.array(sample['t'])
    
    # Basic stats
    duration_ms = t[-1] - t[0]
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
    
    # Also calculate velocity in normalized units per second
    velocities_per_sec = velocities_per_ms * 1000
    
    # X and Y ranges
    x_range = x.max() - x.min()
    y_range = y.max() - y.min()
    
    return {
        'duration_ms': duration_ms,
        'num_points': num_points,
        'total_distance': total_distance,
        'x_range': x_range,
        'y_range': y_range,
        'avg_velocity_per_ms': np.mean(velocities_per_ms),
        'max_velocity_per_ms': np.max(velocities_per_ms),
        'min_velocity_per_ms': np.min(velocities_per_ms),
        'avg_velocity_per_sec': np.mean(velocities_per_sec),
        'max_velocity_per_sec': np.max(velocities_per_sec),
        'avg_dt_ms': np.mean(dt),
        'min_dt_ms': np.min(dt),
        'max_dt_ms': np.max(dt),
        'avg_point_distance': np.mean(distances),
        'x_coords_sample': x[:5].tolist(),
        'y_coords_sample': y[:5].tolist(),
        't_coords_sample': t[:5].tolist(),
    }

def extract_and_analyze_futo(parquet_path: str, words: List[str], max_samples: int = 3):
    """Extract and deeply analyze FUTO samples for specific words."""
    
    print("=" * 70)
    print("FUTO DATA DEEP ANALYSIS")
    print("=" * 70)
    
    parquet_file = pq.ParquetFile(parquet_path)
    word_samples = {w.lower(): [] for w in words}
    words_lower = [w.lower() for w in words]
    
    # Extract samples
    for i in range(parquet_file.metadata.num_row_groups):
        table = parquet_file.read_row_group(i)
        df = table.to_pandas()
        
        for _, row in df.iterrows():
            word = row['word'].lower()
            if word in words_lower and len(word_samples[word]) < max_samples:
                points = row['data']
                canvas_w = row['canvas_width']
                canvas_h = row['canvas_height']
                
                # Normalize coordinates to 0-1
                x = [p['x'] / canvas_w for p in points]
                y = [p['y'] / canvas_h for p in points]
                t = [p['t'] for p in points]
                
                word_samples[word].append({
                    'x': x, 'y': y, 't': t,
                    'canvas_width': canvas_w,
                    'canvas_height': canvas_h
                })
        
        if all(len(word_samples[w]) >= max_samples for w in words_lower):
            break
    
    # Analyze each word
    for word in words:
        word_lower = word.lower()
        samples = word_samples.get(word_lower, [])
        
        if not samples:
            print(f"\n'{word}': No samples found")
            continue
        
        print(f"\n{'='*70}")
        print(f"Word: '{word}' ({len(samples)} samples)")
        print(f"{'='*70}")
        
        all_stats = []
        for i, sample in enumerate(samples):
            stats = analyze_futo_sample(sample)
            all_stats.append(stats)
            
            if i == 0:  # Print detailed info for first sample
                print(f"\nSample 1 (detailed):")
                print(f"  Canvas: {sample['canvas_width']} x {sample['canvas_height']}")
                print(f"  Duration: {stats['duration_ms']:.0f}ms")
                print(f"  Points: {stats['num_points']}")
                print(f"  Total distance (normalized): {stats['total_distance']:.4f}")
                print(f"  X range: {stats['x_range']:.4f}")
                print(f"  Y range: {stats['y_range']:.4f}")
                print(f"\n  Velocity (normalized units/sec):")
                print(f"    Avg: {stats['avg_velocity_per_sec']:.4f}")
                print(f"    Max: {stats['max_velocity_per_sec']:.4f}")
                print(f"\n  Time intervals (ms):")
                print(f"    Avg dt: {stats['avg_dt_ms']:.1f}ms")
                print(f"    Min dt: {stats['min_dt_ms']:.0f}ms")
                print(f"    Max dt: {stats['max_dt_ms']:.0f}ms")
                print(f"\n  First 5 coordinates:")
                print(f"    X: {stats['x_coords_sample']}")
                print(f"    Y: {stats['y_coords_sample']}")
                print(f"    T: {stats['t_coords_sample']}")
        
        # Aggregate stats across samples
        print(f"\n  Aggregated across {len(samples)} samples:")
        print(f"    Avg duration: {np.mean([s['duration_ms'] for s in all_stats]):.0f}ms")
        print(f"    Avg velocity: {np.mean([s['avg_velocity_per_sec'] for s in all_stats]):.4f} units/sec")
        print(f"    Avg point count: {np.mean([s['num_points'] for s in all_stats]):.0f}")
        print(f"    Avg dt between points: {np.mean([s['avg_dt_ms'] for s in all_stats]):.1f}ms")

def main():
    parquet_path = 'futo_swipes.parquet'
    test_words = ['the', 'ham', 'about', 'you', 'and']
    
    extract_and_analyze_futo(parquet_path, test_words)
    
    # Now let's analyze our synthetic data the same way
    print("\n\n")
    print("=" * 70)
    print("SYNTHETIC DATA ANALYSIS (for comparison)")
    print("=" * 70)
    
    from generate_synthetic_swipes import generate_synthetic_swipes
    
    for word in test_words:
        samples = generate_synthetic_swipes(word, num_variations=3)
        
        if not samples:
            print(f"\n'{word}': Could not generate")
            continue
        
        print(f"\n{'='*70}")
        print(f"Word: '{word}' (synthetic, {len(samples)} samples)")
        print(f"{'='*70}")
        
        all_stats = []
        for i, sample in enumerate(samples):
            synth_sample = {
                'x': sample['curve']['x'],
                'y': sample['curve']['y'],
                't': sample['curve']['t'],
            }
            stats = analyze_futo_sample(synth_sample)
            all_stats.append(stats)
            
            if i == 0:
                print(f"\nSample 1 (detailed):")
                print(f"  Duration: {stats['duration_ms']:.0f}ms")
                print(f"  Points: {stats['num_points']}")
                print(f"  Total distance (normalized): {stats['total_distance']:.4f}")
                print(f"  X range: {stats['x_range']:.4f}")  
                print(f"  Y range: {stats['y_range']:.4f}")
                print(f"\n  Velocity (normalized units/sec):")
                print(f"    Avg: {stats['avg_velocity_per_sec']:.4f}")
                print(f"    Max: {stats['max_velocity_per_sec']:.4f}")
                print(f"\n  Time intervals (ms):")
                print(f"    Avg dt: {stats['avg_dt_ms']:.1f}ms")
                print(f"    Min dt: {stats['min_dt_ms']:.0f}ms")
                print(f"    Max dt: {stats['max_dt_ms']:.0f}ms")
        
        print(f"\n  Aggregated across {len(samples)} samples:")
        print(f"    Avg duration: {np.mean([s['duration_ms'] for s in all_stats]):.0f}ms")
        print(f"    Avg velocity: {np.mean([s['avg_velocity_per_sec'] for s in all_stats]):.4f} units/sec")
        print(f"    Avg point count: {np.mean([s['num_points'] for s in all_stats]):.0f}")
        print(f"    Avg dt between points: {np.mean([s['avg_dt_ms'] for s in all_stats]):.1f}ms")

if __name__ == "__main__":
    main()
