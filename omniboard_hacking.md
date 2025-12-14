# OmniBoard Mechanic's Bible (The "Sam" Customization Guide)

*Authored by Gemini (Codex), December 2025. Updated with live experience on the dev branch.*

This is the cheat sheet for FlorisBoard customization in this fork. Read before touching layouts or key logic.

---

## 1) Layout Pipeline & Merge (critical)
- Flow: `extension.json` (registry) → `LayoutManager.kt` (merges) → `TextKeyboard.kt`/`TextKeyboardLayout.kt` (render).
- Merge behavior (LayoutManager comment ~265–271):
  ```
  e e e e e e e e e e   (extension)
  c c c c c c c c c c   (main)
   c c c c c c c c c    (mod)
  m c c c c c c c c m
  m m m m m m m m m m
  ```
  Last main row merges with first mod row; placeholders (`code: 0`) in the first mod row preserve the last main row.
- To bypass merge: use a combined layout and set `modifier: null` + `arrangement` in `extension.json`.

Paths (dev):
- Layouts extension: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json` (v0.1.5)
- Localization presets: `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json` (v0.2.2)
- Default layouts: `.../layouts/characters/qwerty.json`, `.../charactersMod/qwerty_default.json`
- Wide layouts (added):
  - Alpha: `.../characters/qwerty_wide.json`
  - Combined: `.../characters/qwerty_wide_full.json` (all rows; intended to avoid merge)
  - Mod (unused by combined): `.../charactersMod/qwerty_wide_default.json`
  - Extension: `qwerty_wide` → `arrangement: org.florisboard.layouts:qwerty_wide_full`, `modifier: null`; also explicit entry `qwerty_wide_full`.
  - Presets: en-US/UK/CA/AU wide → `org.florisboard.layouts:qwerty_wide_full`; standard presets unchanged.
- Known issue: selecting wide still falls back on device (alpha missing, emoji key) → likely preset/loader/validation. Consider runtime toggle to force `qwerty_wide_full` per subtype if presets don’t stick.

## 2) Key Templates & Types (DO NOT freestyle)
- `"$": "auto_text_key"` → letters (auto-casing per locale/shift).
- `"$": "text_key"` → explicit symbols/punctuation.
- `"$": "navigation"` → arrows/Esc/Tab; do NOT set labels; icons/labels come from the template.
- Modifiers: use proper `KeyCode` and `type: "modifier"`; Enter: `type: "enter_editing"`.
- Selectors available (SerialName in `KeyData.kt`): `case_selector`, `shift_state_selector`, `variation_selector`, `layout_direction_selector`, `char_width_selector`, `kana_selector`.

## 3) Key sizing
- Code-side defaults: `TextKey.kt` (flayWidthFactor/flayShrink/flayGrow). Backspace/Shift get special shrink; some keys get width >1. Restore wider Backspace/Tab by adding them to these cases or set JSON `"units"` > 1.
- JSON width: `"units": <float>` in layout rows; default 1. Use this for per-key width overrides.

## 4) Row height (5-Row Hack Implemented)
- **Problem:** 5 rows is too tall.
- **Solution:** `FlorisImeSizing.kt` caps height at `4.5 * base` if rowCount is 5. `TextKeyboard.kt` distributes this unevenly: Top 3 rows get 100% height, Bottom 2 rows get 75% height. This keeps alpha keys big and mods compact.

## 5) Current behavior changes (dev)
- Ctrl: latches until next key; sends ctrl+char chords; tinted primary/variant like Enter. Files: `KeyboardManager.kt`, `FlorisImeThemeBaseStyle.kt`.
- Layout tweaks (default): Esc → bottom row before Ctrl; backspace → after P; system key → home row; arrows on mod row; Ctrl label/width bump. Files: `characters/qwerty.json`, `charactersMod/qwerty_default.json`.
- Wide layout assets present (see above), but selection currently falls back; needs investigation/toggle.

## 6) Clipboard
- Logic is complete; history is off by default. Enable “Use internal clipboard” + “Clipboard history” in settings to behave like Gboard. Defaults: `useInternalClipboard=false`, `historyEnabled=false`. Files: `ClipboardManager.kt`, prefs in `AppPrefs.kt`.

## 7) Troubleshooting “blackout” (only mod row / missing rows)
- Main layout missing/not packaged (file not under assets).
- Preset/layoutMap still points to default.
- Merge replaced last main row (first mod row not placeholder).
- Validation failure: bad SerialName/type/unknown template.
- Extension version not bumped/cache not cleared.

## 8) How to switch layouts (safe)
- Use subtype presets (localization extension). Wide presets exist (en-US/UK/CA/AU) pointing to `qwerty_wide_full`; standard presets unchanged. If presets don’t apply, add a runtime toggle to force `qwerty_wide_full` per subtype.

## 9) Do not do these (common mistakes)
- Do not hardcode navigation labels; use `"$": "navigation"` with proper codes.
- Do not hand-roll letter keys; use `auto_text_key`.
- Do not omit placeholders when merging main+mod; you’ll lose the last main row.
- Do not leave layout files outside `app/src/main/assets/...`; they won’t be packaged.

## 10) Creating Custom Action Keys (The "Toggle" Hack)
To create a key that runs custom logic (like toggling a setting):
1.  **Define Code:** Add constant in `KeyCode.kt` (e.g., `TOGGLE_NUMBER_ROW = -305`).
2.  **Register Data:** Add to `TextKeyData.kt` (`InternalKeys` list + static val).
3.  **Map Icon:** Update `ComputingEvaluator.kt` -> `computeImageVector`.
4.  **Implement Logic:** Update `KeyboardManager.kt` -> `onInputKeyUp`.
    ```kotlin
    KeyCode.TOGGLE_NUMBER_ROW -> scope.launch { prefs.keyboard.numberRow.let { it.set(!it.get()) } }
    ```
5.  **Place Key:** Use `"$": "text_key", "code": -305` in layout JSON.

## 11) The Symbol Layout Overlay (Hint System)
- FlorisBoard generates key hints (small numbers/symbols) by overlaying the **Symbol Layout** on top of the **Main Layout**.
- **Alignment is Key:** If Main is 5 rows and Symbol is 4 rows, the overlay aligns to the *bottom*, leaving the top rows blank (no hints).
- **The Fix:** Create a matching 5-row Symbol Layout (`western_wide.json`) and register it in `extension.json` -> `layouts.symbols`. Point the subtype preset to use it.
- This forces a 1:1 overlay (Row 0->0, Row 1->1), ensuring hints appear correctly on all rows.

## 12) Swipe Layout & Smartbar (Proven Configuration)
- **Wide Swipe Layout:** 5 rows. Backspace on Z-row (width 1.25f to match Shift).
- **Mod Row:** `[TAB] [LEFT] [DOWN] [RIGHT] ...`. Use `navigation` template for arrows (-21, -24, -22).
- **Smartbar Icons:** `ComputingEvaluator.computeImageVector` maps codes to icons. If a code (like ESC -15) is missing there, Smartbar renders nothing (or text if label exists).
- **Proven Smartbar:** ESC as first item (added `Icons.Default.Close` mapping). Removed Redo/Undo/Arrows from defaults to save space.