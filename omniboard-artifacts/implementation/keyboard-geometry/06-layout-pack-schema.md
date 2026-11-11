# Stage 06 Prompt — Layout-Pack Schema and Restoration

You are implementing Stage 06 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents. The common semantic/geometry model is authoritative; layout packs must now join that contract.

## Objective

Create a versioned layout-pack schema that preserves semantic rows and responsive structural inputs, safely decodes old packs, and restores the selected pack/profile across process restart.

## Required serialized capabilities

- schema version;
- stable pack/layout ID and label;
- stable row ID;
- semantic row role;
- ordered keys and explicit spacers;
- structural width units;
- enabled/visibility conditions;
- validated optional policy/override fields;
- explicit handling of unknown semantic fields.

Do not treat historical row IDs such as `row_letters_*` or `row_space` as authoritative semantics without an explicit compatibility mapping.

## Required scope

1. Add a versioned decoder/encoder and validation model.
2. Decode existing packs through a deterministic compatibility path.
3. Do not default every old pack key to alpha or invent two bottom modifier rows.
4. Validate:
   - unique stable IDs;
   - allowed roles;
   - finite positive units;
   - satisfiable row constraints;
   - key/spacer structure;
   - supported visibility conditions;
   - unknown behavior-bearing fields.
5. Round-trip new packs without semantic loss.
6. Persist selected pack/layout identity separately from subtype and profile.
7. Replace the empty `loadInitialLayout()` sentinel behavior with safe startup restoration.
8. Provide safe fallback for missing, corrupt, unsupported-newer, or deleted selected packs.
9. Preserve the existing fixed user file through a migration or compatibility import; do not overwrite it destructively.

## Migration policy

- Back up or preserve the original old pack until the new pack is successfully written and validated.
- Migration is idempotent.
- Unsupported-newer schemas fail visibly and safely rather than being silently truncated by unknown-key ignoring.
- Ambiguous old row semantics produce a named compatibility classification and diagnostic.

## Required tests

- Old pack decode.
- New pack round trip.
- Old-to-new migration and repeated migration.
- Disabled rows, spacers, units, and visibility conditions.
- Duplicate IDs and invalid roles/units.
- Unknown semantic fields and unsupported schema version.
- Missing/corrupt selected pack fallback.
- Process restart restores profile, selected pack, semantic rows, and geometry.

## Non-goals

- No full visual Layout Builder redesign.
- No arbitrary-coordinate format.
- No broad import/export ecosystem.
- No dead bundled-asset cleanup.

## Commit boundary

Separate schema/domain changes, persistence migration, and startup restoration where practical.

Finish with the standard report-back and sample old/new pack snippets.

