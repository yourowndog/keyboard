"""Featurization contract — torch-free, shared by train.py and nn_scorer.py.

This module IS feature_spec.md in code form; NeuralScorer.kt mirrors it.
Any change here is a model-version bump.
"""

import math

PAD, APOS, BOW, EOW, UNK = 0, 27, 28, 29, 30
VOCAB = 31
MAX_WORD = 22          # ids incl. BOW/EOW
HASH_BUCKETS = 30000   # 0 = null
MAX_CANDS = 12
N_SCALARS = 5


def char_ids(word: str):
    ids = [BOW]
    for ch in word[:MAX_WORD - 2]:
        if "a" <= ch <= "z":
            ids.append(ord(ch) - 96)
        elif ch == "'":
            ids.append(APOS)
        else:
            ids.append(UNK)
    ids.append(EOW)
    return ids


def fnv_bucket(word):
    if not word:
        return 0
    h = 2166136261
    for b in word.encode("utf-8"):
        h = ((h ^ b) * 16777619) & 0xFFFFFFFF
    return (h % 29999) + 1


def scalar_row(typed, term, edit_dist, ln_freq, bigram_count):
    return (edit_dist / 2.0,
            ln_freq / 16.0,
            math.log(bigram_count + 1.0) / 12.0,
            len(typed) / 20.0,
            1.0 if term == typed else 0.0)
