# Autocorrect Refactor Archive (2026)

This archive contains historical phasing, progress, and planning documentation related to the large autocorrect refactor that was completed in July 2026.

## Status: Complete
The autocorrect refactor is fully complete and merged. The active implementation partitions work cleanly into Retriever, Judge, Gate, and Caser layers.

For the active design and future "Gboard-style" north-star plan, refer to the active plan:
- [harvest-2026-07-15-analysis-and-north-star-plan.md](file:///home/sam/projects/keyboard/docs/development/harvest-2026-07-15-analysis-and-north-star-plan.md)

## Archived Documents

The following documents have been moved here to preserve history and avoid confusion for agents and developers:

- [2026-07-11-direction-map.md](file:///home/sam/projects/keyboard/docs/archive/autocorrect-refactor-2026/2026-07-11-direction-map.md) — Historical snapshot of the autocorrect overhaul planning and Phase 1 completion.
- [2026-07-12-direction-map.md](file:///home/sam/projects/keyboard/docs/archive/autocorrect-refactor-2026/2026-07-12-direction-map.md) — Historical snapshot of correction-system stabilization progress during Phase 2.
- [autocorrect-regression-forensic-record.md](file:///home/sam/projects/keyboard/docs/archive/autocorrect-refactor-2026/autocorrect-regression-forensic-record.md) — Canonical forensic investigation of Phase 2 baseline regression.
- [interrupted-autocorrect-recovery.md](file:///home/sam/projects/keyboard/docs/archive/autocorrect-refactor-2026/interrupted-autocorrect-recovery.md) — Temporary handoff instructions for recovering the interrupted Fable session.

## Canonical Documentation

For the current behavior of the autocorrect system, please consult the canonical documentation:

- [docs/autocorrect/README.md](file:///home/sam/projects/keyboard/docs/autocorrect/README.md) — Main entry point for autocorrect and suggestions.
- [docs/autocorrect/live-pipeline.md](file:///home/sam/projects/keyboard/docs/autocorrect/live-pipeline.md) — Detailed live pipeline mechanics.
- [docs/autocorrect/heuristic-scoring.md](file:///home/sam/projects/keyboard/docs/autocorrect/heuristic-scoring.md) — Heuristic ranking layers.
- [docs/autocorrect/neural-scorer.md](file:///home/sam/projects/keyboard/docs/autocorrect/neural-scorer.md) — ONNX model scoring and commit gate authority.
- [docs/autocorrect/phrase-prediction.md](file:///home/sam/projects/keyboard/docs/autocorrect/phrase-prediction.md) — Secondary smartbar row phrase prediction.
- [docs/autocorrect/harvesting.md](file:///home/sam/projects/keyboard/docs/autocorrect/harvesting.md) — Data harvesting and feedback loops.
