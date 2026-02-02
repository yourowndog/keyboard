import re
from collections import Counter

INPUT_FILE = "sam_journal.txt"
OUTPUT_FILE = "sam_bigrams.tsv"

def get_tokens(text):
    # Basic tokenization: lowercase, remove non-alphanumeric (keep apostrophes?)
    # Let's keep it simple: split by space, strip punctuation
    text = text.lower()
    # Replace common punctuation with space to break bigrams across sentences
    text = re.sub(r'[.!?,\n]', ' ', text)
    tokens = [t.strip() for t in text.split() if t.strip()]
    return tokens

with open(INPUT_FILE, 'r') as f:
    text = f.read()

tokens = get_tokens(text)
bigrams = zip(tokens, tokens[1:])
counts = Counter(bigrams)

with open(OUTPUT_FILE, 'w') as f:
    for (w1, w2), count in counts.most_common():
        f.write(f"{w1}\t{w2}\t{count}\n")

print(f"Generated {len(counts)} bigrams to {OUTPUT_FILE}")
