# Project Neural Map

## I. The Mission & State

*   **Core Mission:** OmniBoard is a personal, highly-customizable Android keyboard. Its primary purpose is to serve as an "AI command center" for daily use, integrating various AI features directly into the input experience. It is not intended for mass distribution.
*   **Key Goals:**
    1.  **Deep AI Integration:** The Whisper API for voice input is the first of many planned AI features. The Smart Bar is the primary surface for these future integrations.
    2.  **Rich Customization:** The keyboard must be visually customizable to a high degree to match the user's themed device aesthetic.
    3.  **Hacker's Keyboard Replacement:** The layout engine must support advanced, PC-style keys (Ctrl, Alt, Tab, Esc, Arrow Keys) to be a viable daily driver for terminal sessions in apps like Termux.
*   **Current Status (as of Nov 2025):** Development is active. Recent efforts have focused on fixing bugs and improving the robustness of the custom layout engine, specifically around the handling of special key codes. The immediate roadmap item is to refine the Whisper API proof-of-concept into a polished feature.
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

### Module Structure

The project is divided into several Gradle modules to enforce separation of concerns:
*   `:app`: The main application module containing the IME service, settings UI, and feature-specific logic.
*   `:lib:snygg`: The custom CSS-like styling and theming engine. All themeable UI components are built using this.
*   `:lib:compose`: Shared, custom Jetpack Compose UI components used across the app.
*   `:lib:kotlin`: Core Kotlin utility and extension functions.
*   `:lib:native`: JNI bridge to native Rust code (currently a dummy implementation).
*   `:lib:color`: Color manipulation utilities.
*   `:lib:android`: Core Android-specific helper functions.

### Main Entry Points

*   **Application Class:** `dev.patrickgold.florisboard.FlorisApplication` (The first code to run).
*   **IME Service:** `dev.patrickgold.florisboard.FlorisImeService` (The core keyboard background service).
*   **Settings UI Activity:** `dev.patrickgold.florisboard.app.FlorisAppActivity` (The main screen for all settings).
*   **Spell Checker Service:** `dev.patrickgold.florisboard.FlorisSpellCheckerService`.

## III. The "Nervous System" (Key Files & Directories)

This is a curated list of the most important files and their roles.

*   `GEMINI.md`: **Agent Protocol.** Defines project context and rules for AI agent interaction.
*   `ROADMAP.md`: **Project Goals.** The source of truth for current and future features.
*   `DEVLOG.md`: **Recent History.** A log of tasks and changes made to the codebase.
*   `settings.gradle.kts`: **Module Definitions.** Defines the project's multi-module structure.
*   `gradle/libs.versions.toml`: **Dependencies.** Lists all external libraries and their versions.
*   `app/src/main/AndroidManifest.xml`: **Android Manifest.** Declares all core components, permissions, and entry points.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt`: **App Singleton.** Holds singleton instances of managers (e.g., `ClipboardManager`).
*   `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`: **IME Core.** The primary service class for the keyboard itself.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`: **State Coordinator.** Manages the active keyboard state, including which layout is shown.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt`: **Layout Engine.** The heart of the layout system; responsible for loading, merging, and computing final keyboard layouts.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/key/KeyCode.kt`: **Key Function Dictionary.** Defines all possible special actions a key can perform (e.g., `CTRL`, `ARROW_LEFT`, `VOICE_INPUT`).
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/ThemeManager.kt`: **Theme Orchestrator.** Manages loading and switching keyboard themes.
*   `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/SnyggStylesheet.kt`: **Theme Model.** The data class representing a full theme, with methods for loading from JSON or a Kotlin DSL.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/Smartbar.kt`: **Smartbar UI.** The top-level Composable that builds the Smartbar.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickAction.kt`: **Smartbar Action Model.** Defines the data structure for a customizable Smartbar button.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`: **Clipboard Logic.** Manages the clipboard history, database interaction, and system clipboard sync.
*   `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`: **Clipboard Database.** Defines the Room database schema for storing clipboard history.

***Note on Foreign Language Support:*** Files related to extensive localization and multiple language packs (e.g., `app/src/main/res/values-*`, `app/src/main/assets/ime/keyboard/org.florisboard.localization/*`, etc.) are not a priority for this personal-use project and can be considered for future cleanup.

## IV. Feature Mechanics (The "How-To")

### The Snygg Theming Engine
The `snygg` engine is a custom, CSS-like styling system.

1.  **Mechanism:** UI elements must be built with the special `Snygg*` Composables (e.g., `SnyggBox`, `SnyggText`) to be themeable. Each component has an `elementName` (like a CSS class). A `SnyggStylesheet` contains rules that map selectors (element name + state, e.g., `key:pressed`) to properties (`background`, `font-size`).
2.  **Theme Definition:** Themes are `SnyggStylesheet` objects. There are two ways to create them:
    *   **JSON:** Create a `stylesheet.json` file and package it in a `.flex` extension archive. The `ThemeManager` unzips and parses this file.
    *   **Kotlin DSL:** (Most powerful method for personal use). Create a theme directly in Kotlin code using the `SnyggStylesheet.v2 { ... }` builder, as seen in `FlorisImeThemeBaseStyle.kt`. This provides type-safety and full programmatic control.
3.  **Application:** The `ProvideSnyggTheme` Composable in `FlorisImeTheme.kt` applies the active theme to the UI tree. `ThemeManager.kt` controls which theme is active.

### The Smartbar & AI Command Center
The Smartbar is the primary surface for AI integrations. It is composed of word suggestions and "Quick Actions".

1.  **Structure:** The main UI is in `Smartbar.kt`. It dynamically shows content based on user preferences.
2.  **Quick Actions:** The customizable buttons are the key to the "AI Command center".
3.  **Action Model:** An action is defined by the `QuickAction.kt` sealed class. The most important type is `QuickAction.InsertKey`, which makes a Smartbar button behave identically to a regular keyboard key.
4.  **Customization Path:**
    1.  Define a new action by adding a constant to `ime/text/key/KeyCode.kt` (e.g., `const val SUMMARIZE_TEXT = -400`).
    2.  Add business logic for this new `KeyCode` in `KeyboardManager.kt` or a related class. This logic would perform the AI call.
    3.  Give the action a name for the editor UI in `quickaction/QuickAction.kt`'s `computeDisplayName` function.
    4.  The action will now be available in the app at **Settings -> Smartbar -> Customize quick actions**, ready to be added to the row.

### The Layout Customization Engine (Hacker's Keyboard)
This is how you can add `Ctrl`, `Tab`, arrows, and other PC-style keys.

1.  **Layout Definition:** The base layouts are defined in JSON files in `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/`. A layout is an array of rows, which are arrays of key objects.
2.  **Key Definition:** A single key is a JSON object. For a standard character, it's `{"code": 113, "label": "q"}`. For a special key, you use its string name: `{"code": "CTRL", "label": "Ctrl"}`. A list of all possible string codes is in `KeyCode.kt`. You can also specify `"units": 2.0` to make a key wider.
3.  **The "Builder" Workflow:** The UI at "Layout Builder" is an **importer/applier**, not a visual editor.
    1.  **Create/Edit:** Author your custom layout in a text editor as a JSON file (following the structure of the built-in layouts).
    2.  **Import:** Use the `Import` button in the Layout Builder screen to load your JSON file.
    3.  **Apply:** Use the `Apply` button to set your custom layout as the active one for the keyboard.
4.  **Engine Logic:** The `LayoutManager.kt` class is responsible for taking your custom layout data. Its `resolveLayoutPackTextKeyData` function reads the string `code` (e.g., `"CTRL"`) from your JSON and maps it to the correct internal `KeyCode` integer, creating the final key data that gets rendered.

### The Whisper Pipeline (Voice-to-Text)
This flow serves as a template for future AI integrations.

1.  **Trigger:** The `VOICE_INPUT` action, which can be placed as a `QuickAction` in the Smartbar, is pressed. Its `KeyCode` is `-233`.
2.  **Permissions:** The `KeyboardManager` requests the `RECORD_AUDIO` permission if not already granted.
3.  **Capture:** A `Recorder` class captures audio and saves it as a temporary `.mp4` file in the device cache.
4.  **API Call:** A client class (`WhisperClient.kt` - as per `ROADMAP.md`) uses the OkHttp library to send the audio file in a multipart form data request to the Whisper API endpoint.
5.  **Response & UI Update:** The client parses the JSON response from the API and sends the transcribed text to the `InputEditor`, effectively typing it into the active text field.

### The Clipboard Manager
This feature provides a history of copied items.

1.  **Core Logic:** `ClipboardManager.kt` is the central class. It listens for changes to the Android system clipboard.
2.  **Database:** When a new item is copied, a `ClipboardItem` object is created and saved to a Room database defined in `provider/ClipboardDatabase.kt`.
3.  **UI:** The `ClipboardInputLayout.kt` Composable observes the database (via the manager) and displays the history in a list. It is fully themeable via `snygg` components.
4.  **Media:** For non-text items like images, `ClipboardFileStorage.kt` saves the file to internal storage, and the `ClipboardItem` record stores the URI.

## V. Agent Protocols & Archives

*   **Core Protocol (`GEMINI.md`):** Agents must read `ROADMAP.md` and the latest `DEVLOG.md` entries at the start of any session. Builds (`gradlew build`) are forbidden; all compilation is handled by CI. Agents must use `grep -rl` to find search terms before reading files to conserve tokens.
*   **Recent Experiments (`DEVLOG.md`):** The most recent work involved heavy refactoring of the layout engine to correctly handle special key codes from user-defined layouts. This included fixing bugs in `LayoutManager.kt`, `LayoutValidation.kt`, and the `LayoutBuilderScreen.kt`. This work was critical to enabling the "Hacker's Keyboard" goal and has now stabilized.
