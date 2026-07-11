#!/usr/bin/env python3
"""extract.py — Parse usage_harvest.md + usage_harvest.jsonl into training inputs.

Outputs (training/data/):
  clean_corpus.txt      V\t<text> / T\t<text>, triage=clean session lines only
  eval_pairs.jsonl      {typed, intended, prev, prev2, src}  — EVAL ONLY, never train
  eval_negatives.jsonl  {typed, prev, prev2, src}            — firing here = failure
  trace_samples.jsonl   {trace, final, src}                  — noise-model input

Rules encoded here (see handoff.md):
- jsonl AUTO_APPLIED whose id appears in a REVERTED.undoes was a WRONG correction
  -> eval_negatives, not eval_pairs.
- jsonl quirk: when prev is null, prev2 may hold a stale pre-sentence-boundary
  word -> prev2 forced null.
- Case-only corrections (this -> This) are casing-system business, not scorer
  business -> counted and dropped.
- MANUAL_FIX/MANUAL_EDIT pairs with levenshtein > max(3, len/2) are cross-word
  edits, not typos -> dropped.
- No-backspace traces that merely restate an eval pair are skipped (they'd
  double-count ops in the noise model).
"""

import json
import re
import sys
from collections import Counter

from common import (DATA, REPO, WORD_RE, MAX_TOKEN_LEN, classify_session,
                    is_letter_spaced, levenshtein, replay_trace, BACKSPACE)

MD_PATH = REPO / "data/harvest/raw/usage_harvest.md"
JSONL_PATH = REPO / "data/harvest/raw/usage_harvest.jsonl"

MD_LINE = re.compile(r"^\[([A-Z_]+(?::[A-Z]+)?)\] (\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \| (.*)$")


def valid_word(w):
    return bool(w) and len(w) <= MAX_TOKEN_LEN and WORD_RE.match(w)


def valid_intended(w):
    # intended may contain one space (dropped-space fix: "ofthe" -> "of the")
    return bool(w) and len(w) <= 2 * MAX_TOKEN_LEN and re.match(r"^[A-Za-z']+( [A-Za-z']+)?$", w)


def edit_filter_ok(before, after):
    return levenshtein(before.lower(), after.lower()) <= max(3, max(len(before), len(after)) // 2)


def parse_md(stats):
    corpus, pairs, negatives, traces = [], [], [], []
    last_session = None
    with open(MD_PATH, encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            m = MD_LINE.match(raw.rstrip("\n"))
            if not m:
                continue
            tag, _ts, rest = m.groups()
            parts = [p.strip() for p in rest.split(" | ")]
            fields = {}
            for p in parts[1:]:
                if ":" in p:
                    k, v = p.split(":", 1)
                    fields[k.strip()] = v.strip().strip('"')

            if tag.startswith("SESSION"):
                text = parts[0].strip().strip('"').strip()
                src = "V" if "VOICE" in tag else "T"
                if classify_session(text) != "clean" or is_letter_spaced(text):
                    stats["md_session_dropped"] += 1
                    continue
                if not any(c.isalpha() for c in text):
                    stats["md_session_dropped"] += 1
                    continue
                if (src, text) == last_session:  # voice chunks re-log identical text
                    stats["md_session_dupe"] += 1
                    continue
                last_session = (src, text)
                corpus.append((src, text))

            elif tag == "ACCEPTED":
                m2 = re.match(r"^(.*?) → (.*)$", parts[0])
                if not m2:
                    stats["md_accepted_unparsed"] += 1
                    continue
                typed, intended = m2.group(1).strip(), m2.group(2).strip()
                prev = None
                tri = fields.get("trigram")
                if tri:
                    tri_words = tri.split()
                    if len(tri_words) == 2 and tri_words[1].lower() == typed.lower():
                        prev = tri_words[0].lower()
                if not valid_word(typed) or not valid_intended(intended):
                    stats["md_accepted_junk"] += 1
                elif typed.lower() == intended.lower():
                    stats["case_only_dropped"] += 1
                elif not edit_filter_ok(typed, intended):
                    stats["md_accepted_too_far"] += 1
                else:
                    pairs.append({"typed": typed.lower(), "intended": intended.lower(),
                                  "prev": prev, "prev2": None, "src": "md"})

            elif tag == "REJECTED":
                m2 = re.match(r"^(.*?) ← (.*?)( \(reverted\))?$", parts[0])
                if not m2:
                    continue
                typed = m2.group(1).strip()
                prev = None
                tri = fields.get("trigram")
                if tri and len(tri.split()) == 2:
                    prev = tri.split()[0].lower()
                if valid_word(typed):
                    negatives.append({"typed": typed.lower(), "prev": prev,
                                      "prev2": None, "src": "md-rejected"})

            elif tag in ("INSISTED", "NEW_WORD"):
                word = parts[0].strip().strip('"')
                if valid_word(word):
                    negatives.append({"typed": word.lower(), "prev": None,
                                      "prev2": None, "src": "md-" + tag.lower()})
                else:
                    stats["md_insisted_junk"] += 1

            elif tag == "MANUAL_FIX":
                m2 = re.match(r'^"(.*)" → "(.*)"$', parts[0])
                if not m2:
                    continue
                before, after = m2.groups()
                if valid_word(before) and valid_word(after) and \
                        before.lower() != after.lower() and edit_filter_ok(before, after):
                    traces.append({"trace": before, "final": after, "src": "md-manual"})
                else:
                    stats["manual_filtered"] += 1
    return corpus, pairs, negatives, traces


def parse_jsonl(stats):
    corpus, pairs, negatives, traces = [], [], [], []
    events = []
    with open(JSONL_PATH, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                stats["jsonl_bad_lines"] += 1

    reverted_ids = {e["undoes"] for e in events
                    if e.get("type") == "REVERTED" and e.get("undoes") is not None}
    pair_keys = set()   # (typed, final) already represented in eval_pairs
    trace_seen = set()
    trace_rows = []     # (trace, final, from_auto_applied)

    def ctx(e):
        prev = e.get("prev")
        prev2 = e.get("prev2") if prev is not None else None  # stale-prev2 quirk
        return (prev.lower() if prev else None), (prev2.lower() if prev2 else None)

    for e in events:
        etype = e.get("type")
        if etype == "SESSION_TEXT":
            text = (e.get("text") or "").strip()
            src = "V" if e.get("src") == "VOICE" else "T"
            if classify_session(text) == "clean" and not is_letter_spaced(text) \
                    and any(c.isalpha() for c in text):
                corpus.append((src, text))
            else:
                stats["jsonl_session_dropped"] += 1

        elif etype == "AUTO_APPLIED":
            typed, applied = e.get("typed"), e.get("applied")
            if not (valid_word(typed or "") and valid_intended(applied or "")):
                stats["jsonl_pair_junk"] += 1
                continue
            prev, prev2 = ctx(e)
            if e["id"] in reverted_ids:
                negatives.append({"typed": typed.lower(), "prev": prev,
                                  "prev2": prev2, "src": "jsonl-reverted"})
            elif typed.lower() == applied.lower():
                stats["case_only_dropped"] += 1
            elif not edit_filter_ok(typed, applied):
                stats["jsonl_pair_too_far"] += 1
            else:
                pairs.append({"typed": typed.lower(), "intended": applied.lower(),
                              "prev": prev, "prev2": prev2, "src": "jsonl"})
                pair_keys.add((typed.lower(), applied.lower()))
            trace = e.get("trace")
            if trace and (trace, applied) not in trace_seen:
                trace_seen.add((trace, applied))
                trace_rows.append((trace, applied))

        elif etype == "WORD_COMMITTED":
            word, trace = e.get("word"), e.get("trace")
            if word and trace and trace != word and (trace, word) not in trace_seen:
                trace_seen.add((trace, word))
                trace_rows.append((trace, word))

        elif etype in ("INSISTED", "NEW_WORD"):
            word = e.get("word")
            if valid_word(word or ""):
                prev, prev2 = ctx(e)
                negatives.append({"typed": word.lower(), "prev": prev,
                                  "prev2": prev2, "src": "jsonl-" + etype.lower()})

        elif etype == "MANUAL_EDIT":
            before, after = e.get("before"), e.get("after")
            if before and after and valid_word(before) and valid_word(after) \
                    and before.lower() != after.lower() and edit_filter_ok(before, after):
                traces.append({"trace": before, "final": after, "src": "jsonl-manual"})
            else:
                stats["manual_filtered"] += 1

    for trace, final in trace_rows:
        replayed = replay_trace(trace)
        if replayed.lower() == final.lower() and BACKSPACE not in trace:
            stats["trace_no_info"] += 1          # pure autocap / restated commit
            continue
        if BACKSPACE not in trace and (replayed.lower(), final.lower()) in pair_keys:
            stats["trace_dupes_pair"] += 1       # would double-count noise ops
            continue
        traces.append({"trace": trace, "final": final, "src": "jsonl"})
    return corpus, pairs, negatives, traces


def main():
    DATA.mkdir(parents=True, exist_ok=True)
    stats = Counter()
    md_corpus, md_pairs, md_negs, md_traces = parse_md(stats)
    js_corpus, js_pairs, js_negs, js_traces = parse_jsonl(stats)

    corpus = md_corpus + js_corpus
    pairs = md_pairs + js_pairs
    negatives = md_negs + js_negs
    traces = md_traces + js_traces

    with open(DATA / "clean_corpus.txt", "w", encoding="utf-8") as fh:
        for src, text in corpus:
            fh.write(f"{src}\t{text}\n")
    for name, rows in (("eval_pairs.jsonl", pairs),
                       ("eval_negatives.jsonl", negatives),
                       ("trace_samples.jsonl", traces)):
        with open(DATA / name, "w", encoding="utf-8") as fh:
            for row in rows:
                fh.write(json.dumps(row, ensure_ascii=False) + "\n")

    n_tokens = sum(len(text.split()) for _, text in corpus)
    n_voice = sum(1 for s, _ in corpus if s == "V")
    print(f"clean_corpus.txt     : {len(corpus)} lines ({n_voice} voice), ~{n_tokens} tokens")
    print(f"eval_pairs.jsonl     : {len(pairs)}  (md={len(md_pairs)}, jsonl={len(js_pairs)})")
    print(f"eval_negatives.jsonl : {len(negatives)}  (md={len(md_negs)}, jsonl={len(js_negs)})")
    print(f"trace_samples.jsonl  : {len(traces)}  (md={len(md_traces)}, jsonl={len(js_traces)})")
    print("\nfilter stats:")
    for k in sorted(stats):
        print(f"  {k:<24} {stats[k]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
