#!/usr/bin/env python3
"""
Prepare Combined Training Data for Neural Swipe Typing

Combines:
1. FUTO real swipe data (939k samples)
2. Synthetic swipe data for missing vocabulary (6k samples)

Output: combined_training_data.jsonl in neural-swipe-typing format
"""

import pyarrow.parquet as pq
import json
import os
from collections import defaultdict

# Paths
FUTO_PARQUET = 'futo_swipes.parquet'
SYNTHETIC_JSONL = 'synthetic_swipes_final.jsonl'
OUTPUT_JSONL = 'combined_training_data.jsonl'

# Limit FUTO samples per word to avoid imbalance
MAX_SAMPLES_PER_WORD = 50

def convert_futo_to_jsonl():
    """Convert FUTO parquet to JSONL format."""
    print("Converting FUTO parquet to JSONL...")
    
    parquet_file = pq.ParquetFile(FUTO_PARQUET)
    word_counts = defaultdict(int)
    total_samples = 0
    skipped = 0
    
    futo_samples = []
    
    for i in range(parquet_file.metadata.num_row_groups):
        print(f"  Processing row group {i+1}/{parquet_file.metadata.num_row_groups}...")
        table = parquet_file.read_row_group(i)
        df = table.to_pandas()
        
        for _, row in df.iterrows():
            word = row['word']
            
            # Skip if we have enough samples for this word
            if word_counts[word] >= MAX_SAMPLES_PER_WORD:
                skipped += 1
                continue
            
            points = row['data']
            
            # FUTO data is already normalized 0-1
            x = [float(p['x']) for p in points]
            y = [float(p['y']) for p in points]
            t = [int(p['t']) for p in points]
            
            # Skip empty or invalid samples
            if len(x) < 3:
                continue
            
            # Normalize timestamps to start at 0
            t_start = t[0]
            t = [ts - t_start for ts in t]
            
            sample = {
                "word": word,
                "curve": {
                    "x": x,
                    "y": y,
                    "t": t,
                    "grid_name": "qwerty_en"
                }
            }
            
            futo_samples.append(sample)
            word_counts[word] += 1
            total_samples += 1
    
    print(f"  Converted {total_samples} FUTO samples ({skipped} skipped for balance)")
    print(f"  Unique words: {len(word_counts)}")
    
    return futo_samples

def load_synthetic():
    """Load synthetic swipe data."""
    print(f"Loading synthetic data from {SYNTHETIC_JSONL}...")
    
    samples = []
    with open(SYNTHETIC_JSONL, 'r') as f:
        for line in f:
            sample = json.loads(line.strip())
            samples.append(sample)
    
    print(f"  Loaded {len(samples)} synthetic samples")
    return samples

def main():
    print("="*60)
    print("PREPARING COMBINED TRAINING DATA")
    print("="*60)
    
    # Convert FUTO
    futo_samples = convert_futo_to_jsonl()
    
    # Load synthetic
    synthetic_samples = load_synthetic()
    
    # Combine
    all_samples = futo_samples + synthetic_samples
    
    print(f"\nCombined dataset:")
    print(f"  FUTO samples: {len(futo_samples)}")
    print(f"  Synthetic samples: {len(synthetic_samples)}")
    print(f"  Total: {len(all_samples)}")
    
    # Write output
    print(f"\nWriting to {OUTPUT_JSONL}...")
    with open(OUTPUT_JSONL, 'w') as f:
        for sample in all_samples:
            f.write(json.dumps(sample) + '\n')
    
    # Stats
    file_size = os.path.getsize(OUTPUT_JSONL) / (1024 * 1024)
    print(f"Done! Output file: {file_size:.1f} MB")
    
    print("\n" + "="*60)
    print("NEXT STEP:")
    print("="*60)
    print(f"Copy {OUTPUT_JSONL} or reference it in your training config.")
    print("Then run: python -m src.train --train_config configs/train/train_english.json")

if __name__ == "__main__":
    main()
