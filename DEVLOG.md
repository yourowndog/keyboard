### 2025-12-01
* **Task:** Overhauled Autocorrect with QWERTY adjacency and Backspace-Undo learning.
* **Files:** `ime/nlp/SymSpellManager.kt`, `ime/editor/EditorInstance.kt`, `ime/dictionary/UserDictionary.kt`, `ime/dictionary/DictionaryManager.kt`
* **Details:**
  - Implemented "Backspace Undo" learning: Reverting an autocorrect adds the pair to an `ignored_autocorrects` DB table.
  - Added `spatialCost` to `SymSpellManager`: Uses hardcoded QWERTY neighbor map to penalize far-key typos (2.0) vs near-key typos (0.5).
  - Boosted apostrophe variants (-20.0) to ensure contractions like "im" -> "I'm" always win.
  - `DictionaryManager` now exposes `learnUserIgnore` and `isUserIgnored`.