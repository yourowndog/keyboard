# Harvesting and Feedback Review

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `HarvestManager.kt`, `HarvestJsonl.kt`, editor hooks, current
> analysis scripts, and reconciled harvest reports

Harvesting records behavior for later analysis; it does not automatically
retrain or rewrite the packaged dictionaries.

## Outputs

The app attempts to create files in the public Documents directory and falls
back to app-private storage when necessary:

- `usage_harvest.md`: human-readable legacy/current event log.
- `usage_harvest.jsonl`: machine-readable structured event stream.

The JSONL stream dual-writes legacy schema-v3 events and schema-v4 `WORD_SLOT`
records. V4 keeps the complete touch/edit/offer/outcome journey in one record;
v3 remains schema-compatible for the existing review tools. New readers prefer a
v4 slot when its `slot` join key also appears on a v3 mirror row.

Repository copies live under `data/harvest/raw/`. Exact device captures first
land in the ignored `data/harvest/inbox/`; capture never merges or line-deduplicates
the canonical corpus. Operational commands are documented in
`tools/harvesting/README.md`.

## Sensitive fields

Editor context includes a single pre-assembly harvest guard. Markdown, v3 JSONL,
and v4 JSONL reject events in password, visible-password, web-password,
email-address, and web-email-address fields. This is implemented behavior, not
merely a planned privacy fix.

## Event interpretation

Events are evidence, not labels that can always be trusted independently:

- An applied correction followed by continued typing is useful positive
  evidence.
- A revert is negative evidence about the applied correction.
- A manual edit may identify the actual intended target.
- Insistence or a new word is useful leave-alone evidence.
- Voice sessions are language/context evidence but not touch-typing error data.
- Termux, URLs, code, and commands must not be treated as ordinary prose without
  register-aware classification.
- Missing or ambiguous follow-up events remain unresolved; do not force-label
  them.

## Training coverage and historical migration

Schema v3 remains fully useful for language/register clustering, typed-versus-
voice corpus weighting, autocorrect ranking/outcome labels, candidate lists that
were actually logged, and the subset of edits represented by traces or explicit
manual-edit events. Schema v4 additionally enables spatial-personalization,
touch timing, stable per-layout conditioning, complete backspace/retype journeys,
reliable smartbar-pick labels, input-attachment boundaries, and continuous
candidate reachability measurement.

Historical rows cannot reconstruct touch coordinates, touch duration, layout
fingerprints, or unwired smartbar taps. The migration therefore leaves those
features missing; it never substitutes zeroes.

Create new canonical training inputs without modifying the raw corpus:

```bash
python3 tools/harvesting/migrate_harvest_v4.py \
  data/harvest/raw/usage_harvest.jsonl \
  data/harvest/derived/word_slots.v4.jsonl
```

The companion `word_slots.v4.sessions.jsonl` retains v3 `SESSION_TEXT` language
context. The converter preserves source lines/event IDs, folds v3
`AUTO_APPLIED`/`REVERTED`/`SUGGESTIONS_SHOWN` evidence into word slots, prefers
native v4 during mixed-schema deduplication, and fails if v3 word-count
preservation does not balance. `training/extract.py` also reads mixed v3/v4
directly and suppresses joined v3 mirrors.

## Review boundary

The review workflow should be proposal-based:

1. Sync or select only new events.
2. Measure provenance and data quality.
3. Separate typing, voice, code/command, and conversational registers.
4. Join event sequences where IDs or adjacency permit.
5. Propose dictionary, bigram, personal-vocabulary, anti-correction, heuristic,
   or neural-training changes with counts and examples.
6. Apply only explicitly approved categories.
7. Rebuild, test, and mark the reviewed boundary.

Historical scripts and reports used multiple paths and evolving Markdown
formats. They should be retained under a coherent harvesting tool directory
until their current compatibility is tested.

## Evolution rule

Harvest infrastructure has changed repeatedly. When generations conflict, the
newer implementation, event schema, and verified workflow are presumed
authoritative. Preserve older discoveries, but do not reinstate obsolete sync,
parsing, deduplication, or labeling behavior without evidence that it is still
needed.
