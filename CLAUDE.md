# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**OmniBoard** is a customized fork of FlorisBoard, an open-source Android keyboard (IME - Input Method Editor). This fork adds advanced autocorrect capabilities, OpenAI Whisper voice integration, personal typing pattern learning, and special Linux/Termux control keys.

## MANDATORY: Use jcodemunch MCP for ALL Codebase Navigation

**You MUST use the `jcodemunch` MCP tools for all code exploration in this repo.**
Do NOT use Bash find/grep, Glob, or Grep tools for searching or navigating code — they are slower, token-wasteful, and fail on this device (ripgrep ENOENT).

### jcodemunch Tool Reference

| Tool | Purpose |
|------|---------|
| `mcp__jcodemunch__list_repos` | Check which repos are already indexed |
| `mcp__jcodemunch__index_repo` | Index the full repo (do once per session, or after major changes) |
| `mcp__jcodemunch__index_folder` | Re-index a specific subdirectory after edits |
| `mcp__jcodemunch__invalidate_cache` | Force re-index if stale/wrong results appear |
| `mcp__jcodemunch__get_repo_outline` | High-level overview of entire repo structure |
| `mcp__jcodemunch__get_file_tree` | Directory/file tree for a given path |
| `mcp__jcodemunch__get_file_outline` | Classes, functions, structure of a file (without full content) |
| `mcp__jcodemunch__get_file_content` | Full content of a specific file |
| `mcp__jcodemunch__get_symbol` | Definition + context for a single named symbol |
| `mcp__jcodemunch__get_symbols` | Definitions for multiple symbols at once |
| `mcp__jcodemunch__search_symbols` | Find symbols by name/pattern across the whole repo |
| `mcp__jcodemunch__search_text` | Full-text search across all indexed files |

### Standard Workflow for Any Task

1. `list_repos` — confirm keyboard-local is indexed
2. If not indexed: `index_repo` with path `~/projects/keyboard-local`
3. `get_repo_outline` — orient yourself in the codebase
4. `search_symbols` or `search_text` — locate relevant classes/functions
5. `get_file_outline` — understand a file's structure before reading it fully
6. `get_symbol` / `get_file_content` — read only what you need
7. Edit with the Edit tool
8. `index_folder` on changed directories to keep the index current

**Bash is only acceptable for: git commands, build commands, running scripts.**

---

## Hard Constraints — Read Before Suggesting Anything

- **Single user only.** OmniBoard is built exclusively for Sam. Do not generalize analysis to "other users", "common typing patterns", or "average frequency distributions". The only patterns that matter are Sam's.

- **Dictionary is hand-curated.** The word list was built from scratch drawing on AOSP and personally assembled sources. Do not suggest replacing or supplementing it with generic corpora or frequency lists from other projects.

- **Android user dictionary is not a solution.** The system user dictionary is wiped on every rebuild/reinstall. This is a structural incompatibility with the dev workflow, not a configuration problem. The harvest system exists precisely because persistent on-device learning cannot be relied on. Never suggest the user dictionary as a workaround for any learning or personalization problem.

- **On-device sessions mean no ADB.** When working in Termux on the device, ADB commands are unavailable. Use `python3 harvest.py` to sync harvest data, not `adb pull`.

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

## Factory Build System (CI/CD)

The project uses a remote "Factory" server to handle builds. This allows you to trigger a full compilation and artifact generation on a dedicated machine without using local resources.

### Remote Configuration

The local repository must be configured with a git remote named `factory`:
- **URL**: `ssh://silo@beksinski/home/silo/git/omniboard.git`
- **Purpose**: Automated headless CI/CD

To add it if missing: `git remote add factory ssh://silo@beksinski/home/silo/git/omniboard.git`

### Triggering a Build

Push any branch to the `factory` remote. The server automatically detects the push, checks out that branch, and runs the build script.

```bash
# Standard build (dev branch)
git push factory dev

# Feature branch build
git push factory feature/your-branch-name

# Force a rebuild (if the branch hasn't changed)
git push factory <branch-name> --force
```

### Server-Side Logic (post-receive hook)

The server uses a Git `post-receive` hook at `/home/silo/git/omniboard.git/hooks/post-receive`:
- **Checkout**: Force-checkouts the incoming branch to `/home/silo/build_dir`
- **Execution**: Runs `/home/silo/build_script.sh`
- **Feedback**: Build progress and status (Success/Failure) stream back to your terminal in real-time as part of the `git push` output

### Agent Workflow

1. Verify the factory remote exists: `git remote -v`
2. Commit your local changes
3. Push to `factory` to validate compilation and generate debug APKs
4. Once the build succeeds, push to `origin` (GitHub) to persist the work

## OpenClaw Interface

**OpenClaw** is a local AI interface that runs in a background proot environment on the device.

- **Purpose**: OpenClaw acts as an interactive workspace for running and managing AI agents and tasks. It has access to MCP tools like jCodeMunch and Tasker.
- **Location**: Runs in a Debian environment via Termux's `proot-distro`.
- **User context**: It runs under the isolated user `openclaw`.
- **How to Start**: 
  1. Open a terminal.
  2. Start the gateway server: `proot-distro login debian --user openclaw -- start-claw`
- **How to Use**:
  - The primary interface is a Terminal UI.
  - Access it by running: `proot-distro login debian --user openclaw -- openclaw tui`
- **Configuration**: Settings (like the OpenAI API key, profiles, and MCP tools) are stored at `/home/openclaw/.openclaw/openclaw.json` inside the Debian rootfs.
