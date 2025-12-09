### 2025-12-01
* **Task:** Overhauled Autocorrect architecture (Adjacency, Learning, Revert) & UI Polish.
* **Files:** `ime/nlp/SymSpellManager.kt`, `ime/editor/EditorInstance.kt`, `ime/editor/AbstractEditorInstance.kt`, `ime/dictionary/UserDictionary.kt`, `ime/dictionary/DictionaryManager.kt`, `ime/smartbar/CandidatesRow.kt`
* **Details:**
  - **Spatial Cost:** Implemented QWERTY adjacency map; penalizes far typos (2.0) vs near typos (0.5) vs length mismatch (1.0).
  - **Undo/Learn Loop:** `EditorInstance` captures undo state; Backspace Revert restores "word + space"; Revert adds pair to `ignored_autocorrects` DB.
  - **State Persistence:** `AbstractEditorInstance` prevents undo-state clearing on non-correcting space commits.
  - **Ranking Logic:** Exact matches (dist 0) get -100.0 bonus; Apostrophe shortcuts get -20.0/-10.0 bonus; Removed aggressive bigram multiplier.
  - **Shortcuts:** `CONTRACTION_SHORTCUTS` (including "wint"->"won't") are prioritized at the top of suggestions.
  - **UI:** Removed pipe separator; Used `Arrangement.SpaceEvenly` for centered suggestions; Added automatic space after committed suggestions.
  - **Status:** Stable. Infrastructure for frequency/probability learning (count column) is in place for future tuning.

### 2025-12-01
* **Task:** Reposition esc/backspace and swap ctrl/shift.
* **Files:** `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty.json`, `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_default.json`
* **Notes:** Esc removed from top-left, moved to bottom row before ctrl (with label); backspace moved to top row after P; system key lives in home row after L (removed from bottom row); arrows on mod row (navigation codes) with up arrow before Enter; top mod row trimmed to shift + placeholder; bottom row placeholder removed to free space. – Codex

### 2025-12-01
* **Task:** Make ctrl use primary color in base theme (normal/pressed/focus).
* **Files:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisImeThemeBaseStyle.kt`
* **Notes:** Ctrl matches Enter’s primary when idle, primary-variant when pressed, and primary when latched (FOCUS), so custom themes inheriting base vars pick up the tint. – Codex

### 2025-12-01
* **Task:** Add alt “QWERTY Wide” layout (safe alongside default).
* **Files:** `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty_wide.json`, `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_wide_default.json`, `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json`
* **Notes:** New layout mirrors requested rows (alpha-only rows; Shift row: “Shift , Space . ↑ Del”; bottom row: “Ctrl Esc Mod ← ↓ → Enter”). Added as a new entry (`qwerty_wide`) so existing default stays untouched and selectable. – Codex

### 2025-12-01
* **Task:** Add subtype presets for quick QWERTY Wide selection.
* **Files:** `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json`
* **Notes:** Added wide variants for en-US/UK/CA/AU and bumped version so preset dialog shows both standard and wide; default presets remain unchanged. – Codex

### 2025-12-01
* **Task:** Bump layouts extension version for wide layout discovery.
* **Files:** `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json`
* **Notes:** Version 0.1.3 to force reload of bundled layouts so `qwerty_wide` + modifier are discoverable. – Codex

### 2025-12-01
* **Task:** Fix wide layout assets/merge.
* **Files:** `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty_wide.json`, `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_wide_default.json`, `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json`
* **Notes:** Moved alpha layout into repo path; added placeholder-only mod row so third alpha row renders; removed duplicate mod entry and bumped extension to 0.1.3. – Codex

### 2025-12-03
* **Task:** Fixed 5-row wide layout using "Split Strategy" (Main + Mod with placeholder).
* **Files:** `qwerty_wide.json`, `qwerty_wide_mod.json`, `layouts/extension.json`, `localization/extension.json`
* **Notes:** Split layout into main (4 rows) and mod (2 rows). Row 1 of mod is a placeholder to inject the shift/space row. Row 2 is the custom ctrl/arrow row. Updated extension registry to link them properly. – Gemini

### 2025-12-03
* **Task:** Refined QWERTY Wide key mappings and labels.
* **Files:** `qwerty_wide.json`, `qwerty_wide_mod.json`
* **Notes:** Filled Row 3 gaps with Tab, /, and @. Added Up arrow to Row 4. Fixed Row 5: Esc label (⎋), added Nuclear/Mod key (-202), and fixed Enter label (↵). – Gemini

### 2025-12-03
* **Task:** Reshuffled Wide Layout for better ergonomics.
* **Files:** `qwerty_wide.json`, `qwerty_wide_mod.json`
* **Notes:**
    *   **Row 3:** Shift | z x c v b n m | Del (Backpsace).
    *   **Row 4:** Tab | , | Space | . | Up | Enter.
    *   **Row 5:** Ctrl | Esc | Mod | Left | Down | Right. – Gemini
### 2025-12-08
* **Task:** Integrated Gemma 2B (Q4_K_M) via local llama.cpp server with Vulkan acceleration.
* **Files:** `GemmaClient.kt`, `SuggestionEngine.kt`, `KeyboardManager.kt`, `AndroidManifest.xml`
