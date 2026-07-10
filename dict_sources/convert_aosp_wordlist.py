#!/usr/bin/env python3
"""
Convert AOSP-style wordlist (en_wordlist.combined.txt) into SymSpell-friendly TSV.

Input format (after the header line):
 word=the,f=222,flags=,originalFreq=222

Output format:
 the<TAB>scaled_freq

Scaling: log-linear. We map log(f+1)/log(max_source+1) linearly onto the target
 max frequency observed in frequency_dictionary_en.cleaned.txt so that tiny AOSP
 buckets get a usable dynamic range in our scorer.
"""
import math
from pathlib import Path

SRC = Path("en_wordlist.combined.txt")
TARGET = Path("aosp_unigram.tsv")
REFERENCE = Path("frequency_dictionary_en.cleaned.txt")


def read_source_max(path: Path) -> int:
    max_f = 1
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("dictionary="):
                continue
            # line shape: " word=the,f=222,flags=,originalFreq=222"
            parts = line.split(",")
            freq_part = next((p for p in parts if p.startswith("f=")), None)
            if not freq_part:
                continue
            try:
                freq = int(freq_part.split("=", 1)[1])
                if freq > max_f:
                    max_f = freq
            except Exception:
                continue
    return max_f


def read_reference_max(path: Path) -> int:
    max_f = 1
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if "\t" not in line:
                continue
            try:
                freq = int(line.split("\t")[1])
                if freq > max_f:
                    max_f = freq
            except Exception:
                continue
    return max_f


def convert():
    if not SRC.exists():
        raise SystemExit(f"Source file not found: {SRC}")
    if not REFERENCE.exists():
        raise SystemExit(f"Reference file not found: {REFERENCE}")

    src_max = read_source_max(SRC)
    ref_max = read_reference_max(REFERENCE)
    log_src_max = math.log(src_max + 1)

    print(f"Source max freq: {src_max}")
    print(f"Reference max freq: {ref_max}")

    out_count = 0
    with SRC.open("r", encoding="utf-8") as src, TARGET.open("w", encoding="utf-8") as dst:
        for line in src:
            line = line.strip()
            if not line or line.startswith("dictionary="):
                continue
            parts = line.split(",")
            word_part = next((p for p in parts if p.startswith("word=")), None)
            freq_part = next((p for p in parts if p.startswith("f=")), None)
            if not word_part or not freq_part:
                continue
            word = word_part.split("=", 1)[1]
            try:
                f_raw = int(freq_part.split("=", 1)[1])
            except Exception:
                continue
            # Log-linear scaling onto the reference max range
            scaled = int((math.log(f_raw + 1) / log_src_max) * ref_max)
            scaled = max(1, scaled)
            dst.write(f"{word}\t{scaled}\n")
            out_count += 1

    print(f"Wrote {out_count} entries -> {TARGET}")


if __name__ == "__main__":
    convert()
