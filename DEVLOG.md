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
