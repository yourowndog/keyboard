#!/usr/bin/env python3
"""
Compare FUTO swipe data vocabulary with keyboard dictionary.
Find gaps and overlaps to inform synthetic data generation.
"""
import pyarrow.parquet as pq
import sys

# Load FUTO words
print("Loading FUTO word list...")
parquet_file = pq.ParquetFile('futo_swipes.parquet')
futo_words = set()

for i in range(parquet_file.metadata.num_row_groups):
    table = parquet_file.read_row_group(i)
    df = table.to_pandas()
    futo_words.update(df['word'].unique())

print(f"FUTO has {len(futo_words)} unique words")

# Load dictionary words (with frequencies)
print("\nLoading keyboard dictionary...")
dict_words = {}
with open('/home/sam/projects/keyboard/app/src/main/assets/ime/dict/unified_dictionary.tsv', 'r') as f:
    for line in f:
        parts = line.strip().split('\t')
        if len(parts) >= 2:
            word = parts[0]
            freq = int(parts[1])
            dict_words[word] = freq

print(f"Dictionary has {len(dict_words)} unique words")

# Find gaps
print("\n" + "="*60)
print("ANALYSIS")
print("="*60)

# Words in dictionary but NOT in FUTO
dict_only = set(dict_words.keys()) - futo_words
dict_only_sorted = sorted(dict_only, key=lambda w: dict_words.get(w, 0), reverse=True)

print(f"\n1. Words in DICTIONARY but NOT in FUTO: {len(dict_only)}")
print("\nTop 50 most frequent missing words:")
for i, word in enumerate(dict_only_sorted[:50], 1):
    freq = dict_words[word]
    print(f"{i:2d}. {word:20s} (freq: {freq:,})")

# Words in FUTO but NOT in dictionary
futo_only = futo_words - set(dict_words.keys())
print(f"\n2. Words in FUTO but NOT in DICTIONARY: {len(futo_only)}")
print("\nSample (first 30):")
for i, word in enumerate(sorted(list(futo_only))[:30], 1):
    print(f"{i:2d}. {word}")

# Overlap
overlap = futo_words & set(dict_words.keys())
print(f"\n3. Words in BOTH: {len(overlap)}")

# Summary
print("\n" + "="*60)
print("RECOMMENDATION")
print("="*60)
print(f"\nGenerate synthetic swipes for the top {min(1000, len(dict_only))} missing words")
print(f"This covers the most common words users will actually type.")

# Save top 1000 missing words to file
output_file = 'missing_words_top1000.txt'
with open(output_file, 'w') as f:
    for word in dict_only_sorted[:1000]:
        f.write(f"{word}\n")

print(f"\nSaved top 1000 missing words to: {output_file}")
