### 2025-12-01
* **Task:** Overhauled Autocorrect with QWERTY adjacency and Backspace-Undo learning.
* **Files:** `ime/nlp/SymSpellManager.kt`, `ime/editor/EditorInstance.kt`, `ime/dictionary/UserDictionary.kt`, `ime/dictionary/DictionaryManager.kt`
* **Details:**
  - Implemented "Backspace Undo" learning: Reverting an autocorrect adds the pair to an `ignored_autocorrects` DB table.
  - Added `spatialCost` to `SymSpellManager`: Uses hardcoded QWERTY neighbor map to penalize far-key typos (2.0) vs near-key typos (0.5).
  - Boosted apostrophe variants (-20.0) to ensure contractions like "im" -> "I'm" always win.
  - `DictionaryManager` now exposes `learnUserIgnore` and `isUserIgnored`.

### 2025-12-01
* **Task:** Latched ctrl softkey until next key press and dispatch ctrl+key chords for character keys.
* **Files:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
* **Notes:** Ctrl down keeps state; next key sends ctrl chord (letters/digits/space/enter) and then clears. Ctrl also applies to arrows and clears after use; ctrl-up no longer cancels. – Codex

### 2025-12-01
* **Task:** Make ctrl key readable and give it extra width.
* **Files:** `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_default.json`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKey.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/ComputingEvaluator.kt`
* **Notes:** Label set to `CTRL`; width factor bumped (1.30) so the modifier is easier to hit; computeLabel now returns the layout label so it renders text. – Codex

### 2025-12-01
* **Task:** Tint ctrl when latched using enter’s primary color.
* **Files:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisImeThemeBaseStyle.kt`, `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/floris_day*.json`, `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/floris_night*.json`, `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/floris_pure_night*.json`
* **Notes:** ctrl uses selector FOCUS while latched; all bundled themes map that to the enter primary background. – Codex
