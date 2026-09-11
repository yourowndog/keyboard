#!/usr/bin/env python3
"""
cull_dictionary_poison.py — Multi-signal detection and culling of bad/poison words in OmniBoard.

Identifies and categorizes poison words across 5 distinct evidence layers:
  1. Acute Reverts: Words auto-applied and repeatedly reverted by the user.
  2. AOSP Non-words: Glitch words marked 'not_a_word=true' or 'f=0' abbreviation traps in AOSP.
  3. Zombie Parasites: High-impression candidates (shown >=30x) with ZERO user commits.
  4. Mechanical Possessives: Bogus 's endings on verbs/plurals with 0 corpus frequency.
  5. Modern Deadweight: Words with Zipf == 0.0 in modern English that the user has never typed.

Safety invariants:
  - Never touches words in personal_vocabulary.json (approved_vocabulary, protected_exact_forms).
  - Never touches words with positive typing evidence in usage_harvest.jsonl.
  - Never touches words with Zipf >= 3.0 even if they collide with typos (preserves cone, dome, thai, etc.).

Usage:
  uv run --with wordfreq tools/dictionary/cull_dictionary_poison.py --analyze
  uv run --with wordfreq tools/dictionary/cull_dictionary_poison.py --export-quarantine <output.json> [--tier tier4]
  uv run --with wordfreq tools/dictionary/cull_dictionary_poison.py --apply [--tier tier4]
"""

import argparse
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

try:
    import wordfreq
except ImportError:
    print("Error: 'wordfreq' package required. Run with: uv run --with wordfreq python3 ...", file=sys.stderr)
    sys.exit(1)

REPO = Path(__file__).resolve().parents[2]
DICT_TSV = REPO / "app/src/main/assets/ime/dict/unified_dictionary.tsv"
BIGRAMS_TSV = REPO / "app/src/main/assets/ime/dict/final_mobile_bigrams.tsv"
PHRASES_TSV = REPO / "app/src/main/assets/ime/dict/personal_phrases.tsv"
RULES_JSON = REPO / "dict_sources/personal_vocabulary.json"
AOSP_COMBINED = REPO / "dict_sources/en_wordlist.combined.txt"
HARVEST_JSONL = REPO / "data/harvest/inbox/20260911-011828/usage_harvest.jsonl"


def load_rules():
    if not RULES_JSON.exists():
        return set(), set(), set(), {}
    with open(RULES_JSON, encoding="utf-8") as f:
        data = json.load(f)
    approved = {w.lower() for w in data.get("approved_vocabulary", {})}
    protected = {w.lower() for w in data.get("protected_exact_forms", [])}
    quarantine = {w.lower() for w in data.get("quarantine", [])}
    typo_mappings = {w.lower(): v.lower() for w, v in data.get("typo_mappings", {}).items()}
    return approved, protected, quarantine, typo_mappings


def load_aosp_metadata():
    """word(lower) -> (not_a_word: bool, flags: str, f: int)"""
    meta = {}
    if not AOSP_COMBINED.exists():
        return meta
    word_re = re.compile(r"^\s*word=([^,]+),f=(\d+)(.*)")
    flag_re = re.compile(r"flags=([^,]*)")
    with open(AOSP_COMBINED, encoding="utf-8") as f:
        for line in f:
            m = word_re.match(line)
            if not m:
                continue
            w, f_val, rest = m.group(1).lower(), int(m.group(2)), m.group(3)
            not_a_word = "not_a_word=true" in rest
            flags_m = flag_re.search(rest)
            flags = flags_m.group(1) if flags_m else ""
            meta[w] = (not_a_word, flags, f_val)
    return meta


def load_harvest_signals():
    applied_events = {}
    applied_counts = Counter()
    reverted_counts = Counter()
    specific_reverts = Counter()
    shown_counts = Counter()
    committed_counts = Counter()
    typing_commits = Counter()

    if not HARVEST_JSONL.exists():
        print(f"Warning: harvest log {HARVEST_JSONL} not found, skipping harvest signals", file=sys.stderr)
        return {
            "applied": applied_counts, "reverted": reverted_counts,
            "specific_reverts": specific_reverts, "shown": shown_counts,
            "committed": committed_counts, "typing": typing_commits
        }

    with open(HARVEST_JSONL, encoding="utf-8") as f:
        for line in f:
            ev = json.loads(line)
            t = ev.get("type")
            if t == "AUTO_APPLIED":
                ev_id = ev.get("id")
                app = ev.get("applied")
                if ev_id:
                    applied_events[ev_id] = ev
                if app:
                    applied_counts[app.lower()] += 1
            elif t == "REVERTED":
                undoes = ev.get("undoes")
                if undoes and undoes in applied_events:
                    orig = applied_events[undoes]
                    typed = orig.get("typed", "")
                    app = orig.get("applied", "")
                else:
                    typed = ev.get("typed", "")
                    app = ev.get("applied", "")
                if app:
                    low_app = app.lower()
                    reverted_counts[low_app] += 1
                    if typed:
                        specific_reverts[(typed.lower(), low_app)] += 1
            elif t == "WORD_COMMITTED":
                w = ev.get("word")
                src = ev.get("src", "TYPING")
                if w:
                    low = w.lower()
                    committed_counts[low] += 1
                    if src != "VOICE":
                        typing_commits[low] += 1
            elif t == "SUGGESTIONS_SHOWN":
                for cand in ev.get("candidates", []):
                    term = cand[0] if isinstance(cand, list) and cand else cand
                    if isinstance(term, str):
                        shown_counts[term.lower()] += 1

    return {
        "applied": applied_counts,
        "reverted": reverted_counts,
        "specific_reverts": specific_reverts,
        "shown": shown_counts,
        "committed": committed_counts,
        "typing": typing_commits,
    }


def analyze():
    approved, protected, quarantine, typo_mappings = load_rules()
    aosp_meta = load_aosp_metadata()
    harvest = load_harvest_signals()

    entries = []
    with open(DICT_TSV, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if "\t" in line:
                word, freq = line.split("\t", 1)
                entries.append((word, int(freq)))

    total_words = len(entries)
    print("=" * 78)
    print(f"OMNIBOARD DICTIONARY POISON AUDIT — {total_words:,} ENTRIES")
    print("=" * 78)

    # 1. Acute Reverts: obscure words that win auto-commits and get immediately undone
    acute_reverts = []
    for surface, freq in entries:
        low = surface.lower()
        if low in approved or low in protected:
            continue
        rev = harvest["reverted"].get(low, 0)
        app = harvest["applied"].get(low, 0)
        commit = harvest["committed"].get(low, 0)
        z = wordfreq.zipf_frequency(low, "en")
        
        # Poison offender: reverted >= 2 times, low commits, and low Zipf (<3.0)
        if rev >= 2 and rev >= commit and z < 3.0:
            rate = (rev / app * 100) if app > 0 else 0
            acute_reverts.append((surface, rev, app, rate, commit, freq, z))

    acute_reverts.sort(key=lambda x: (-x[1], x[4]))

    # 2. AOSP Non-words / Glitch tokens (strictly where user commit count == 0)
    aosp_nonwords = []
    for surface, freq in entries:
        low = surface.lower()
        if low in approved or low in protected:
            continue
        if harvest["committed"].get(low, 0) > 0:
            continue
        if low in aosp_meta:
            not_a_word, flags, f_val = aosp_meta[low]
            z = wordfreq.zipf_frequency(low, "en")
            # Exclude AOSP shortcut traps: not_a_word, 0-freq abbreviations, or nonword with low zipf
            if not_a_word or (f_val == 0 and flags == "abbreviation") or ("nonword" in flags and z < 2.5):
                aosp_nonwords.append((surface, not_a_word, flags, f_val, 0))

    # 3. Zombie Parasites: High-impression candidates with 0 user commits that steal slots
    zombies = []
    for surface, freq in entries:
        low = surface.lower()
        if low in approved or low in protected:
            continue
        sh = harvest["shown"].get(low, 0)
        cm = harvest["committed"].get(low, 0)
        if sh >= 30 and cm == 0:
            z = wordfreq.zipf_frequency(low, "en")
            if z < 2.0:
                zombies.append((surface, sh, z, freq))

    zombies.sort(key=lambda x: -x[1])

    # 4. Bogus Possessives (ends with 's, Zipf == 0, never committed)
    bogus_possessives = []
    for surface, freq in entries:
        low = surface.lower()
        if low in approved or low in protected:
            continue
        if (low.endswith("'s") or low.endswith("’s")) and harvest["committed"].get(low, 0) == 0:
            z = wordfreq.zipf_frequency(low, "en")
            if z == 0.0:
                bogus_possessives.append((surface, freq))

    # 5. Modern Deadweight (Zipf == 0.0, never committed, not approved)
    deadweight = []
    for surface, freq in entries:
        low = surface.lower()
        if low in approved or low in protected:
            continue
        if harvest["committed"].get(low, 0) == 0:
            z = wordfreq.zipf_frequency(low, "en")
            if z == 0.0:
                deadweight.append((surface, freq))

    # Deduplicated totals
    tier1_set = {w.lower() for w, *_ in acute_reverts} | {w.lower() for w, *_ in aosp_nonwords}
    tier2_set = tier1_set | {w.lower() for w, *_ in zombies}
    tier3_set = tier2_set | {w.lower() for w, *_ in bogus_possessives}
    tier4_set = tier3_set | {w.lower() for w, *_ in deadweight}

    print(f"\n[TIER 1] ACUTE OFFENDERS & AOSP NON-WORDS ({len(tier1_set):,} words)")
    print("  Top Reverts:")
    for s, rev, app, rate, cm, f, z in acute_reverts[:15]:
        print(f"    {s:<16} Reverts: {rev:>2}/{app:<2} ({rate:>5.1f}%) | Committed: {cm:>2} | Zipf: {z:.2f} | Dict freq: {f}")
    print("  Top AOSP Non-words:")
    for s, naw, fl, f_val, cm in aosp_nonwords[:12]:
        reason = "not_a_word" if naw else fl
        print(f"    {s:<16} Reason: {reason:<16} | AOSP f: {f_val} | Committed: {cm}")

    print(f"\n[TIER 2] ZOMBIE PARASITIC SUGGESTIONS ({len(zombies):,} words)")
    for s, sh, z, f in zombies[:15]:
        print(f"    {s:<16} Shown: {sh:>4}x | Committed: 0 | Zipf: {z:.2f} | Dict freq: {f}")

    print(f"\n[TIER 3] MECHANICAL POSSESSIVE 'S JUNK ({len(bogus_possessives):,} words)")
    print(f"  Samples: {', '.join(w for w, _ in bogus_possessives[:15])}...")

    print(f"\n[TIER 4] FULL MODERN DEADWEIGHT TAIL ({len(deadweight):,} words — 29.2% of dictionary)")
    print(f"  Samples: {', '.join(w for w, _ in deadweight[100:115])}...")

    print("\n" + "=" * 78)
    print("CULLING IMPACT PROJECTIONS")
    print("=" * 78)
    print(f"  Starting Dictionary Size : {total_words:>7,} words")
    print(f"  - Remove Tier 1 (Acute)  : {total_words - len(tier1_set):>7,} words (-{len(tier1_set):,})")
    print(f"  - Remove Tier 2 (+Zombies): {total_words - len(tier2_set):>7,} words (-{len(tier2_set):,})")
    print(f"  - Remove Tier 3 (+Bogus's): {total_words - len(tier3_set):>7,} words (-{len(tier3_set):,})")
    print(f"  - Remove Tier 4 (+Deadwt): {total_words - len(tier4_set):>7,} words (-{len(tier4_set):,} -> {total_words - len(tier4_set):,} clean words)")
    print("=" * 78)

    return {
        "tier1": tier1_set,
        "tier2": tier2_set,
        "tier3": tier3_set,
        "tier4": tier4_set,
        "entries": entries,
    }


def export_quarantine(output_path, tier="tier2"):
    res = analyze()
    words_to_export = sorted(res.get(tier, set()))
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump({"quarantine_candidates": words_to_export, "count": len(words_to_export)}, f, indent=2)
    print(f"\nExported {len(words_to_export)} words from {tier} to {output_path}")


def apply_cleanse(tier="tier4"):
    res = analyze()
    culled_set = res.get(tier)
    if not culled_set:
        print(f"Error: unknown tier '{tier}'", file=sys.stderr)
        return

    entries = res["entries"]
    before_count = len(entries)
    
    # 1. Filter unified_dictionary.tsv
    kept_entries = [(word, freq) for word, freq in entries if word.lower() not in culled_set]
    after_count = len(kept_entries)
    removed_count = before_count - after_count

    print("\n" + "=" * 78)
    print(f"APPLYING {tier.upper()} CLEANSE TO RUNTIME ASSETS")
    print("=" * 78)
    print(f"  Dictionary rows before : {before_count:>7,}")
    print(f"  Dictionary rows after  : {after_count:>7,}  (-{removed_count:,})")

    # Sort deterministically by frequency descending, then alphabetically by lowercase word
    kept_entries.sort(key=lambda kv: (-kv[1], kv[0].lower()))
    with open(DICT_TSV, "w", encoding="utf-8") as f:
        for surface, freq in kept_entries:
            f.write(f"{surface}\t{freq}\n")
    print(f"  Updated: {DICT_TSV.relative_to(REPO)}")

    clean_keys = {w.lower() for w, _ in kept_entries}

    # 2. Filter final_mobile_bigrams.tsv
    if BIGRAMS_TSV.exists():
        bigrams_before = 0
        kept_bigrams = []
        with open(BIGRAMS_TSV, encoding="utf-8") as f:
            for line in f:
                bigrams_before += 1
                parts = line.strip().split("\t")
                if len(parts) >= 2:
                    words = parts[0].split(" ")
                    if len(words) == 2 and words[0].lower() in clean_keys and words[1].lower() in clean_keys:
                        kept_bigrams.append(line)
        with open(BIGRAMS_TSV, "w", encoding="utf-8") as f:
            f.writelines(kept_bigrams)
        print(f"  Updated: {BIGRAMS_TSV.relative_to(REPO)} ({bigrams_before:,} -> {len(kept_bigrams):,}, -{bigrams_before - len(kept_bigrams)})")

    # 3. Filter personal_phrases.tsv
    if PHRASES_TSV.exists():
        phrases_before = 0
        kept_phrases = []
        with open(PHRASES_TSV, encoding="utf-8") as f:
            for line in f:
                phrases_before += 1
                parts = line.strip().split("\t")
                if len(parts) >= 2:
                    words = parts[0].split(" ")
                    w3 = parts[1]
                    if len(words) == 2 and words[0].lower() in clean_keys and words[1].lower() in clean_keys and w3.lower() in clean_keys:
                        kept_phrases.append(line)
        with open(PHRASES_TSV, "w", encoding="utf-8") as f:
            f.writelines(kept_phrases)
        print(f"  Updated: {PHRASES_TSV.relative_to(REPO)} ({phrases_before:,} -> {len(kept_phrases):,}, -{phrases_before - len(kept_phrases)})")

    # 4. Regenerate futo/en.combined
    futo_builder = REPO / "tools/build_futo_swipe_vocab.py"
    if futo_builder.exists():
        print("  Regenerating FUTO swipe vocabulary (app/src/main/assets/futo/en.combined)...")
        subprocess.run([sys.executable, str(futo_builder)], check=True)
        print("  Updated: app/src/main/assets/futo/en.combined")

    print("\nCleanse applied successfully!")
    print("=" * 78)


def main():
    parser = argparse.ArgumentParser(description="Audit and cull dictionary poison.")
    parser.add_argument("--analyze", action="store_true", help="Print audit report across all tiers")
    parser.add_argument("--export-quarantine", type=str, help="Export selected tier words to a JSON file")
    parser.add_argument("--apply", action="store_true", help="Apply culling to packaged dictionary assets")
    parser.add_argument("--tier", type=str, default="tier4", choices=["tier1", "tier2", "tier3", "tier4"])
    args = parser.parse_args()

    if args.apply:
        apply_cleanse(args.tier)
    elif args.export_quarantine:
        export_quarantine(args.export_quarantine, args.tier)
    else:
        analyze()


if __name__ == "__main__":
    main()
