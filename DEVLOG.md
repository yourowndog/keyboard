### 2025-12-01
* **Task:** Overhauled Autocorrect architecture (Adjacency, Learning, Revert).
* **Files:** `ime/nlp/SymSpellManager.kt`, `ime/editor/EditorInstance.kt`, `ime/editor/AbstractEditorInstance.kt`, `ime/dictionary/UserDictionary.kt`, `ime/dictionary/DictionaryManager.kt`
* **Details:**
  - **Spatial Cost:** Implemented QWERTY adjacency map; penalizes far typos (2.0) vs near typos (0.5) vs length mismatch (1.0).
  - **Undo/Learn Loop:** `EditorInstance` captures undo state; Backspace Revert restores "word + space"; Revert adds pair to `ignored_autocorrects` DB.
  - **State Persistence:** `AbstractEditorInstance` prevents undo-state clearing on non-correcting space commits.
  - **Ranking Logic:** Exact matches (dist 0) get -100.0 bonus; Apostrophe shortcuts get -20.0/-10.0 bonus; Removed aggressive bigram multiplier.
  - **Shortcuts:** `CONTRACTION_SHORTCUTS` (including "wint"->"won't") are prioritized at the top of suggestions.
  - **Status:** Stable. Infrastructure for frequency/probability learning (count column) is in place for future tuning.