### 2026-02-18
* **Task:** Fixed looping/gibberish phrase predictions
* **Files:** `BigramTable.kt`, `NlpManager.kt`
* **Details:**
    * **Cycle Detection:** Added `visited` set to beam search — words can't repeat in a path, killing `you→are→you→are` loops.
    * **Quality Threshold:** Raised from 0.05 to 0.15 to cut incoherent chains.
    * **Depth Cap:** Reduced from 4 to 3 words — bigram chains degrade fast.
    * **Priority Restructure:** PhraseTable (curated) is now Tier 1; beam search only fills remaining empty slots as fallback. – Gemini

### 2026-02-18
* **Task:** Always-on SmartbarPhraseRow with settings toggle
* **Files:** `Smartbar.kt`, `AppPrefs.kt`, `SmartbarScreen.kt`
* **Details:**
    * **Always-On:** Replaced `AnimatedVisibility` (height pop) with permanent height reservation + `animateFloatAsState` alpha fade. Row is always present when enabled; content fades in/out smoothly.
    * **Settings Toggle:** Added `phraseRowEnabled` boolean pref (`smartbar__phrase_row_enabled`, default true). Toggle appears in Smartbar settings screen. Disabling removes the entire second row. – Gemini

### 2026-02-18
* **Task:** Oscilloscope waveform for recording, slow cascade bars for processing.
* **Files:** `Smartbar.kt`, `SymSpellManager.kt`
* **Details:**
    * **Recording:** Replaced bar visualizer with oscilloscope/EKG-style waveform — rolling buffer of 200 amplitude samples drawn as a continuous `Path`, scrolling left like a heartbeat monitor.
    * **Processing:** 80 high-density bars, slow graceful gaussian cascade (6s sweep), gentle shimmer, sharp rectangular corners.
    * **Bug fix:** Fixed `candidate.count` compile error in `SymSpellManager.kt`. – Gemini

### 2026-02-18
* **Task:** Code review fixes — beam search dedup, `its`/`it's` context, frequency scoring, bigram spam cleanup
* **Files:** `BigramTable.kt`, `SymSpellManager.kt`, `EditorInstance.kt`, `final_mobile_bigrams.tsv`, `personal_phrases.tsv`, `inject_anchors.py`, `rescale_bigrams.py`
* **Details:**
    * **Beam Search Dedup:** Fixed `predictPhrases()` to only add terminated paths and post-filter strict prefixes, preventing slots wasted on overlapping phrases.
    * **Context-Aware `its`:** Moved from blind `its→it's` shortcut to context-aware logic (possessive after determiners/prepositions).
    * **Frequency Scoring:** Now passes `ln(candidate.count + 1)` to `CandidateScorer.score()` so common words win tiebreaks.
    * **Bigram Spam:** Cleaned 184 SMS/telecom spam entries (`sptv`, `smsrewards`, `wq`, `ntt`, etc.) that caused garbage phrase predictions like "w wq norm p min ntt".
    * **Phrase Jargon:** Removed 11 dev jargon entries from `personal_phrases.tsv` (`mod row`, `gemini.md`, `vault slash`, etc.).
    * **Perf:** Cached `DamerauLevenshteinDistance()` and `buildAppContext()` to avoid per-keystroke allocation.
    * **Script Paths:** Fixed `inject_anchors.py` and `rescale_bigrams.py` to use correct repo-relative asset paths. – Gemini

### 2026-02-17
* **Task:** Implemented SmartbarPhraseRow UI + Harvest Analysis Pipeline
* **Files:** `Smartbar.kt`, `NlpManager.kt`, `PersonalPreferences.kt`, `personal_phrases.tsv`, `final_mobile_bigrams.tsv`
* **Details:**
    * **SmartbarPhraseRow:** Created composable that reads `phraseCandidatesFlow` and renders phrase predictions in a second Smartbar row. Animates in/out via `AnimatedVisibility`. Integrated into all 3 layout modes (above/below/overlay).
    * **Phrase Bug Fix:** Fixed `getPreviousWord()` to allow apostrophes; fixed `suggest()` to extract phrases from `content` param directly.
    * **Harvest:** Pulled device data, ran `harvest_analyze.py`. Applied 20+ anti-corrections to `PersonalPreferences.kt`, merged 4,558 bigrams, installed 837 personal phrases. – Gemini

### 2026-02-17
* **Task:** Enhanced Voice Session — pause/resume, WhisperBar redesign, persistent history, pending queue.
* **Files:** `Recorder.kt`, `KeyboardState.kt`, `KeyboardManager.kt`, `Smartbar.kt`, `VoiceManager.kt`
* **Details:**
    * **Pause/Resume:** `Recorder.kt` now supports `pause()`/`resume()` (API 24+). Visualizer flatlines when paused.
    * **WhisperBar:** Redesigned recording UI: [⏸ Pause] [✕ Cancel] | Visualizer | [➤ Submit]. Separate transcribing and idle states.
    * **Persistent History:** `VoiceManager` upgraded from in-memory to SharedPreferences-backed JSON storage (survives restarts).
    * **Pending Queue:** Failed transcriptions auto-queue with cached audio files. `retryAllPending()` processes the queue. – Gemini

### 2026-02-17
* **Task:** Overhaul Whisper API integration with new "Whisper Bar" UI, sound wave visualization, and transcription history.
* **Files:** `ImeUiMode.kt`, `KeyboardState.kt`, \`KeyboardManager.kt\`, `Smartbar.kt`, `FlorisApplication.kt`, `FlorisImeService.kt`, `Recorder.kt`, `VoiceManager.kt`, `VoiceTranscriptionInputLayout.kt`
* **Details:**
    * **ImeUiMode:** Added `VOICE` and `VOICE_HISTORY` modes.
    * **Whisper Bar:** Created a dedicated UI for voice input in the Smartbar. It expands from the Mic button and features:
        * **VoiceVisualizer:** A dynamic sound wave/bar visualization that reacts to microphone amplitude in real-time.
        * **States:** Handles recording, transcribing (with a sine-wave animation), and timeout/error states.
        * **Controls:** Added Cancel (X), Retry (Refresh), and History (Clock) buttons directly in the bar.
    * **Transcription History:** Implemented a new "Voice History" screen (modeled after the clipboard history) to view and re-insert past transcriptions.
    * **Resilience:** Added logic to store the `lastAudioFile` for easy retries if the Whisper API times out or fails.
    * **Integration:** Integrated with the existing `Recorder` (enhanced with amplitude polling) and `WhisperClient`. – Gemini

### 2026-02-17
* **Task:** Created comprehensive AUTOCORRECT_FLOW.md technical documentation.
* **Files:** `AUTOCORRECT_FLOW.md`
* **Details:**
    * Maps the complete "Brain Transplant" suggestion pipeline: Retrieval (SymSpell) -> Ranking (Ngram/CandidateScorer) -> Casing (CasingUtils).
    * Documents initialization sequence, early-return logic, and auto-commit criteria.
    * Includes debugging hypotheses for the "zero corrections" bug.
    * Provides a file reference map for the core NLP codebase. – Gemini

### 2025-12-16
* **Task:** Created web-based Keyboard Layout Previewer tool for design iteration.
* **Files:** `tools/previewer/index.html`
* **Details:**
    * Ports `mergeLayouts()` algorithm from `LayoutManager.kt` (main + mod merge at placeholder).
    * Ports row height compression from `TextKeyboard.kt` (75% for extension and bottom 2 rows).
    * Ports key width factors from `TextKey.kt` (Shift 1.25x, arrows 0.8x, space grows).
    * Parses Snygg theme stylesheets with `@defines` variable resolution and `key[code=...]` selector matching.
    * Supports QWERTY, QWERTY Wide, QWERTY Wide Swipe layouts with all LCARS themes.
    * Enables agent-driven design iteration without APK builds. – Gemini


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
* **Files:** `qwerty_wide.json`, \`qwerty_wide_mod.json\`
* **Notes:**
    *   **Row 3:** Shift | z x c v b n m | Del (Backpsace).
    *   **Row 4:** Tab | , | Space | . | Up | Enter.
    *   **Row 5:** Ctrl | Esc | Mod | Left | Down | Right. – Gemini
### 2025-12-08
* **Task:** Integrated Gemma 2B (Q4_K_M) via local llama.cpp server with Vulkan acceleration.
* **Files:** `GemmaClient.kt`, `SuggestionEngine.kt`, `KeyboardManager.kt`, `AndroidManifest.xml`

### 2025-12-11
* **Task:** NLP Codebase Cleanup: Phases 2-3 complete. Removed duplicate bigram loading, dead code, consolidated casing logic.
* **Files:** `SuggestionEngine.kt`, `LatinLanguageProvider.kt`, `SymSpellManager.kt`, `CasingUtils.kt` (NEW), `BigramTable.kt`
* **Details:**
  - **Phase 2:** Removed `loadBigrams()`, `applyCasing()`, `bucketedWords` from NgramSuggestionEngine. Simplified constructor from 5 params to 3. Memory savings ~2MB.
  - **Phase 3:** Created `CasingUtils.kt` as single source of truth for casing. SymSpellManager now delegates to it.
  - **Architecture:** Brain Transplant pattern documented: SymSpell→Retriever, NgramEngine→Judge, CasingUtils→Caser.


### 2025-12-12
* **Task:** Fixed keyboard height jump when toggling number/dev rows.
* **Files:** `TextKeyboard.kt`, `FlorisImeSizing.kt`
* **Details:**
  - **Root Cause:** The special row height compression (75% for bottom 2 rows) only triggered when `rowCount == 5` exactly. Toggling dev/number rows changed row count (5→6 or 6→5), causing height to jump.
  - **Fix:** Changed `rowCount == 5` to `rowCount >= 5` with dynamic formula: `baseRowCount = rowCount - (rowCount - 4) * 0.5f` (e.g. 5→4.5, 6→5, 7→5.5).
  - **Result:** Bottom 2 rows now stay compressed regardless of toggle state, preventing jarring layout shifts.


### 2025-12-14
* **Task:** Finalized `qwerty_wide_swipe` layout and smartbar customization.
* **Files:** `qwerty_wide_swipe.json`, `qwerty_wide_swipe_mod.json`, `QuickActionArrangement.kt`, `ComputingEvaluator.kt`, `TextKey.kt`
* **Details:**
    * Fixed Z-row (Shift/Backspace width match 1.25f).
    * Reordered Mod row: `[TAB] [LEFT] [DOWN] [RIGHT] [-] [_] [Greeks] [REDO] [UNDO]`.
    * Added `/` to penultimate row.
    * Moved ESC to Smartbar (added icon support in `ComputingEvaluator`).
    * Removed Undo/Redo/Arrows/Incognito/Autocorrect from Smartbar defaults.

### 2025-12-14
* **Task:** Unified candidate scoring for tap and swipe input modes.
* **Files:** `SuggestionEngine.kt`, `NlpProviders.kt`, `LatinLanguageProvider.kt`, `NlpManager.kt`, `StatisticalGlideTypingClassifier.kt`
* **Details:**
    * Added `scoreWord()` method throughout provider chain: `NlpManager` → `SuggestionProvider` → `LatinLanguageProvider` → `NgramSuggestionEngine`.
    * Refactored swipe classifier to use unified scorer instead of inline frequency/bigram lookups.
    * BigramTable still used (access now goes through `NgramSuggestionEngine.scoreWord()` → `bigramBonus()`).
    * **Architecture:** Creates seam for future neural LM drop-in replacement. – Gemini

### 2025-12-14
* **Task:** Integrated SmolLM 135M for neural word scoring.
* **Files:** `SmolLMClient.kt` (NEW), `GemmaClient.kt`, `SuggestionEngine.kt`
* **Details:**
    * Created `SmolLMClient.kt` on port 8080 for fast word scoring (autocorrect/swipe).
    * Moved `GemmaClient.kt` to port 8081 for text generation (AI rewrite/reply).
    * `NgramSuggestionEngine.scoreWord()` now tries SmolLM first, falls back to n-gram if server unavailable.


### 2025-12-15
* **Task:** QWERTY-WIDE layout reorganization and ABC key fix.
* **Files:** `qwerty_wide.json`, `qwerty_wide_mod.json`, `qwerty_wide_swipe_mod.json`
* **Details:**
    * Fixed ABC key bug: Changed code -202 (VIEW_SYMBOLS) to -201 (VIEW_CHARACTERS) so MOD→ABC returns to main layout.
    * Reorganized MOD row: Replaced SelectAll with Ψ (AI key, -307), replaced Copy with Slash (/).
    * Removed slash from main Z row (now only in MOD layer). – Gemini

### 2025-12-15
* **Task:** Dictionary cleanup - strict 2-3 letter whitelists
* **Files:** `utils/create_unified_dict.py`, `app/src/main/assets/ime/dict/unified_dictionary.tsv`
* **Impact:** Nuked ~1,590 garbage entries (acronyms like RKO, RFK, BBC, km, dB). 2-letter words: 204→34. 3-letter words: 1719→299. All remaining short words are real conversational words.

### 2025-12-15
* **Task:** Developer Harvest System + UX Fixes
* **Files:** `HarvestManager.kt` (NEW), `EditorInstance.kt`, `FlorisImeService.kt`, `usage_harvest.md`
* **Details:**
    * **HarvestManager:** New singleton that writes usage events to `/sdcard/Documents/usage_harvest.md`. Logs ACCEPTED/REJECTED autocorrects, NEW_WORDS, INSISTED, and PICKED events with timestamps and context.
    * **Punctuation Eats Space:** Fixed iOS/Gboard-style behavior where typing `.` after `word ` now produces `word.` instead of `word .` (checks phantomSpace + punctuation).
    * **getPreviousWord():** Added helper to EditorInstance for context logging.
    * **Workflow:** Use keyboard → `cp /sdcard/Documents/usage_harvest.md ~/vault/projects/keyboard/` → review with agents to update dictionary/ignore lists. – Gemini

### 2025-12-15
* **Task:** Unified Scoring Architecture — Single source of truth for autocorrect + suggestions
* **Files:** `CandidateScorer.kt` (NEW), `SymSpellManager.kt`, `SuggestionEngine.kt`
* **Details:**
    * **CandidateScorer:** New utility in `ime/nlp/shared/` that consolidates all scoring logic. Flat primitives in/out for future neural net replacement.
    * **Unified Scoring:** `SymSpellManager.fix()`, `suggest()`, `findPrefixCandidates()`, `NgramSuggestionEngine.rank()`, and `scoreWord()` now all use `CandidateScorer.score()`.
    * **Magic Numbers Centralized:** All tuning constants (BIGRAM_WEIGHT=0.5, APOSTROPHE_EXACT_BONUS=-20.0, EXACT_MATCH_BONUS=-100.0, etc.) now in one place.
    * **Dead Code Removed:** Deleted duplicate `bigramBonus()` functions, `BigramScore` class, and inline scoring logic (~100 lines removed).
    * **Architecture:** "Brain Transplant" pattern: SymSpell=Retriever, CandidateScorer=Judge, CasingUtils=Caser. – Gemini

### 2025-12-21
* **Task:** Dictionary and harvest improvements based on usage_harvest.md analysis
* **Files:** `unified_dictionary.tsv`, `CasingUtils.kt`, `SymSpellManager.kt`, `HarvestManager.kt`, `EditorInstance.kt`
* **Changes:**
  - Added 27 words: 's contractions (what's, he's, etc.), slang (fuckin, meds, bb, rq, fr, idk, idek), tech (termux, sudo, pacman)
  - Added ac→AC and itd→it'd as contraction shortcuts
  - Extended harvest logging to capture trigram context on accept/reject events

### 2025-12-27
* **Task:** Added Bottom Row Height slider and Per-Key Customization system.
* **Files:** `AppPrefs.kt`, `Key.kt`, \`KeyCustomization.kt\` (NEW), \`KeyCustomizationScreen.kt\` (NEW), \`TextKeyboard.kt\`, \`TextKeyboardLayout.kt\`, \`Routes.kt\`, \`KeyboardScreen.kt\`, \`strings.xml\`, \`Keyboard.kt\`
* **Details:**
  - **Bottom Row Height:** New slider (50-100%) in Keyboard settings to control compressed row height.
  - **Per-Key Customization:** New settings screen with dropdown (Space/Enter/Shift/Backspace/Arrows) + sliders for padding (Top/Bottom/Left/Right 0-20dp) and height factor (50-200%).
  - **Storage:** JSON map in SharedPreferences persists customizations across restarts.
  - **Integration:** \`TextKeyboardLayout\` applies padding to \`visibleBounds\` after layout. – Gemini

### 2025-12-27
* **Task:** Created comprehensive Neon Synthwave theme utilizing ALL Snygg styling properties.
* **Files:** \`neon_synthwave.json\` (NEW), \`extension.json\`
* **Details:**
  - **Full Property Utilization:** Used every available Snygg property: background, foreground, shadow-elevation, shadow-color, border-width, border-color, shape, padding, margin, font-family, font-size, font-weight, font-style, letter-spacing, text-align, text-max-lines, text-overflow, clip.
  - **Intelligent Key Groupings:** Alphas 97-122 (cyan glow), Numbers 48-57 (magenta), Navigation -21 to -28 (orange), Modifiers -1 to -6 (blue), Shift -11/-13 (yellow), Delete -7 to -10 (red), Enter (green), Clipboard -31 to -35 (purple), Layout switchers -201 to -207 (pink).
  - **Glow Effects:** Colored shadows create neon glow effect using rgba() with high alpha values and matching border colors.
  - **Shape Variety:** Cut-corner shapes for cyber aesthetic (--shape-key, --shape-key-asymm), rounded for pills/popups.
  - **Big Palette:** 30+ unique colors including primaries, dims, tints, glows, and surface layers.
  - **Complete UI Coverage:** Styled all elements: keys, hints, popups, smartbar, clipboard, emoji panel, one-handed mode, subtype panel, glide trail, incognito indicator, extracted landscape input. – Gemini


### 2026-01-21
* **Task:** Implemented independent Alpha/Mod row height controls and Dynamic Keyboard Height.
* **Files:** \`FlorisImeSizing.kt\`, \`TextKeyboard.kt\`, \`TextKeyboardLayout.kt\`, \`KeyboardScreen.kt\`, \`AppPrefs.kt\`, \`Keyboard.kt\`, \`EditRuleDialog.kt\`, \`strings.xml\`
* **Details:**
  - **Dynamic Height:** Rewrote \`FlorisImeSizing.keyboardUiHeight\` to calculate the total frame height as a sum of its parts (\`AlphaRows * AlphaFactor + ModRows * ModFactor\`) instead of fitting to a fixed percentage. This eliminates the "seesaw" effect where resizing one section distorts the other.
  - **Alpha Row Slider:** Added \`alphaRowHeightFactor\` preference and UI slider (50-150%) to control the height of the top 3 rows (QWERTY/ASDF/ZXCV).
  - **Layout Engine:** Updated \`TextKeyboard.layout\` and \`Keyboard.layout\` abstract method to accept \`alphaRowHeightFactor\`. The engine now lays out rows using exact unit heights derived from the dynamic container size.
  - **Result:** Users can now shrink the spacebar row to save screen real estate without stretching the alpha keys, or vice-versa. – Gemini
### 2026-03-03
* **Task:** Updated NLP dictionary and anti-corrections from March harvest; verified clean remote build.
* **Files:** `anti_corrections.txt`, `unified_dictionary.tsv`, `PersonalPreferences.kt`, `bigrams_combined.tsv`, `harvest_summary.md`
### 2026-03-03
* **Task:** Synchronized toggleable row heights (Number/Dev) with the Mod row height factor.
* **Files:** `FlorisImeSizing.kt`, `TextKeyboard.kt`
### 2026-03-03
* **Task:** Restructured mod rows in qwerty_wide_mod: moved Escape and Tab to left corners, added Home/End cluster, replaced PSI with Slash.
* **Files:** `qwerty_wide_mod.json`
### 2026-03-03
* **Task:** Fixed mod row restructure and synchronized Escape/Tab widths.
* **Files:** `TextKey.kt`, `qwerty_wide_mod.json`

### 2026-03-03
* **Task:** Fixed 'black-on-black' visibility issues in LCARS Neon, Tactical, and Ops themes by adding missing SNYGG selectors for Clipboard, Smartbar, and Media sections.
* **Files:** `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/lcars_neon.json`, `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/lcars_tactical.json`, `app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/lcars.json`

### 2026-03-10
* **Task:** Implemented OmniBoard Ergonomic Upgrades: Dynamic Alpha Key Width, Split Spacing (Alpha/Mode), Hitbox Expansion, and Period Key Punctuation Popup.
* **Files:** `AppPrefs.kt`, `KeyboardScreen.kt`, `TextKey.kt`, `LayoutManager.kt`, `TextKeyboard.kt`, `TextKeyboardLayout.kt`, `qwerty_default.json`

### 2026-03-10
* **Task:** State-of-the-World Recovery — Fixed build break, number toggle layout catastrophe, and spacebar long-press toggle.
* **Files:** `SymSpellManager.kt`, `LayoutManager.kt`, `TextKeyboardLayout.kt`
* **Details:**
  - Build fix: `candidate.count` → `candidate.frequency` (SymSpellKt v3.4.0 API change)
  - Layout fix: `modRowsVisible=false` no longer nulls out the entire modifier layout; it preserves row 0 (shift/space/enter) and only hides extra mod rows
  - Long-press fix: swipe detector no longer cancels SPACE long-press coroutine before the 2.5x timer fires
  - Verified: HarvestManager password detection, "app"/"fix" in dictionary, CandidateScorer penalty-based bigrams

### 2026-03-10
* **Task:** Fixed spacebar sizing on mod row toggle; retrieved Whisper API key for local builds.
* **Files:** `LayoutManager.kt`, `qwerty_default.json`, `local.properties`
* **Details:**
  - **Spacebar Sizing:** Split the modifier layout (`qwerty_default.json`) into 3 logical rows (row 0: shift/merge, row 1: space/enter/punctuation, row 2: nav/utility). The `modRowsVisible` toggle now cleanly skips row 2 while leaving the essential spacebar row intact, allowing it to correctly expand (`hasSlimSpaceRow=false`).
  - **Whisper Integration:** Pulled the missing `OPENAI_API_KEY` from the factory server (`silo@beksinski`) and injected it into `local.properties` so local builds now have Whisper functionality.
  - **Wide Layout Spacebar Fix:** Removed the hardcoded `hasSlimSpaceRow` sizing override in `TextKey.kt` to allow the spacebar to always scale (`flayGrow=1.0f`). Rewrote `LayoutManager.kt` mod row parsing to dynamically check each mod row for the `SPACE` keycode, ensuring the space row is protected while other rows correctly hide on toggle, regardless of the layout structure (`qwerty_default` vs `qwerty_wide`). Documented the interaction in `omniboard_hacking.md`.

### 2026-07-09
* **Task:** Sanitized leaked credentials (API keys, GitHub tokens, cookies) from user harvest logs and bigram files, then amended the local commit.
* **Files:** `usage_harvest.md`, `bigrams_combined.tsv`, `bigrams_typing.tsv`, `personal_phrases.tsv`, `DEVLOG.md`

