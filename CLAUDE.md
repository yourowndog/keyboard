# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**OmniBoard** is a customized fork of FlorisBoard, an open-source Android keyboard (IME - Input Method Editor). This fork adds advanced autocorrect capabilities, OpenAI Whisper voice integration, personal typing pattern learning, and special Linux/Termux control keys.

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
# Run lint checks
./gradlew lint

# Generate lint report
./gradlew lintDebug
```

## Module Architecture

This is a multi-module Gradle project:

- **app**: Main Android application containing FlorisImeService (the keyboard service) and UI
- **lib/android**: Android-specific utilities and helpers
- **lib/color**: Material 3 color scheme management for theming
- **lib/compose**: Jetpack Compose UI utilities and components
- **lib/kotlin**: Pure Kotlin utilities (platform-independent)
- **lib/native**: JNI/native code bindings
- **lib/snygg**: Custom theming engine for keyboard appearance

## Key Architectural Components

### IME (Input Method Editor) Architecture
- **FlorisImeService**: Core keyboard service (`app/src/main/kotlin/.../FlorisImeService.kt`)
- **KeyboardManager**: Handles keyboard state, key events, special keys (Ctrl/Esc)
- **AbstractEditorInstance**: Text input connection, manages composing text and commits
- Input flow: User input → Composing buffer → Space/commit → Autocorrect pipeline → Text editor

### NLP & Autocorrect System
Located in `app/src/main/kotlin/.../ime/nlp/`:

- **SymSpellManager.kt**: SymSpell-based spelling correction engine
  - Uses unified dictionary (`app/src/main/assets/ime/dict/unified_dictionary.tsv`)
  - Bigram support for context-aware corrections
  - Contraction handling (don't, can't, etc.)

- **CandidateScorer.kt**: Scores and ranks autocorrect suggestions
  - Bigram weighting for context
  - Edit distance penalties

- **PersonalPreferences.kt**: Anti-corrections map
  - Blocks unwanted autocorrects (e.g., "id" → "I'd")
  - Manually curated from usage patterns

- **SuggestionEngine.kt**: Orchestrates candidate generation

- **GemmaClient.kt / SmolLMClient.kt**: Experimental on-device LLM integration

### Harvest System (Typing Pattern Learning)
The harvest system collects typing data to improve autocorrect:

- **HarvestManager.kt**: Logs typing events to `/sdcard/Documents/usage_harvest.md`
- Event types: ACCEPTED, REJECTED, NO_SUGGESTION, MULTI_ATTEMPT, IGNORED_SUGGESTIONS, etc.
- Session types: TYPING (manual input) vs VOICE (Whisper transcriptions)

**Workflow**:
1. Use keyboard → auto-logs events
2. `python3 harvest.py` → pulls data from device to local repo
3. `python3 harvest_analyze.py` → generates recommendations:
   - `anti_corrections.txt` → add to PersonalPreferences.kt
   - `dictionary_additions.txt` → add to unified_dictionary.tsv
   - `bigrams_combined.tsv` → merge into final_mobile_bigrams.tsv
   - `harvest_summary.md` → stats and insights
4. Apply changes → rebuild → test

### Voice Input (Whisper Integration)
- **KeyboardManager.kt**: Manages voice capture and Whisper API calls
- Requires `OPENAI_API_KEY` in `local.properties`
- Records audio → sends to Whisper API → injects transcribed text
- Voice sessions tagged separately in harvest data

### Theming (Snygg)
- **lib/snygg**: Custom CSS-like theming system
- Theme definitions in `app/src/main/assets/ime/theme/`
- Supports rules, stylesheets, property sets
- Material 3 color schemes in `lib/color`

## Important Files & Directories

### Dictionary & Bigrams
- `app/src/main/assets/ime/dict/unified_dictionary.tsv` - Main word dictionary
- `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv` - Word pair frequencies
- Format: `word\tfrequency` or `word1 word2\tfrequency`

### Configuration
- `local.properties` - API keys (not in git):
  ```properties
  OPENAI_API_KEY=your_key_here
  WHISPER_MODEL=whisper-1
  ```
- `gradle.properties` - Build configuration, version codes
- `app/build.gradle.kts` - App-level build config, dependencies

### Layouts & Assets
- `app/src/main/assets/ime/keyboard/` - Keyboard layout definitions
- `app/src/main/assets/ime/media/` - Media resources
- `app/src/main/assets/ime/swipe/` - Swipe gesture data

### Testing
- `app/src/test/kotlin/.../ime/nlp/SuggestionEngineTest.kt` - Autocorrect tests
- Tests use JUnit 5 (see `build.gradle.kts:188`)

## Development Workflow

### Making Autocorrect Changes

1. **Add anti-corrections** (block specific corrections):
   - Edit `app/src/main/kotlin/.../ime/nlp/PersonalPreferences.kt`
   - Add to `ANTI_CORRECTIONS` map: `"typed" to "intended"`

2. **Add dictionary words**:
   - Edit `app/src/main/assets/ime/dict/unified_dictionary.tsv`
   - Format: `word\tfrequency` (tab-separated)
   - Higher frequency = higher priority

3. **Add bigrams** (word pair context):
   - Edit `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv`
   - Format: `word1 word2\tfrequency`
   - Sort by frequency: `sort -t$'\t' -k2 -nr file.tsv`

4. **Adjust autocorrect logic**:
   - `SymSpellManager.kt` - Correction generation
   - `CandidateScorer.kt` - Scoring weights (e.g., BIGRAM_WEIGHT)

### Building and Testing

1. Build debug APK: `./gradlew assembleDebug`
2. Install on device: `adb install -r app/build/outputs/apk/debug/*.apk`
3. Enable keyboard: Settings → System → Languages & Input → On-screen keyboard
4. Test typing → review harvest logs
5. Pull harvest data: `python3 harvest.py`
6. Analyze: `python3 harvest_analyze.py`
7. Apply recommendations → repeat

### Working with Native Code

If modifying `lib/native`:
- Requires CMake and NDK (see `.github/workflows/build.yml` for setup)
- Native sources in `lib/native/src/main/cpp/`

## Build Variants

- **debug**: Development build with OmniBoard branding
  - Package: `dev.patrickgold.florisboard.debug`
  - Debuggable, not minified

- **beta**: Pre-release with optimizations
  - Package: `dev.patrickgold.florisboard.beta`
  - Minified, ProGuard enabled

- **release**: Production build
  - Package: `dev.patrickgold.florisboard`
  - Minified, ProGuard enabled

All variants use OmniBoard branding (see `app/build.gradle.kts:130-163`).

## CI/CD

GitHub Actions workflow (`.github/workflows/build.yml`):
- Triggers on push to main
- Runs `assembleDebug`
- Uploads APK as artifact
- Requires JDK 17, CMake, Ninja

## Key Dependencies

- **AndroidX**: Jetpack libraries (Compose, Room, Navigation)
- **Kotlin Coroutines**: Async operations
- **Kotlin Serialization**: JSON handling
- **SymSpellKt**: Spelling correction algorithm
- **MediaPipe Tasks GenAI**: On-device AI models
- **OkHttp**: HTTP client for Whisper API
- **AboutLibraries**: License management

See `app/build.gradle.kts` dependencies section for full list.

## Notes

- Minimum SDK: 26 (Android 8.0)
- Target SDK: 35 (Android 15)
- Compile SDK: 35
- JDK: 17 (Temurin distribution)
- Uses version catalogs (`gradle/libs.versions.toml`, `gradle/tools.versions.toml`)
