# Project Neural Map

## I. The Mission & State

*   **Core Mission:** OmniBoard is a personal, highly-customizable Android keyboard. Its primary purpose is to serve as an "AI command center" for daily use, integrating various AI features directly into the input experience. It is not intended for mass distribution.
*   **Key Goals:**
    1.  **Deep AI Integration:** The Whisper API for voice input is the first of many planned AI features. The Smart Bar is the primary surface for these future integrations.
    2.  **Rich Customization:** The keyboard must be visually customizable to a high degree to match the user's themed device aesthetic.
    3.  **Hacker's Keyboard Replacement:** The layout engine must support advanced, PC-style keys (Ctrl, Alt, Tab, Esc, Arrow Keys) to be a viable daily driver for terminal sessions in apps like Termux.
*   **Current Status (as of Dec 2025):** Development is active. The project is transitioning to a "Reflexes + Brains" NLP architecture. The Whisper API is functional (logic-wise).
*   **Project Name:** The project is "OmniBoard", but the codebase and package names still widely use the original name "FlorisBoard".

## II. Core Architecture & Technologies

OmniBoard is a multi-module Android application written in Kotlin. It uses modern, declarative UI practices with Jetpack Compose.

*   **Primary Language:** Kotlin
*   **UI Framework:** Jetpack Compose
*   **Concurrency:** KotlinX Coroutines
*   **Database:** AndroidX Room (for Clipboard history, etc.)
*   **HTTP Client:** OkHttp (for Whisper API calls)
*   **Preferences:** `patrickgold.jetpref` (a custom library)
*   **Theming:** `snygg` (a custom, in-house styling engine)
*   **NLP Engine:** Hybrid approach using `SymSpellKt` (Reflexes) and `MediaPipe GenAI` (Brains/LLM).

### Module Structure

The project is divided into several Gradle modules to enforce separation of concerns:
*   `:app`: The main application module containing the IME service, settings UI, and feature-specific logic.
*   `:lib:snygg`: The custom CSS-like styling and theming engine.
*   `:lib:compose`: Shared, custom Jetpack Compose UI components used across the app.
*   `:lib:kotlin`: Core Kotlin utility and extension functions.
*   `:lib:native`: JNI bridge to native Rust code.
*   `:lib:color`: Color manipulation utilities.
*   `:lib:android`: Core Android-specific helper functions.

### Main Entry Points

*   **Application Class:** `dev.patrickgold.florisboard.FlorisApplication` (The first code to run).
*   **IME Service:** `dev.patrickgold.florisboard.FlorisImeService` (The core keyboard background service).
*   **Settings UI Activity:** `dev.patrickgold.florisboard.app.FlorisAppActivity` (The main screen for all settings).

## III. The "Nervous System" (Key Files & Directories)

This is a curated list of the most important files and their roles.

*   `GEMINI.md`: **Agent Protocol.** The single source of truth for project context, rules, and S.O.P.
*   `OMNI_REFLEX.md`: **NLP Blueprint.** Details the "Reflexes + Brains" architecture (SymSpell + Gemma).
*   `DEVLOG.md`: **Recent History.** A log of tasks and changes made to the codebase.
*   `settings.gradle.kts`: **Module Definitions.** Defines the project's multi-module structure.
*   `app/src/main/AndroidManifest.xml`: **Android Manifest.** Declares all core components, permissions, and entry points.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`: **State Coordinator.** Manages the active keyboard state, layout, and key handling (including Voice/Whisper trigger).
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt`: **Layout Engine.** Loads and builds keyboard layouts.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt`: **NLP Engine (Reflexes).** Handles spell checking and typo correction.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/GemmaBridge.kt`: **LLM Interface (Brains).** Wraps the on-device LLM for next-word prediction.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/net/WhisperClient.kt`: **Voice Client.** Handles audio transmission to OpenAI.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/audio/Recorder.kt`: **Audio Capture.** Manages microphone recording (Currently handles `.mp4`).

## IV. Feature Mechanics (The "How-To")

### The "Reflexes + Brains" NLP Engine
Replacing the stock prediction engine with a two-tier system.

1.  **Reflexes (SymSpell):**
    *   **Role:** Instant typo correction ("teh" -> "the") and bigram context ("High School" vs "High Skull").
    *   **Data:** Loaded from `frequency_dictionary_en.txt` (unigrams) and `frequency_bigram_en.txt` (bigrams) in assets.
    *   **User Data:** Dynamic injection from `UserDictionaryDao` with infinite weight.
2.  **Brains (Gemma 2B):**
    *   **Role:** "Ghostwriter" next-word prediction.
    *   **Engine:** MediaPipe GenAI Tasks via `GemmaBridge.kt`.
    *   **Trigger:** Activates when the user stops typing (passive suggestion).

### The Whisper Pipeline (Voice-to-Text)
1.  **Trigger:** `VOICE_INPUT` key (Code `-233`) toggles recording.
*   **Capture:** `Recorder.kt` captures audio to a temporary file (currently `.mp4`).
3.  **Transcribe:** `WhisperClient.kt` sends the file to OpenAI's Whisper API. Requires `BuildConfig` API key.
4.  **Output:** Transcribed text is committed to the input field.

### The Snygg Theming Engine
Custom CSS-like styling.
*   **Mechanism:** UI components use `Snygg*` composables.
*   **Styles:** Defined in `SnyggStylesheet` (JSON or Kotlin DSL).
*   **Application:** `ThemeManager` applies the active stylesheet via `ProvideSnyggTheme`.

### The Layout Customization Engine
*   **Definition:** Layouts are JSON files in `assets/ime/keyboard/...`.
*   **Custom Keys:** Supports special keys via string codes (e.g., `"CTRL"`, `"ESC"`).
*   **Logic:** `LayoutManager` resolves these codes to internal integer `KeyCode`s.

## V. Agent Protocols & Archives

*   **Core Protocol:** Defined in `GEMINI.md`. Agents must read `GEMINI.md` and the latest `DEVLOG.md` entries.
*   **Archives:**
    *   `raw_data/`: Contains raw dictionary source files (e.g., Subtlex).
    *   `utils/`: Contains Python helper scripts.