# OmniBoard Codebase Index (Antigravity IDE Knowledge Module)

## 1. Architecture Overview
- **System Architecture**: Android IME Service based on FlorisBoard.
- **Core Modules**:
    - `app`: Main Android application module.
    - `ime`: Core input method logic (keyboard, text, nlp, media).
    - `lib`: Shared libraries (snygg, kotlin extensions, android extensions).
- **IME Service Lifecycle**: Managed by `FlorisImeService` (extends `InputMethodService`).
- **Event Flow**:
    1.  **Input**: `TextKeyboardLayout` (Compose) captures Touch/Swipe events.
    2.  **Dispatch**: `TextKeyboardLayoutController` -> `InputEventDispatcher`.
    3.  **Processing**: `KeyboardManager` handles key codes, `LatinLanguageProvider` handles NLP.
    4.  **Output**: `FlorisEditorInfo` updates the editor, `InputConnection` commits text.

## 2. Input Handling
### 2.1 Gesture Input
- **Glide/Swipe**:
    -   **Continuous**: `GlideTypingGesture.kt` tracks pointer paths for swipe typing.
    -   **Discrete**: `SwipeGesture.kt` detects directional swipes (Up, Down, Left, Right) on individual keys.
    -   **Manager**: `GlideTypingManager` maps gesture paths to layout keys.
### 2.2 Tap & Long-Press Input
-   **Tap**: Handled by `TextKeyboardLayoutController.onTouchEventInternal`.
-   **Long-Press**: Detected via `InputEventDispatcher.sendDown` callback. Triggers popups or secondary actions.
-   **Multi-touch**: `PointerMap` tracks multiple pointers for simultaneous key presses.

## 3. Key System
-   **Rendering**: `TextKeyButton` (Compose) renders keys using `SnyggBox` and `SnyggText`/`SnyggIcon`.
-   **Data Model**: `TextKey` wraps `TextKeyData` (code, label, type).
-   **Popups**: `PopupUiController` manages extended key popups (long-press menus).
-   **Dimensions**: Controlled by `FlorisImeSizing` and `LayoutManager`.
-   **Styling**: `Snygg` theme engine (`SnyggStylesheet`) defines appearance.

## 4. Layout Engine
-   **Definition**: JSON files in `assets/ime/keyboard/`.
-   **Pipeline**: `LayoutManager` loads and merges layouts:
    -   **Main**: Base characters (e.g., QWERTY).
    -   **Modifier**: Bottom row/special keys.
    -   **Extension**: Number row, etc.
-   **Rendering**: `TextKeyboardLayout` renders the computed `TextKeyboard`.
-   **Dynamic Switching**: `KeyboardMode` (CHARACTERS, SYMBOLS, NUMERIC) drives layout selection.

## 5. Suggestions & Language Intelligence
-   **Pipeline**: `LatinLanguageProvider` orchestrates the flow.
    1.  **Retrieval**: `SymSpellManager` finds candidates (fuzzy match, edit distance 2).
    2.  **Ranking**: `NgramSuggestionEngine` ranks candidates using bigram probabilities.
-   **SymSpell**: `SymSpellManager.kt` (using `symspellkt`). Handles:
    -   Autocorrect (with `CONTRACTION_SHORTCUTS` and `PROPER_OVERRIDES`).
    -   Fuzzy matching (Damerau-Levenshtein).
    -   User dictionary overrides.
-   **N-grams**: `NgramEngineManager` loads `unified_dictionary.tsv` and `final_mobile_bigrams.tsv`.
-   **Casing**: `SymSpellManager.applyPredictedCasing` handles sentence capitalization and "i" -> "I" logic.

## 6. AI Integrations
### 6.1 Whisper Integration
-   **Client**: `WhisperClient.kt`.
-   **Mechanism**: Sends audio file to `https://api.openai.com/v1/audio/transcriptions`.
-   **Config**: Requires `BuildConfig.OPENAI_API_KEY` and `BuildConfig.WHISPER_MODEL`.
-   **Trigger**: `KeyboardManager` handles voice input key codes.

### 6.2 Gemma Integration
-   **Client**: `GemmaClient.kt`.
-   **Mechanism**: HTTP POST to local sidecar service (`http://127.0.0.1:8080/completion`).
-   **Modes**: `REPLY`, `REWRITE`, `CONTINUE`.
-   **Persona**: Loaded from `assets/ime/nlp/gemma_persona.txt`.

## 7. Dictionary & Language Packs
-   **Static**: TSV files in `assets/ime/dict/` (`unified_dictionary.tsv`).
-   **User Dictionary**: `DictionaryManager` manages user-added words (`UserDictionaryScreen`).
-   **Subtypes**: `Subtype` class defines locale and layout mapping.

## 8. UI/UX Components
-   **Smartbar**: `SmartbarLayout` contains candidates, clipboard, and quick actions.
-   **Candidates**: `CandidatesRow` displays suggestions from `LatinLanguageProvider`.
-   **Clipboard**: `ClipboardInputLayout` shows history.
-   **Theme**: `Snygg` (Swedish for "Stylish") is the theming system.

## 9. Accessibility & Internationalization
-   **RTL**: Supported via Android's native layout direction.
-   **Localization**: Standard Android `strings.xml` resources.

## 10. Customization & Extensions
-   **Extensions**: `ExtensionManager` handles loading of `.flex` (Floris Extension) files.
-   **Preferences**: `FlorisPreferenceStore` (JetPref) manages settings.
-   **Themes**: JSON-based stylesheets loaded dynamically.

## 11. Metrics, Logging & Debugging
-   **Logging**: `flogDebug`, `flogInfo`, `flogError` wrappers around Android Log.
-   **Debug Tools**: `DeveloperSettings` screen. `DebugLayoutComputationResult` for layout issues.

## 12. System & OS Integration
-   **Clipboard**: `ClipboardManager` listens to system clipboard changes.
-   **InputConnection**: Interface to the text field (commit text, delete, selection).

## 13. Agent Collaboration Notes
-   **NLP Tuning**: `SymSpellManager.kt` contains hardcoded weights and overrides.
-   **Layouts**: Editing JSON assets requires `LayoutManager` cache invalidation (or app restart).
-   **AI**: Whisper and Gemma clients are distinct; Whisper is cloud-based, Gemma is local-server based.
