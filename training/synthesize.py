#!/usr/bin/env python3
"""synthesize.py — Generate the synthetic training set from the clean corpus.

Walks clean_corpus.txt; each word is rendered the way Sam would physically
type it (apostrophes drop with p_apostrophe_drop — the layout has no ' key),
then corrupted with a sampled op from noise_model.json. Rows are shaped
EXACTLY like inference:

  {"typed": "wprd", "prev": "the", "prev2": null,
   "cands": [["word", 1, 13.2, 412], ["wprd", 0, 0.0, 0], ...],
   "label": "word"}

cands = SymSpell lookup (same dictionary as the device) with
[term, edit_dist, ln_unigram_freq, bigram_count(prev, term)]; the typed
string itself is ALWAYS a candidate — abstaining is a class.

Restraint is the product: identity rows (label == typed) are kept at
~IDENTITY_RATIO : 1 over correction rows, never below 10:1.

Split is by corpus line (session), not by row. Deterministic per --seed.
"""

import argparse
import hashlib
import json
import random
import string
import sys

from common import DATA, AOSP_COMBINED, CandidateGen, BigramTable, load_unigrams

LINGO = {"bc", "rn", "ya", "im"}
IDENTITY_RATIO = 12          # identity rows per correction row (>= 10 per handoff)
VAL_FRACTION = 20            # 1/20 of sessions -> val.jsonl
SENTENCE_END = {".", "!", "?"}


def load_overlay():
    """Personal words in unified_dictionary but absent from the AOSP base."""
    import re
    aosp = set()
    word_re = re.compile(r"^\s*word=([^,]+),")
    if AOSP_COMBINED.exists():
        with open(AOSP_COMBINED, encoding="utf-8") as fh:
            for line in fh:
                m = word_re.match(line)
                if m:
                    aosp.add(m.group(1).lower())
    return aosp


class Corruptor:
    def __init__(self, model, rng):
        self.m = model
        self.rng = rng
        self.op_names = list(model["op_mix"].keys())
        self.op_weights = list(model["op_mix"].values())
        self.sub = {a: (list(r.keys()), list(r.values()))
                    for a, r in model["sub"].items()}
        self.dels = model["del"]
        self.ins_after = {a: (list(r.keys()), list(r.values()))
                          for a, r in model["ins_after"].items()}
        self.swaps = list(model["swap_counts"].keys())

    def apply_op(self, word):
        rng = self.rng
        letters = [i for i, c in enumerate(word) if c in string.ascii_lowercase]
        if not letters:
            return None
        op = rng.choices(self.op_names, weights=self.op_weights)[0]
        if op == "sub":
            i = rng.choice(letters)
            row = self.sub.get(word[i])
            if not row:
                return None
            return word[:i] + rng.choices(row[0], weights=row[1])[0] + word[i + 1:]
        if op == "del":
            if len(word) < 3:
                return None
            weights = [self.dels.get(word[i], 0.001) for i in letters]
            i = rng.choices(letters, weights=weights)[0]
            return word[:i] + word[i + 1:]
        if op == "ins":
            i = rng.choice(letters)
            if rng.random() < self.m["p_doubled_given_ins"]:
                ch = word[i]
            else:
                row = self.ins_after.get(word[i])
                if not row:
                    return None
                ch = rng.choices(row[0], weights=row[1])[0]
            return word[:i + 1] + ch + word[i + 1:]
        # swap adjacent distinct letters
        spots = [i for i in range(len(word) - 1)
                 if word[i] != word[i + 1]
                 and word[i] in string.ascii_lowercase
                 and word[i + 1] in string.ascii_lowercase]
        if not spots:
            return None
        i = rng.choice(spots)
        return word[:i] + word[i + 1] + word[i] + word[i + 2:]

    def corrupt(self, word):
        n_ops = 2 if self.rng.random() < self.m["p_two_ops"] else 1
        out = word
        for _ in range(n_ops):
            nxt = self.apply_op(out)
            if nxt is None:
                break
            out = nxt
        return out if out and out != word else None


def iter_words(text):
    """Yield (word_lower, sentence_start) over a session line."""
    import re
    start = True
    for tok in re.findall(r"[A-Za-z']+|[.!?]", text):
        if tok in SENTENCE_END:
            start = True
            continue
        w = tok.strip("'").lower()
        if w and any(c.isalpha() for c in w) and len(w) <= 22:
            yield w, start
            start = False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--passes", type=int, default=8)
    ap.add_argument("--p-corrupt", type=float, default=0.05,
                    help="synthesis corruption rate (oversampled vs real p_err; "
                         "the identity ratio is what teaches restraint)")
    ap.add_argument("--seed", type=int, default=1337)
    ap.add_argument("--max-rows", type=int, default=0, help="0 = no cap")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    with open(DATA / "noise_model.json", encoding="utf-8") as fh:
        model = json.load(fh)
    corruptor = Corruptor(model, rng)

    unigrams = load_unigrams()
    gen = CandidateGen(unigrams)
    bigrams = BigramTable()
    aosp = load_overlay()
    protected = LINGO | {w for w in unigrams if w not in aosp and len(w) >= 2}
    print(f"dictionary={len(unigrams)}  personal-overlay+lingo protected={len(protected)}")

    lines = []
    with open(DATA / "clean_corpus.txt", encoding="utf-8") as fh:
        for idx, line in enumerate(fh):
            src, _, text = line.rstrip("\n").partition("\t")
            lines.append((idx, text))
    # identity keep-probability tuned so identity:correction ~= IDENTITY_RATIO
    p_corr_eff = args.p_corrupt + 0.03  # + apostrophe-drop correction rows (rough)
    identity_keep = min(1.0, IDENTITY_RATIO * p_corr_eff / max(1e-9, 1 - p_corr_eff))
    print(f"passes={args.passes} p_corrupt={args.p_corrupt} "
          f"identity_keep={identity_keep:.3f}")

    files = {"train": open(DATA / "train.jsonl", "w", encoding="utf-8"),
             "val": open(DATA / "val.jsonl", "w", encoding="utf-8")}
    n = {"train": 0, "val": 0}
    stats = {"corrupted": 0, "apos": 0, "identity": 0, "label_unreachable": 0,
             "protected_identity": 0}

    def emit(split, typed, prev, prev2, label):
        cands = [c[:] for c in gen.lookup(typed)]
        terms = {c[0] for c in cands}
        if label not in terms:
            if label != typed:
                stats["label_unreachable"] += 1
            label = typed  # right answer absent -> abstaining IS correct
        for c in cands:
            c.append(bigrams.get_frequency(prev, c[0]) if prev else 0)
        row = {"typed": typed, "prev": prev, "prev2": prev2,
               "cands": cands, "label": label}
        files[split].write(json.dumps(row, ensure_ascii=False) + "\n")
        n[split] += 1

    done = False
    for p in range(args.passes):
        if done:
            break
        for idx, text in lines:
            if done:
                break
            h = hashlib.sha1(f"{idx}".encode()).digest()[0]
            split = "val" if h % VAL_FRACTION == 0 else "train"
            prev = prev2 = None
            for word, sent_start in iter_words(text):
                if sent_start:
                    prev = prev2 = None
                # what Sam's fingers produce before typos: apostrophe drops
                base = word
                if "'" in word and rng.random() < model["p_apostrophe_drop"]:
                    base = word.replace("'", "")
                is_protected = word in protected
                corrupted = None
                if len(base) >= 2 and not is_protected and rng.random() < args.p_corrupt:
                    corrupted = corruptor.corrupt(base)
                if corrupted:
                    emit(split, corrupted, prev, prev2, word)
                    stats["corrupted"] += 1
                elif base != word:
                    emit(split, base, prev, prev2, word)  # apostrophe correction row
                    stats["apos"] += 1
                elif is_protected and len(word) >= 2:
                    emit(split, word, prev, prev2, word)  # lingo/overlay identity, always
                    stats["protected_identity"] += 1
                elif rng.random() < identity_keep:
                    emit(split, word, prev, prev2, word)
                    stats["identity"] += 1
                prev2, prev = prev, word
                if args.max_rows and n["train"] + n["val"] >= args.max_rows:
                    done = True
                    break

    for fh in files.values():
        fh.close()
    total = n["train"] + n["val"]
    ident = stats["identity"] + stats["protected_identity"]
    corr = stats["corrupted"] + stats["apos"]
    print(f"\nrows: train={n['train']}  val={n['val']}  total={total}")
    print(f"identity={ident}  corrections={corr} "
          f"(typo={stats['corrupted']}, apostrophe={stats['apos']})  "
          f"ratio={ident / max(1, corr):.1f}:1")
    print(f"label_unreachable->abstain: {stats['label_unreachable']}")
    with open(DATA / "train.jsonl", encoding="utf-8") as fh:
        print("\nsample correction rows:")
        shown = 0
        for line in fh:
            row = json.loads(line)
            if row["label"] != row["typed"]:
                print(" ", line.rstrip()[:220])
                shown += 1
                if shown >= 6:
                    break
    return 0


if __name__ == "__main__":
    sys.exit(main())
