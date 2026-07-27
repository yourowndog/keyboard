# Stage 04 Prompt — Profiles, Persistence, and Naming

You are implementing Stage 04 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents. The geometry foundation must already be authoritative and tested.

## Objective

Establish truthful Text/Coding profile identity and scoped persistence while preserving the upgraded user's current Coding experience and language subtype data.

## Required decisions

- Add canonical profile IDs for `text` and `coding`.
- Add a globally persisted `activeProfileId`.
- Existing global keyboard geometry, visibility, and customization settings migrate into Coding.
- Text receives clean defaults rather than inheriting Coding tuning.
- Bottom/window offset and other genuinely device-level concerns remain shared.
- Geometry, row visibility, number/developer visibility, and layout customization become profile- or layout-scoped.
- `modRowsVisible=false` remains compact Coding.
- Subtype IDs and eight component-family mappings remain independent and unchanged.

## Required scope

1. Introduce a versioned, idempotent preference migration.
2. Preserve rollback/unknown-data safety to the extent supported by the existing preference system.
3. Add truthful display terminology for Text and Coding.
4. Introduce stable internal layout IDs and compatibility aliases only after locating every persisted or referenced identifier.
5. Keep current bundled Coding composition working while names migrate.
6. Provide a settings-level profile selector or an internal selector seam if the actual Text layout is intentionally deferred to Stage 08. The application must never select a nonexistent/incomplete Text runtime by default.
7. Include profile identity and relevant scoped revisions in Compose/cache inputs.

## Naming constraint

Do not blindly rename `qwerty_wide` files or component IDs. First inventory usages and persistence. Use aliases/migration when an identifier is externally or persistently meaningful. User-facing language must become truthful even if a compatibility ID temporarily remains.

## Required tests

- Fresh install defaults.
- Upgrade from populated old preferences.
- Existing Coding settings preserved exactly after upgrade.
- Active subtype and component mappings preserved.
- Compact Coding preserved.
- Repeated migration is idempotent.
- Unknown/corrupt profile ID falls back safely.
- Process restart restores the selected available profile.

## Non-goals

- No permanent on-keyboard switching gesture.
- No per-app/per-field automatic profile selection.
- No new Text asset unless required as a nonselectable placeholder.
- No broad asset-directory reorganization.

## Commit boundary

Keep the persistence migration isolated from user-facing naming/selector wiring where practical.

Finish with the standard report-back, including old-to-new preference mappings.

