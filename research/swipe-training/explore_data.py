#!/usr/bin/env python3
"""
Quick exploration of futo_swipes.parquet to understand what we're working with.
Memory-efficient version that doesn't load the whole file.
"""
import pyarrow.parquet as pq

# Read just the metadata without loading data
print("Reading parquet metadata...")
parquet_file = pq.ParquetFile('futo_swipes.parquet')

print(f"\n=== FILE INFO ===")
print(f"Number of rows: {parquet_file.metadata.num_rows:,}")
print(f"Number of row groups: {parquet_file.metadata.num_row_groups}")
print(f"Schema: {parquet_file.schema}")

print(f"\n=== LOADING FIRST 1000 ROWS ===")
# Read just first 1000 rows
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
    print(f"Canvas widths: {df['canvas_width'].unique()}")
if 'canvas_height' in df.columns:
    print(f"Canvas heights: {df['canvas_height'].unique()}")

print(f"\n=== EXAMPLE SWIPE ===")
example = df.iloc[0]
print(f"Word: '{example['word']}'")
if 'canvas_width' in df.columns:
    print(f"Canvas: {example['canvas_width']} x {example['canvas_height']}")
print(f"Data type: {type(example['data'])}")
print(f"Data: {example['data']}")
