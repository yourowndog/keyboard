# Gemini Project Context: OmniBoard

*This file is the "source of truth" for the agent (Gemini). It is based on `agents.md`.*

## Tone and Expectations
- **Persona:** Relaxed, casual, supportive mentor/buddy ("Sam").
- **Protocol (Critical):** Before *ANY* code change:
  1. **Explain the "Why":** 1-2 sentences in plain English. What & Why.
  2. **Pause:** Do not show the code block until the explanation is clear.
  3. **Then Code:** Only present the diff/tool call after setting the stage.
- **Communication:** Explain concepts simply. Don't overwhelm with jargon.
- **Project:** OmniBoard (personal, mobile-first keyboard, daily driver). No public release concerns.
- **Environment:** Termux on Android. Builds are done via CI/CD or Android Studio, NOT locally in this shell.
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

## Cognitive Map (System Architecture)
- **Layouts:** `extension.json` → `LayoutManager` builds `TextKeyboard` by loading layouts, applying popup mappings; caches into `KeyboardManager`.
- **Rendering:** `ComputingEvaluator.computeLabel`/`computeImageVector` + `TextKey.computeLabelsAndDrawables` decide label vs icon. ENTER/VIEW_SYMBOLS forced to text; backspace still icon (apply ENTER pattern to force text).
- **Runtime:** `KeyboardManager.handleKeyCode` dispatches esc/tab/voice/ctrl/shift/etc. Ctrl is placeholder; shift handled via `handleShiftUp`. `VOICE_INPUT` toggles recorder and calls `WhisperClient`. ESC/TAB send key events. Uninstall button is elsewhere (`HomeScreen`).
- **Smartbar:** `CandidatesRow` reads `nlpManager.activeCandidatesFlow`, displays via `Text` (maxLines=1, overflow Visible), wrapContent, scroll when >1.
- **Whisper:** `WhisperClient` posts audio to OpenAI with `BuildConfig.OPENAI_API_KEY`/`WHISPER_MODEL`. `KeyboardManager.startVoiceCapture/stopVoiceCapture` handles recorder and transcription commit.
- **User dict/vault:** `DictionaryManager` + `UserDictionaryScreen` manage SAF export/import (`user_dict.txt`); auto-import if DB empty and URI set.
- **NLP:** `SymSpellManager` uses cleaned dicts, bigrams; `LatinLanguageProvider` delegates to SymSpell; `NlpManager` routes suggestion flows.

## Custom Keys and Templates
- Use `$` templates (`auto_text_key`, `text_key`, `variation_selector`, `navigation`, etc.) in JSON.
- **Current customs:** tab (-14, ⇥) in `qwerty.json`; esc (-15), ctrl placeholder (-1), system key (-202) in `qwerty_default.json`; enter as text (code 10). Backspace currently icon.
- **Force text instead of icon:** set label in JSON, ensure `computeLabel` returns it, and return null icon in `computeImageVector`/`TextKey` (ENTER example). Apply to backspace if desired.

## Smartbar Behavior
- WrapContent items; horizontal scroll when >1; max 5 (Classic 3).
- Plain `Text`, `maxLines=1`, `overflow=Visible`, centered. Scroll to see long items; no ellipses.

## Autocorrect/NLP Snapshot (current)
- **Architecture:** `SymSpellManager` (logic) + `DictionaryManager` (data) + `EditorInstance` (undo/learn loop).
- **Logic:** QWERTY adjacency reranking (near=0.5, far=2.0); Backspace-Undo learning (`ignored_autocorrects` table); aggressive apostrophe boosts (-20.0) for contractions.
- **Dicts:** Cleaned unigrams/bigrams; `BLACKLIST` filters "wont"/"hows"; `CONTRACTION_SHORTCUTS` force-corrects "im"->"I'm", "wint"->"won't".
- **Status:** "Backspace Revert" handles trailing spaces; undo state persists across space commits.
- **Pending:** Fine-tuning spatial weights?

## Voice/Whisper Snapshot
- **Status:** Logic is PERFECT. Online and working.
- **Trigger:** `VOICE_INPUT` key code -233; handled in `KeyboardManager.handleKeyCode`.
- **Flow:** Recorder start/stop; on stop, sends file to `WhisperClient.transcribe`, commits text on success; toasts for status.
- **Known Issue:** `WhisperClient` requires `BuildConfig.OPENAI_API_KEY` and `WHISPER_MODEL`. These are currently only injected during GitHub builds. Local builds will fail/missing key.

## Known Issues / TODOs (Goals)
- **Ctrl key:** no label/behavior; wire like shift toggle (see `handleShiftUp`/state handling in `KeyboardManager`).
- **Uninstall button in HomeScreen:** stubbed; wire to package uninstall intent for fast reinstall loop.
- **Backspace icon → text:** follow ENTER pattern (null icon + label in layout).
- **Autocorrect:** add keyboard-adjacency rerank, contraction normalization, freq nudges, undo/ignore learning.
- **Whisper:** inject API key/model for AS builds (local development).
- **Gemma/LLM:** planned, not integrated; note when added.
- **Typo asset path exists** (`org.florisborad...`); be aware when editing assets.

## Project History (brief)
- Recent commits: autocorrect/dict cleanup, ctrl attempts, vault permission fix, smartbar tweaks, uninstall stub, Whisper wiring (BuildConfig key missing). Run `git log --oneline` for details.

## Operating Reminders
- Address Sam casually.
- Explain technical concepts simply.
- Use `$` templates when editing layouts; alpha = `qwerty.json`, bottom row = `qwerty_default.json`.
- Log accepted changes in `DEVLOG.md` with implicit "Gemini" sign-off.
- For any TODO above, jump directly to the listed files; avoid grep unless necessary.
