# tools/autocorrect

Offline autocorrect analysis and forensic replay. These tools do **not** mutate runtime assets;
they read logs/corpora and produce read-only analysis.

| Script | Purpose | Reads | Writes |
|---|---|---|---|
| `autocorrect_trace.py` | Frozen offline replay of the correction pipeline for forensics (froze the 2026-07-12 autocorrect regression investigation) | harvest JSONL / corpus | stdout / analysis output |

See also: [`docs/autocorrect/`](../../docs/autocorrect/) for the live pipeline, heuristic scoring,
and neural scorer docs, and [`docs/development/data-and-scripts-map.md`](../../docs/development/data-and-scripts-map.md)
for the full script/data-flow index.
