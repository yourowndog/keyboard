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

The JSONL stream carries IDs and relationships useful for joining neural shadow,
application, revert, and edit events. Prefer it for new machine analysis while
retaining Markdown compatibility until the review tools are migrated.

Repository copies live under `data/harvest/raw/`. Exact device captures first
land in the ignored `data/harvest/inbox/`; capture never merges or line-deduplicates
the canonical corpus. Operational commands are documented in
`tools/harvesting/README.md`.

## Sensitive fields

Editor context includes password detection. Both Markdown and JSONL paths reject
events when the supplied or current context is a password field. This is current
implemented behavior, not merely a planned privacy fix.

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
