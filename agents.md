# Agents Guide (Sam’s Keyboard)

## Tone and Expectations
- Address Sam like a teammate/friend; casual, concise; light wit is fine. Use “Sam.”
- **Protocol (Critical):** Before *ANY* code change:
  1. **Explain the "Why":** 1-2 sentences in plain English. What & Why.
  2. **Pause:** Do not show the code block until the explanation is clear.
  3. **Then Code:** Only present the diff/tool call after setting the stage.
- Personal, mobile-first keyboard (Termux-heavy, daily driver). No public release concerns.
- Log accepted changes in `DEVLOG.md` and sign “– Codex.”
- Build via Android Studio, GH runner, or **Remote Build Factory** (see below).
- Hardware context: laptop T480 on Arch + i3/Alacritty; phone Galaxy Ultra 25.

## Remote Build Factory (CI/CD)
- **Purpose:** Phone-only dev (no local Android Studio).
- **Action:** `git push factory dev` triggers build on remote server.
- **Output:** `http://142.93.94.124:8000/app-debug.apk`
- **Branch Policy:**
  - `main`: Stable only.
  - `dev`: Working branch. Factory builds this.
- **Constraints:**
  - Do NOT redesign factory architecture.
  - Do NOT modify server-side paths (`/home/silo/...`) unless debugging hooks.

## Quick File Tree / Jump Points
- Assets (layouts/themes):
  - `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/qwerty.json` – alpha rows; `$` templates; tab (-14, ⇥); enter text (code 10).
  - `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/qwerty_default.json` – bottom row: shift, ctrl placeholder (-1), esc (-15), system key (-202), variation selector, space, period.
  - `app/src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json` – binds characters qwerty + modifier qwerty_default. Gotcha: duplicate typo path exists (`org.florisborad...`).
  - Themes: `app/src/main/assets/ime/theme/...` (snygg stylesheets).
- Core Kotlin (keyboard/NLP/UI):
  - Label/Icon overrides: `ime/keyboard/ComputingEvaluator.kt`; `ime/text/keyboard/TextKey.kt`.
  - Layout composition: `ime/keyboard/LayoutManager.kt` (loads extension/layout packs, merges popups, builds `TextKeyboard`).
  - Key handling: `ime/keyboard/KeyboardManager.kt` (handlers for esc/tab/voice/ctrl/shift; mic toggles).
  - Smartbar UI: `ime/smartbar/CandidatesRow.kt`.
  - NLP: `ime/nlp/SymSpellManager.kt`, `.../latin/LatinLanguageProvider.kt`, `NlpManager.kt`.
  - Dicts: `app/src/main/assets/ime/dict/...` (cleaned uni/bi); cleaner script `utils/clean_frequency.py`.
  - User dict: `ime/dictionary/*` + `app/.../settings/dictionary/UserDictionaryScreen.kt` + `DictionaryManager.kt`.
  - Voice/Whisper: `net/WhisperClient.kt` + VOICE_INPUT handling in `KeyboardManager.kt`.
  - Uninstall stub: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt`.
  - Key codes/data: `ime/text/key/KeyCode.kt`, `ime/text/keyboard/TextKeyData.kt`.

## Cognitive Map (who talks to whom)
- Layouts → `extension.json` → `LayoutManager` builds `TextKeyboard` by loading layouts, applying popup mappings; caches into `KeyboardManager`.
- Rendering: `ComputingEvaluator.computeLabel`/`computeImageVector` + `TextKey.computeLabelsAndDrawables` decide label vs icon. ENTER/VIEW_SYMBOLS forced to text; backspace still icon (apply ENTER pattern to force text).
- Runtime: `KeyboardManager.handleKeyCode` dispatches esc/tab/voice/ctrl/shift/etc. Ctrl is placeholder; shift handled via `handleShiftUp`. VOICE_INPUT toggles recorder and calls WhisperClient. ESC/TAB send key events. Uninstall button is elsewhere (HomeScreen).
- Smartbar: `CandidatesRow` reads `nlpManager.activeCandidatesFlow`, displays via `Text` (maxLines=1, overflow Visible), wrapContent, scroll when >1.
- Whisper: `WhisperClient` posts audio to OpenAI with `BuildConfig.OPENAI_API_KEY`/`WHISPER_MODEL`; missing key in AS builds causes failure. `KeyboardManager.startVoiceCapture/stopVoiceCapture` handles recorder and transcription commit.
- User dict/vault: `DictionaryManager` + `UserDictionaryScreen` manage SAF export/import (`user_dict.txt`); auto-import if DB empty and URI set.
- NLP: `SymSpellManager` uses cleaned dicts, bigrams; `LatinLanguageProvider` delegates to SymSpell; `NlpManager` routes suggestion flows.

## Custom Keys and Templates
- Use `$` templates (`auto_text_key`, `text_key`, `variation_selector`, `navigation`, etc.) in JSON.
- Current customs: tab (-14, ⇥) in `qwerty.json`; esc (-15), ctrl placeholder (-1), system key (-202) in `qwerty_default.json`; enter as text (code 10). Backspace currently icon.
- Force text instead of icon: set label in JSON, ensure `computeLabel` returns it, and return null icon in `computeImageVector`/`TextKey` (ENTER example). Apply to backspace if desired.

## Smartbar Behavior
- WrapContent items; horizontal scroll when >1; max 5 (Classic 3).
- Plain `Text`, `maxLines=1`, `overflow=Visible`, centered. Scroll to see long items; no ellipses.

## Autocorrect/NLP Snapshot (current)
- Dicts cleaned: ~68,877 unigrams / 50k bigrams; 3-letter consonant junk filtered; apostrophes kept; custom boosts (kiry, family names, ok/fr/lol/doin'/chungus).
- Bigram weight 1.5 + no-hit penalty; apostrophe variants favored; skip-next-autocorrect on undo; contraction shortcuts include well/he’ll/she’ll/its/whats, etc.
- Pending: adjacency rerank, contraction casing normalization, freq nudges for apostrophe pairs, undo/ignore learning.

## Voice/Whisper Snapshot
- VOICE_INPUT key code -233; handled in `KeyboardManager.handleKeyCode`.
- Recorder start/stop; on stop, sends file to `WhisperClient.transcribe`, commits text on success; toasts for status.
- WhisperClient requires `BuildConfig.OPENAI_API_KEY` and `WHISPER_MODEL`; missing in AS builds → failure.

## Known Issues / TODOs (new session anchors)
- Ctrl key: no label/behavior; wire like shift toggle (see `handleShiftUp`/state handling in KeyboardManager).
- Uninstall button in HomeScreen: stubbed; wire to package uninstall intent for fast reinstall loop.
- Backspace icon → text: follow ENTER pattern (null icon + label in layout).
- Autocorrect: add keyboard-adjacency rerank, contraction normalization, freq nudges, undo/ignore learning.
- Whisper: inject API key/model for AS builds.
- Gemma/LLM: planned, not integrated; note when added.
- Typo asset path exists (`org.florisborad...`); be aware when editing assets.

## Build/Env Notes
- Build in AS, GH runner, or **Remote Build Factory** (see above).
- Hardware: T480 Arch+i3/Alacritty; Galaxy Ultra 25.

## Project History (brief)
- Recent commits: autocorrect/dict cleanup, ctrl attempts, vault permission fix, smartbar tweaks, uninstall stub, Whisper wiring (BuildConfig key missing). Run `git log --oneline` for details.

## Operating Reminders
- Address Sam casually; be concise.
- Use `$` templates when editing layouts; alpha = `qwerty.json`, bottom row = `qwerty_default.json`.
- Log accepted changes in `DEVLOG.md` with “– Codex.”
- For any TODO above, jump directly to the listed files; avoid grep unless necessary.