# OmniBoard Documentation

> Status: Canonical index  
> Last verified: 2026-07-11

This directory describes the current OmniBoard codebase. A document is not
canonical merely because it is detailed or confidently written. Canonical
documents identify the implementation they were checked against and separate
current behavior from plans, experiments, and history.

## Documentation status

- **Canonical**: checked against the current implementation and intended to be
  maintained with it.
- **Experimental**: describes code or a workflow that exists but is not the
  normal production path.
- **Historical**: preserves why a system changed; it must not be used as a
  description of current behavior.
- **Generated**: produced from code or data. Edit its source or generator rather
  than treating the output as independent truth.

## Start here

- [System map](architecture/system-map.md): modules, runtime entry points, and
  subsystem ownership.
- [Keyboard construction](keyboard/README.md): layouts, keys, geometry, popups,
  customization, and touch handling.
- [Autocorrect](autocorrect/README.md): candidate retrieval, heuristic ranking,
  neural gating, phrase prediction, and feedback.
- [Snygg theming](theming/README.md): verified selectors, authoring, packaging,
  coverage, and hard-won lessons.
- [Development](development/building.md): local/Beksinski builds, tests, device
  validation, and repository organization.
- [Agent reorientation](development/agent-reorientation.md): entering the
  cleaned repository or recovering interrupted work safely.
- [Upstream Backport & Modernization Radar](development/upstream-backport-radar.md): comprehensive technical plan for backporting non-floating upstream features, bug fixes, state flow optimizations, and Snygg theming capabilities.
- [Swipe synthesis & training architecture](swipe-data-and-pipeline-architecture.md): lossless FUTO raw data foundation, dataset boundaries, target vocabulary, and pipeline quarantine.
- [Historical refactor docs](file:///home/sam/projects/keyboard/docs/archive/autocorrect-refactor-2026/README.md): Completed autocorrect refactor (July 2026) planning and checkpoints.

## Truth hierarchy

When sources conflict, use this order:

1. Observed runtime behavior on the current build.
2. Tests and executable schemas.
3. Current implementation and packaged assets.
4. Canonical documentation in this directory.
5. Plans, handoffs, journals, and historical documents.

Runtime behavior outranks source only when the build and configuration being
tested are known. Otherwise, record the discrepancy instead of guessing.

For harvest formats, analysis methods, and tuning workflows that evolved over
time, prefer the most recent implementation and verified report generation.
Older material remains useful for history and hard-won lessons, but it does not
override newer event schemas, IDs, privacy guards, or calibrated procedures
merely because it is more detailed.

## Updating documentation

When behavior changes:

1. Update the closest canonical topic document.
2. Link to the implementation rather than copying large code blocks.
3. Move superseded explanations to a history note only when the reason remains
   useful.
4. Update the verification date only for the sections actually rechecked.
5. Do not create another root-level handoff document as a substitute for
   maintaining the relevant guide.
