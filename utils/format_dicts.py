import os
import re

DICT_DIR = "app/src/main/assets/ime/dict"
UNIGRAM_FILE = os.path.join(DICT_DIR, "frequency_dictionary_en.txt")
BIGRAM_FILE = os.path.join(DICT_DIR, "frequency_bigram_en.txt")

# Config
MIN_FREQ = 50
# Only allow standard English letters and apostrophes. 
# Rejects numbers, symbols, and accented characters.
VALID_CHARS = re.compile(r"^[a-z']+$") 
# For bigrams: "word1 word2". Both must be valid.
VALID_BIGRAM = re.compile(r"^[a-z']+( [a-z']+)+$") 

def clean_and_convert(filepath, is_bigram=False):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return

    print(f"Processing {filepath}...")
    temp_path = filepath + ".tmp"
    
    kept = 0
    dropped_freq = 0
    dropped_chars = 0
    
    with open(filepath, 'r', encoding='utf-8') as infile, open(temp_path, 'w', encoding='utf-8') as outfile:
        for line in infile:
            line = line.strip()
            if not line: continue
            
            # Split frequency (last element)
            # Handle both Space and Tab separation robustly.
            # text = all parts except last, joined by space (to preserve bigram structure if any)
            # freq = last part
            parts = line.split()
            if len(parts) < 2:
                continue

            try:
                freq_str = parts[-1]
                freq = int(freq_str)
                text = " ".join(parts[:-1])
            except ValueError:
                continue # Skip malformed lines

            # Filter 1: Frequency
            if freq < MIN_FREQ:
                dropped_freq += 1
                continue

            # Filter 2: Characters
            # Ensure text is treated as lowercase for the regex check
            text_check = text.lower()
            
            pattern = VALID_BIGRAM if is_bigram else VALID_CHARS
            if not pattern.match(text_check):
                dropped_chars += 1
                continue

            # Write: "text\tfreq" (SymSpell Format)
            outfile.write(f"{text}\t{freq}\n")
            kept += 1

    # Overwrite original
    os.replace(temp_path, filepath)
    print(f"Done. Kept: {kept}. Dropped (Low Freq): {dropped_freq}. Dropped (Bad Chars): {dropped_chars}.")

if __name__ == "__main__":
    clean_and_convert(UNIGRAM_FILE, is_bigram=False)
    clean_and_convert(BIGRAM_FILE, is_bigram=True)
