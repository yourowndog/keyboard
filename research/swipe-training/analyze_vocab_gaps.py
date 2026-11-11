#!/usr/bin/env python3
"""
Compare FUTO swipe data vocabulary with keyboard dictionary.
Find gaps and overlaps to inform synthetic data generation.
"""
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT_DIR = Path(__file__).resolve().parent
FUTO_WORDS_TXT = SCRIPT_DIR / "futo_words_unique.txt"
UNIFIED_DICT_TSV = REPO_ROOT / "app" / "src" / "main" / "assets" / "ime" / "dict" / "unified_dictionary.tsv"
DEFAULT_SHARDS = sorted((REPO_ROOT / "data" / "swipe" / "raw" / "futo" / "swipe-1" / "train").glob("*.parquet"))

# Load FUTO words
futo_words = set()
if FUTO_WORDS_TXT.exists():
    print(f"Loading FUTO word list from {FUTO_WORDS_TXT.name}...")
    with open(FUTO_WORDS_TXT, "r", encoding="utf-8") as f:
        for line in f:
            w = line.strip()
            if w:
                futo_words.add(w)
elif DEFAULT_SHARDS:
    import pyarrow.parquet as pq
    print(f"Loading FUTO words from {len(DEFAULT_SHARDS)} parquet shards...")
    for file_path in DEFAULT_SHARDS:
        parquet_file = pq.ParquetFile(str(file_path))
        for i in range(parquet_file.metadata.num_row_groups):
            table = parquet_file.read_row_group(i)
            df = table.to_pandas()
            futo_words.update(df['word'].dropna().astype(str).unique())
else:
    print("Error: Neither futo_words_unique.txt nor FUTO parquet files were found.")
    sys.exit(1)

print(f"FUTO reference vocabulary: {len(futo_words):,} unique words")

# Load dictionary words (with frequencies)
print(f"\nLoading keyboard dictionary from {UNIFIED_DICT_TSV.name}...")
dict_words = {}
if UNIFIED_DICT_TSV.exists():
    with open(UNIFIED_DICT_TSV, 'r', encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                word = parts[0]
                try:
                    freq = int(parts[1])
                except ValueError:
                    freq = 0
                dict_words[word] = freq
else:
    print(f"Warning: {UNIFIED_DICT_TSV} not found.")

print(f"Dictionary has {len(dict_words):,} unique words")

# Find gaps
print("\n" + "="*60)
print("ANALYSIS")
print("="*60)

dict_only = set(dict_words.keys()) - futo_words
dict_only_sorted = sorted(dict_only, key=lambda w: dict_words.get(w, 0), reverse=True)

print(f"\n1. Words in DICTIONARY but NOT in FUTO: {len(dict_only):,}")
print("\nTop 20 most frequent missing words:")
for i, word in enumerate(dict_only_sorted[:20], 1):
    freq = dict_words[word]
    print(f"{i:2d}. {word:20s} (freq: {freq:,})")

futo_only = futo_words - set(dict_words.keys())
print(f"\n2. Words in FUTO but NOT in DICTIONARY: {len(futo_only):,}")

overlap = futo_words & set(dict_words.keys())
print(f"\n3. Words in BOTH: {len(overlap):,}")

# Save top 1000 missing words to file
output_file = SCRIPT_DIR / 'missing_words_top1000.txt'
with open(output_file, 'w', encoding="utf-8") as f:
    for word in dict_only_sorted[:1000]:
        f.write(f"{word}\n")

print(f"\nUpdated top 1000 missing words to: {output_file.relative_to(REPO_ROOT)}")
