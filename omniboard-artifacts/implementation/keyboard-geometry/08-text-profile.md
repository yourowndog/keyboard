# Stage 08 Prompt — Text Profile

You are implementing Stage 08 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents. Do not build Text by copying historical Coding accidents.

## Objective

Add a conventional, compact, first-class Text profile on the same semantic-row and geometry engine as Coding.

## Product requirements

- Text is a familiar everyday layout comparable in structure to a conventional phone keyboard.
- Text and Coding have distinct defaults and independently scoped customization.
- Text is not “Coding with utilities hidden.”
- The primary action row remains explicitly `PRIMARY_ACTION`.
- The dead fourth row from `characters/qwerty_wide.json` is not the Text source.

## Required scope

1. Add truthful Text layout asset/model identity and display naming.
2. Define Text semantic rows explicitly.
3. Use the common solver, boundary model, popup inputs, and customization pipeline.
4. Preserve subtype/language mappings and mode access.
5. Provide an explicit settings selector between Text and Coding.
6. Persist global active profile across restart.
7. Switch safely even when one profile's layout is missing/corrupt.
8. Keep compact Coding as a separate Coding preference.
9. Ensure Text Symbols behavior follows declared Text frame policy.
10. Add migration-safe aliases only where existing identifiers require them.

## Deferred interaction

Do not repurpose long-press Space or invent a permanent on-keyboard profile gesture in this stage. If an existing gesture still toggles Coding utility visibility, keep it scoped to Coding and make its Text behavior explicit/no-op as appropriate.

## Required tests

- Text and Coding row/role snapshots.
- Profile switch and restart persistence.
- Independent per-profile geometry/customization values.
- Subtype/language preservation during switching.
- Characters/Symbols transitions in each profile.
- Orientation, popup, bottom-edge touch, and non-default geometry.
- Missing/corrupt profile layout fallback.
- Compact Coding remains distinct from Text.

## Device checkpoint

Install and validate normal typing, profile switching in settings, symbols, popups, bottom-edge Space/Enter, rotation, restart, and return to the user's tuned Coding layout.

## Non-goals

- No automatic per-app/per-field profile selection.
- No permanent on-keyboard switch gesture.
- No broad visual redesign.
- No key-binding changes.

## Commit boundary

Separate the Text layout/domain asset from profile-selection UI when practical.

Finish with the standard report-back and screenshots/checklist for both profiles.

