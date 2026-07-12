#!/usr/bin/env python3
"""
build_dictionary.py - Rebuild OmniBoard's language assets from first principles.

Replaces the legacy 4-source unified dictionary with:
  1. unified_dictionary.tsv  - AOSP English wordlist as the frequency spine,
                               overlaid with vocabulary + frequencies mined from
                               Sam's harvest corpus (usage_harvest.md).
  2. final_mobile_bigrams.tsv - bigrams rebuilt 100% from the harvest corpus.
  3. personal_phrases.tsv     - next-word phrase predictions ("w1 w2" -> w3),
                               rebuilt 100% from the harvest corpus.

Frequency model:
  - AOSP f (0..255, already log-scale) maps log-linearly onto [100 .. 10^7]:
        ln(freq) = ln(100) + (f / f_max) * (ln(10^7) - ln(100))
    The runtime consumes ln(freq+1) (SuggestionEngine.loadUnigrams), so this
    preserves AOSP's relative Zipf spacing exactly. (The old converter mapped
    log(f)/log(max) which crushed everything into the top decade.)
  - Personal counts map linearly onto the same axis, calibrated so the most
    frequent corpus word sits at 10^7: freq_p = count * (10^7 / max_count).
  - Words in both: max(base, personal). Personal-only words need evidence:
    typing_count >= 4 (habitual = intentional), OR voice_count >= 2 CORROBORATED
    by typing_count >= 1, OR explicit membership in approved_vocabulary.
    Voice alone is NOT sufficient: Whisper repeats the same mis-transcription
    consistently, so voice repetition manufactures phantom vocabulary.

Usage:
  python3 build_dictionary.py            # writes to app/src/main/assets/ime/dict/
  python3 build_dictionary.py --dry-run  # stats + sanity checks only
"""

import json
import math
import re
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
HARVEST = REPO / "data/harvest/raw/usage_harvest.md"
DICT_DIR = REPO / "app/src/main/assets/ime/dict"
AOSP_COMBINED = REPO / "dict_sources/en_wordlist.combined.txt"

FREQ_MIN, FREQ_MAX = 100.0, 10_000_000.0
LN_MIN, LN_MAX = math.log(FREQ_MIN), math.log(FREQ_MAX)

# Corpus admission thresholds for words absent from the AOSP base
VOICE_MIN, TYPING_MIN = 2, 4
MAX_TOKEN_LEN = 22
BIGRAM_MIN, PHRASE_MIN = 2, 3

TOKEN_RE = re.compile(r"[A-Za-z']+")

# --- corpus triage (ported from harvest_manifest.py) -------------------------

def classify_session(text: str) -> str:
    if text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    if not text:
        return "empty"
    balanced_braces = text.count("{") > 0 and text.count("{") == text.count("}")
    balanced_brackets = text.count("[") > 0 and text.count("[") == text.count("]")
    special = sum(1 for c in text if not (c.isalnum() or c.isspace()))
    if balanced_braces or balanced_brackets or special / len(text) > 0.25:
        return "code_json"
    if any(s in text for s in ("http://", "https://", "www.")) or \
            text.strip().startswith("$") or "/data/" in text:
        return "url_command"
    tokens = text.split()
    if not tokens:
        return "empty"
    mean_len = sum(len(t) for t in tokens) / len(tokens)
    if any(len(t) > MAX_TOKEN_LEN for t in tokens) or mean_len > 12.0:
        return "concatenated"
    return "clean"


def parse_line(line: str):
    line = line.strip()
    if not line or line[0] in "#<-" or line.startswith("Copy to"):
        return None
    parts = [p.strip() for p in line.split("|")]
    if len(parts) < 2:
        return None
    m = re.match(r"^\[([^\]]+)\]", parts[0])
    if not m:
        return None
    return m.group(1), parts[1]


def tokenize(text: str):
    for tok in TOKEN_RE.findall(text):
        tok = tok.strip("'")
        if 1 <= len(tok) <= MAX_TOKEN_LEN and any(c.isalpha() for c in tok):
            yield tok


# --- inputs -------------------------------------------------------------------

def load_aosp(path: Path):
    """word(lower) -> (surface, f)"""
    base = {}
    f_max = 1
    word_re = re.compile(r"^\s*word=([^,]+),f=(\d+)")
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = word_re.match(line)
            if not m:
                continue
            surface, f = m.group(1), int(m.group(2))
            if " " in surface or len(surface) > MAX_TOKEN_LEN:
                continue
            key = surface.lower()
            if key not in base or f > base[key][1]:
                base[key] = (surface, f)
            f_max = max(f_max, f)
    return base, f_max


def mine_corpus(path: Path):
    voice, typing = Counter(), Counter()
    surfaces = Counter()          # (lower, surface) -> count
    insisted_words = set()        # INSISTED events
    new_words = set()             # NEW_WORD events
    reverted_words = set()        # REJECTED reverted events
    bigrams, phrases = Counter(), Counter()
    kept_lines = dropped_lines = 0

    re_reverted = re.compile(r"^([A-Za-z\x27]+)\s+←\s+([A-Za-z\x27]+)\s+\(reverted\)")

    with open(path, encoding="utf-8") as fh:
        for raw in fh:
            parsed = parse_line(raw)
            if not parsed:
                continue
            tag, content = parsed
            if tag == "REJECTED":
                m = re_reverted.match(content.strip())
                if m:
                    reverted_words.add(m.group(1).lower())
                continue
            if tag == "INSISTED":
                word = content.strip().strip('"').lower()
                if word and TOKEN_RE.fullmatch(word.strip("'") or ""):
                    insisted_words.add(word)
                continue
            if tag == "NEW_WORD":
                word = content.strip().strip('"').lower()
                if word and TOKEN_RE.fullmatch(word.strip("'") or ""):
                    new_words.add(word)
                continue
            if not tag.startswith("SESSION"):
                continue
            if classify_session(content) != "clean":
                dropped_lines += 1
                continue
            kept_lines += 1
            text = content.strip('"')
            counter = voice if "VOICE" in tag else typing
            toks = list(tokenize(text))
            lows = [t.lower() for t in toks]
            for t, low in zip(toks, lows):
                counter[low] += 1
                surfaces[(low, t)] += 1
            for a, b in zip(lows, lows[1:]):
                bigrams[(a, b)] += 1
            for a, b, c in zip(lows, lows[1:], lows[2:]):
                phrases[(a, b, c)] += 1

    return voice, typing, surfaces, insisted_words, new_words, reverted_words, bigrams, phrases, kept_lines, dropped_lines


# --- build --------------------------------------------------------------------

def main():
    dry_run = "--dry-run" in sys.argv

    # Load reviewed vocabulary rules
    rules_path = REPO / "dict_sources/personal_vocabulary.json"
    with open(rules_path, encoding="utf-8") as fh:
        rules = json.load(fh)

    approved_vocabulary = {w.lower(): f for w, f in rules["approved_vocabulary"].items()}
    approved_case_map = {w.lower(): w for w in rules["approved_vocabulary"]}
    protected_exact_forms = {w.lower() for w in rules["protected_exact_forms"]}
    protected_case_map = {w.lower(): w for w in rules["protected_exact_forms"]}
    typo_mappings = {w.lower(): target.lower() for w, target in rules["typo_mappings"].items()}
    quarantine = {w.lower() for w in rules["quarantine"]}

    base, f_max = load_aosp(AOSP_COMBINED)
    
    # Filter AOSP base to exclude typos, quarantine, and protected-only forms
    base = {low: (surf, f) for low, (surf, f) in base.items()
            if low not in typo_mappings and low not in quarantine and not (low in protected_exact_forms and low not in approved_vocabulary)}

    voice, typing, surfaces, insisted, new_words, reverted, bigrams, phrases, kept, dropped = mine_corpus(HARVEST)
    total = voice + typing

    def base_freq(f: int) -> float:
        return math.exp(LN_MIN + (f / f_max) * (LN_MAX - LN_MIN))

    personal_scale = FREQ_MAX / max(total.values())

    # dominant surface form per lowercase word (for casing of personal-only words)
    forms_by_low = {}
    for (low, s), cnt in surfaces.items():
        cur = forms_by_low.get(low)
        if cur is None or cnt > cur[0] or (cnt == cur[0] and s < cur[1]):
            forms_by_low[low] = (cnt, s)

    entries = {}  # lower -> (surface, freq)
    for low, (surface, f) in base.items():
        freq = base_freq(f)
        if low in total:
            freq = max(freq, total[low] * personal_scale)
        if low in approved_vocabulary:
            surface = approved_case_map.get(low, surface)
            if approved_vocabulary[low] is not None:
                freq = max(freq, approved_vocabulary[low])
        entries[low] = (surface, freq)

    admitted = []
    # Admit personal vocabulary words if they meet clean session thresholds OR are explicitly approved
    for low, cnt in total.items():
        if low in entries:
            continue
        if low in typo_mappings or low in quarantine:
            continue
        if low in protected_exact_forms and low not in approved_vocabulary:
            continue
        # Voice repetition alone must NOT admit a non-AOSP token. Whisper repeats the
        # same hallucination consistently (Pyrrhus->Pyrus x11, Termux->Termlux x4), so a
        # voice count is not independent evidence of a real word. A voice-only token must
        # be corroborated by at least one of: explicit approval, real typing evidence, or
        # another deliberately-defined trusted source.
        typed_ok = typing[low] >= TYPING_MIN
        voice_corroborated = voice[low] >= VOICE_MIN and typing[low] >= 1
        ok = (typed_ok or voice_corroborated or low in approved_vocabulary)
        if not ok or len(low.strip("'")) < 2:
            continue
        surface = forms_by_low.get(low, (0, low))[1]
        surface = approved_case_map.get(low, surface)
        freq = cnt * personal_scale
        if low in approved_vocabulary and approved_vocabulary[low] is not None:
            freq = max(freq, approved_vocabulary[low])
        entries[low] = (surface, freq)
        admitted.append((cnt, low))

    # Approved vocabulary words never seen in clean sessions
    for low in approved_vocabulary:
        if low not in entries:
            if low in typo_mappings or low in quarantine:
                continue
            if len(low.strip("'")) < 2:
                continue
            surface = approved_case_map.get(low, low)
            static_freq = approved_vocabulary[low]
            if static_freq is None:
                static_freq = FREQ_MIN * 10
            freq = max(static_freq, total[low] * personal_scale)
            entries[low] = (surface, freq)
            admitted.append((total[low], low))

    dict_keys = set(entries)
    
    # Sort deterministically
    out_bigrams = sorted(
        ((a, b, c) for (a, b), c in bigrams.items()
         if c >= BIGRAM_MIN and a in dict_keys and b in dict_keys),
        key=lambda x: (-x[2], x[0], x[1]),
    )
    out_phrases = sorted(
        ((a, b, c, n) for (a, b, c), n in phrases.items()
         if n >= PHRASE_MIN and a in dict_keys and b in dict_keys and c in dict_keys),
        key=lambda x: (-x[3], x[0], x[1], x[2]),
    )

    # --- report ---
    admitted.sort(reverse=True)
    print(f"AOSP base words        : {len(base):>7}  (f_max={f_max})")
    print(f"corpus lines kept      : {kept:>7}  (dropped {dropped} code/url/concat)")
    print(f"corpus tokens          : {sum(total.values()):>7}  ({len(total)} unique)")
    print(f"personal words admitted: {len(admitted):>7}")
    print(f"final dictionary       : {len(entries):>7}")
    print(f"bigrams (n>={BIGRAM_MIN})         : {len(out_bigrams):>7}")
    print(f"phrases (n>={PHRASE_MIN})         : {len(out_phrases):>7}")
    print("\ntop admitted personal words:", [w for _, w in admitted[:25]])
    print("\nsanity checks:")
    for w in ("the", "wife's", "don't", "class", "function", "bc", "rn",
              "termux", "proot", "symlink", "llm", "autocorrect", "ya"):
        e = entries.get(w)
        print(f"  {w:<12} {'%9d' % e[1] if e else '   ABSENT'}")

    if dry_run:
        print("\n--dry-run: nothing written")
        return

    with open(DICT_DIR / "unified_dictionary.tsv", "w", encoding="utf-8") as fh:
        # Sort deterministically by frequency descending, then alphabetically by lowercase word
        for low, (surface, freq) in sorted(entries.items(), key=lambda kv: (-kv[1][1], kv[0])):
            fh.write(f"{surface}\t{int(round(freq))}\n")
            
    with open(DICT_DIR / "final_mobile_bigrams.tsv", "w", encoding="utf-8") as fh:
        for a, b, c in out_bigrams:
            fh.write(f"{a} {b}\t{c}\n")
            
    with open(DICT_DIR / "personal_phrases.tsv", "w", encoding="utf-8") as fh:
        for a, b, c, n in out_phrases:
            fh.write(f"{a} {b}\t{c}\t{n}\n")

    with open(DICT_DIR / "protected_forms.txt", "w", encoding="utf-8") as fh:
        # Sort protected exact forms alphabetically
        for w in sorted(rules["protected_exact_forms"]):
            fh.write(f"{w}\n")
            
    print("\nwrote unified_dictionary.tsv, final_mobile_bigrams.tsv, personal_phrases.tsv, protected_forms.txt")


if __name__ == "__main__":
    main()
