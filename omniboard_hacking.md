# OmniBoard Mechanic's Bible (The "Sam" Customization Guide)

*Authored by Gemini (Codex), December 2025. Updated with live experience on the dev branch.*

This is the cheat sheet for FlorisBoard customization in this fork. Read before touching layouts or key logic.

Start with `KEYBOARD_LAYOUT_GUIDE.md` for the current source-of-truth map of layout JSON, registry files, merge behavior, rendering, hints, and width/height code paths. This file is a running field log and may contain older notes.

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

## 13) Ergonomic Width Sliders (Alpha vs Mod Key Width) — 2026-03-10

### The Slider Math Trap (DO NOT REPEAT)
The layout function receives `alphaKeyWidthFactor` and `modKeyWidthFactor` (floats, 0.8–1.4).
If you compute `unitWidth = screenWidth / sum(key.flayWidthFactor * widthFactor)` and then
compute `keyWidth = key.flayWidthFactor * widthFactor * unitWidth`, **the factor cancels out
completely** — it appears in both numerator and denominator. The sliders will do nothing.

**Correct approach** (`TextKeyboard.kt`):
1. Compute `baseAlphaUnitWidth` from the widest alpha row using **base `flayWidthFactor` only** (no slider multiplier).
2. For each mod-only row, compute `baseModRowUnitWidth = screenWidth / sum(key.flayWidthFactor)` — again, no slider.
3. Then apply the slider ONLY when computing actual key pixel width: `key.flayWidthFactor * widthFactor * baseUnitWidth`.

This way `alphaKeyWidthFactor=0.8` makes alpha keys 80% of their reference size (centered row),
and `modKeyWidthFactor=0.8` uniformly shrinks mod rows — fully independent.

### Mod-Row Symbol Hints Pollution
The hint system (`LayoutManager.addRowHints`) bottom-aligns the symbols keyboard rows onto the
characters keyboard rows **by position index**. With qwerty_wide + mod (6 char rows) and
western_wide symbols (5 rows), `rOffset=1`, so the 3-key comma/space/period mod row aligns with
`western_wide` symbols row 2: `[\ | _ = [ ] ...]`. Period is position 2 → gets `_` as its
`computedSymbolHint`, which then appears in the long-press popup.

**Fix:** In `addRowHints`, skip keys where `!key.isAlpha`. Mod-row keys are already
explicit symbols — positional hint alignment doesn't make semantic sense for them.

### The `placeholder` Key (code 0) in Mod JSON Files
The mod layout's **first row** is merged with the main layout's **last row** in LayoutManager.
The placeholder key (`{ "code": 0, "type": "placeholder" }`) is the signal: when encountered,
it splices in ALL alpha keys from the last main row at that position. Keys before/after the
placeholder in the mod row appear as mod keys flanking the alpha block.

**NEVER** put non-placeholder keys in mod row 0 without a placeholder — you'll silently DROP
the entire ZXCVBNM alpha row. All subsequent rows (row 1, 2, ...) of the mod file are appended
as pure mod rows with `isAlpha=false`.

### Spacebar `flayWidthFactor` in Mod Rows
`TextKey.kt` originally assigned `flayWidthFactor = 5.0f` only when `hasSlimSpaceRow = false` (when `bottomModRowCount < 3`). When hiding mod rows via toggle (which reduces `bottomModRowCount` to 1), the spacebar was arbitrarily forced down to `1.0f` width and `0.0f` grow, causing it to shrink to a "postage stamp" shape.
**The Fix:** We stripped the `hasSlimSpaceRow` override. The spacebar must ALWAYS have a baseline width factor of `5.0f` and a `flayGrow` of `1.0f`. This ensures it aggressively consumes available horizontal space, expanding gracefully across any row regardless of how many modifier rows are toggled.

### Dynamic Mod-Row Hiding (The "Sigma" Toggle)
When implementing a toggle key to show/hide extra modifier rows (like the number or navigation rows), **do not hardcode row numbers** to skip.
Wide layouts (like `qwerty_wide_mod`) place their keys in different rows than standard layouts (`qwerty_default`). Hardcoding "always keep Row 1" will explicitly preserve the wrong keys (like arrows/tab) on wide layouts.
**The Fix:** To hide mod rows programmatically, `LayoutManager.kt` must scan the keys dynamically:
```kotlin
val hasSpace = modRow.any { it.compute(DefaultComputingEvaluator)?.code == KeyCode.SPACE }
if (!hasSpace) continue // Hide this row
```
Also, remember to dynamically update the `bottomModRows` count based on how many extra mod rows are actually retained, so proportional layout scaling executes correctly.
