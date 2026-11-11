# Stage 05 Prompt — Mode Semantics and Frame Policy

## Starting state

Stages 0 through 4.5 are complete. The Stage 4.5 repair build
`0.5.0-debug+65dae525` was installed in place and accepted on the physical
`SM_S938U` on 2026-08-05; OmniBoard remained selected and both persisted
subtypes survived. The `qwerty_wide_full` compatibility id now resolves to the
shipped QWERTY Wide arrangement and coding modifier.

Do not reopen the Settings/Layouts legibility work or Factory default recovery
without a demonstrated regression. Keep `LayoutPack`, `LayoutPackRepository`,
and `LayoutValidation` for Stage 6. The missing `symbols2/western_wide` default
remains open and must keep the focused commit boundary below.

You are implementing Stage 05 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents and confirm profile-scoped persistence and solved geometry are active.

## Objective

Remove alpha/modifier masquerading from specialized modes and replace last-Characters frame borrowing with explicit profile/mode policy.

## Required scope

1. Assign and consume honest mode roles:
   - Numeric, Numeric-Advanced, Phone, and Phone2 rows: `NUMERIC`;
   - Symbols and Symbols2 rows: `SYMBOL`;
   - number/developer inserted rows: `EXTENSION`;
   - Characters rows retain their actual semantic roles.
2. Define explicit frame groups:
   - Characters, Symbols, and Numeric-Advanced may use one profile-stable frame group;
   - Numeric and Phone families use their declared mode policy unless evidence requires another explicit group.
3. Resolve frame height through the common solver. Remove mode code that borrows the last Characters evaluator as a substitute geometry authority.
4. Review symbol hint alignment and any bottom-offset arithmetic that assumes anonymous row counts.
5. Correct cache invalidation so utility-row visibility and semantic changes invalidate every affected mode.
6. Investigate the missing default `symbols2/western_wide` component. Fix it only in a focused commit with direct tests, or document a concrete separate blocker.

## Compatibility requirements

- Mode switches designated as same-frame must remain visually stable.
- Specialized rows respond to applicable height controls without inheriting alpha width/touch/hint policy.
- No numeric or phone mode receives Coding boundary gaps.
- Existing key actions and symbol contents remain unchanged.

## Required tests

- Per-mode semantic row assertions.
- Default and non-default height/spacing/gap matrices.
- Characters/Symbols/Numeric-Advanced stable-frame transitions.
- Numeric/Phone independent frame behavior.
- Extension combinations.
- Symbol hint pairing/alignment.
- Runtime utility-visibility changes without restart.
- Symbols2 default/fallback behavior.

## Device checkpoint

Validate transitions among all reachable modes in portrait and landscape, with non-default height and gap preferences.

## Non-goals

- No layout-pack schema work.
- No Text layout construction.
- No key-action redesign.
- No broad symbol asset cleanup.

## Suggested order

1. Pin the current semantic rows, frame heights, and cache behavior for every
   reachable mode with characterization tests.
2. Assign honest `NUMERIC`, `SYMBOL`, and `EXTENSION` roles and make the solver
   consume them without changing key contents or actions.
3. Add explicit profile/mode frame grouping through the common solver, then
   remove last-Characters evaluator borrowing.
4. Audit symbol hints, bottom-offset/gap arithmetic, and invalidation against
   the new roles and frame policy.
5. Handle the missing Symbols2 default in its own focused commit and tests.
6. Run the full build/unit suite, then validate all reachable transitions in
   portrait and landscape with representative non-default geometry settings.

## Commit boundary

Separate semantic/policy migration from the missing-Symbols2 repair.

Finish with the standard report-back.
