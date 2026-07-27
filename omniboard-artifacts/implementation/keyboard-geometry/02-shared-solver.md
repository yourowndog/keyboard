# Stage 02 Prompt — Shared Geometry Solver

You are implementing Stage 02 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents. Confirm semantic rows exist on all construction paths and all prior tests pass.

## Objective

Introduce one pure, deterministic geometry solver and immutable solved-result model. Run it beside legacy geometry for comparison; do not yet remove the legacy production path.

## Solver input

The input must explicitly include:

- available width and relevant height constraints;
- ordered semantic rows and ordered keys/spacers;
- structural width units;
- profile/mode frame policy;
- row-height and boundary-gap policies;
- validated user overrides;
- orientation/inset inputs that structurally matter.

Do not pass `bottomModRowCount`, `rowCount >= 5`, or a derived “contains Space” flag as semantic input.

## Solver output

The immutable result must include:

- total content/frame size;
- structural rectangle for every row;
- structural rectangle for every key and spacer;
- explicit boundary gaps;
- stable IDs linking outputs to rows/items;
- declared inputs needed later to derive touch, visual, and popup geometry.

Use one numeric coordinate convention and document rounding ownership. The final pixel allocation must conserve available width/height without cumulative drift.

## Required invariants

- No structural overlap.
- Every rectangle is finite and non-negative.
- Items fit within their row and rows fit within content bounds.
- Width units reflow neighbors.
- Frame height equals the sum of structural rows and declared gaps, subject only to documented outer insets.
- Results are deterministic for identical immutable inputs.
- Row count alone never determines semantics.

## Comparison mode

Add a debug/test-only comparator between legacy outputs and the solver:

- Default Coding should be treated as a compatibility target where behavior is intentional.
- Short/specialized mode differences must be classified as expected fixes or unresolved differences.
- Do not distort semantic roles to reproduce known defects.

## Required tests

Use table/property-style coverage for:

- row counts 0–8;
- Coding visible/hidden utilities;
- extensions;
- specialized modes;
- portrait/landscape dimensions;
- default and representative extreme preferences;
- asymmetrical units/spacers/margins;
- invalid/unsatisfiable constraints;
- rounding conservation.

## Non-goals

- No production renderer cutover.
- No removal of old formulas.
- No preference-schema migration.
- No profile UI.
- No visual editor changes.

## Commit boundary

Commit the pure model/solver separately from comparison wiring and tests when practical.

Finish with the standard report-back, including a compact difference table.

