#!/usr/bin/env python3
"""
Count how many swipe samples we have for each word.
"""
import pyarrow.parquet as pq

print("Counting word samples from all 939,550 swipes...")
print("(This processes the file in chunks to avoid memory issues)\n")

parquet_file = pq.ParquetFile('futo_swipes.parquet')

# Process all row groups
word_counts = {}
total_rows = 0

for i in range(parquet_file.metadata.num_row_groups):
    table = parquet_file.read_row_group(i)
    df = table.to_pandas()
    
    # Count words in this chunk
    for word, count in df['word'].value_counts().items():
        word_counts[word] = word_counts.get(word, 0) + count
    
    total_rows += len(df)
    print(f"Processed row group {i+1}/{parquet_file.metadata.num_row_groups} ({total_rows:,} rows so far)")

print(f"\n=== FINAL STATS ===")
print(f"Total swipes: {total_rows:,}")
print(f"Unique words: {len(word_counts):,}")
print(f"Average samples per word: {total_rows / len(word_counts):.1f}")

# Sort by count
sorted_words = sorted(word_counts.items(), key=lambda x: x[1], reverse=True)

print(f"\n=== TOP 50 WORDS BY SAMPLE COUNT ===")
for i, (word, count) in enumerate(sorted_words[:50], 1):
    print(f"{i:2d}. '{word}': {count:,} samples")

print(f"\n=== BOTTOM 20 WORDS (rarest) ===")
for word, count in sorted_words[-20:]:
    print(f"'{word}': {count} sample(s)")

# Check specific common words you'd type
test_words = ['the', 'and', 'you', 'for', 'that', 'with', 'have', 'this', 'from', 'hello', 'good', 'thanks']
print(f"\n=== SAMPLES FOR COMMON TYPING WORDS ===")
for word in test_words:
    count = word_counts.get(word, 0)
    if count > 0:
        print(f"'{word}': {count:,} samples")
    else:
        print(f"'{word}': NOT IN DATASET")
