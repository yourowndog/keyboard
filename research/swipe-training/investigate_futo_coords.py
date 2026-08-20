#!/usr/bin/env python3
"""
Investigate FUTO coordinate system to understand normalization and canvas ranges.
"""

import sys
from pathlib import Path
import pyarrow.parquet as pq
import numpy as np

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_PARQUET = REPO_ROOT / "data" / "swipe" / "raw" / "futo" / "swipe-1" / "train" / "0.parquet"

parquet_path = sys.argv[1] if len(sys.argv) > 1 else (
    "futo_swipes.parquet" if Path("futo_swipes.parquet").exists() else str(DEFAULT_PARQUET)
)

print(f"Reading from: {parquet_path}")
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
            print(f"  Point {i}: x={point['x']:.4f}, y={point['y']:.4f}, t={point['t']}")
        
        # Calculate ranges
        x_vals = [p['x'] for p in row['data']]
        y_vals = [p['y'] for p in row['data']]
        print(f"\nRaw coordinate ranges:")
        print(f"  X: {min(x_vals):.4f} to {max(x_vals):.4f} (range: {max(x_vals)-min(x_vals):.4f})")
        print(f"  Y: {min(y_vals):.4f} to {max(y_vals):.4f} (range: {max(y_vals)-min(y_vals):.4f})")
        break

# Now check multiple samples
print("\n" + "="*60)
print("CHECKING COORDINATE SCALE ACROSS MULTIPLE SAMPLES")
print("="*60)

sample_count = 0
for _, row in df.iterrows():
    if sample_count >= 10:
        break
    
    x_vals = [p['x'] for p in row['data']]
    y_vals = [p['y'] for p in row['data']]
    print(f"[{row['word']}] Canvas: {row['canvas_width']}x{row['canvas_height']}, X: [{min(x_vals):.3f}, {max(x_vals):.3f}], Y: [{min(y_vals):.3f}, {max(y_vals):.3f}], Points: {len(row['data'])}")
    sample_count += 1
