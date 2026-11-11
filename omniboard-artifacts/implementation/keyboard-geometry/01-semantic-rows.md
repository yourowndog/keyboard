# Stage 01 Prompt — Semantic Rows

You are implementing Stage 01 of OmniBoard's keyboard-row and geometry migration.

Read the governing architecture/current-state/decision/migration documents and confirm Stage 00 evidence is present and passing.

## Objective

Preserve explicit semantic row identity and provenance through every `TextKeyboard` construction path without intentionally changing rendered geometry.

## Required model

Introduce the smallest immutable normalized row model capable of carrying:

- stable row ID;
- semantic role;
- provenance;
- ordered key/spacer contents or a compatible bridge;
- geometry-policy reference or explicit placeholder for later policy.

Initial roles must truthfully cover:

- `ALPHA`;
- `PRIMARY_ACTION`;
- `CODING_UTILITY`;
- `EXTENSION`;
- `NUMERIC`;
- `SYMBOL`;
- `PLACEHOLDER`.

Empty sentinel keyboards contain no semantic rows and must identify themselves as sentinels rather than inheriting ordinary constructor defaults.

## Required scope

1. Assign roles and provenance while `mergeLayouts()` still knows source and splice/replacement history.
2. Produce the same normalized contract from `computeKeyboardFromLayoutPack()` using an explicit compatibility mapping. Do not silently default every row to alpha or two modifier rows.
3. Give loading rows `PLACEHOLDER` semantics.
4. Make Editing and smartbar empty keyboards explicit sentinels.
5. Preserve current `arrangement`/consumer compatibility through a narrow adapter if necessary.
6. Retain `isAlpha` and `bottomModRowCount` only as deprecated derived compatibility projections while legacy consumers remain.
7. Prevent downstream code added in this stage from inferring roles using row index, row count, literal Space, or filename.

## Role assignment requirements

- The merged Space/punctuation/action row is `PRIMARY_ACTION`, regardless of its source asset directory.
- Appended Coding work rows are `CODING_UTILITY`.
- Numeric and Phone rows are `NUMERIC`.
- Symbol rows are `SYMBOL`.
- Extension rows are `EXTENSION`.
- Letter-entry rows are `ALPHA`.

Where a legacy layout pack is genuinely ambiguous, use a named compatibility result and surface diagnostics. Do not claim inferred semantics are native pack metadata.

## Non-goals

- Do not switch sizing/layout to semantic roles yet.
- Do not add profile persistence.
- Do not change gap behavior.
- Do not remove `isAlpha` or `bottomModRowCount`.
- Do not alter key actions or assets.

## Required tests

- Every Stage 00 constructor/composition fixture now asserts explicit row IDs, roles, and provenance.
- Compatibility geometry snapshots remain unchanged unless a test exposed constructor-default behavior for a sentinel; document any intentional sentinel-only correction.
- Duplicate or missing stable IDs fail validation.
- Pack ambiguity/fallback behavior is deterministic and tested.

## Commit boundary

Prefer one commit for the domain model and one for construction-path adoption/tests. End with all production geometry still using the compatibility projection.

Finish with the standard report-back.

