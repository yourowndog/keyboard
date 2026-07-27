# Stage 05 Prompt — Mode Semantics and Frame Policy

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

## Commit boundary

Separate semantic/policy migration from the missing-Symbols2 repair.

Finish with the standard report-back.

