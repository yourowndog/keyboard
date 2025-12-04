"""
Clean and re-rank unigram/bigram dictionaries for the keyboard.

- Blends original counts with SUBTLEX (SUBTLWF) to favor everyday usage.
- Preserves apostrophes (contractions/possessives) with a reasonable shape rule.
- Hardcodes a whitelist for 2-letter words to avoid noisy scientific abbreviations.
- Allows manual boosts/keeps for personal names/terms.
- Filters and trims bigrams to a sane subset.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import math
import re

ROOT = Path(__file__).resolve().parents[1]
BASE_UNIGRAM = ROOT / "app/src/main/assets/ime/dict/frequency_dictionary_en.txt"
SUBTLEX = ROOT / "SUBTLEXus74286wordstextversion.txt"
OUT_UNIGRAM = ROOT / "app/src/main/assets/ime/dict/frequency_dictionary_en.cleaned.txt"
BASE_BIGRAM = ROOT / "app/src/main/assets/ime/dict/frequency_bigram_en.txt"
OUT_BIGRAM = ROOT / "app/src/main/assets/ime/dict/frequency_bigram_en.cleaned.txt"

# Knobs
ALPHA = 0.8  # SUBTLEX weight in blended score
MAX_UNIGRAM = 70_000
MAX_BIGRAM = 50_000

TWO_LETTER = {
    "am",
    "an",
    "as",
    "at",
    "be",
    "by",
    "do",
    "go",
    "he",
    "hi",
    "if",
    "in",
    "is",
    "it",
    "me",
    "my",
    "no",
    "of",
    "on",
    "or",
    "so",
    "to",
    "up",
    "us",
    "we",
    "ya",
    "yo",
    "ok",
    "fr",
}

ALLOW_BOOST = {
    "ok",
    "fr",
    "lol",
    "doin'",
    "chungus",
    "kiry",
    "kiry's",
    "levi",
    "levi's",
    "elijah",
    "elijah's",
    "violet",
    "violet's",
    "sam",
    "sam's",
    "dad",
    "dad's",
    "mom",
    "mom's",
    "mixer",
    "truckers",
}

BAD_BIGRAM_TOKENS = {
    "porn",
    "porno",
    "sex",
    "sexy",
    "boob",
    "boobs",
    "nipple",
    "nipples",
    "fuck",
    "shit",
    "ass",
    "dick",
    "cunt",
}

WORD_PATTERN = re.compile(r"[a-z]+(?:'[a-z]+)?$")

THREE_LETTER_ALLOW = {
    # Core function words
    "and", "the", "you", "for", "but", "not", "are", "can", "was",
    "who", "why", "how", "any", "new", "now", "out", "our", "off",
    "too", "two", "one", "got", "get", "put", "run", "fun", "bad",
    "dad", "mom", "lol", "yes", "hey", "man", "her", "his", "she",
    "him", "its", "let", "say", "see", "use", "try", "did", "has",
    "had", "all", "some", "per", "via", "own",
    # Exceptions without vowel
    "gym", "nth", "tsk", "cpr", "wtf", "brb", "sos",
}
THREE_LETTER_MIN_FREQ = 900_000  # floor for non-allowlisted 3-letter words

def has_vowel(w: str) -> bool:
    return any(v in w for v in "aeiou")


@dataclass
class WordScore:
    score: float
    orig_freq: int


def load_subtlex(path: Path) -> dict[str, float]:
    sub_freq: dict[str, float] = {}
    with path.open(encoding="utf-8") as f:
        next(f, None)  # header
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 6:
                continue
            word = parts[0].lower()
            try:
                freq = float(parts[5])  # SUBTLWF
            except ValueError:
                continue
            sub_freq[word] = freq
    return sub_freq


def word_shape_ok(w: str) -> bool:
    if len(w) < 2 or len(w) > 20:
        return False
    if len(w) == 2 and w not in TWO_LETTER:
        return False
    if len(w) == 3:
        if w not in THREE_LETTER_ALLOW:
            # Require at least one vowel for 3-letter words unless allowed
            if not has_vowel(w):
                return False
    if not WORD_PATTERN.fullmatch(w):
        return False
    return True


def build_unigrams() -> dict[str, WordScore]:
    sub_freq = load_subtlex(SUBTLEX)
    best: dict[str, WordScore] = {}

    with BASE_UNIGRAM.open(encoding="utf-8") as f:
        for line in f:
            if "\t" not in line:
                continue
            w_raw, freq_str = line.rstrip("\n").split("\t", 1)
            w = w_raw.lower()
            try:
                freq = int(freq_str)
            except ValueError:
                continue

            if not word_shape_ok(w):
                continue
            if len(w) == 3 and w not in THREE_LETTER_ALLOW and freq < THREE_LETTER_MIN_FREQ:
                continue

            base_log = math.log10(freq + 1)
            sub_val = sub_freq.get(w)
            sub_log = math.log10((sub_val if sub_val is not None else 0.01) + 1)

            penalty = 0.0
            if len(w) >= 12:
                penalty += 0.3
            if w.endswith("ism") or w.endswith("ology"):
                penalty += 0.2
            if sub_val is None:
                penalty += 0.5  # down-rank words absent from SUBTLEX

            bonus = 0.0
            if w in TWO_LETTER:
                bonus += 1.5
            if w in ALLOW_BOOST:
                bonus += 1.5
            if "'" in w:
                bonus += 0.2

            score = base_log + ALPHA * sub_log + bonus - penalty

            prev = best.get(w)
            if prev is None or score > prev.score:
                best[w] = WordScore(score=score, orig_freq=freq)

    # Force-include allowlist words even if missing
    for w in ALLOW_BOOST:
        if w not in best:
            best[w] = WordScore(score=1e6, orig_freq=100000)

    return best


def write_unigrams(best: dict[str, WordScore]) -> None:
    rows = sorted(best.items(), key=lambda x: (-x[1].score, -x[1].orig_freq))
    rows = rows[:MAX_UNIGRAM]
    with OUT_UNIGRAM.open("w", encoding="utf-8") as o:
        for w, data in rows:
            o.write(f"{w}\t{data.orig_freq}\n")
    print(f"Unigrams kept: {len(rows)} -> {OUT_UNIGRAM}")


def load_bigrams(best: dict[str, WordScore]) -> list[tuple[float, int, str, str]]:
    bigrams: list[tuple[float, int, str, str]] = []
    if not BASE_BIGRAM.exists():
        print("No bigram file found; skipping bigram cleaning.")
        return bigrams

    with BASE_BIGRAM.open(encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if "\t" in line:
                pair_str, freq_str = line.split("\t", 1)
            elif " " in line:
                pair_str, freq_str = line.rsplit(" ", 1)
            else:
                continue

            try:
                freq = int(freq_str)
            except ValueError:
                continue

            if " " not in pair_str:
                continue
            w1, w2 = pair_str.split(" ", 1)
            w1, w2 = w1.lower(), w2.lower()

            def ok_word(w: str) -> bool:
                if w in BAD_BIGRAM_TOKENS:
                    return False
                if len(w) == 2 and w not in TWO_LETTER:
                    return False
                if len(w) < 2 or len(w) > 20:
                    return False
                if not WORD_PATTERN.fullmatch(w):
                    return False
                return True

            if not (ok_word(w1) and ok_word(w2)):
                continue

            uni_score1 = best.get(w1, WordScore(0.0, 0)).score
            uni_score2 = best.get(w2, WordScore(0.0, 0)).score
            score = math.log10(freq + 1) + 0.3 * (uni_score1 + uni_score2)
            bigrams.append((score, freq, w1, w2))

    return bigrams


def write_bigrams(bigrams: list[tuple[float, int, str, str]]) -> None:
    if not bigrams:
        return
    bigrams.sort(key=lambda x: (-x[0], -x[1]))
    kept = bigrams[:MAX_BIGRAM]
    with OUT_BIGRAM.open("w", encoding="utf-8") as o:
        for _, freq, w1, w2 in kept:
            o.write(f"{w1} {w2}\t{freq}\n")
    print(f"Bigrams kept: {len(kept)} -> {OUT_BIGRAM}")


def main() -> None:
    best = build_unigrams()
    write_unigrams(best)
    bigrams = load_bigrams(best)
    write_bigrams(bigrams)


if __name__ == "__main__":
    main()
