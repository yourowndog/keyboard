# Upstream Backport & Modernization Radar

> **Status**: Technical Backport Plan (On Radar / Non-Immediate Priority)  
> **Last Verified**: 2026-08-05  
> **Target Upstream Version Span**: FlorisBoard `v0.5.0` through `v0.6.0-alpha02`  
> **Base Commit**: [`5562d495468e307f8a9d470a6fe2a810bf8bb44b`](file:///home/sam/projects/keyboard) (2025-09-30)

---

## 1. Overview & Strategy

This document specifies the technical plan for selectively backporting desirable upstream FlorisBoard enhancements, bug fixes, engine optimizations, and theming features into **OmniBoard**. 

OmniBoard has diverged significantly from upstream FlorisBoard in layout geometry computation ([`TextKeyboardGeometryBridge`](file:///home/sam/projects/keyboard/docs/keyboard/geometry-hitboxes.md)), candidate retrieval and neural autocorrect ([`autocorrect/README.md`](file:///home/sam/projects/keyboard/docs/autocorrect/README.md)), local AI/Whisper integrations ([`architecture/voice-ai.md`](file:///home/sam/projects/keyboard/docs/architecture/voice-ai.md)), and custom coding key features.

### 🎯 Scope & Explicit Exclusions
* **IN SCOPE**:
  * Core bug fixes (numeric field composing region fix, stylus/S-Pen touch event handling).
  * System modernization (Target API 36 / Android 15 & 16 insets, LiveData ➔ Kotlin StateFlow low-latency state migration).
  * Smartbar improvements (inline autofill z-order layering, emoji keyword/name weighting, layout/media context swipe gestures).
  * Snygg theming capabilities (Material You dynamic wallpaper palettes, time-based day/night theme cycling, subtype list item styling).
* **EXPLICITLY EXCLUDED**:
  * **Floating Keyboard Architecture (`FlorisImeWindow`)**: Upstream's floating drag-handles, resizer overlays, and floating dock indicators are explicitly excluded. OmniBoard will remain fixed to standard IME window bounds, preserving its Stage 4.5 geometry normalization.

---

## 2. Exhaustive Feature-by-Feature Specification & File Mapping

### Feature 2.1: Numeric Field Composing Fix (PR #3195)
* **Goal**: Stop the keyboard from creating word-composing regions or attempting spellcheck when the cursor is inside numeric, PIN, phone, or date/time input fields.
* **Why**: Eliminates character duplication and backspace bugs in 2FA prompts, PIN boxes, and banking apps.
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/FlorisEditorInfo.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/FlorisEditorInfo.kt#L23)
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt#L398)
* **Code Parsing & Logic**:
  * In `FlorisEditorInfo.kt`, add:
    ```kotlin
    val isNumberInput: Boolean
        get() = when (inputAttributes.type) {
            InputAttributes.Type.NUMBER,
            InputAttributes.Type.PHONE,
            InputAttributes.Type.DATETIME -> true
            else -> false
        }

    val isComposingAllowed: Boolean
        get() = isRichInputEditor && !isNumberInput
    ```
  * In `AbstractEditorInstance.kt`, update `commitTextInternal` (Line 398) and `finalizeComposingText` (Line 488) to verify `if (!activeInfo.isComposingAllowed)` before issuing `setComposingText` or `setComposingRegion`.
* **Codebase Differences**: OmniBoard contains custom autocorrect logic (`getAutoCommitCandidate()`) inside `commitTextInternal`. Guarding against `isNumberInput` prevents `getAutoCommitCandidate()` from attempting to score digits.

---

### Feature 2.2: Stylus / S-Pen Touch Event Handling (PR #3160)
* **Goal**: Ensure stylus, S-Pen, and external pointer events are registered cleanly without dropping keypresses.
* **Why**: Support daily-driver usage on Samsung Galaxy Note/Ultra and Android tablets.
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt#L193-L215)
* **Code Parsing & Logic**:
  * Inside `TextKeyboardLayout.kt` (`pointerInteropFilter`), event masks currently check `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`, `ACTION_CANCEL`.
  * Update filter to pass hover and stylus tool actions:
    ```kotlin
    MotionEvent.ACTION_HOVER_ENTER,
    MotionEvent.ACTION_HOVER_MOVE,
    MotionEvent.ACTION_HOVER_EXIT,
    ```
    and forward `clonedEvent` to `touchEventChannel`.
* **Codebase Differences**: OmniBoard processes touch events through `touchEventChannel` to sync hitboxes with `TextKeyboardGeometryBridge`. Upstream directly dispatches to Compose pointers. The channel pass-through must be preserved.

---

### Feature 2.3: Target API 36 (Android 15 & 16 Insets & OS Modernization)
* **Goal**: Upgrade build targets to SDK 36 and ensure window insets comply with Android 15+ edge-to-edge standards.
* **Why**: Prevents black navigation bar blocks and miscalculated window heights on newer Android OS versions.
* **Files Touched**:
  * OmniBoard Target: [`gradle.properties`](file:///home/sam/projects/keyboard/gradle.properties#L11-L12)
  * OmniBoard Target: [`app/build.gradle.kts`](file:///home/sam/projects/keyboard/app/build.gradle.kts#L29-L31)
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt)
* **Code Parsing & Logic**:
  * Update `gradle.properties`:
    ```properties
    projectTargetSdk=36
    projectCompileSdk=36
    ```
  * In `FlorisImeService.kt` (`onComputeInsets`), ensure window insets handle system gesture bar heights cleanly without truncating bottom row coding keys.

---

### Feature 2.4: LiveData ➔ Kotlin StateFlow Migration
* **Goal**: Replace legacy Android `LiveData` primitives across `EditorInstance` and `FlorisImeService` with Kotlin `StateFlow` and `SharedFlow`.
* **Why**: Eliminates main-thread UI looper stalls during rapid typing sessions and reduces latency when dispatching candidate updates.
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt#L72-L95)
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt)
* **Code Parsing & Logic**:
  * In `AbstractEditorInstance.kt`, `_activeContentFlow` and `_activeInfoFlow` already use `MutableStateFlow`. Upstream completed the remaining state bindings in `KeyboardManager` and `SubtypeManager`.
  * Ensure all UI observers consume state via `collectAsState()` in Compose without falling back to `observeAsState()` on main thread loopers.

---

### Feature 2.5: Smartbar Inline Autofill Layering Z-Order Fix (PR #3013)
* **Goal**: Ensure password manager autofill chips (Bitwarden, 1Password, Google Autofill) do not cover up key popups or candidate long-press menus.
* **Why**: Improves UX when entering credentials.
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/InlineSuggestionsUi.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/InlineSuggestionsUi.kt#L62-L95)
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/popup/PopupUiController.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/popup/PopupUiController.kt)
* **Code Parsing & Logic**:
  * Track `GlobalStateNumPopupsShowing.collectAsState()` in `InlineSuggestionsUi.kt`.
  * In `AndroidView.update`, pass `view.isZOrderedOnTop = (numPopupsShowing == 0)`.

---

### Feature 2.6: Emoji Suggestion Weighting & Name Search (PR #3008 & #3025)
* **Goal**: Implement combined weighting for emoji candidate search.
* **Why**: Increases emoji discovery accuracy when typing keywords like "fire", "happy", or "smile".
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/EmojiSuggestionProvider.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/EmojiSuggestionProvider.kt#L71-L91)
* **Code Parsing & Logic**:
  * Update candidate score mapping:
    ```kotlin
    val nameWeight = emoji.name.containsWeighted(query, ignoreCase = true)
    val keywordWeight = emoji.keywords.any { it.contains(query, ignoreCase = true) }.let { if (it) 1.0 else 0.0 }
    emoji to (nameWeight * 0.7 + keywordWeight * 0.3)
    ```
* **Codebase Differences**: OmniBoard's autocorrect (`CandidateScorer`) operates exclusively on word candidates. `EmojiSuggestionProvider` outputs `EmojiSuggestionCandidate` objects, avoiding any pollution of word auto-commit.

---

### Feature 2.7: Media Context & Layout Switching Gestures (PR #3143 & #3238)
* **Goal**: Allow swipe gestures to switch directly into the Media (Emoji/Kaomoji) context or cycle subtypes.
* **Why**: Speeds up media panel access during single-handed typing.
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/SwipeAction.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/SwipeAction.kt#L22)
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt#L553)
* **Code Parsing & Logic**:
  * Add `SWITCH_TO_MEDIA_CONTEXT` to `SwipeAction` enum.
  * In `KeyboardManager.kt` (`executeSwipeAction`), resolve `SwipeAction.SWITCH_TO_MEDIA_CONTEXT` to `TextKeyData.IME_UI_MODE_MEDIA`.

---

### Feature 2.8: Snygg Theming Enhancements (Material You & Time-Based Cycling)
* **Goal**: Backport Material You wallpaper color extraction and time-based automatic theme scheduling.
* **Why**: Expands theme customization and provides automatic night/day theme swapping.
* **Files Touched**:
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/ThemeManager.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/ThemeManager.kt)
  * OmniBoard Target: [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisImeUi.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisImeUi.kt#L21)
* **Code Parsing & Logic**:
  * Add `SnyggListItem` to `FlorisImeUi` enum for styling subtype dialogs.
  * Add time-based schedule evaluator to `ThemeManager.kt` to swap stylesheet IDs based on local time.

---

## 3. Deep Dangers, Risks, & Architectural Hazards

> ⚠️ **CAUTION**: The hazards listed below represent identified architectural friction points based on code analysis. They are **not** guaranteed to be exhaustive. Further runtime risks may emerge during compilation or device validation.

### ⚠️ Hazard A: Geometry Pipeline & `FlorisImeSizing.kt` Corruption
* **The Risk**: Upstream display scaling and floating window commits attempt to refactor [`FlorisImeSizing.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/FlorisImeSizing.kt). OmniBoard uses a dynamic row height math formula (`AlphaRows * AlphaFactor + ModRows * ModFactor`) and relies on [`TextKeyboardGeometryBridge`](file:///home/sam/projects/keyboard/docs/keyboard/geometry-hitboxes.md) for Stage 4.5 normalization.
* **Mitigation**: **Do NOT blindly merge upstream `FlorisImeSizing.kt`.** Cherry-pick only the font scaling calculations (`fontScale`) without touching row height or bottom offset calculation methods.

### ⚠️ Hazard B: Custom Autocorrect & Unified `CandidateScorer` Contamination
* **The Risk**: Upstream's `NlpManager` contains legacy spelling logic. Blindly merging `NlpManager.kt` or `NlpProviders.kt` could overwrite OmniBoard's unified `CandidateScorer` (which integrates SymSpell, Ngram, BigramTable, NeuralScorer, GemmaClient, and HarvestManager).
* **Mitigation**: Restrict NLP backports strictly to `EmojiSuggestionProvider.kt` and `InlineSuggestionsUi.kt`.

### ⚠️ Hazard C: Preference Serialization Breakdown (`AppPrefs.kt` & JetPref)
* **The Risk**: Upgrading JetPref or changing preference data keys could reset OmniBoard's custom preferences (e.g. coding key geometry sliders, AI/Whisper configs, harvest toggles).
* **Mitigation**: Retain all preference keys defined in [`AppPrefs.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt).

### ⚠️ Hazard D: Window Inset & Touch-Through Inconsistency
* **The Risk**: While visual transparency (`window.background` alpha) reveals the active application, `FlorisImeService.onComputeInsets()` must maintain opaque touch region bounds so key touches do not pass through to the background app.

---

## 4. Verification Checklist & Next Steps

When this backport task is activated in a future iteration:
1. Validate build with `./gradlew assembleDebug`.
2. Perform device validation on a physical device using `docs/development/device-validation.md`.
3. Check 2FA/PIN field entries in target apps to verify numeric composing fix.
4. Verify LCARS Tactical & Neon themes render without styling regression.
