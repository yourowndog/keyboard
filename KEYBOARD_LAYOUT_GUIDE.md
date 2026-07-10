# Keyboard Layout Guide

This is the short map for editing the on-screen text keyboard. Treat this file as the starting point before changing layout JSON, key sizing, or popup hints.

## Current Pipeline

1. A subtype preset chooses layout component names.
   - File: `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json`
   - Example: the wide English presets choose `characters: org.florisboard.layouts:qwerty_wide` and `symbols: org.florisboard.layouts:western_wide`.

2. The layouts extension registers those component names.
   - File: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json`
   - Each component has an `id`, optional `modifier`, and optional `arrangementFile`.
   - If `arrangementFile` is omitted, `LayoutArrangementComponent.arrangementFile()` resolves it as:

```text
layouts/<layout type id>/<component id>.json
```

3. `LayoutManager` loads and merges the JSON.
   - File: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt`
   - Main entry: `computeKeyboardAsync()`
   - Merge entry: `mergeLayouts()`
   - JSON files deserialize to `LayoutArrangement`, which is `List<List<AbstractKeyData>>`.

4. `TextKey` computes live key data.
   - File: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKey.kt`
   - `TextKey.compute()` applies selectors, locale/shift behavior, popup data, icon/label state, and hardcoded width factors for special keys.

5. `TextKeyboard` computes geometry.
   - File: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboard.kt`
   - `TextKeyboard.layout()` turns rows into touch bounds and visible bounds.
   - Alpha rows use `alphaKeyWidthFactor`; non-space modifier rows use `modKeyWidthFactor`; space rows are intentionally immune to the mod-width slider.

6. `TextKeyboardLayout` renders and handles touch.
   - File: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`
   - This composable observes spacing/height/key customization prefs, calls `keyboard.layout(...)`, applies per-key customizations, then renders `TextKeyButton`.

## Main JSON Families

Layout JSON lives under:

```text
app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/
```

Important folders:

- `characters/`: main letter layouts, such as `qwerty.json` and `qwerty_wide.json`.
- `charactersMod/`: modifier rows for character layouts, such as `qwerty_default.json` and `qwerty_wide_mod.json`.
- `symbols/`: symbol layouts used directly and also as character-key hint sources.
- `symbolsMod/`: modifier rows for symbol layouts.
- `numericRow/`: optional rows inserted above the main layout when number row or developer row prefs are enabled.
- `numeric/`, `numericAdvanced/`, `phone/`, `phone2/`: non-character modes.

## Live QWERTY Files

Standard English:

- Main: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty.json`
- Modifier: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_default.json`
- Registry entry: `qwerty` in `org.florisboard.layouts/extension.json`
- Presets: standard `en-US`, `en-UK`, `en-CA`, `en-AU` entries in `org.florisboard.localization/extension.json`

Wide English:

- Main: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty_wide.json`
- Modifier: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_wide_mod.json`
- Registry entry: `qwerty_wide` with `modifier: org.florisboard.layouts:qwerty_wide_mod`
- Presets: wide English entries use `popupMapping: org.florisboard.localization:en_wide`

Note: older notes mention `qwerty_wide_full.json`. That file is not present in the current assets and is not the live wide path.

## Merge Rules

`LayoutManager.mergeLayouts()` is the source of truth.

- Extension rows, such as numeric or developer rows, are inserted first.
- Main layout rows are added next.
- If the main layout has a modifier:
  - All main rows except the last are copied directly.
  - The last main row is merged with the first modifier row.
  - A modifier key with `code: 0` is a placeholder. It expands to the full last main row at that position.
  - Modifier rows after row 0 are appended as extra non-alpha rows.
- If the user hides mod rows, `LayoutManager` keeps only extra modifier rows containing `SPACE` and skips the others.
- `bottomModRowCount` is passed into `TextKeyboard` and affects row height distribution.

Practical warning: if modifier row 0 does not contain a placeholder, the last main row can be replaced instead of preserved. For classic QWERTY this is why `charactersMod/qwerty_default.json` begins with shift plus `code: 0`.

## Key JSON Shapes

Most keys are serialized `AbstractKeyData`.

Common forms:

```json
{ "$": "auto_text_key", "code": 113, "label": "q" }
```

`auto_text_key` changes with shift and locale. Use it for letters.

```json
{ "$": "text_key", "code": 32, "label": "space" }
```

`text_key` is fixed. Use it for literals and internal key codes.

```json
{ "$": "variation_selector", "default": { "code": 44, "label": "," }, "email": { "code": 64, "label": "@" } }
```

Selectors compute different key data based on state. See `KeyData.kt` for available selectors.

```json
{ "code": 0, "type": "placeholder" }
```

In modifier row 0 only, this splices in the final main alpha row during merge.

## Width And Height Controls

There are two layers:

- JSON can set `units` only when using layout packs through the layout builder path.
- Current bundled layout JSON width mostly comes from code, not per-key JSON.

For bundled layouts, `TextKey.compute()` sets `flayWidthFactor` from the computed key code. Examples:

- Space: `5.00f`
- Enter and Tab: `1.50f`
- Escape, Ctrl, number-row toggle: `1.25f`
- Arrow/navigation cluster: narrower hardcoded factors
- Default: `1.00f`

`TextKeyboard.layout()` then combines those factors with user prefs:

- Alpha rows are measured against the widest alpha row.
- Alpha keys use `alphaKeyWidthFactor`.
- Modifier-only rows use `modKeyWidthFactor`.
- Rows containing space use alpha sizing so the spacebar does not collapse when modifier rows are toggled or resized.

Row height is also code-driven:

- `TextKeyboard.layout()` uses `bottomRowHeightFactor` and `alphaRowHeightFactor` for layouts with extra rows.
- `TextKeyboardLayout.kt` applies extra mod-row gaps after base geometry.

## Hints And Popups

There are three popup/hint sources:

- Inline key `popup` fields inside layout JSON.
- Popup mapping files under `org.florisboard.localization/popupMappings/`.
- Symbol-layout hints copied by `LayoutManager.addRowHints()`.

For character mode:

- The symbols keyboard is computed separately.
- Number hints can come from symbols row 0 when hinted number row is enabled.
- Symbol hints are bottom-aligned by row offset.
- `addRowHints()` skips non-alpha keys, so modifier rows should not inherit symbol hints.

## When JSON Is Not Enough

Edit code instead of JSON for these:

- A key exists but has the wrong physical width: `TextKey.compute()`.
- Row or spacing math is wrong: `TextKeyboard.layout()` or the gap block in `TextKeyboardLayout.kt`.
- A key code needs a rendered icon: `ComputingEvaluator.computeImageVector`.
- A key code needs behavior: `KeyboardManager.onInputKeyUp()` or nearby input-dispatch handling.
- A layout does not load or merges incorrectly: `LayoutManager.mergeLayouts()` and the relevant `extension.json` entry.
- A subtype selects the wrong layout: `org.florisboard.localization/extension.json`.

## Suggested Debug Order

1. Confirm the active subtype preset in `org.florisboard.localization/extension.json`.
2. Confirm the registry entry and modifier in `org.florisboard.layouts/extension.json`.
3. Open the main and modifier JSON files resolved from that registry entry.
4. Check row count and placeholder placement before changing individual keys.
5. If geometry still looks wrong, inspect `TextKey.compute()` width factors and `TextKeyboard.layout()`.
6. If hints or long-press options look wrong, inspect symbol layout alignment and popup mappings.
