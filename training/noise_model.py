#!/usr/bin/env python3
"""noise_model.py — Fit Sam's personal typo distribution.

Sources (merged):
  1. eval_pairs.jsonl    char-aligned typed vs intended (Damerau ops)
  2. trace_samples.jsonl backspace-burst replay recovers the pre-correction
                         string autocorrect never saw (self-fixed typos)

Smoothing: QWERTY-adjacency prior — P(sub a->b) ∝ count(a->b) + α·adj(a,b),
α=0.5, adj=1.0 for physically adjacent keys else 0.05.

Output: training/data/noise_model.json
"""

import json
import string
import sys
from collections import Counter

from common import (DATA, BACKSPACE, align_ops, is_adjacent, levenshtein,
                    recover_pre_correction, replay_trace)

ALPHA = 0.5
ADJ_NEAR, ADJ_FAR = 1.0, 0.05
LETTERS = string.ascii_lowercase + "'"


def load(name):
    with open(DATA / name, encoding="utf-8") as fh:
        return [json.loads(l) for l in fh if l.strip()]


def count_ops(intended, typed, counters):
    """Ops that turn the clean word into what Sam produced."""
    subs, dels, inss, swaps = counters
    for op in align_ops(intended.lower(), typed.lower()):
        if op[0] == "sub":
            subs[(op[1], op[2])] += 1
        elif op[0] == "del":
            dels[op[1]] += 1
        elif op[0] == "ins":
            inss[(op[2], op[1])] += 1  # (after, char)
        elif op[0] == "swap":
            swaps[op[1]] += 1


def main():
    pairs = load("eval_pairs.jsonl")
    traces = load("trace_samples.jsonl")

    subs, dels, inss, swaps = Counter(), Counter(), Counter(), Counter()
    counters = (subs, dels, inss, swaps)

    apos_total = apos_dropped = 0
    space_dropped = 0
    used_pairs = 0
    for p in pairs:
        typed, intended = p["typed"], p["intended"]
        if " " in intended:
            space_dropped += 1
            continue
        if "'" in intended:
            apos_total += 1
            if typed == intended.replace("'", ""):
                apos_dropped += 1
                continue  # pure apostrophe drop; modeled by its own rate
        count_ops(intended, typed, counters)
        used_pairs += 1

    self_fixed = 0
    for t in traces:
        trace, final = t["trace"], t["final"]
        if BACKSPACE in trace:
            pre = recover_pre_correction(trace, final)
        else:
            pre = replay_trace(trace)  # MANUAL_FIX rows: trace is the raw string
        if not pre or pre.lower() == final.lower():
            continue
        if levenshtein(pre.lower(), final.lower()) > max(3, len(final) // 2):
            continue
        count_ops(final, pre, counters)
        self_fixed += 1

    # --- rates -----------------------------------------------------------
    typed_tokens = 0
    with open(DATA / "clean_corpus.txt", encoding="utf-8") as fh:
        for line in fh:
            src, _, text = line.partition("\t")
            if src == "T":
                typed_tokens += len(text.split())
    p_err = (used_pairs + self_fixed) / max(1, typed_tokens)

    total_ins = sum(inss.values())
    doubled = sum(c for (after, ch), c in inss.items() if after == ch)
    p_doubled_given_ins = doubled / total_ins if total_ins else 0.35
    p_apos_drop = apos_dropped / apos_total if apos_total else 0.95

    n_sub, n_del, n_swap = sum(subs.values()), sum(dels.values()), sum(swaps.values())
    n_ops = n_sub + n_del + total_ins + n_swap
    op_mix = {"sub": n_sub / n_ops, "del": n_del / n_ops,
              "ins": total_ins / n_ops, "swap": n_swap / n_ops}

    # --- smoothed sub table: P(b | a) -------------------------------------
    sub_table = {}
    for a in string.ascii_lowercase:
        row = {}
        for b in string.ascii_lowercase:
            if a == b:
                continue
            prior = ADJ_NEAR if is_adjacent(a, b) else ADJ_FAR
            row[b] = subs.get((a, b), 0) + ALPHA * prior
        z = sum(row.values())
        sub_table[a] = {b: round(v / z, 6) for b, v in row.items()}

    del_table = {a: dels.get(a, 0) + ALPHA * 0.2 for a in string.ascii_lowercase}
    z = sum(del_table.values())
    del_table = {a: round(v / z, 6) for a, v in del_table.items()}

    ins_after = {}   # P(char | after) for non-doubling insertions
    for a in string.ascii_lowercase:
        row = {}
        for c in string.ascii_lowercase:
            if c == a:
                continue  # doubling handled by p_doubled_given_ins
            prior = ADJ_NEAR if is_adjacent(a, c) else ADJ_FAR
            row[c] = inss.get((a, c), 0) + ALPHA * prior
        z = sum(row.values())
        ins_after[a] = {c: round(v / z, 6) for c, v in row.items()}

    swap_counts = {k: v for k, v in swaps.items()}

    model = {
        "meta": {
            "eval_pairs_used": used_pairs,
            "self_fixed_traces": self_fixed,
            "typed_tokens": typed_tokens,
            "apostrophe_pairs": apos_total,
            "dropped_space_pairs": space_dropped,
            "raw_op_counts": {"sub": n_sub, "del": n_del, "ins": total_ins,
                              "swap": n_swap},
        },
        "p_err": round(p_err, 5),
        "p_two_ops": 0.15,
        "p_apostrophe_drop": round(p_apos_drop, 4),
        "p_doubled_given_ins": round(p_doubled_given_ins, 4),
        "op_mix": {k: round(v, 4) for k, v in op_mix.items()},
        "sub": sub_table,
        "del": del_table,
        "ins_after": ins_after,
        "swap_counts": swap_counts,
    }
    with open(DATA / "noise_model.json", "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=1)

    # --- sanity report -----------------------------------------------------
    print(f"pairs used={used_pairs}  self-fixed traces={self_fixed}  "
          f"typed tokens={typed_tokens}")
    print(f"p_err={p_err:.4f}  apostrophe-drop={p_apos_drop:.2f} "
          f"(n={apos_total})  doubled|ins={p_doubled_given_ins:.2f}")
    print(f"op mix: {model['op_mix']}")
    print("\ntop-20 raw substitutions (should look like fat-finger neighbors):")
    for (a, b), c in subs.most_common(20):
        tag = "adj" if is_adjacent(a, b) else "   "
        print(f"  {a} -> {b}   x{c}  {tag}")
    print("\ntop-10 deletions:", dels.most_common(10))
    print("top-10 insertions (after,char):", inss.most_common(10))
    print("top-10 swaps:", swaps.most_common(10))
    return 0


if __name__ == "__main__":
    sys.exit(main())
