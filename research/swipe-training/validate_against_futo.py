#!/usr/bin/env python3
"""
Validate Synthetic Swipe Generator Against Real FUTO Data

This script:
1. Extracts real swipe samples from FUTO for specific "extreme" test words
2. Analyzes their physics characteristics (velocity, curvature, timing)
3. Generates synthetic versions using our algorithm
4. Compares the two to validate/tune our physics model

Test words selected for:
- Sharp direction changes (ham, bag, zip)
- Long straight runs (you, run)
- Mix of both (hello, about)
"""

import pyarrow.parquet as pq
import numpy as np
import json
from dataclasses import dataclass
from typing import List, Tuple, Dict
import sys

# Import our generator
from generate_synthetic_swipes import (
    FingerSimulator, FingerPhysics, word_to_key_sequence,
    generate_synthetic_swipes, QWERTY_LAYOUT, KEY_WIDTH
)

# =============================================================================
# TEST WORDS
# =============================================================================

# Words chosen to stress-test different aspects of swipe physics
TEST_WORDS = [
    "ham",      # H→A→M: sharp corner at A (row change + direction reversal)
    "bag",      # B→A→G: corner at A
    "you",      # Y→O→U: relatively straight (top row)
    "run",      # R→U→N: long diagonal then corner
    "the",      # T→H→E: common word, good baseline
    "and",      # A→N→D: cross-keyboard sweep
    "about",    # Long word with multiple direction changes
]

# =============================================================================
# FUTO DATA EXTRACTION
# =============================================================================

def extract_futo_samples(parquet_path: str, words: List[str], max_samples: int = 5) -> Dict[str, List[dict]]:
    """
    Extract real swipe samples from FUTO for specific words.
    Returns dict mapping word -> list of samples
    """
    print(f"Extracting FUTO samples for: {words}")
    
    parquet_file = pq.ParquetFile(parquet_path)
    word_samples = {w: [] for w in words}
    words_lower = [w.lower() for w in words]
    
    for i in range(parquet_file.metadata.num_row_groups):
        table = parquet_file.read_row_group(i)
        df = table.to_pandas()
        
        for _, row in df.iterrows():
            word = row['word'].lower()
            if word in words_lower and len(word_samples[word]) < max_samples:
                points = row['data']
                # FUTO data is ALREADY normalized 0-1! Do NOT divide by canvas
                x = [p['x'] for p in points]
                y = [p['y'] for p in points]
                t = [p['t'] for p in points]
                
                word_samples[word].append({
                    'x': x, 'y': y, 't': t,
                })
        
        # Check if we have enough samples
        if all(len(word_samples[w]) >= max_samples for w in words_lower):
            break
    
    return word_samples

# =============================================================================
# PHYSICS ANALYSIS
# =============================================================================

@dataclass
class SwipeMetrics:
    """Metrics extracted from a swipe for comparison."""
    total_duration_ms: int
    avg_velocity: float          # normalized units per second
    max_velocity: float
    min_velocity: float
    velocity_at_corners: List[float]  # velocity at direction changes
    corner_radii: List[float]    # radius of turns at direction changes
    time_at_keys: List[int]      # timestamp when passing each key
    overshoot_amounts: List[float]  # how far past key center before turning

def calculate_velocity(x: List[float], y: List[float], t: List[int]) -> np.ndarray:
    """Calculate instantaneous velocity at each point."""
    if len(x) < 2:
        return np.array([0.0])
    
    x, y, t = np.array(x), np.array(y), np.array(t)
    
    # Distance traveled between points
    dx = np.diff(x)
    dy = np.diff(y)
    distances = np.sqrt(dx**2 + dy**2)
    
    # Time intervals in seconds
    dt = np.diff(t) / 1000.0
    dt = np.maximum(dt, 0.001)  # Avoid division by zero
    
    # Velocity magnitude
    velocities = distances / dt
    
    # Pad to match original length
    velocities = np.concatenate([[velocities[0]], velocities])
    
    return velocities

def calculate_curvature(x: List[float], y: List[float]) -> np.ndarray:
    """Calculate curvature at each point."""
    if len(x) < 3:
        return np.array([0.0] * len(x))
    
    x, y = np.array(x), np.array(y)
    
    dx = np.gradient(x)
    dy = np.gradient(y)
    ddx = np.gradient(dx)
    ddy = np.gradient(dy)
    
    numerator = np.abs(dx * ddy - dy * ddx)
    denominator = (dx**2 + dy**2)**(3/2) + 1e-10
    
    return numerator / denominator

def find_corners(x: List[float], y: List[float], threshold: float = 0.5) -> List[int]:
    """Find indices where direction changes significantly."""
    curvature = calculate_curvature(x, y)
    
    # Corners are where curvature exceeds threshold
    mean_curv = np.mean(curvature)
    corners = np.where(curvature > mean_curv + threshold * np.std(curvature))[0]
    
    # Cluster nearby corners into single events
    if len(corners) == 0:
        return []
    
    clustered = [corners[0]]
    for c in corners[1:]:
        if c - clustered[-1] > 5:  # At least 5 points apart
            clustered.append(c)
    
    return clustered

def find_key_passages(x: List[float], y: List[float], word: str) -> List[Tuple[int, str]]:
    """Find when the swipe passes near each key in the word."""
    keys = word_to_key_sequence(word)
    passages = []
    
    for key in keys:
        if key not in QWERTY_LAYOUT:
            continue
        key_pos = np.array(QWERTY_LAYOUT[key])
        
        # Find closest point to key
        distances = [np.sqrt((x[i] - key_pos[0])**2 + (y[i] - key_pos[1])**2) 
                     for i in range(len(x))]
        closest_idx = np.argmin(distances)
        passages.append((closest_idx, key))
    
    return passages

def analyze_swipe(x: List[float], y: List[float], t: List[int], word: str) -> SwipeMetrics:
    """Analyze a single swipe and extract physics metrics."""
    velocities = calculate_velocity(x, y, t)
    corners = find_corners(x, y)
    key_passages = find_key_passages(x, y, word)
    
    # Velocity at corners
    velocity_at_corners = [velocities[c] for c in corners if c < len(velocities)]
    
    # Corner radii (inverse of curvature)
    curvature = calculate_curvature(x, y)
    corner_radii = [1.0 / (curvature[c] + 0.01) for c in corners if c < len(curvature)]
    
    # Time at key passages
    time_at_keys = [t[idx] for idx, _ in key_passages if idx < len(t)]
    
    # Overshoot - how far past key center before velocity reverses
    # (simplified: distance from key center at closest approach)
    overshoot_amounts = []
    for idx, key in key_passages[:-1]:  # Skip last key
        key_pos = np.array(QWERTY_LAYOUT[key])
        min_dist = np.sqrt((x[idx] - key_pos[0])**2 + (y[idx] - key_pos[1])**2)
        overshoot_amounts.append(min_dist)
    
    return SwipeMetrics(
        total_duration_ms=t[-1] - t[0] if t else 0,
        avg_velocity=float(np.mean(velocities)),
        max_velocity=float(np.max(velocities)),
        min_velocity=float(np.min(velocities)),
        velocity_at_corners=velocity_at_corners,
        corner_radii=corner_radii,
        time_at_keys=time_at_keys,
        overshoot_amounts=overshoot_amounts
    )

# =============================================================================
# COMPARISON
# =============================================================================

def compare_swipes(word: str, futo_samples: List[dict], synthetic_samples: List[dict]) -> dict:
    """Compare FUTO samples vs synthetic samples for a word."""
    
    futo_metrics = []
    for s in futo_samples:
        try:
            m = analyze_swipe(s['x'], s['y'], s['t'], word)
            futo_metrics.append(m)
        except Exception as e:
            print(f"  Error analyzing FUTO sample: {e}")
    
    synth_metrics = []
    for s in synthetic_samples:
        try:
            m = analyze_swipe(s['curve']['x'], s['curve']['y'], s['curve']['t'], word)
            synth_metrics.append(m)
        except Exception as e:
            print(f"  Error analyzing synthetic sample: {e}")
    
    if not futo_metrics or not synth_metrics:
        return None
    
    # Aggregate metrics
    def avg_metric(metrics, attr):
        vals = [getattr(m, attr) for m in metrics]
        return np.mean(vals)
    
    def avg_list_metric(metrics, attr):
        all_vals = []
        for m in metrics:
            all_vals.extend(getattr(m, attr))
        return np.mean(all_vals) if all_vals else 0
    
    return {
        'word': word,
        'futo': {
            'duration_ms': avg_metric(futo_metrics, 'total_duration_ms'),
            'avg_velocity': avg_metric(futo_metrics, 'avg_velocity'),
            'max_velocity': avg_metric(futo_metrics, 'max_velocity'),
            'min_velocity': avg_metric(futo_metrics, 'min_velocity'),
            'velocity_at_corners': avg_list_metric(futo_metrics, 'velocity_at_corners'),
            'corner_radius': avg_list_metric(futo_metrics, 'corner_radii'),
            'overshoot': avg_list_metric(futo_metrics, 'overshoot_amounts'),
        },
        'synthetic': {
            'duration_ms': avg_metric(synth_metrics, 'total_duration_ms'),
            'avg_velocity': avg_metric(synth_metrics, 'avg_velocity'),
            'max_velocity': avg_metric(synth_metrics, 'max_velocity'),
            'min_velocity': avg_metric(synth_metrics, 'min_velocity'),
            'velocity_at_corners': avg_list_metric(synth_metrics, 'velocity_at_corners'),
            'corner_radius': avg_list_metric(synth_metrics, 'corner_radii'),
            'overshoot': avg_list_metric(synth_metrics, 'overshoot_amounts'),
        }
    }

def print_comparison(comparison: dict):
    """Pretty print a comparison result."""
    if not comparison:
        return
    
    word = comparison['word']
    futo = comparison['futo']
    synth = comparison['synthetic']
    
    print(f"\n{'='*60}")
    print(f"Word: '{word}'")
    print(f"{'='*60}")
    print(f"{'Metric':<25} {'FUTO':>12} {'Synthetic':>12} {'Delta':>10}")
    print(f"{'-'*60}")
    
    metrics = [
        ('Duration (ms)', 'duration_ms'),
        ('Avg Velocity', 'avg_velocity'),
        ('Max Velocity', 'max_velocity'),
        ('Min Velocity', 'min_velocity'),
        ('Velocity at Corners', 'velocity_at_corners'),
        ('Corner Radius', 'corner_radius'),
        ('Overshoot', 'overshoot'),
    ]
    
    for name, key in metrics:
        f_val = futo[key]
        s_val = synth[key]
        if f_val != 0:
            delta = ((s_val - f_val) / f_val) * 100
            delta_str = f"{delta:+.1f}%"
        else:
            delta_str = "N/A"
        print(f"{name:<25} {f_val:>12.3f} {s_val:>12.3f} {delta_str:>10}")

# =============================================================================
# MAIN
# =============================================================================

def main():
    parquet_path = 'futo_swipes.parquet'
    
    print("="*60)
    print("SYNTHETIC SWIPE VALIDATION")
    print("Comparing our physics model against real FUTO data")
    print("="*60)
    
    # Extract FUTO samples
    futo_samples = extract_futo_samples(parquet_path, TEST_WORDS, max_samples=5)
    
    # Generate synthetic samples
    print("\nGenerating synthetic samples...")
    
    all_comparisons = []
    
    for word in TEST_WORDS:
        word_lower = word.lower()
        
        if not futo_samples.get(word_lower):
            print(f"  No FUTO samples found for '{word}'")
            continue
        
        print(f"  Analyzing '{word}'...")
        
        # Generate synthetic
        synthetic = generate_synthetic_swipes(word, num_variations=5)
        
        if not synthetic:
            print(f"    Couldn't generate synthetic swipes")
            continue
        
        # Compare
        comparison = compare_swipes(word_lower, futo_samples[word_lower], synthetic)
        if comparison:
            all_comparisons.append(comparison)
            print_comparison(comparison)
    
    # Summary
    print("\n" + "="*60)
    print("SUMMARY")
    print("="*60)
    
    if all_comparisons:
        avg_deltas = {}
        for key in ['duration_ms', 'avg_velocity', 'velocity_at_corners', 'corner_radius', 'overshoot']:
            deltas = []
            for c in all_comparisons:
                f_val = c['futo'][key]
                s_val = c['synthetic'][key]
                if f_val != 0:
                    deltas.append(abs((s_val - f_val) / f_val) * 100)
            if deltas:
                avg_deltas[key] = np.mean(deltas)
        
        print("\nAverage absolute delta from FUTO ground truth:")
        for key, delta in avg_deltas.items():
            status = "✓ GOOD" if delta < 30 else "⚠ NEEDS TUNING" if delta < 50 else "✗ OFF"
            print(f"  {key:<25}: {delta:>6.1f}% {status}")
        
        print("\nRECOMMENDATION:")
        if all(d < 30 for d in avg_deltas.values()):
            print("  Our physics model is well-calibrated! ✓")
        else:
            print("  Some parameters need adjustment. Check the deltas above.")
    else:
        print("No valid comparisons could be made.")

if __name__ == "__main__":
    main()
