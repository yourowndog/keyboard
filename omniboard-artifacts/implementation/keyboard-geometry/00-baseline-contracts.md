# Stage 00 Prompt — Baseline Contracts

You are implementing Stage 00 of OmniBoard's keyboard-row and geometry migration.

Read the four governing documents in `docs/architecture/` before editing. The inspected baseline was `805d3e58e947215a9eb88ab9ed92b46366c54ef0`; first record the actual branch, HEAD, and worktree state.

## Objective

Create characterization tests and diagnostic fixtures that make the present construction, geometry, and persistence behavior measurable before production ownership changes.

This is not a geometry refactor. Do not introduce the target solver or semantic-row production model in this stage.

## Required scope

1. Cover all five verified `TextKeyboard` construction sites:
   - normal bundled composition;
   - layout-pack composition;
   - Editing empty sentinel;
   - loading placeholder;
   - smartbar quick-actions empty sentinel.
2. Add composition fixtures for:
   - default Coding;
   - Coding utilities hidden;
   - number extension;
   - developer extension;
   - both extensions;
   - Characters and wide Symbols;
   - Numeric, Numeric-Advanced, Phone, and Phone2;
   - representative layout pack with spacers/units and disabled rows.
3. Capture current outputs relevant to:
   - ordered row/key sequence;
   - `isAlpha`;
   - `bottomModRowCount`;
   - total frame calculation;
   - per-row allocation;
   - current gap mutation;
   - structural/touch/visible bounds;
   - popup base/anchor inputs.
4. Add upgrade fixtures for populated old preferences, subtype JSON, customization JSON, and saved old layout packs.
5. Add a focused fixture for placeholder-present versus placeholder-absent QWERTY merge behavior.
6. Record the missing `symbols2/western_wide` default as a narrowly scoped failing/expected diagnostic or issue; do not redesign Symbols2 here.

## Test classification

Mark assertions as one of:

- `COMPATIBILITY` — intentionally preserved through migration;
- `KNOWN_DEFECT` — evidence of behavior that later stages must fix;
- `MIGRATION_FIXTURE` — persisted input that future upgrades must decode.

Do not turn known defects into permanent golden requirements.

## Non-goals

- No row-role taxonomy implementation.
- No production geometry changes.
- No preference-key migration.
- No asset deletion or rename.
- No profile UI.
- No unrelated repository cleanup.

## Verification

Run the smallest relevant tests during development, then the repository's full unit-test and debug-build commands. If layout code cannot be tested directly, introduce the smallest pure seam needed for observation without changing production outputs.

## Commit boundary

Commit only test infrastructure, fixtures, and behavior-neutral observation seams. Split unrelated Symbols2 issue documentation into a separate commit if needed.

Finish with the standard report-back from this prompt set.

