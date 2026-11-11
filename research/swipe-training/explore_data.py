#!/usr/bin/env python3
"""
Quick exploration of FUTO swipe parquet shards to inspect schema and sample rows.
Memory-efficient version that doesn't load the whole file.
"""
import sys
from pathlib import Path
import pyarrow.parquet as pq

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_PARQUET = REPO_ROOT / "data" / "swipe" / "raw" / "futo" / "swipe-1" / "train" / "0.parquet"

parquet_path = sys.argv[1] if len(sys.argv) > 1 else (
    "futo_swipes.parquet" if Path("futo_swipes.parquet").exists() else str(DEFAULT_PARQUET)
)

print(f"Reading parquet metadata from: {parquet_path}")
parquet_file = pq.ParquetFile(parquet_path)

print(f"\n=== FILE INFO ===")
print(f"Number of rows: {parquet_file.metadata.num_rows:,}")
print(f"Number of row groups: {parquet_file.metadata.num_row_groups}")
print(f"Schema: {parquet_file.schema}")

print(f"\n=== LOADING FIRST 1000 ROWS ===")
table = parquet_file.read_row_group(0)
df = table.to_pandas().head(1000)

print(f"Columns: {list(df.columns)}")
print(f"\nFirst 3 rows:")
print(df.head(3))

print(f"\n=== WORD STATS (from first 1000) ===")
print(f"Unique words in sample: {df['word'].nunique()}")
print(f"\nMost common words in sample:")
print(df['word'].value_counts().head(20))

print(f"\n=== CANVAS INFO ===")
if 'canvas_width' in df.columns:
    print(f"Canvas widths: {df['canvas_width'].unique()[:5]}")
if 'canvas_height' in df.columns:
    print(f"Canvas heights: {df['canvas_height'].unique()[:5]}")

print(f"\n=== EXAMPLE SWIPE ===")
example = df.iloc[0]
print(f"Word: '{example['word']}'")
if 'canvas_width' in df.columns:
    print(f"Canvas: {example['canvas_width']} x {example['canvas_height']}")
print(f"Data type: {type(example['data'])}")
print(f"Data sample: {str(example['data'])[:150]}...")
