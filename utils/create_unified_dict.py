#!/usr/bin/env python3
"""
Create a unified, production-quality dictionary for OmniBoard.

Strategy:
1. Start with AOSP (mobile-tuned word selection)
2. Filter out garbage (obscure proper nouns, non-alpha junk)
3. Keep essential proper nouns (days, months, common names)
4. Rescale frequencies to have good dynamic range
5. Apply custom boosts (contractions, user favorites)

Output: unified_dictionary.tsv (~60k words)
"""

import re
from pathlib import Path
from math import log, exp

# Paths
AOSP_INPUT = Path("app/src/main/assets/ime/dict/aosp_unigram.tsv")
OUTPUT = Path("app/src/main/assets/ime/dict/unified_dictionary.tsv")

# Target size and frequency range
TARGET_WORDS = 65000
MAX_FREQ = 10000000  # Output scale max
MIN_FREQ = 100       # Output scale min

# ============================================================================
# STRICT 2-LETTER WHITELIST - Only real conversational words, no acronyms
# ============================================================================
TWO_LETTER_WHITELIST = {
    # Core function words
    "to", "of", "in", "is", "as", "on", "by", "at", "or", "an",
    "be", "we", "he", "it", "so", "no", "do", "go", "my", "me",
    "up", "if", "us", "am",
    # Common casual
    "ok", "hi", "yo", "ya", "ew", "uh", "oh", "ah", "eh",
    # Pronouns / articles
    "id",  # as in "I'd" variant or Freudian id
}

# ============================================================================
# STRICT 3-LETTER WHITELIST - Common conversational words only
# No acronyms (BBC, RFK), no obscure proper nouns, no scientific units
# ============================================================================
THREE_LETTER_WHITELIST = {
    # Core function words / pronouns
    "the", "and", "for", "are", "but", "not", "you", "all", "can",
    "had", "her", "was", "one", "our", "out", "has", "his", "how",
    "its", "let", "may", "who", "boy", "did", "get", "him", "got",
    "now", "old", "see", "two", "way", "new", "any", "day", "too",
    "use", "she", "own", "say", "why",
    # Common verbs
    "add", "ask", "ate", "buy", "cut", "die", "eat", "end", "fly",
    "hit", "lay", "led", "lie", "met", "pay", "put", "ran", "run",
    "sat", "saw", "set", "sit", "sit", "top", "try", "win", "won",
    # Common nouns
    "age", "air", "arm", "art", "bag", "bar", "bat", "bed", "bit",
    "box", "bus", "car", "cat", "cup", "dad", "dog", "dot", "ear",
    "egg", "end", "eye", "fan", "fat", "few", "fun", "god", "gun",
    "guy", "hat", "ice", "ill", "ink", "job", "joy", "key", "kid",
    "law", "leg", "lip", "lot", "low", "man", "map", "men", "mix",
    "mom", "mud", "net", "oil", "pan", "pay", "pen", "pet", "pie",
    "pig", "pin", "pop", "pot", "pub", "red", "rib", "rod", "row",
    "rug", "sad", "sea", "sex", "sin", "sir", "six", "sky", "son",
    "sum", "sun", "tan", "tap", "tax", "tea", "ten", "tie", "tin",
    "tip", "toe", "top", "toy", "van", "war", "web", "wet", "wig",
    "win", "wit", "zoo",
    # Common adjectives
    "bad", "big", "dry", "due", "far", "fit", "gay", "hot", "mad",
    "odd", "raw", "shy",
    # Common adverbs / prepositions
    "ago", "yet", "off", "per", "via",
    # Casual / internet
    "lol", "omg", "wtf", "brb", "wow", "yes", "yep", "nah", "nay",
    "hey", "bye", "bro", "sis", "sup", "yay", "aww", "hmm", "umm",
    "duh", "huh", "meh", "ugh", "ooh", "oops",
    # Contractions parts (useful for suggestions)
    "ain", "aint",
    # Common names that people actually type
    "sam", "dan", "tom", "joe", "ben", "bob", "ted", "tim", "jim",
    "amy", "ann", "eve", "kim", "sue", "jen", "max", "ray", "lee",
    # Body / nature
    "gut", "hip", "paw", "rib", "bay", "fog", "mud", "oak", "oak",
    # Food / drink
    "ale", "bun", "ham", "jam", "nut", "oat", "pie", "rye", "soy",
    # Miscellaneous common
    "act", "aid", "aim", "arc", "ban", "bet", "bid", "bow", "cab",
    "cap", "cop", "cow", "cue", "dip", "dug", "era", "fee", "gap",
    "gem", "gym", "hen", "hug", "jam", "jar", "jet", "jog", "kit",
    "lab", "lap", "log", "mat", "mob", "nap", "nod", "nun", "nut",
    "oak", "owe", "owl", "pad", "pat", "pea", "peg", "pit", "pod",
    "pup", "rag", "ram", "rat", "rip", "rot", "rub", "sack", "sip",
    "sob", "spa", "spy", "sue", "tag", "tap", "tub", "tug", "vet",
    "vow", "wag", "wed", "win", "wok", "yawn", "zip",
}

# Words to always include (even if filtered)
ALWAYS_INCLUDE = {
    # Days/months
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "march", "april", "may", "june", "july", 
    "august", "september", "october", "november", "december",
    # Common names (first names)
    "sam", "john", "mike", "david", "james", "chris", "alex", "dan", "tom", "joe",
    "mary", "sarah", "lisa", "anna", "emma", "kate", "amy", "jen", "kim", "sue",
    # Tech/modern
    "google", "facebook", "twitter", "instagram", "youtube", "amazon", "apple",
    "iphone", "android", "wifi", "bluetooth", "uber", "lyft", "netflix", "spotify",
    # User favorites
    "kiry", "elijah", "chungus", "ok", "lol", "omg", "wtf", "nope", "yep", "yeah",
}

# Patterns to filter OUT (obscure proper nouns, junk)
FILTER_PATTERNS = [
    r"'s$",           # Possessives like "trujillo's" (keep base word, drop possessive)
    r"^[A-Z].*'s$",   # Capitalized possessives
]

# Words to explicitly exclude
EXCLUDE_WORDS = {
    "trujillo", "trujillos", "baha'i",  # Examples of obscure proper nouns
}

def load_aosp(path: Path) -> list[tuple[str, int]]:
    """Load AOSP dictionary, returns list of (word, freq) tuples."""
    entries = []
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                word = parts[0]
                try:
                    freq = int(parts[1])
                    entries.append((word, freq))
                except ValueError:
                    continue
    return entries

def should_include(word: str, freq: int) -> bool:
    """Decide if a word should be in the final dictionary."""
    lower = word.lower()
    
    # Always include favorites
    if lower in ALWAYS_INCLUDE:
        return True
    
    # Exclude explicit blocklist
    if lower in EXCLUDE_WORDS:
        return False
    
    # Filter by pattern
    for pattern in FILTER_PATTERNS:
        if re.search(pattern, word):
            return False
    
    # Filter out single letters (except a, i, I)
    if len(word) == 1 and lower not in ('a', 'i'):
        return False
    
    # Filter out words with weird characters
    if not re.match(r"^[a-zA-Z']+$", word):
        return False
    
    # Filter out very long words (usually garbage)
    if len(word) > 20:
        return False
    
    # ========================================================================
    # STRICT 2-LETTER FILTER - Only whitelisted words allowed
    # No acronyms (UK, TV, km), no obscure abbreviations
    # ========================================================================
    if len(lower) == 2:
        if lower not in TWO_LETTER_WHITELIST:
            return False
    
    # ========================================================================
    # STRICT 3-LETTER FILTER - Only whitelisted words allowed  
    # No acronyms (BBC, RFK, RKO), no scientific units, no obscure proper nouns
    # ========================================================================
    if len(lower) == 3:
        if lower not in THREE_LETTER_WHITELIST:
            return False
    
    # Keep common words (high frequency = common)
    return True

def rescale_frequencies(entries: list[tuple[str, int]]) -> list[tuple[str, int]]:
    """
    Rescale frequencies to have better dynamic range.
    
    AOSP frequencies are compressed (7M-10M). We want more spread
    so that "this" vs "trujillo" has a meaningful difference in score.
    
    Strategy: Log-linear rescaling
    """
    if not entries:
        return entries
    
    # Get min/max of input frequencies
    freqs = [f for _, f in entries]
    in_min = min(freqs)
    in_max = max(freqs)
    
    # Log-scale the input, then linear map to output range
    log_in_min = log(in_min + 1)
    log_in_max = log(in_max + 1)
    log_out_min = log(MIN_FREQ)
    log_out_max = log(MAX_FREQ)
    
    result = []
    for word, freq in entries:
        # Log of input frequency
        log_freq = log(freq + 1)
        # Normalize to 0-1
        normalized = (log_freq - log_in_min) / (log_in_max - log_in_min) if log_in_max > log_in_min else 0.5
        # Map to output log range
        log_output = log_out_min + normalized * (log_out_max - log_out_min)
        # Convert back from log
        output_freq = int(exp(log_output))
        result.append((word, output_freq))
    
    return result

def main():
    print(f"📖 Loading AOSP dictionary from {AOSP_INPUT}...")
    entries = load_aosp(AOSP_INPUT)
    print(f"   Loaded {len(entries)} words")
    
    # Filter
    print("🧹 Filtering...")
    filtered = [(w, f) for w, f in entries if should_include(w, f)]
    print(f"   Kept {len(filtered)} words after filtering")
    
    # Sort by frequency (descending) and take top N
    filtered.sort(key=lambda x: x[1], reverse=True)
    if len(filtered) > TARGET_WORDS:
        filtered = filtered[:TARGET_WORDS]
        print(f"   Trimmed to top {TARGET_WORDS} words")
    
    # Rescale frequencies
    print("📊 Rescaling frequencies for better dynamic range...")
    rescaled = rescale_frequencies(filtered)
    
    # Inject custom words that aren't in AOSP AND boost underweighted common words
    existing_words = {w.lower(): i for i, (w, _) in enumerate(rescaled)}
    
    # Custom words to inject or boost
    CUSTOM_WORDS = {
        # Common short words that need boosting (iOS-tier responsiveness)
        "wow": 2000000,
        "hi": 2000000,
        "bye": 1500000,
        "yes": 2500000,
        "no": 3000000,
        "ok": 3000000,
        "so": 2500000,
        "go": 2000000,
        "me": 3000000,
        "we": 3000000,
        "he": 2500000,
        "be": 2500000,
        "do": 2500000,
        "up": 2000000,
        "at": 2500000,
        "or": 2500000,
        "an": 2500000,
        "as": 2500000,
        "if": 2500000,
        "my": 2500000,
        # Custom/internet words
        "chungus": 500000,
        "kiry": 800000,
        "doin'": 600000,
        "lol": 1500000,
        "omg": 1000000,
        "wtf": 800000,
        "nope": 1200000,
        "yep": 1500000,
        "yeah": 2000000,
        "I'm": 3000000,
    }
    
    injected_count = 0
    boosted_count = 0
    for word, freq in CUSTOM_WORDS.items():
        lower = word.lower()
        if lower in existing_words:
            # Boost existing word if our value is higher
            idx = existing_words[lower]
            if rescaled[idx][1] < freq:
                rescaled[idx] = (rescaled[idx][0], freq)
                boosted_count += 1
        else:
            # Inject new word
            rescaled.append((word, freq))
            injected_count += 1
    
    if injected_count > 0:
        print(f"   Injected {injected_count} custom words")
    if boosted_count > 0:
        print(f"   Boosted {boosted_count} underweighted words")
    
    # Sort alphabetically for consistent output
    rescaled.sort(key=lambda x: x[0].lower())
    
    # Write output
    print(f"💾 Writing to {OUTPUT}...")
    with open(OUTPUT, 'w', encoding='utf-8') as f:
        for word, freq in rescaled:
            f.write(f"{word}\t{freq}\n")
    
    # Stats
    freqs = [f for _, f in rescaled]
    print(f"\n✅ Done! Created unified dictionary with {len(rescaled)} words")
    print(f"   Frequency range: {min(freqs):,} - {max(freqs):,}")
    
    # Show samples
    print("\n📝 Sample entries:")
    samples = ["the", "this", "hello", "trujillo", "sam", "chungus", "beautiful"]
    for word in samples:
        match = next(((w, f) for w, f in rescaled if w.lower() == word.lower()), None)
        if match:
            print(f"   {match[0]}: {match[1]:,}")
        else:
            print(f"   {word}: (not in dict)")

if __name__ == "__main__":
    main()
