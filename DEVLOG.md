### 2025-11-07 (Fix)
* **Task:** [Fixed a build failure caused by incorrect icon resource references in `AndroidManifest.xml`.]
* **Action:** [Moved the new icon to the `res/drawable` directory and updated all `android:icon` and `android:roundIcon` attributes in `AndroidManifest.xml` to point directly to the new `@drawable/omni_icon` resource. This bypasses the adaptive icon system that was causing resource linking errors.]
* **Files:** `[app/src/main/AndroidManifest.xml]`, `[app/src/main/res/drawable/omni_icon.png]`

### 2025-11-08
* **Task:** [Repaired missing clipboard strings and unified icon references to resolve resource linking errors.]
* **Files:** `[app/src/main/res/values/strings.xml]`, `[app/src/main/res/values/themes.xml]`, `[app/src/main/res/xml/method.xml]`, `[app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/AboutScreen.kt]`
