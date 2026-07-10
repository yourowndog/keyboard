#!/usr/bin/env python3
"""evaluate.py — Score ANY scorer against the gold eval set.

Metrics:
  fix-recall@1   top choice == intended, over eval_pairs.jsonl (real corrections)
  false-fire     top choice != typed,   over eval_negatives.jsonl (leave-alone truth)

Baseline = faithful port of shared/CandidateScorer.kt as called by
NgramSuggestionEngine.rank() (ln-frequencies; PersonalPreferences veto excluded
on both sides — it wraps whichever scorer ships).

No training run ships unless it beats baseline on BOTH axes (handoff.md).

A neural scorer plugs in via --scorer module:function where function(typed,
prev, prev2, cands) -> list of scores (higher = better), cands rows
[term, edit_dist, ln_freq, bigram_count].
"""

import argparse
import importlib
import json
import sys

from common import DATA, CandidateGen, BigramTable, baseline_score, load_unigrams


def load(name):
    with open(DATA / name, encoding="utf-8") as fh:
        return [json.loads(l) for l in fh if l.strip()]


def make_baseline(bigrams):
    def score(typed, prev, prev2, cands):
        # rank() confidence = -penalty (+ user boosts we don't model).
        # The typed string is only a real runtime candidate when it's in the
        # dictionary (ln_freq > 0); our synthetic abstain row must not
        # collect the exact-match bonus otherwise.
        out = []
        for term, dist, ln_freq, _bg in cands:
            if term == typed and ln_freq == 0.0:
                out.append(float("-inf"))
            else:
                out.append(-baseline_score(typed, term, dist, prev, ln_freq, bigrams))
        return out
    return score


def make_symspell_naive():
    def score(typed, prev, prev2, cands):
        # retrieval order itself: distance asc, frequency desc; typed only a
        # candidate when it's a dictionary word (same rule as the baseline)
        return [float("-inf") if term == typed and ln_freq == 0.0
                else -(dist * 1000.0 - ln_freq)
                for term, dist, ln_freq, _bg in cands]
    return score


def top_choice(score_fn, typed, prev, prev2, cands):
    scores = score_fn(typed, prev, prev2, cands)
    return max(zip(scores, (c[0] for c in cands)), key=lambda x: x[0])[1]


def run(score_fn, pairs, negatives, gen, bigrams, name):
    def cands_for(typed, prev):
        cands = [c[:] for c in gen.lookup(typed)]
        for c in cands:
            c.append(bigrams.get_frequency(prev, c[0]) if prev else 0)
        return cands

    hits = fired_on_pairs = reachable = reachable_hits = 0
    for p in pairs:
        typed, intended, prev = p["typed"], p["intended"], p["prev"]
        cands = cands_for(typed, prev)
        in_cands = any(c[0] == intended for c in cands)
        reachable += in_cands
        top = top_choice(score_fn, typed, prev, p.get("prev2"), cands)
        if top != typed:
            fired_on_pairs += 1
        if top == intended:
            hits += 1
            reachable_hits += in_cands

    false_fires = 0
    for neg in negatives:
        typed, prev = neg["typed"], neg["prev"]
        cands = cands_for(typed, prev)
        top = top_choice(score_fn, typed, prev, neg.get("prev2"), cands)
        if top != typed:
            false_fires += 1

    n_p, n_n = len(pairs), len(negatives)
    print(f"\n=== {name} ===")
    print(f"fix-recall@1 : {hits}/{n_p} = {hits / n_p:.3f}"
          f"   (reachable subset: {reachable_hits}/{reachable} = "
          f"{reachable_hits / max(1, reachable):.3f})")
    print(f"false-fire   : {false_fires}/{n_n} = {false_fires / n_n:.3f}")
    print(f"fired on pairs: {fired_on_pairs}/{n_p}"
          f"   (candidate coverage: {reachable}/{n_p} = {reachable / n_p:.3f})")
    return {"name": name, "fix_recall": hits / n_p,
            "false_fire": false_fires / n_n,
            "reachable": reachable / n_p}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scorer", default=None,
                    help="extra scorer as module:function (e.g. nn_scorer:score)")
    args = ap.parse_args()

    pairs = load("eval_pairs.jsonl")
    negatives = load("eval_negatives.jsonl")
    print(f"eval pairs={len(pairs)}  negatives={len(negatives)}")

    gen = CandidateGen(load_unigrams())
    bigrams = BigramTable()

    results = [
        run(make_symspell_naive(), pairs, negatives, gen, bigrams,
            "symspell order (dist, freq) — reference"),
        run(make_baseline(bigrams), pairs, negatives, gen, bigrams,
            "CandidateScorer baseline (the one to beat)"),
    ]
    if args.scorer:
        mod, fn = args.scorer.split(":")
        sys.path.insert(0, str(DATA.parent))
        score_fn = getattr(importlib.import_module(mod), fn)
        results.append(run(score_fn, pairs, negatives, gen, bigrams, args.scorer))

    with open(DATA / "eval_results.json", "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
