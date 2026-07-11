#!/usr/bin/env python3
"""
Investigate FUTO coordinate system to understand the normalization.
"""

import pyarrow.parquet as pq
import numpy as np

parquet_path = 'futo_swipes.parquet'
parquet_file = pq.ParquetFile(parquet_path)

# Get first row group
table = parquet_file.read_row_group(0)
df = table.to_pandas()

# Find a sample for 'the'
for _, row in df.iterrows():
    if row['word'].lower() == 'the':
        print(f"Word: {row['word']}")
        print(f"Canvas: {row['canvas_width']} x {row['canvas_height']}")
        print(f"Number of points: {len(row['data'])}")
        print("\nRaw point data (first 5 points):")
        for i, point in enumerate(row['data'][:5]):
            print(f"  Point {i}: x={point['x']:.2f}, y={point['y']:.2f}, t={point['t']}")
        
        # Calculate ranges
        x_vals = [p['x'] for p in row['data']]
        y_vals = [p['y'] for p in row['data']]
        print(f"\nRaw coordinate ranges:")
        print(f"  X: {min(x_vals):.2f} to {max(x_vals):.2f} (range: {max(x_vals)-min(x_vals):.2f})")
        print(f"  Y: {min(y_vals):.2f} to {max(y_vals):.2f} (range: {max(y_vals)-min(y_vals):.2f})")
        
        print(f"\nIf we normalize by canvas:")
        print(f"  X_norm range: {(max(x_vals)-min(x_vals))/row['canvas_width']:.6f}")
        print(f"  Y_norm range: {(max(y_vals)-min(y_vals))/row['canvas_height']:.6f}")
        
        print("\n" + "="*60)
        print("INTERPRETATION:")
        print("="*60)
        print(f"The raw X coordinates go from {min(x_vals):.2f} to {max(x_vals):.2f}")
        print(f"The canvas width is {row['canvas_width']}")
        print(f"So the swipe is only using {(max(x_vals)-min(x_vals))/row['canvas_width']*100:.2f}% of the canvas width")
        break

# Now check multiple samples
print("\n\n" + "="*60)
print("CHECKING RAW COORDINATE SCALE ACROSS MULTIPLE SAMPLES")
print("="*60)

sample_count = 0
x_ranges = []
y_ranges = []
x_maxes = []
canvas_widths = []

for _, row in df.iterrows():
    if sample_count >= 20:
        break
    
    x_vals = [p['x'] for p in row['data']]
    y_vals = [p['y'] for p in row['data']]
    
    x_ranges.append(max(x_vals) - min(x_vals))
    y_ranges.append(max(y_vals) - min(y_vals))
    x_maxes.append(max(x_vals))
    canvas_widths.append(row['canvas_width'])
    sample_count += 1

print(f"\nAcross {sample_count} samples:")
print(f"  Raw X ranges: {np.mean(x_ranges):.2f} (min: {np.min(x_ranges):.2f}, max: {np.max(x_ranges):.2f})")
print(f"  Raw Y ranges: {np.mean(y_ranges):.2f} (min: {np.min(y_ranges):.2f}, max: {np.max(y_ranges):.2f})")
print(f"  Max X values seen: {np.max(x_maxes):.2f}")
print(f"  Canvas widths: {np.mean(canvas_widths):.2f} (min: {np.min(canvas_widths):.2f}, max: {np.max(canvas_widths):.2f})")

print("\n")
print("="*60)
print("CONCLUSION")
print("="*60)
print("The raw X,Y coordinates appear to be in PIXELS, not normalized.")
print("We should NOT divide by canvas_width/height if we want 0-1 normalization")
print("based on keyboard position. Instead, we need to understand what coordinate")
print("system FUTO is using.")
