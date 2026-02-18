#!/usr/bin/env python3
"""
Clean SMS/telecom spam and dev jargon from bigram and phrase data.
Run from the repo root: python3 clean_bigram_spam.py
"""
import re
import sys

BIGRAM_FILE = "app/src/main/assets/ime/dict/final_mobile_bigrams.tsv"
PHRASE_FILE = "app/src/main/assets/ime/dict/personal_phrases.tsv"

# Known SMS/telecom spam tokens found in corpus
SMS_SPAM_TOKENS = {
    "sptv", "smsrewards", "jsco", "txtno", "ntt", "wq", "ntet",
    "topup", "plase", "dnt", "frm", "bfore", "lyk", "shd", "gurl",
    "giv", "thx", "luv", "dey", "wen", "stil", "d8",
    "ltd", "plc", "pvt",  # Business suffixes from SMS
}

# Dev/project jargon that shouldn't be in personal phrases
DEV_JARGON_PATTERNS = [
    r"\bgemini\b", r"\.md\b", r"\.kt\b", r"\.json\b", r"\.tsv\b",
    r"\bsnygg\b", r"\bmod row", r"\bmod rows\b", r"\balpha row",
    r"\bflorisboard\b", r"\bsymspell\b", r"\bcomposable\b",
    r"\bkotlin\b", r"\bjetpack\b", r"\bsmartbar\b",
    r"\btilde\b", r"\bslash vault\b", r"\bremote origin\b",
    r"\bpadding is\b", r"\bgit\b", r"\bcommit\b",
    r"\bnpx\b", r"\bvault slash\b",
]
DEV_JARGON_RE = re.compile("|".join(DEV_JARGON_PATTERNS), re.IGNORECASE)


def is_spam_bigram(pair_str):
    """Check if a bigram pair contains SMS spam tokens."""
    words = pair_str.lower().split()
    for w in words:
        if w in SMS_SPAM_TOKENS:
            return True
        # Filter single-letter nonsense pairs (but keep legit ones like "i")
        if len(w) == 1 and w not in {"i", "a"}:
            # Only flag if BOTH words are single letters
            pass
    return False


def clean_bigrams():
    print(f"🧹 Cleaning bigrams from {BIGRAM_FILE}...")

    with open(BIGRAM_FILE, 'r') as f:
        lines = f.readlines()

    original_count = len(lines)
    cleaned = []
    removed = []

    for line in lines:
        parts = line.strip().split('\t')
        if len(parts) != 2:
            continue
        pair = parts[0]
        if is_spam_bigram(pair):
            removed.append(pair)
        else:
            cleaned.append(line)

    with open(BIGRAM_FILE, 'w') as f:
        f.writelines(cleaned)

    print(f"   Original: {original_count} bigrams")
    print(f"   Removed:  {len(removed)} spam entries")
    print(f"   Kept:     {len(cleaned)} clean entries")

    if removed:
        print(f"   Sample removed: {removed[:10]}")


def clean_phrases():
    print(f"\n🧹 Cleaning dev jargon from {PHRASE_FILE}...")

    with open(PHRASE_FILE, 'r') as f:
        lines = f.readlines()

    original_count = len(lines)
    cleaned = []
    removed = []

    for line in lines:
        parts = line.strip().split('\t')
        if len(parts) < 3:
            continue

        context = parts[0]
        continuation = parts[1]
        full_text = f"{context} {continuation}"

        if DEV_JARGON_RE.search(full_text):
            removed.append(full_text.strip())
        else:
            cleaned.append(line)

    with open(PHRASE_FILE, 'w') as f:
        f.writelines(cleaned)

    print(f"   Original: {original_count} phrases")
    print(f"   Removed:  {len(removed)} jargon entries")
    print(f"   Kept:     {len(cleaned)} clean entries")

    if removed:
        print(f"   Sample removed: {removed[:15]}")


if __name__ == "__main__":
    clean_bigrams()
    clean_phrases()
    print("\n✅ Cleanup complete!")
