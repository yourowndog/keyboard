### 2025-11-11
* **Task:** Implemented Option 1 to address structural issues in the layout builder. This included making `LayoutPackRepository.kt` a shared component, fixing key code validation, and adding Toast messages for silent failures.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutBuilderScreen.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutPackRepository.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutValidation.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt]`

### 2025-11-11
* **Task:** Removed `Toast` messages from `LayoutBuilderScreen.kt` to avoid potential coroutine errors, as the `show...Toast` functions are `suspend` functions and were being called from a non-coroutine context.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutBuilderScreen.kt]`

### 2025-11-11
* **Task:** Fixed a series of build errors reported by the build pipeline. This included adding missing imports, fixing type inference errors, and correcting syntax errors.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutPackRepository.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutBuilderScreen.kt]`

### 2025-11-11
* **Task:** Fixed a new set of build errors. This included changing the visibility of a private property and refactoring `runCatching` blocks to simpler `try-catch` blocks to avoid type inference issues.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyData.kt]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutBuilderScreen.kt]`

### 2025-11-18
* **Task:** Patched the layout validator to accept a whitelist of special internal key codes (e.g., `KEYCODE_TAB`, `MODE_SYMBOLS`) in custom layouts.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/app/layoutbuilder/LayoutValidation.kt]`

### 2025-11-18
* **Task:** Refactored the runtime layout engine to correctly map special key codes from user-defined layouts to their corresponding `TextKeyData` actions.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt]`

### 2025-11-18
* **Task:** Introduced an alias map for internal key codes to ensure consistent resolution of special keys in custom layouts.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt]`

### 2025-11-18
* **Task:** Refactored `resolveLayoutPackTextKeyData` to correctly handle special keys like ENTER and TAB in custom layouts.
* **Files:** `[app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt]`

### 2025-11-22
* **Task:** Generated the `PROJECT_NEURAL_MAP.md` file, a comprehensive deep-context manual for the OmniBoard project, by analyzing the entire repository's structure, build configurations, and core feature implementations.
* **Files:** `[PROJECT_NEURAL_MAP.md]`

### 2025-11-23
* **Task:** Fixed broken PATH variable in `source_env.sh` by replacing undefined `ANDROID_SDK_ROOT` with `ANDROID_HOME`.
* **Files:** `source_env.sh`

### 2025-11-25
* **Task:** Upgraded the prediction engine to a "Smart & Fast" architecture by integrating SymSpellKt, creating a SymSpellManager, initializing it in FlorisImeService, and modifying LatinLanguageProvider and AbstractEditorInstance to use the new engine.
* **Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt`

### 2025-11-25
* **Task:** Hardcoded SymSpellKt and MediaPipe dependencies in `app/build.gradle.kts` to resolve Gradle Kotlin DSL syntax error and bypass Version Catalog resolution issues.
* **Files:** `app/build.gradle.kts`
