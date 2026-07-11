# Harvest Data

> Status: Mixed raw, generated, and historical data  
> Last verified: 2026-07-11

## `raw/`

The canonical reviewed corpus:

- `usage_harvest.md`: chronological human-readable stream spanning legacy and
  current event formats.
- `usage_harvest.jsonl`: newer structured stream with event IDs, session IDs,
  app context, neural shadow records, and relationships.

Do not sort, line-deduplicate, reformat, or silently clean these files. Repeated
events and file order may be meaningful.

## `reports/`

Aggregate or narrative analysis derived from a particular corpus state. Reports
must not be treated as live application behavior or permanent metrics without
checking their generation date and input boundary.

## `derived/`

Reproducible or review-oriented outputs such as bigram proposals, phrase tables,
anti-correction suggestions, personal seed dictionaries, and reversible
segmentation mappings. These are not automatically packaged into the app.

## `inbox/`

Ignored, timestamped exact snapshots captured from the device. Review snapshots
before promotion into `raw/`. Keeping capture separate prevents a sync mistake
from corrupting the canonical corpus.

See `tools/harvesting/README.md` for operations and
`docs/autocorrect/harvesting.md` for event semantics.

