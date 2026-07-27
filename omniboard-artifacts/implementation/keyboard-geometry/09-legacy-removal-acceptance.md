# Stage 09 Prompt — Legacy Removal and Acceptance

You are implementing the final cleanup and acceptance stage of OmniBoard's keyboard-row and geometry migration.

Read all governing documents and the reports from Stages 00–08. Do not remove a legacy mechanism until repository search and tests prove it has no production consumer.

## Objective

Remove superseded semantic/geometry authorities, clean the verified dead QWERTY asset path, and complete automated plus physical-device acceptance.

## Required removals

When proven unused:

- `bottomModRowCount`;
- row classification through `isAlpha`;
- literal Space-content row policy;
- `rowCount >= 5` geometry branching;
- `N-2/N-1` gap positioning;
- independent legacy outer-frame and internal-row formulas;
- last-Characters frame borrowing;
- post-layout structural width/height mutation;
- default constructor semantics for sentinels/placeholders;
- obsolete compatibility aliases whose migration window is complete.

If a genuinely key-level alpha/hint concept remains, rename and scope it truthfully; do not keep `isAlpha` as a hidden row-role channel.

## Dead QWERTY row

1. Re-run the placeholder/fallback tests.
2. Confirm normal runtime and supported fallback no longer require `characters/qwerty_wide.json` row 3.
3. Remove it or move its historical description into documentation according to repository conventions.
4. Prove that neither duplicate nor missing primary rows result.

Do not perform broad unrelated FlorisBoard asset pruning.

## Repository verification

- Search for every removed field, formula, heuristic, old preference key, and compatibility adapter.
- Confirm all five constructor paths still satisfy the contract.
- Confirm saved old preferences and layout packs still migrate from fixtures.
- Confirm there is exactly one structural geometry authority.
- Confirm popup, touch, and visual consumers use declared layers.

## Full automated matrix

Run the complete test/build suite plus the cross-stage matrix:

- Text and Coding;
- all keyboard modes;
- Coding utilities visible/hidden;
- all extension combinations;
- portrait/landscape;
- default and non-default geometry;
- bundled, old-pack, and new-pack layouts;
- fresh install, upgrade, and restart;
- taps, bottom-edge taps, popups, and long press.

## Physical-device acceptance

Install the final debug build on the target phone. Validate:

- tuned Coding appearance retained where intended;
- Text layout and settings selection;
- no row/key overlap at customization extremes;
- bottom bezel reachability;
- popup placement on center and edge keys;
- hidden Coding utilities;
- all modes and rotations;
- process restart and profile/layout restoration;
- no regression in key actions, autocorrect entry, or inline-autofill surface placement.

Record failures as reproducible coordinates/settings/profile/mode, not subjective summaries.

## Non-goals

- No new features.
- No architecture redesign.
- No unrelated cleanup.
- No performance rewrite unless profiling shows a regression caused by this migration.

## Commit boundary

Use separate commits for:

1. semantic/geometry legacy removal;
2. preference/adapter cleanup;
3. verified dead-row cleanup;
4. test/acceptance fixes.

Finish with the standard report-back, final search inventory, and signed-off device checklist.

