# Layout Pipeline

> Status: Canonical  
> Last verified: 2026-07-12
> Verified against: `LayoutManager.kt`, `LayoutArrangement.kt`, `KeyData.kt`,
> layout extension assets, subtype assets, and `app/layoutbuilder`

## Bundled extension layouts

1. The active subtype supplies component names for characters, symbols,
   numeric rows, popup mappings, and related layout families.
2. `org.florisboard.layouts/extension.json` registers each layout component.
3. A component without an explicit arrangement file resolves conventionally to
   `layouts/<layout-type>/<component-id>.json`.
4. `LayoutManager` loads and caches the registered layout and popup data.
5. `mergeLayouts()` creates the final rows for the current keyboard mode.

The active subtype registry is:

```text
app/src/main/assets/ime/keyboard/
  org.florisboard.localization/extension.json
```

Bundled layout components and files are under:

```text
app/src/main/assets/ime/keyboard/
  org.florisboard.layouts/
    extension.json
    layouts/
```

## Layout selection settings

> Last verified: 2026-08-05 against `LocalizationScreen.kt`,
> `SubtypeEditorScreen.kt`, `SubtypeManager.kt`, `Routes.kt`, and
> `DevtoolsScreen.kt`.

Settings opens **Layouts** directly onto the subtype list. A subtype binds its
language to the character, symbol, numeric, phone, popup, currency, composer,
punctuation, and NLP components used by the keyboard.

When no subtype is persisted, the runtime uses `Subtype.DEFAULT`. The list
shows that effective subtype as **Default** instead of reporting an empty
configuration. It is a presentation entry only: it is not inserted into
`subtypesFlow` or serialized until the user edits and saves it. Saving follows
the normal add path, assigns a real subtype id, and preserves the rest of the
configured list. The implicit entry cannot be deleted.

Every subtype editor also exposes a permanent **Factory default** recovery
card. Loading it fills the editor with the exact `Subtype.DEFAULT` bindings but
does not save immediately. When editing a configured subtype, the recovery
preserves that subtype's id, so the normal Save action repairs the existing
entry in place instead of appending another subtype.

The persisted `qwerty_wide_full` compatibility id resolves explicitly to the
shipped `qwerty_wide.json` arrangement and `qwerty_wide_mod` modifier. The
explicit arrangement path prevents that selectable compatibility entry from
falling through to a nonexistent `characters/qwerty_wide_full.json` file.

Configured subtypes remain in persisted order. Each row shows the character,
primary-symbol, secondary-symbol, and currency-set binding. Numeric and phone
families remain in the editor because adding all eight slots would turn the
compact list summary into another crowded editor.

The display-language selector, subtype-language key-label switch, and language
pack manager are still backed by their original preferences and route, but are
available from Devtools rather than the primary Layouts screen. Layout Builder
is likewise routed only from Devtools. Its `LayoutPack`, repository, validation,
and application/runtime constructor wiring remain intact.

## Row families

- `characters/`: main character rows.
- `charactersMod/`: character modifier and utility rows.
- `symbols/` and `symbols2/`: symbol modes and character hint sources.
- `symbolsMod/` and `symbols2Mod/`: symbol-mode modifier rows.
- `numericRow/`: optional number or developer rows inserted at the top.
- `numeric/`, `numericAdvanced/`, `phone/`, `phone2/`: specialized modes.

Directory names retain upstream mixed casing. Do not create a second spelling
without checking how `LayoutType` resolves paths.

## Merge behavior

Optional extension rows are added first. Main rows are then merged with the
modifier layout:

- Every main row except the last is copied as an alpha row.
- Modifier row zero is walked key by key.
- A `TextKeyData` entry with code `0` splices in the last main alpha row.
- Other keys in modifier row zero remain non-alpha keys.
- Modifier rows after row zero are appended as extra non-alpha rows.

This placeholder behavior is structural. Removing the placeholder can replace
the last alpha row rather than decorating it.

When modifier rows are hidden, extra modifier rows are skipped unless the row
contains `SPACE` or `CJK_SPACE`. The merge result records the count of modifier
rows that remain visible because geometry uses that count.

## Number and developer rows

For character mode, enabled number and developer rows are loaded as extension
layouts before the main layout. They are not part of the character JSON itself.

## Hints

After row assembly, the symbols keyboard is computed and aligned with character
rows:

- Numeric hints may come from the first symbol row.
- Symbol hints are aligned from the bottom using the difference in row counts.
- `addRowHints()` only assigns hints to keys marked `isAlpha`.
- Popup mappings and inline popup declarations are merged later when each
  `TextKey` is evaluated.

The active QWERTY Wide modifier layout declares the period key's inline popup
set as single quote, double quote, exclamation mark, and question mark. Its
modifier row replaces the last row of the main QWERTY Wide layout, so editing
the period popup in the main character file does not change this active key.

## Layout Builder packs

Layout Builder packs use `LayoutPack`, `LayoutRow`, and `LayoutKey`, then enter
`LayoutManager.computeKeyboardFromLayoutPack()`.

Important differences from bundled JSON:

- Currently supported only for character mode.
- Rows marked `enabled = false` are skipped.
- Each key has integer `units`; those units become the key's runtime width
  factor after evaluation.
- A spacer occupies units but is disabled and not rendered.
- Internal key labels, aliases, single Unicode code points, and numeric key
  codes can be resolved.
- `showIfSetting` exists in the serialized model but is not consumed by the
  current runtime computation path.
- Pack and row unit totals are checked by `LayoutValidation`, but row-level
  conditional behavior should not be assumed merely because it is representable
  in the data model.

## Debug order

1. Confirm the active subtype.
2. Confirm the registered component and modifier.
3. Resolve the exact main, modifier, and extension files.
4. Inspect placeholder placement and row classification.
5. Determine whether a Layout Builder pack overrides the bundled character
   layout.
6. Check evaluated key data and visibility.
7. Only then debug geometry, rendering, or touch dispatch.
