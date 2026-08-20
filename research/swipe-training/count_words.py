#!/usr/bin/env python3
"""
Count swipe sample frequencies per word across FUTO dataset parquet shards.
"""
import sys
from pathlib import Path
import pyarrow.parquet as pq

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_SHARDS = sorted((REPO_ROOT / "data" / "swipe" / "raw" / "futo" / "swipe-1" / "train").glob("*.parquet"))

if len(sys.argv) > 1:
    target_files = [Path(sys.argv[1])]
elif Path("futo_swipes.parquet").exists():
    target_files = [Path("futo_swipes.parquet")]
elif DEFAULT_SHARDS:
    target_files = DEFAULT_SHARDS
else:
    print("Error: No FUTO parquet shards found. Run acquire_futo_data.py first.")
    sys.exit(1)

print(f"Counting word samples from {len(target_files)} parquet file(s)...")

word_counts = {}
total_rows = 0

for file_path in target_files:
    print(f"Reading {file_path.name}...")
    parquet_file = pq.ParquetFile(str(file_path))
    for i in range(parquet_file.metadata.num_row_groups):
        table = parquet_file.read_row_group(i)
        df = table.to_pandas()
        for word, count in df['word'].value_counts().items():
            w = str(word)
            word_counts[w] = word_counts.get(w, 0) + count
        total_rows += len(df)

print(f"\n=== FINAL STATS ===")
print(f"Total swipes: {total_rows:,}")
print(f"Unique words: {len(word_counts):,}")
print(f"Average samples per word: {total_rows / max(len(word_counts), 1):.1f}")

# Sort by count
sorted_words = sorted(word_counts.items(), key=lambda x: x[1], reverse=True)

print(f"\n=== TOP 20 WORDS BY SAMPLE COUNT ===")
for i, (word, count) in enumerate(sorted_words[:20], 1):
    print(f"{i:2d}. '{word}': {count:,} samples")
