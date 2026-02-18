#!/usr/bin/env python3
import math

BIGRAM_FILE = "keyboard-local/app/src/main/assets/ime/dict/final_mobile_bigrams.tsv"
GAMMA = 0.4
TARGET_MAX = 10000

def rescale():
    print(f"🔄 Rescaling bigram frequencies (gamma={GAMMA})...")
    
    with open(BIGRAM_FILE, 'r') as f:
        lines = f.readlines()

    # Find max for scaling factor
    max_old = 0
    data = []
    for line in lines:
        parts = line.strip().split('\t')
        if len(parts) != 2: continue
        try:
            freq = int(parts[1])
            if freq > max_old: max_old = freq
            data.append((parts[0], freq))
        except ValueError:
            continue

    if max_old == 0:
        print("❌ No data found.")
        return

    factor = TARGET_MAX / (max_old ** GAMMA)
    
    new_lines = []
    for words, old_freq in data:
        new_freq = int((old_freq ** GAMMA) * factor)
        # Ensure at least 1
        new_freq = max(1, new_freq)
        new_lines.append(words + "\t" + str(new_freq) + "\n")

    with open(BIGRAM_FILE, 'w') as f:
        f.writelines(new_lines)
    
    print(f"✅ Successfully rescaled {len(new_lines)} bigrams.")
    print(f"📈 Old Max: {max_old} -> New Max: {TARGET_MAX}")

if __name__ == "__main__":
    rescale()
