import os
import re

DICT_DIR = "app/src/main/assets/ime/dict"
UNIGRAM_FILE = os.path.join(DICT_DIR, "frequency_dictionary_en.txt")
BIGRAM_FILE = os.path.join(DICT_DIR, "frequency_bigram_en.txt")

# Config
# "Platinum List" Strategy
# 1. Keep ALL words with apostrophes (Contractions).
# 2. Keep only the Top 15,000 Alpha-only words.
TOP_N_LIMIT = 15000

def clean_and_convert(filepath, is_bigram=False):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return

    print(f"Processing {filepath}...")
    temp_path = filepath + ".tmp"
    
    all_entries = []
    
    # 1. Read ALL entries into memory
    with open(filepath, 'r', encoding='utf-8') as infile:
        for line in infile:
            line = line.strip()
            if not line: continue
            
            parts = line.split()
            if len(parts) < 2: continue

            try:
                freq_str = parts[-1]
                freq = int(freq_str)
                text = " ".join(parts[:-1])
                all_entries.append((text, freq))
            except ValueError:
                continue

    # 2. Sort by Frequency (Descending)
    # This ensures the "Top N" are actually the most frequent.
    all_entries.sort(key=lambda x: x[1], reverse=True)

    kept_entries = []
    alpha_count = 0
    
    for text, freq in all_entries:
        # Rule 1: Keep ALL Contractions (words with apostrophe)
        if "'" in text:
            kept_entries.append((text, freq))
            continue
            
        # Rule 2: Limit Alpha-only words to TOP_N_LIMIT
        # We assume the list is sorted, so the first 15k alpha words we see are the top 15k.
        if is_bigram:
             # Bigrams logic: Keep if it looks like "word word" (alpha/apostrophe only)
             # We don't strictly apply the 15k limit to bigrams yet unless requested, 
             # but let's keep them if they are reasonably frequent?
             # User instruction was specific to "Dictionary" (Unigrams). 
             # Let's pass bigrams through with a basic check for now to avoid nuking context.
             if all(part.replace("'", "").isalpha() for part in text.split()):
                 kept_entries.append((text, freq))
        else:
            # Unigrams logic
            if text.isalpha():
                if alpha_count < TOP_N_LIMIT:
                    kept_entries.append((text, freq))
                    alpha_count += 1
            # If not alpha and no apostrophe (e.g. numbers), drop it.

    print(f"Kept {len(kept_entries)} entries. (Alpha limit: {TOP_N_LIMIT})")

    # 3. Write back
    with open(temp_path, 'w', encoding='utf-8') as outfile:
        for text, freq in kept_entries:
            outfile.write(f"{text}\t{freq}\n")

    os.replace(temp_path, filepath)

if __name__ == "__main__":
    clean_and_convert(UNIGRAM_FILE, is_bigram=False)
    # clean_and_convert(BIGRAM_FILE, is_bigram=True) # Optional: Skip bigrams processing to preserve them? 
    # Actually, user didn't ask to nuke bigrams, but bigrams are useless if unigrams are missing.
    # But for now, let's leave bigrams ALONE as they were already filtered > 50 freq.
    # Only processing UNIGRAMS for the "Platinum List".
