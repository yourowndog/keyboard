# tools/autocorrect

Offline autocorrect analysis and evaluation. These tools do **not** mutate runtime
assets; they read packaged dictionaries/corpora and produce read-only analysis or
machine-readable JSON.

| Script | Purpose | Reads | Writes |
|---|---|---|---|
| `autocorrect_trace.py` | Frozen offline replay of the correction pipeline for forensics (froze the 2026-07-12 autocorrect regression investigation) | harvest JSONL / corpus | stdout / analysis output |
| `evaluate.py` | Reproducible autocorrect/suggestion evaluation harness with a committed baseline | `app/src/main/assets/ime/dict/*` | stdout / JSON file |
| `evaluate-baseline.json` | Fixed-point baseline summary produced by `evaluate.py --compact` (`tools/autocorrect/`) | n/a (comparison artifact) | n/a |

See also: [`docs/autocorrect/`](../../docs/autocorrect/) for the live pipeline, heuristic
scoring, and neural scorer docs, and
[`docs/development/data-and-scripts-map.md`](../../docs/development/data-and-scripts-map.md)
for the full script/data-flow index.

## evaluate.py — autocorrect evaluation harness

Turns "autocorrect is bad" into measurable regressions/improvements. It replays the
offline edit/prefix retrieval + candidate scoring + commit policy against the packaged
dictionary, bigram, protected-form, anti-correction and shortcut tables (the same
offline reconstruction `autocorrect_trace.py` uses), and runs a fixed case suite.

**Coverage** (see `build_test_suite()` in `evaluate.py`):

- exact-word preservation (known/valid words are not rewritten)
- typo correction and auto-commit (including the digit/`NUMERIC_TOKEN` guard)
- candidate ranking (rank assertions against the ranked list)
- punctuation / spacing: apostrophe contractions, licensed-form bonus, ambiguity
- correction undo/rejection (valid-word immunity keeps valid typed words literal)
- protected vocabulary and anti-correction pair filtering
- contraction shortcuts
- contextual/secondary bigram influence
- corpus/dictionary health signals (entry, bigram, protected, anti-correction,
  shortcut and licensed-form counts) in the JSON `summary.corpus_stats`

Swipe-to-word handoff is **out of scope** for the offline harness: there is no gesture
corpus in the repo to replay, so it is deliberately not asserted here.

### Commands

```sh
# From the repository/worktree root.
# Full JSON (per-case candidate detail) to stdout:
python3 tools/autocorrect/evaluate.py --worktree .

# Compact machine-readable summary (suited to CI/baseline): one row per case.
python3 tools/autocorrect/evaluate.py --worktree . --compact

# Write JSON to a file instead of stdout:
python3 tools/autocorrect/evaluate.py --worktree . --compact --output /tmp/eval.json
```

Exit codes: `0` all cases pass; `1` usage error; `2` dictionary assets not found;
`3` one or more cases failed or an evaluation error occurred.

### Comparing against the baseline

`evaluate-baseline.json` is the fixed commit-time snapshot
(`--compact`, all cases pass). To see whether a future worker changed behavior:

```sh
python3 tools/autocorrect/evaluate.py --worktree . --compact \
    --output /tmp/eval-after.json
# Ignore summary.timestamp; compare:
#   summary.by_category.*.passed/failed  (per-category regressions)
#   summary.corpus_stats.*               (dictionary health / schema changes)
#   results[].{typed,top,commits,passed} (per-case behavior)
# A baseline diff: count any results[].passed flip from true->false as a regression;
# false->true as an improvement; changed `top`/`commits` as behavior change to review.
```

Note: `id` is a known dictionary word stored as `ID` and currently auto-commits the
casing (documented, not protected). If it should be preserved instead of auto-committing,
make it protected and flip the two `anti_correction`/`contextual_bigram` `id` cases to
`expected_commit=False` — the baseline will then track the change.