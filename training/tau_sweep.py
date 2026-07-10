#!/usr/bin/env python3
"""tau_sweep.py — Calibrate the fire threshold τ on the REAL eval set.

Decision rule (feature_spec.md): fire iff top != typed and
P(top) − P(typed) > τ. Prints the recall/false-fire trade-off so Sam can
pick the shipped default; τ stays a runtime pref.
"""

import json
import sys

import nn_scorer
from common import DATA, BigramTable, CandidateGen, load_unigrams


def load(name):
    with open(DATA / name, encoding="utf-8") as fh:
        return [json.loads(l) for l in fh if l.strip()]


def main():
    pairs, negatives = load("eval_pairs.jsonl"), load("eval_negatives.jsonl")
    gen, bigrams = CandidateGen(load_unigrams()), BigramTable()

    def decide(rows, key_intended=None):
        out = []  # (delta, top, intended-or-None, typed)
        for r in rows:
            typed, prev = r["typed"], r["prev"]
            cands = [c[:] for c in gen.lookup(typed)]
            for c in cands:
                c.append(bigrams.get_frequency(prev, c[0]) if prev else 0)
            p = nn_scorer.probs(typed, prev, r.get("prev2"), cands)
            terms = [c[0] for c in cands[:len(p)]]
            top_i = int(p.argmax())
            typed_i = terms.index(typed)
            delta = float(p[top_i] - p[typed_i])
            out.append((delta, terms[top_i],
                        r[key_intended] if key_intended else None, typed))
        return out

    pair_d = decide(pairs, "intended")
    neg_d = decide(negatives)

    print(f"{'tau':>5} {'fix-recall@1':>13} {'false-fire':>11}")
    baseline = (0.488, 0.440)
    results = []
    for tau in [0.0, 0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]:
        hits = sum(1 for d, top, intended, typed in pair_d
                   if top != typed and d > tau and top == intended)
        fires = sum(1 for d, top, _, typed in neg_d if top != typed and d > tau)
        r, f = hits / len(pairs), fires / len(negatives)
        beats = "  <- beats baseline both axes" if r > baseline[0] and f < baseline[1] else ""
        print(f"{tau:>5.2f} {r:>13.3f} {f:>11.3f}{beats}")
        results.append({"tau": tau, "fix_recall": round(r, 4),
                        "false_fire": round(f, 4)})
    with open(DATA / "tau_sweep_real.json", "w") as fh:
        json.dump(results, fh, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
