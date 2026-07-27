# Stage 03 Prompt — Structural Gaps and Bounds

You are implementing Stage 03 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents and the Stage 02 comparison report. Do not proceed if unexplained default-Coding differences remain.

## Objective

Make solved geometry authoritative for structural frame/row/key placement, replace positional gap mutation with semantic boundaries, and explicitly derive touch and visual bounds.

## Required scope

1. Make outer frame sizing and `TextKeyboard` internal placement consume the same solved result or the same immutable solution owner.
2. Remove production dependence on independently recomputing height in `FlorisImeSizing.keyboardUiHeight()` and `TextKeyboard.layout()`.
3. Represent gaps using semantic boundaries, including:
   - alpha to primary action;
   - primary action to Coding utility;
   - Coding utility to Coding utility;
   - final row to bottom edge.
4. Remove `N-2/N-1` structural mutation from Compose.
5. Derive:
   - structural allocations;
   - touch bounds;
   - visible bounds;
   as named, separately testable steps.
6. Preserve bottom-edge hitability through an explicit edge-touch policy.
7. Keep service/window bottom offset outside solved row geometry.
8. Route popup sizing and anchoring through documented solved/visual geometry inputs.
9. Update Compose state/memoization so profile/semantic/solver revisions recompute correctly and exactly when needed.

## Compatibility policy

Preserve intentional default Coding appearance and actions. Intentionally fix:

- gaps landing inside alpha rows when utilities are hidden;
- positional gaps on Numeric/Phone;
- outer/inner disagreement on short keyboards;
- accidental bounds divergence caused by structural gap mutation.

Any additional visible or touch change requires an evidence-backed explanation.

## Required tests

- Frame conservation and no-overlap tests.
- Boundary-coordinate tests for Coding visible/hidden, numeric families, symbols, and target Text-shaped rows.
- Touch-versus-visual assertions for gaps and bottom edge.
- Popup size/anchor tests at center and screen edges.
- Runtime preference-change recomputation tests.
- Portrait/landscape tests.

## Device checkpoint

Produce an installable debug build and a concise manual script covering:

- bottom row taps at the physical screen edge;
- non-zero gap controls;
- hidden/visible Coding utilities;
- symbols and Numeric-Advanced transitions;
- popup placement on edge keys.

Do not claim final interaction correctness without this device checkpoint.

## Non-goals

- No Text profile UI.
- No layout-pack schema redesign.
- No structural customization migration.
- No dead asset deletion.

## Commit boundary

Separate solver cutover, gap/bounds derivation, and popup/recomposition adaptation when this improves reviewability. Every intermediate commit must build.

Finish with the standard report-back and device checklist.

