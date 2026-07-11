#!/usr/bin/env python3
"""analyze_shadow.py — Analyze NEURAL_SHADOW events from usage_harvest.jsonl.

Compares the neural model's on-device counterfactual decisions against the
ngram-based system that actually controlled the suggestion row. This is the
step-3 evaluation from handoff.md: "Pull harvest data and evaluate v1 against
the current dictionary-fixed baseline."

Usage:
    python analyze_shadow.py                        # uses repo's usage_harvest.jsonl
    python analyze_shadow.py path/to/harvest.jsonl  # explicit path

Output:
    Prints summary statistics and writes data/shadow_analysis.json with:
    - Agreement rate: how often neural and ngram pick the same top candidate
    - Fire rate: how often neural would override the ngram result
    - Breakdown of disagreements (neural says correct, ngram wrong, or vice versa)
    - Confidence distributions
"""

import json
import sys
from collections import Counter
from pathlib import Path

DATA = Path(__file__).parent / "data"
REPO = Path(__file__).parent.parent


def load_shadow_events(path):
    events = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            if e.get("type") == "NEURAL_SHADOW":
                events.append(e)
    return events


def analyze(events):
    stats = Counter()
    disagreements = []
    margin_buckets = Counter()  # margin rounded to 0.1

    for e in events:
        stats["total"] += 1
        typed = e.get("typed", "")
        ngram_top = e.get("ngramTop")
        neural_top = e.get("neuralTop")
        would_fire = e.get("wouldFire", False)
        agrees = e.get("agrees", False)
        margin = e.get("margin", 0.0)
        typed_p = e.get("typedP", 0.0)
        top_p = e.get("topP", 0.0)

        # Bucket margin for distribution
        bucket = round(margin * 10) / 10
        margin_buckets[f"{bucket:.1f}"] += 1

        if agrees:
            stats["agrees"] += 1
        else:
            stats["disagrees"] += 1
            disagreements.append({
                "typed": typed,
                "prev": e.get("prev"),
                "ngramTop": ngram_top,
                "neuralTop": neural_top,
                "margin": round(margin, 4),
                "typedP": round(typed_p, 4),
                "topP": round(top_p, 4),
                "wouldFire": would_fire,
            })

        if would_fire:
            stats["would_fire"] += 1
            if agrees:
                stats["fire_and_agree"] += 1
            else:
                stats["fire_and_disagree"] += 1
        else:
            stats["would_not_fire"] += 1

        # Cases where neural top == typed (model says "keep it")
        if neural_top and neural_top.lower() == typed.lower():
            stats["neural_says_keep"] += 1
        else:
            stats["neural_says_change"] += 1

        # Cases where ngram top == typed (ngram says "keep it")
        if ngram_top and ngram_top.lower() == typed.lower():
            stats["ngram_says_keep"] += 1
        else:
            stats["ngram_says_change"] += 1

    return stats, disagreements, margin_buckets


def main():
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        path = REPO / "data/harvest/raw/usage_harvest.jsonl"

    if not path.exists():
        print(f"ERROR: {path} not found. Pull from device first:")
        print(f"  rtk adb pull /sdcard/Documents/usage_harvest.jsonl {REPO}/data/harvest/raw/")
        return 1

    events = load_shadow_events(path)
    if not events:
        print(f"No NEURAL_SHADOW events found in {path}")
        print("Shadow logging may not have been active, or no typing since install.")
        return 0

    stats, disagreements, margin_buckets = analyze(events)
    total = stats["total"]

    print(f"\n=== Neural Shadow Analysis ({total} events) ===\n")
    print(f"Agreement rate   : {stats['agrees']}/{total} = {stats['agrees']/total:.1%}")
    print(f"Disagreement rate: {stats['disagrees']}/{total} = {stats['disagrees']/total:.1%}")
    print()
    print(f"Neural would fire: {stats['would_fire']}/{total} = {stats['would_fire']/total:.1%}")
    print(f"  fire + agree   : {stats['fire_and_agree']}")
    print(f"  fire + disagree: {stats['fire_and_disagree']}")
    print(f"Neural would hold: {stats['would_not_fire']}/{total} = {stats['would_not_fire']/total:.1%}")
    print()
    print(f"Neural says keep : {stats['neural_says_keep']}/{total} = {stats['neural_says_keep']/total:.1%}")
    print(f"Neural says change: {stats['neural_says_change']}/{total} = {stats['neural_says_change']/total:.1%}")
    print(f"Ngram says keep  : {stats['ngram_says_keep']}/{total} = {stats['ngram_says_keep']/total:.1%}")
    print(f"Ngram says change: {stats['ngram_says_change']}/{total} = {stats['ngram_says_change']/total:.1%}")

    print("\nMargin distribution:")
    for bucket in sorted(margin_buckets, key=float):
        count = margin_buckets[bucket]
        bar = "█" * min(count, 60)
        print(f"  {bucket:>5s}  {count:>4d}  {bar}")

    if disagreements:
        print(f"\nTop disagreements (showing first 20):")
        # Sort by absolute margin (strongest neural opinion first)
        disagreements.sort(key=lambda d: -abs(d["margin"]))
        for d in disagreements[:20]:
            fire_str = "FIRE" if d["wouldFire"] else "hold"
            print(f"  typed='{d['typed']}' ngram='{d['ngramTop']}' "
                  f"neural='{d['neuralTop']}' margin={d['margin']:.3f} [{fire_str}]")

    # Write results
    DATA.mkdir(parents=True, exist_ok=True)
    result = {
        "total_events": total,
        "stats": dict(stats),
        "margin_distribution": dict(sorted(margin_buckets.items(), key=lambda x: float(x[0]))),
        "disagreements": disagreements,
    }
    out_path = DATA / "shadow_analysis.json"
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(result, fh, indent=1, ensure_ascii=False)
    print(f"\nFull results written to {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
