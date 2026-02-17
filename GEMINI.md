# Gemini Project Context: OmniBoard

*This file is the "source of truth" for the agent (Gemini). It is based on `agents.md`.*

## Core Mandates
- **Consult Omni Mechanics:** `omniboard_hacking.md` is the technical bible for this fork. Read it before touching layout logic.
- **Capture Wisdom:** If Sam says "we got it," "that's it," or "perfect," update `omniboard_hacking.md` with the solution immediately.

## Current Roadmap
- [ ] **Control Key Upgrades:**
    - [ ] Add `Control` to Per-Key Customization settings.
    - [ ] Implement "Sticky/Lock" behavior (double-tap to lock).
    - [ ] visual feedback (icon/color) for Locked state.
- [ ] **Toggle Key Feedback (Alpha/Epsilon):**
    - [ ] Ensure Number/Dev row toggle keys show "Active/Pressed" state when rows are visible.
    - [ ] Verify Snygg selector logic for these states.
- [ ] **Hitbox Debugging:**
    - [ ] Fix touch offset/misalignment on bottom Mod row when custom padding/gaps are applied.
- [ ] **True Margins (Alpha/Mod):**
    - [ ] Implement independent left/right margins for Alpha and Mod rows that shrink the row width instead of cutting into outer keys.

## Tone and Expectations
- **Persona:** Relaxed, casual, supportive mentor/buddy ("Sam").
- **Protocol (Critical):** Before *ANY* code change:
  1. **Explain the "Why":** 1-2 sentences in plain English. What & Why.
  2. **Pause:** Do not show the code block until the explanation is clear.
  3. **Then Code:** Only present the diff/tool call after setting the stage.
- **Communication:** Explain concepts simply. Don't overwhelm with jargon.
- **Project:** OmniBoard (personal, mobile-first keyboard, daily driver). No public release concerns.
- **Environment:** Termux on Android. Builds are done via CI/CD, Android Studio, or the **Remote Build Factory**.
- **Hardware:** Laptop T480 on Arch + i3/Alacritty; Phone Galaxy Ultra 25.
- **Safety:** Flexible, but avoid context explosions. Use `ripgrep` (`rg`) to find files before reading.

## Standard Operating Procedure (S.O.P.)
- **Devlog:** After completing a task, autonomously append a summary to `DEVLOG.md`.
  - Format:
    ```markdown
    ### YYYY-MM-DD
    * **Task:** [1-sentence summary]
    * **Files:** `[file1]`, `[file2]`
    ```
  - Sign-off: Implicitly "Gemini".

## SNYGG Theme Iteration Routine
**Target:** `lcars.flex` (Zip archive containing themes)

**Protocol:**
1.  **Unzip:** Extract `lcars.flex` to `.gemini/tmp/lcars_work/`.
2.  **Edit:** Modify target theme files (e.g., `stylesheets/lcars_ops.json`).
3.  **Repack:** Zip contents of `.gemini/tmp/lcars_work/` back to `lcars.flex`.
    *   *Command:* `cd .gemini/tmp/lcars_work && zip -r ../../../lcars.flex .`
4.  **Cleanup:** Remove `.gemini/tmp/lcars_work/`.

## Remote Build Factory (CI/CD)
**Context:** When phone-only (no local Android Studio), we use a remote server to build.
- **Trigger:** `git push factory dev` (pushes `dev` branch to `factory` remote).
- **Result:** Factory builds APK and hosts it.
- **Download URL:** `http://142.93.94.124:8000/app-debug.apk`
- **Branch Policy:**
  - `main` = Stable. Do NOT push experimental work here.
  - `dev` = Working branch. Factory builds from this.
- **Agent Constraints:**
  - Do NOT attempt to redesign this architecture.
  - Do NOT modify server paths (e.g., `/home/silo/...`) unless explicitly debugging the factory hook itself.

## Quick File Tree / Jump Points
- **Assets (Layouts/Themes):**
  - `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty.json` – alpha rows; `$` templates; tab (-14, ⇥); enter text (code 10).
  - `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_default.json` – bottom row: shift, ctrl placeholder (-1), esc (-15), system key (-202), variation selector, space, period.
  - `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json` – binds characters qwerty + modifier qwerty_default. **Gotcha:** duplicate typo path exists (`org.florisborad...`).
  - **Themes:** `app/src/main/assets/ime/theme/...` (snygg stylesheets).
- **Core Kotlin (keyboard/NLP/UI):**
  - **Label/Icon overrides:** `ime/keyboard/ComputingEvaluator.kt`; `ime/text/keyboard/TextKey.kt`.
  - **Layout composition:** `ime/keyboard/LayoutManager.kt` (loads extension/layout packs, merges popups, builds `TextKeyboard`).
  - **Key handling:** `ime/keyboard/KeyboardManager.kt` (handlers for esc/tab/voice/ctrl/shift; mic toggles).
  - **Smartbar UI:** `ime/smartbar/CandidatesRow.kt`.
  - **NLP:** `ime/nlp/SymSpellManager.kt`, `.../latin/LatinLanguageProvider.kt`, `NlpManager.kt`.
  - **Dicts:** `app/src/main/assets/ime/dict/...` (cleaned uni/bi); cleaner script `utils/clean_frequency.py`.
  - **User dict:** `ime/dictionary/*` + `app/.../settings/dictionary/UserDictionaryScreen.kt` + `DictionaryManager.kt`.
  - **Voice/Whisper:** `net/WhisperClient.kt` + `VOICE_INPUT` handling in `KeyboardManager.kt`.
  - **Uninstall stub:** `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt`.
  - **Key codes/data:** `ime/text/key/KeyCode.kt`, `ime/text/keyboard/TextKeyData.kt`.
- **Native:** `lib/native` (JNI bridge to Rust).

## Architecture Overview (Reconciled)

### 1. System Architecture
- **Core:** Jetpack Compose IME built atop FlorisBoard.
- **Entrypoint:** `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt` (extends `LifecycleInputMethodService`).
- **Modules:** `app` (Android app), `lib/*` (shared libs: compose, cache, io, crashutility, snygg), `lib/native` (JNI/Rust).
- **Lifecycle:** `FlorisImeService` manages composition, config changes, window insets, IME UI modes, theme updates, Smartbar visibility.
- **Event Flow:** `InputEventDispatcher` -> `KeyboardManager` -> `LayoutManager`/`TextKeyboard` -> `EditorInstance`. NLP (`NlpManager`, `SymSpellManager`) feeds Smartbar.

### 2. Input Handling
- **Gesture:** `GlideTypingManager` (in `ime/text/gestures`) links `GlideTypingGesture.Detector` with `StatisticalGlideTypingClassifier`.
- **Tap/Long-Press:** `KeyboardManager` interprets `KeyCode`, applies modifiers. Long-press popups defined in layout JSON, resolved by `TextKeyData`.

### 3. Key System
- **Rendering:** `TextKey.kt` + Compose drawing.
- **Data:** `TextKeyData.kt` (type, code, label).
- **Computation:** `ComputingEvaluator.kt` evaluates enabled/visible state, computes labels/icons.
- **Sizing:** `FlorisImeSizing.kt`, `TextKey.kt` (width factors).

### 4. Layout Engine
- **Manager:** `LayoutManager.kt` loads, caches, and merges layouts (main + modifier + extension).
- **Assets:** `app/src/main/assets/ime/keyboard` (`org.florisboard.layouts`).
- **Typo Alert:** Note existence of `org.florisborad.layouts` (typo in folder name).

### 5. Suggestions & NLP
- **Manager:** `NlpManager.kt` orchestrates suggestions.
- **Engine:** `SymSpellManager.kt` uses `SymSpell` for spell checking/correction, loads bigrams (`final_mobile_bigrams.tsv`).
- **Dictionary:** `DictionaryManager.kt`, `UserDictionary.kt` (Room-backed).

### 6. AI Integrations
- **Whisper:** `net/WhisperClient.kt` posts audio to OpenAI. Triggered by `VOICE_INPUT` (-233) in `KeyboardManager`.
- **Gemma:** `ime/nlp/GemmaClient.kt` connects to local server (`http://127.0.0.1:8080/completion`). Persona in `assets/ime/nlp/gemma_persona.txt`.

### 7. UI/UX & Customization
- **Theme:** `ThemeManager.kt` manages Snygg-based themes (`ime/theme/*`).
- **Smartbar:** `SmartbarLayout.kt`, `CandidatesRow.kt`.
- **Media:** `MediaInputLayout.kt` (emoji/emoticon).

## Custom Keys and Templates
- Use `$` templates (`auto_text_key`, `text_key`, `variation_selector`, `navigation`, etc.) in JSON.
- **Current customs:** tab (-14, ⇥) in `qwerty.json`; esc (-15), ctrl placeholder (-1), system key (-202) in `qwerty_default.json`; enter as text (code 10). Backspace currently icon.
- **Force text instead of icon:** set label in JSON, ensure `computeLabel` returns it, and return null icon in `computeImageVector`/`TextKey` (ENTER example). Apply to backspace if desired.

## Smartbar Behavior
- WrapContent items; horizontal scroll when >1; max 5 (Classic 3).
- Plain `Text`, `maxLines=1`, `overflow=Visible`, centered. Scroll to see long items; no ellipses.

## Autocorrect/NLP Snapshot (current)
- **Architecture:** Brain Transplant pattern:
  - `SymSpellManager` = Retriever (finds candidates via edit distance)
  - `CandidateScorer` = Judge (scores ALL candidates with unified logic)
  - `CasingUtils` = Caser (applies proper casing)
  - `EditorInstance` = Undo/learn loop
- **Scoring Constants (all in `CandidateScorer.kt`):**
  - BIGRAM_WEIGHT = 0.5
  - APOSTROPHE_EXACT_BONUS = -20.0, APOSTROPHE_TYPO_BONUS = -10.0
  - EXACT_MATCH_BONUS = -100.0
  - USER_WORD_BONUS = -1000.0
  - Spatial: NEIGHBOR=0.5, FAR=2.0, TRANSPOSITION=0.3
- **Dicts:** Cleaned unigrams/bigrams; `BLACKLIST` filters "wont"/"hows"; `CONTRACTION_SHORTCUTS` force-corrects "im"→"I'm".
- **Status:** Unified scoring for autocorrect AND smartbar suggestions. Neural-net-ready interface (flat primitives in/out).

## Developer Harvest System
- **Purpose:** Comprehensive usage data collection for autocorrect refinement. Survives reinstalls (external file).
- **File:** `HarvestManager.kt` writes to `/sdcard/Documents/usage_harvest.md`
- **Status:** Multi-source tracking (typing vs voice), comprehensive failure detection

### Event Types Logged

**Autocorrect Effectiveness:**
- `ACCEPTED` – autocorrect stood (user continued)
- `REJECTED` – autocorrect reverted (user backspaced)
- `INSISTED` – user picked their typed word over suggestions
- `PICKED` – user manually picked a different suggestion

**Critical Failure Detection (NEW):**
- `NO_SUGGESTION` – typed word with NO autocorrect offered (dictionary gap!)
- `MULTI_ATTEMPT` – multiple backspace/retype cycles (user struggling)
- `IGNORED_SUGGESTIONS` – suggestions shown but all ignored (wrong or invisible)
- `BACKSPACE_STORM` – high backspace count on single word (high-effort word)

**Session Tracking:**
- `SESSION:TYPING` – 5-word chunks from manual keyboard input
- `SESSION:VOICE` – transcribed text from Whisper (voice dictation)
- `NEW_WORD` – typed word not in dictionary

### Data Separation Strategy
**TYPING data** (SESSION:TYPING, autocorrect events) →
- Autocorrect effectiveness metrics
- Typo pattern analysis
- Dictionary gap detection
- Suggestion quality analysis

**VOICE data** (SESSION:VOICE) →
- Bigram extraction (natural speech patterns)
- Dictionary vocabulary expansion
- NOT used for autocorrect metrics (already correct text)

### Workflow

**1. Collect Data:**
Use keyboard normally - all events auto-logged to `/sdcard/Documents/usage_harvest.md`

**2. Sync to Repo:**
```bash
cd ~/keyboard-local
python3 harvest.py  # Pulls new data from phone
```

**3. Analyze:**
```bash
python3 harvest_analyze.py  # Generates actionable reports
```

**4. Review Outputs:**
- `harvest_summary.md` - Statistics and recommendations
- `anti_corrections.txt` - Corrections to block (PersonalPreferences.kt)
- `dictionary_additions.txt` - Words to add (unified_dictionary.tsv)
- `bigrams_combined.tsv` - New bigrams (final_mobile_bigrams.tsv)
- `problem_patterns.txt` - Autocorrect failures needing fixes

**5. Apply Changes:**
- Add anti-corrections to `PersonalPreferences.kt`
- Merge bigrams into `final_mobile_bigrams.tsv`
- Add words to `unified_dictionary.tsv`
- Review autocorrect logic for NO_SUGGESTION patterns

### Analyzer Configuration
Edit `harvest_analyze.py` to adjust thresholds:
- `MIN_WORD_FREQ = 3` - Dictionary addition threshold
- `MIN_REJECTION_COUNT = 5` - Anti-correction threshold
- `MIN_BIGRAM_FREQ = 3` - Bigram inclusion threshold

## Voice/Whisper Snapshot
- **Status:** Logic is PERFECT. Online and working.
- **Trigger:** `VOICE_INPUT` key code -233; handled in `KeyboardManager.handleKeyCode`.
- **Flow:** Recorder start/stop; on stop, sends file to `WhisperClient.transcribe`, commits text on success; toasts for status.
- **Known Issue:** `WhisperClient` requires `BuildConfig.OPENAI_API_KEY` and `WHISPER_MODEL`. These are currently only injected during GitHub builds. Local builds will fail/missing key.

## Gemma / LLM Snapshot
- **Model Path (Device):** `/data/local/tmp/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task`
- **Integration:** `ime/nlp/GemmaClient.kt` (Note: previously referred to as Bridge, now Client).
- **Status:** Code exists (`GemmaClient`) but is currently dormant/untested.

## Project History (brief)
- **Jan 2026:** Dynamic Keyboard/Row Height controls; Per-Key Customization; Neon Synthwave theme.
- **Dec 2025:** Autocorrect "Brain Transplant" (SymSpell+CandidateScorer); HarvestManager for data collection; SmolLM integration; Unified tap/swipe scoring.
- **Focus:** Autocorrect refinement and Harvest data analysis.

## Operating Reminders
- Address Sam casually.
- Explain technical concepts simply.
- Use `$` templates when editing layouts; alpha = `qwerty.json`, bottom row = `qwerty_default.json`.
- Log accepted changes in `DEVLOG.md` with implicit "Gemini" sign-off.
- For any TODO above, jump directly to the listed files; avoid grep unless necessary.

## Key Sizing & Width Tuning
- **Logic:** Key widths are controlled in `TextKey.kt` -> `flayWidthFactor`.
- **Standard:** Default is `1.0f`.
- **Wide Keys:** Shift/Enter set to `1.25f` for emphasis.
- **Narrow Keys:** Nav cluster (Arrows/Home/End) set to `0.8f` to save space.
- **Spacebar:** Automatically expands (`flayGrow = 1.0f`) to fill width saved by narrowing other keys.
- **To Adjust:** Add `KeyCode` to the `when` block in `TextKey.kt` with the desired float factor.
