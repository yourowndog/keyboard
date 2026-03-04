# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**OmniBoard** is a customized fork of FlorisBoard, an open-source Android keyboard (IME - Input Method Editor) built exclusively for Sam. This fork adds:
- Advanced autocorrect with a hand-curated dictionary and personal tuning
- Phrase prediction (personal phrases + bigram-chained beam search)
- OpenAI Whisper voice integration with history and pause/resume
- Personal typing pattern learning via the Harvest system
- Special Linux/Termux control keys (Ctrl, Esc, Tab)
- LCARS (Star Trek) and custom visual themes
- Per-app context logging for future per-app learning

## Hard Constraints — Read Before Suggesting Anything

- **Single user only.** OmniBoard is built exclusively for Sam. Do not generalize analysis to "other users", "common typing patterns", or "average frequency distributions". The only patterns that matter are Sam's.

- **Dictionary is hand-curated.** The word list was built from scratch drawing on AOSP and personally assembled sources. Do not suggest replacing or supplementing it with generic corpora or frequency lists from other projects.

- **Android user dictionary is not a solution.** The system user dictionary is wiped on every rebuild/reinstall. The harvest system exists precisely because persistent on-device learning cannot be relied on. Never suggest the user dictionary as a workaround.

- **On-device sessions mean no ADB.** When working in Termux on the device, ADB commands are unavailable. Use `python3 harvest.py` to sync harvest data, not `adb pull`.

- **Do not auto-commit harvest-driven changes.** Present proposals, wait for Sam's explicit approval, then apply. Never auto-apply dictionary/anti-correction/bigram changes.

- **Agent protocol:** Before *any* code change: (1) explain the "Why" in 1-2 plain-English sentences, (2) pause for clarity, (3) only then present the diff/tool call. Log accepted changes in `DEVLOG.md`, sign "– Codex."

---

## Build Commands

### Building APKs
```bash
# Debug build (OmniBoard branding, debug suffix)
./gradlew assembleDebug

# Beta build (minified, optimized)
./gradlew assembleBeta

# Release build (minified, optimized)
./gradlew assembleRelease

# All variants
./gradlew assemble
```

APK output: `app/build/outputs/apk/{debug|beta|release}/*.apk`

### Remote Build Factory (Phone-Only Workflow)
```bash
git push factory dev   # Triggers build on remote server
# Output: http://142.93.94.124:8000/app-debug.apk
```
- `main`: Stable only
- `dev`: Working branch. Factory builds this.

### Testing
```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :app:test
./gradlew :lib:kotlin:test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Code Quality
```bash
./gradlew lint
./gradlew lintDebug
```

---

## Module Architecture

Multi-module Gradle project:

- **app**: Main Android application (`FlorisImeService`, UI, settings)
- **lib/android**: Android-specific utilities and helpers
- **lib/color**: Material 3 color scheme management
- **lib/compose**: Jetpack Compose UI utilities and components
- **lib/kotlin**: Pure Kotlin utilities (platform-independent)
- **lib/native**: JNI/native code bindings
- **lib/snygg**: Custom theming engine for keyboard appearance

---

## Key Architectural Components

### IME Service
- **`FlorisImeService.kt`**: Core keyboard service, lifecycle entry point, initializes all managers
- **`KeyboardManager.kt`**: Handles keyboard state, key events, special keys (Ctrl/Esc/Tab/Voice)
- **`AbstractEditorInstance` / `EditorInstance.kt`**: Text input connection, composing buffer, phantom space, app context building

Input flow: User input → Composing buffer → Space/commit → Autocorrect pipeline → Text editor

### NLP & Autocorrect System
Located in `app/src/main/kotlin/.../ime/nlp/`:

#### Core Engines
- **`SymSpellManager.kt`**: SymSpell-based spelling correction (The Retriever)
  - Dictionary: `unified_dictionary.tsv` (~65k words), `final_mobile_bigrams.tsv` (~84k pairs)
  - Prefix index for fast autocomplete (1–3 char prefixes)
  - Contraction shortcuts, proper noun overrides, user overrides (kiry, family names, etc.)
  - Config: `MAX_EDIT_DISTANCE=2`, `PREFIX_LENGTH=7`

- **`CandidateScorer.kt`** (`ime/nlp/shared/`): Unified scoring — single source of truth (The Judge)
  - **Lower score = better candidate** (penalty-based, additive)
  - Key constants (all in one place): `BIGRAM_WEIGHT=5.0`, `EXACT_MATCH_BONUS=-100.0`, `APOSTROPHE_EXACT_BONUS=-20.0`, `USER_WORD_BONUS=-1000.0`
  - Spatial keyboard cost model: adjacent=0.5, far=2.0, transposition=0.3, length diff=0.5/char
  - `preferIsContext` / `preferIdContext` maps for "id" vs "is" disambiguation (context word lists)
  - Designed for future neural replacement (flat primitives in, single score out)

- **`NgramSuggestionEngine`** / **`SuggestionEngine.kt`**: Ranks candidates using CandidateScorer (The Judge)
- **`LatinLanguageProvider.kt`** (`ime/nlp/latin/`): Orchestrates the full single-word pipeline

#### Shared NLP Utilities (`ime/nlp/shared/`)
- **`BigramTable.kt`**: Shared bigram data — loaded once; normalized log-frequency bonus; beam search phrase prediction with cycle detection and depth cap (3 words)
- **`PhraseTable.kt`**: Personal phrase predictions from `personal_phrases.tsv`; 2-word context key → ranked multi-word continuations; Tier 1 priority over beam search
- **`CasingUtils.kt`**: Sentence capitalization, "i" → "I" logic
- **`CandidateScorer.kt`**: See above

#### Orchestration
- **`NlpManager.kt`**: Central orchestrator; two output flows:
  - `activeCandidatesFlow` → main suggestion row (single words)
  - `phraseCandidatesFlow` → phrase prediction row (multi-word)
  - Phrase prediction runs inside `NlpManager.suggest()` after single-word pipeline

#### Personalization
- **`PersonalPreferences.kt`**: Anti-corrections hard filter (`ANTI_CORRECTIONS` map)
  - Format: `"typed" to listOf("wrongSuggestion1", "wrongSuggestion2")`
  - 70+ curated pairs; updated after each harvest review cycle

#### Experimental
- **`GemmaClient.kt`**: HTTP POST to local sidecar at `127.0.0.1:8081` (REPLY/REWRITE/CONTINUE modes; persona from `assets/ime/nlp/gemma_persona.txt`)
- **`SmolLMClient.kt`**: SmolLM 135M on port 8080 for word scoring; falls back to n-gram if unavailable

### Harvest System (Typing Pattern Learning)

**`HarvestManager.kt`**: Singleton, writes to `/sdcard/Documents/usage_harvest.md`

#### Event Types
| Event | Meaning |
|-------|---------|
| `SESSION:TYPING` | Normal typing — use for bigrams AND autocorrect signal |
| `SESSION:VOICE` | Whisper transcription — use for bigrams ONLY (clean text, not typo signal) |
| `ACCEPTED` | Autocorrect committed, user continued — correction was good |
| `REJECTED` | User backspaced to revert — correction was wrong |
| `INSISTED` | User tapped their exact word in smartbar — strong "add to dict" signal |
| `PICKED` | User manually selected a suggestion |
| `NEW_WORD` | Typed word not in dictionary |
| `MANUAL_FIX` | User corrected a typo that autocorrect missed entirely — highest priority signal |
| `MULTI_ATTEMPT` | Repeated struggle with the same word |
| `BACKSPACE_STORM` | High-effort word correction (many backspaces) |
| `NO_SUGGESTION` | Dictionary gap or edit distance ceiling too tight |

All logging methods accept optional `AppContext` for per-app learning:
```kotlin
data class AppContext(
    val packageName: String,
    val fieldId: Int,
    val inputVariation: String,  // "NORMAL", "URI", "PASSWORD", etc.
    val flags: String,           // comma-separated feature flags or "none"
)
```

#### Harvest Workflow
1. Use keyboard normally → events auto-logged to `/sdcard/Documents/usage_harvest.md`
2. `python3 harvest.py` → syncs from device (deduplicates, adds `<!-- HARVEST BATCH` marker)
3. `python3 harvest_analyze.py` → generates proposals
4. `/harvest` skill → collaborative AI-driven analysis + proposals
5. Sam approves → apply changes → rebuild

**Harvest output files (in repo root):**
- `anti_corrections.txt` → candidates for `PersonalPreferences.kt`
- `dictionary_additions.txt` → candidates for `unified_dictionary.tsv`
- `bigrams_combined.tsv` → merge into `final_mobile_bigrams.tsv`
- `harvest_summary.md` → stats and insights

### Voice Input (Whisper Integration)
- **`VoiceManager.kt`** (`ime/voice/`): Transcription history (≤50 entries), pending recordings queue, SharedPreferences persistence
- **`VoiceTranscriptionInputLayout.kt`**: Composable UI — oscilloscope waveform during recording, slow cascade bars during processing; WhisperBar with history panel (long-press mic to view history)
- **`WhisperClient.kt`** (`net/`): Posts audio to OpenAI Whisper API; requires `BuildConfig.OPENAI_API_KEY` and `BuildConfig.WHISPER_MODEL`
- **`Recorder.kt`** (`audio/`): Audio recording with amplitude polling; pause/resume support (API 24+)
- **`KeyboardManager.kt`**: Handles VOICE_INPUT key code (-233), start/stop/pause/resume, transcription commit
- Voice sessions tagged `SESSION:VOICE` in harvest — use for bigrams only, not as typo signal

### Smartbar UI (`ime/smartbar/`)
- **`Smartbar.kt`**: Main smartbar container; includes optional `SmartbarPhraseRow` (second row for phrases)
- **`CandidatesRow.kt`**: Single-word suggestions from `activeCandidatesFlow`; wrapContent items, horizontal scroll when >1, max 5 candidates
- **`SmartbarPhraseRow`** (inside `Smartbar.kt:723+`): Phrase prediction row from `phraseCandidatesFlow`; animated show/hide; tapping commits entire phrase via `commitCompletion()`
- Phrase row toggled via `prefs.smartbar.phraseRowEnabled`

### Theming (Snygg)
- **`lib/snygg`**: Custom CSS-like theming engine
- Theme definitions: `app/src/main/assets/ime/theme/`
  - `com.brokentooth.lcars/stylesheets/`: `lcars.json`, `lcars_neon.json`, `lcars_sickbay.json`, `lcars_tactical.json`, `neon_synthwave.json`, `vaporwave.json`
  - `org.florisboard.themes/`: standard FlorisBoard themes
- Snygg selectors: element + optional `[attr]`, `:focus`, `:hover`, `PRESSED` for toggle/Ctrl visual states
- Number row styled distinctly in LCARS themes
- **Gotcha:** All key groups AND smartbar/clipboard/media sections must be covered to avoid black-on-black visibility issues

### Layout Engine
- **`LayoutManager.kt`**: Loads JSON layouts, merges modifier layers, builds `TextKeyboard`; cache requires app restart after layout JSON changes
- **`KeyboardManager.kt`**: Dispatches Esc/Tab/Voice/Ctrl/Shift key codes
- **`ComputingEvaluator.kt`**: `computeLabel` / `computeImageVector` — decides label vs icon per key; to force text label, null the icon and set label in JSON (see ENTER key as example)
- **`FlorisImeSizing.kt`**: Dynamic keyboard height: total = `AlphaRows × AlphaFactor + ModRows × ModFactor`; eliminates seesaw effect between row types; Alpha/Mod/Number row heights independently tunable

Layout JSON assets: `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/`

**Key custom layouts:**
- `characters/qwerty.json` — alpha rows (QWERTY/ASDF/ZXCV), Tab key (code -14)
- `characters/qwerty_wide.json` — wide variant with number row
- `charactersMod/qwerty_default.json` — bottom row: shift, ctrl (-1), esc (-15), system (-202), variation selector, space, period
- `charactersMod/qwerty_wide_mod.json` — mod row: Escape/Tab at left corners, Home/End cluster, Slash/PSI at right

Extension binding: `org.florisboard.layouts/extension.json`
- **Gotcha:** duplicate typo path `org.florisborad.layouts` exists — do not remove it

#### Key Codes (Custom)
| Code | Function |
|------|----------|
| -1 | Ctrl placeholder |
| -14 | Tab (⇥) |
| -15 | Esc |
| -201 | VIEW_CHARACTERS (ABC return from symbols) |
| -202 | VIEW_SYMBOLS |
| -233 | VOICE_INPUT |
| -307 | PSI (AI key) |

---

## Important Files & Directories

### Dictionary & Bigrams
- `app/src/main/assets/ime/dict/unified_dictionary.tsv` — Main word dictionary (`word\tfrequency`, ~65k words)
- `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv` — Word pair frequencies (`word1 word2\tfrequency`, ~84k pairs)
- `app/src/main/assets/ime/dict/personal_phrases.tsv` — Personal phrase table (`word1 word2\tcontinuation\tfrequency`, ~820 entries)

Frequency guidelines for new entries: proper nouns/names 200k+, personal slang 150k, technical terms 100k, uncommon but valid 50k.

### NLP Source Files Quick-Reference
| File | Role |
|------|------|
| `ime/nlp/SymSpellManager.kt` | Candidate retrieval, autocorrect |
| `ime/nlp/shared/CandidateScorer.kt` | ALL tuning constants + unified scoring |
| `ime/nlp/shared/BigramTable.kt` | Bigram data + phrase beam search |
| `ime/nlp/shared/PhraseTable.kt` | Personal phrase data |
| `ime/nlp/shared/CasingUtils.kt` | Casing normalization |
| `ime/nlp/PersonalPreferences.kt` | Anti-corrections hard filter |
| `ime/nlp/NlpManager.kt` | Orchestrator, both candidate flows |
| `ime/nlp/latin/LatinLanguageProvider.kt` | Single-word pipeline |
| `ime/nlp/HarvestManager.kt` | Usage event logging |
| `ime/nlp/GemmaClient.kt` | Local LLM client (port 8081) |
| `ime/nlp/SmolLMClient.kt` | Word scoring LLM (port 8080) |

### Configuration
- `local.properties` — API keys (not in git):
  ```properties
  OPENAI_API_KEY=your_key_here
  WHISPER_MODEL=whisper-1
  ```
- `gradle.properties` — Build configuration, version codes
- `app/build.gradle.kts` — App-level build config, dependencies

### Python / Analysis Scripts
| Script | Purpose |
|--------|---------|
| `harvest.py` | Sync harvest file from device; Termux-compatible, no ADB |
| `harvest_analyze.py` | Parse harvest → generate proposals |
| `harvest_analyze_app_context.py` | Per-app context analysis |
| `clean_bigram_spam.py` | Remove spam/telecom bigrams |
| `rescale_bigrams.py` | Rescale bigram frequencies (fixes prediction cliff) |
| `inject_anchors.py` | Golden trigram anchor injection for beam search |
| `utils/create_unified_dict.py` | Build/maintain unified dictionary |
| `utils/clean_frequency.py` | Clean raw frequency files |
| `token_counter.py` | Count tokens for Claude context budgeting |

### Testing
- `app/src/test/kotlin/.../ime/nlp/SuggestionEngineTest.kt` — Autocorrect tests (JUnit 5)

### Documentation Files
| File | Purpose |
|------|---------|
| `CLAUDE.md` | This file — AI agent instructions |
| `DEVLOG.md` | Chronological change log (always update after accepted changes, sign "– Codex") |
| `agents.md` | Agent conventions, cognitive map, quick jump points |
| `HARVESTING.md` | Harvest system user guide |
| `HARVEST_SYSTEM.md` | Harvest technical reference |
| `HARVEST_REVIEW_PROCESS.md` | How agents should analyze harvest data |
| `AUTOCORRECT_FLOW.md` | Full NLP pipeline diagram and data flow |
| `CONTEXT_LOGGING.md` | Per-app context logging reference |
| `KNOWLEDGE.md` | Architecture index for AI agents |
| `OMNI_REFLEX.md` | Reflexes + Brains architecture blueprint |
| `PROJECT_OMNIBOARD.md` | Project vision and backstory |

### Claude Code Skills
- `.claude/skills/harvest/SKILL.md` — `/harvest` skill: collaborative harvest analysis session

---

## Development Workflows

### Making Autocorrect Changes

1. **Add anti-corrections** (block specific corrections):
   - Edit `app/src/main/kotlin/.../ime/nlp/PersonalPreferences.kt`
   - Add to `ANTI_CORRECTIONS` map: `"typed" to listOf("wrongSuggestion")`

2. **Add dictionary words**:
   - Edit `app/src/main/assets/ime/dict/unified_dictionary.tsv`
   - Format: `word\tfrequency` (tab-separated, higher = higher priority)

3. **Add bigrams** (word pair context):
   - Edit `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv`
   - Format: `word1 word2\tfrequency`
   - Sort: `sort -t$'\t' -k2 -nr file.tsv`

4. **Add personal phrases**:
   - Edit `app/src/main/assets/ime/dict/personal_phrases.tsv`
   - Format: `word1 word2\tcontinuation\tfrequency`
   - Generated/updated by `harvest_analyze.py`

5. **Adjust scoring weights** — edit `CandidateScorer.kt`, all constants in one place:
   - `BIGRAM_WEIGHT` — bigram context influence
   - `EXACT_MATCH_BONUS`, `USER_WORD_BONUS` — match quality bonuses
   - Spatial cost constants — QWERTY adjacency penalties
   - `preferIsContext` / `preferIdContext` — "id" disambiguation word lists

### Running a Harvest Session

Use the `/harvest` skill for a full collaborative session:
```
/harvest
```

Manual steps:
```bash
python3 harvest.py             # Sync from device (no ADB needed)
python3 harvest_analyze.py     # Generate proposals
# Review harvest_summary.md
# Analyze usage_harvest.md after most recent <!-- HARVEST BATCH marker
```

Key analytical rules (from HARVEST_REVIEW_PROCESS.md):
- `SESSION:VOICE` → bigrams only; never treat voice text as typo signal
- `MANUAL_FIX` → highest priority: autocorrect had nothing; check adjacency and dict gaps
- Single char commits (`s`, `d`, `t`) → almost always fat-finger; geometry data, not vocabulary
- REJECTED ≥2x → anti-correction candidate

### Building and Testing
```bash
./gradlew assembleDebug
# ADB install (laptop only):
adb install -r app/build/outputs/apk/debug/*.apk
# Phone-only: push to factory branch, download APK from build server
```

### Adding a New Theme
- Create stylesheet JSON in `app/src/main/assets/ime/theme/com.brokentooth.lcars/stylesheets/`
- Register in the relevant `extension.json`
- Use Snygg selectors: element + optional `[attr]`, `:focus`, `:hover`, `PRESSED`
- Must cover all sections: keys, smartbar, clipboard, media (or inherit) — black-on-black is a common failure

### Layout Changes
- Edit JSON in `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/`
- After changes: app restart required (LayoutManager cache does not hot-reload)
- Verify `extension.json` binding is correct
- Do not remove the `org.florisborad.layouts` typo path — it is intentional

---

## Scoring Architecture (For Tuning Sessions)

**Convention: lower score = better candidate.**

```
score = editDistance
      + spatialCost(typed, candidate)        // QWERTY keyboard geometry
      - BIGRAM_WEIGHT × bigramBonus          // context from previous word
      + BIGRAM_NO_HIT_PENALTY                // if prev word exists but no bigram hit
      [+ APOSTROPHE_EXACT_BONUS if contraction exact match]
      [+ APOSTROPHE_TYPO_BONUS if contraction near-miss]
      [+ EXACT_MATCH_BONUS if distance=0 and spatial=0]
      [+ USER_WORD_BONUS if in USER_OVERRIDES]
```

After scoring, `PersonalPreferences.ANTI_CORRECTIONS` applies as a hard filter (removes matching correction pairs from the candidate list).

---

## Build Variants

- **debug**: Development build, package `dev.patrickgold.florisboard.debug`, debuggable, not minified
- **beta**: Pre-release, package `dev.patrickgold.florisboard.beta`, minified, ProGuard enabled
- **release**: Production, package `dev.patrickgold.florisboard`, minified, ProGuard enabled

All variants use OmniBoard branding (see `app/build.gradle.kts:130-163`).

---

## CI/CD

GitHub Actions (`.github/workflows/build.yml`):
- Triggers on push to main
- Runs `assembleDebug`
- Uploads APK as artifact
- Requires JDK 17, CMake, Ninja
- Injects `OPENAI_API_KEY` from GitHub secret into `local.properties`

---

## Key Dependencies

- **AndroidX**: Jetpack libraries (Compose BOM 2025.05.01, Room 2.7.2, etc.)
- **Kotlin**: 2.2.0
- **Kotlin Coroutines**: 1.10.2
- **Kotlin Serialization**: 1.9.0
- **SymSpellKt** (`symspellkt-android:3.4.0`): Spelling correction algorithm
- **MediaPipe Tasks GenAI** (`tasks-genai:0.10.14`): On-device AI models
- **OkHttp** 4.12.0: HTTP client for Whisper API
- **PatrickGold JetPref** 0.3.0-beta02: Settings preferences framework
- **AboutLibraries** 12.1.2: License management

See `app/build.gradle.kts` and `gradle/libs.versions.toml` for full list.

---

## Notes

- Minimum SDK: 26 (Android 8.0)
- Target SDK: 35 (Android 15)
- Compile SDK: 35
- JDK: 17 (Temurin distribution)
- Hardware: laptop T480 on Arch + i3/Alacritty; phone Galaxy S25 Ultra
- Uses version catalogs (`gradle/libs.versions.toml`, `gradle/tools.versions.toml`)
- Log changes in `DEVLOG.md`, sign "– Codex"
- Address Sam casually ("Sam"), keep responses concise, light wit is fine
