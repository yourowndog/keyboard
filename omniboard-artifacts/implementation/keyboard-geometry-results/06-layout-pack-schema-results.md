# Stage 06 Results — Layout-Pack Schema and Restoration

Implementation commit `704609fd5` on branch `wt/t_ffd724a5`, based on accepted Stage 05 commit `cf2b25c96`.

## Outcome

Layout packs now have a strict, versioned schema that carries stable pack and row identities,
semantic row roles, ordered keys and explicit spacers, structural width units, enablement and
visibility conditions, and validated optional structural policy fields. Current-schema packs reject
unknown behavior-bearing fields and unsupported newer versions rather than silently truncating them.

Schema version 1 packs take a deterministic compatibility path. Historical row IDs are resolved only
through an explicit mapping, ambiguous IDs are classified and diagnosed, inert legacy
`showIfSetting` text remains inert so migration does not hide a previously visible row, and legacy
rows whose declared width disagrees with their key widths are normalized to the rendered element
total without moving keys. The fixed legacy user file is preserved byte-for-byte while an idempotent
current-schema copy is written under `layouts/user/packs/`.

The selected layout-pack ID is persisted independently from subtype and keyboard profile. Startup now
restores that selection instead of constructing the old empty sentinel. Missing, corrupt, invalid,
unsupported-newer, and deleted selections safely fall back to normal bundled composition; a valid
selection restores its semantic rows and geometry inputs.

## Main decisions

- Schema version 2 is the only written format. An absent or explicit version 1 selects the frozen
  compatibility decoder; versions newer than 2 are refused before deserialization.
- Semantic roles are serialized using the same stable vocabulary used by normalized row IDs.
  `PLACEHOLDER` is runtime-only and cannot be claimed by a pack.
- Unknown fields are errors in schema version 2. Ignoring them could turn a conditional row into an
  unconditional row or discard future structural policy.
- Keys and spacers are distinct element kinds. An unresolved current-schema key is invalid instead of
  becoming an invisible hole.
- Row policy overrides are serialized and validated but remain inert until Stage 07. Stage 06 does
  not introduce a second geometry authority.
- A saved pack is staged, decoded back, and compared before activation. Replacements retain the prior
  valid file as `<packId>.json.bak`.
- Legacy `showIfSetting` is dropped with a diagnostic even when its text resembles a current
  condition, because schema version 1 never evaluated it. Applying it during migration would change
  existing behavior.
- The shipped legacy bottom letter row declares 12 units while its elements total 13. Migration
  records 13 units and preserves every authored key width, matching what version 1 rendered.

## Sample pack snippets

Schema version 1 input:

```json
{
  "id": "legacy_pack",
  "label": "Legacy Pack",
  "units": 12,
  "rows": [{
    "id": "row_space",
    "units": 12,
    "showIfSetting": "mod_rows_visible",
    "keys": [
      { "label": "123", "code": "MODE_SYMBOLS", "units": 2, "style": "aux" },
      { "label": "Space", "code": "KEYCODE_SPACE", "units": 9, "style": "space" },
      { "label": "Enter", "code": "KEYCODE_ENTER", "units": 1, "style": "special_right" }
    ]
  }]
}
```

Schema version 2 output:

```json
{
  "schemaVersion": 2,
  "id": "legacy_pack",
  "label": "Legacy Pack",
  "units": 12.0,
  "rows": [{
    "id": "row_space",
    "role": "primary_action",
    "units": 12.0,
    "enabled": true,
    "visibleWhen": "always",
    "keys": [
      { "kind": "key", "code": "MODE_SYMBOLS", "label": "123", "widthUnits": 2.0, "style": "aux" },
      { "kind": "key", "code": "KEYCODE_SPACE", "label": "Space", "widthUnits": 9.0, "style": "space" },
      { "kind": "key", "code": "KEYCODE_ENTER", "label": "Enter", "widthUnits": 1.0, "style": "special_right" }
    ]
  }]
}
```

The compatibility outcome also records that `row_space` used `COMPATIBILITY_MAPPING` and that the
legacy visibility field was dropped to preserve always-visible behavior.

## Persistence and restoration

Current packs are stored as:

```text
files/layouts/user/packs/<packId>.json
files/layouts/user/packs/<packId>.json.bak
```

The historical file remains at:

```text
files/layouts/user/lcars_hacker_en_us.user.json
```

It is read for one-time compatibility import and is never rewritten or deleted. Repeated import sees
the current pack already present and performs no write.

`keyboard__selected_layout_pack_id` stores only the selected pack ID. Profile remains in
`keyboard__active_profile_id`, and subtype remains in its existing store. A fresh-process test
reconstructs the profile ID and selected pack ID independently, restores the pack from disk, and
asserts identical row IDs, semantic roles, spacer/unit geometry inputs, and profile resolution.

## Tests

`./gradlew --no-daemon :app:testDebugUnitTest --tests 'dev.patrickgold.florisboard.ime.keyboard.pack.*'`

- 67 Stage 06 tests, 0 failures, 0 errors.
- Covers old-pack decode; new-pack round trip; deterministic and repeated migration; disabled rows;
  spacers; units; visibility conditions; duplicate IDs; invalid roles/units; unknown fields;
  unsupported versions; missing/corrupt/invalid/deleted selection fallback; legacy-file preservation;
  backups; and restart restoration of profile, selected pack, semantic rows, and geometry inputs.

`./gradlew --no-daemon :app:testDebugUnitTest`

- 339 tests in 35 classes, 0 failures, 0 errors, 0 skipped.

`./gradlew --no-daemon :app:assembleDebug`

- BUILD SUCCESSFUL.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (72,670,654 bytes).
- Existing Gradle, SDK XML, CMake deprecation, Room/KSP, and deprecated-toast warnings remain; no new
  build failure was introduced.

## Files and ownership

- `ime/keyboard/pack/` owns schema, codec, compatibility, validation, composition, key resolution,
  and plain-file persistence.
- `LayoutPackRepository` is the Android adapter for app-private files, bundled assets, and document
  URIs.
- `KeyboardManager` owns selected-pack restoration, activation, deselection, and cache invalidation.
- `LayoutManager` consumes resolved pack rows and feeds the existing semantic-row and geometry path.
- `LayoutBuilderScreen` reads, validates, imports, exports, and applies current-schema documents. This
  is wiring for the existing screen, not a visual redesign.

## Not done

- No visual Layout Builder redesign, arbitrary-coordinate format, broad import/export ecosystem,
  bundled-asset cleanup, or theme change.
- No row-policy override is applied to geometry yet; that remains Stage 07.
- No device installation or live IME restart test was performed. The restart contract is covered by
  plain JVM persistence/composition tests; Android lifecycle and user-visible fallback messaging
  remain device-validation work.
- Import/export/apply failures still have TODOs for richer user-facing feedback in the existing
  Layout Builder screen.
