# Stage 04.5 Results — Settings Legibility Pass

Implementation commit: `79f135d3` on `keygeo-phase3-normalization`, based on
`5f31b4a5`.

## Outcome

Settings now reports the layout binding the keyboard actually uses. On an
install with no persisted subtypes, **Layouts** shows the resolved
`Subtype.DEFAULT` entry, labels it **Default**, and retains the existing compact
layout/currency summary. The summary now also includes the secondary Symbols2
binding.

The primary settings entry opens that subtype list directly. Layout Builder is
no longer routed from Settings and remains available from Devtools. The three
working but unused localization controls were moved intact to Devtools.

No IME runtime behavior, subtype persistence format, component id, layout-pack
plumbing, or preference key changed.

## Decisions taken

**The implicit default is a presentation projection, not persisted state.**
`localizationSubtypeEntries` returns the configured list unchanged when it is
non-empty. Only an empty list is projected as a single entry containing the
resolved `activeSubtypeFlow` value. This makes the UI truthful without adding
`Subtype.DEFAULT` to `subtypesFlow` or changing serialized subtype data.

**The synthetic entry is editable but not deletable.** Its row uses the same
editor route with id `-1`, is visibly marked **Default**, and has no long-press
delete action. The editor recognizes that id as the implicit fallback, starts
from `Subtype.DEFAULT`, hides the delete action, and saves through
`SubtypeManager.addSubtype`. The normal add path assigns a real timestamp id,
retains duplicate protection, appends to the configured list, and persists
through the existing preference writer. A small pure list helper exposes that
unchanged add behavior for unit testing.

**The summary was extended only to Symbols2.** Characters, Symbols, Symbols2,
and currency remain compact enough for the list. Adding numeric, advanced
numeric, numeric-row, phone, and phone2 labels would turn the summary back into
an editor; those bindings remain visible in the existing subtype editor.

**Navigation identity stayed stable.** `Routes.Settings.Localization`, subtype
routes, and `Routes.Settings.LanguagePackManager` remain unchanged. Only the
duplicate Settings Layout Builder route/object and Home entry were removed;
`Routes.Devtools.LayoutBuilder`, its composable destination, and the Devtools
entry remain.

**Demotion reused the original controls.** Devtools binds the same
`prefs.localization.displayLanguageNamesIn` list preference, the same
`displayKeyboardLabelsInSubtypeLanguage` switch, and the same language-pack
manager action. There is no preference migration or replacement key, so stored
values and runtime observers are unaffected.

The closest canonical current-behavior guide,
`docs/keyboard/layout-pipeline.md`, now describes the Layouts screen, implicit
default materialization, Devtools ownership, and untouched layout-pack wiring.

## Tests

`SettingsLegibilityTest` — 7 tests, all passing.

Coverage against the stage contract:

| Required | Test/evidence |
| --- | --- |
| Active subtype appears on a fresh install with no configured subtypes | `fresh install presents the active implicit subtype` asserts one projected entry containing `Subtype.DEFAULT` and marked implicit. |
| One, several, and many configured subtypes render correctly | `configured subtype lists preserve one several and many entries` covers lists of 1, 3, and 50 entries, preserving order and marking none implicit. |
| Editing the implicit default produces a real persisted subtype without disturbing others | `implicit default opens prefilled and saves through the add path` covers editor routing; `materializing implicit default appends a real id without disturbing others` covers id assignment and list preservation; `materialization keeps duplicate protection` covers the existing guard. Production `addSubtype` persists the helper's result through the unchanged writer. |
| Demoted preferences retain values and remain settable from Devtools | `localization controls retain their bindings in Devtools only` verifies the same two `PreferenceData` objects, enum entries, and existing language-pack action are bound in Devtools and no control remains on the primary Layouts screen. No localization preference key or declaration changed. |
| Layout Builder is unreachable from Settings and reachable from Devtools | `layout builder is routed from Devtools only` checks Home, both route declarations, the nav graph, and the Devtools entry. |

Commands and exact results:

- `./gradlew --no-daemon :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.localization.SettingsLegibilityTest`
  — **BUILD SUCCESSFUL**; all 7 focused tests passed.
- `./gradlew --no-daemon assembleDebug testDebugUnitTest` — **BUILD SUCCESSFUL
  in 46s**; 218 actionable tasks: 39 executed, 179 up-to-date.

An initial focused invocation through the root aggregate,
`./gradlew --no-daemon testDebugUnitTest --tests …SettingsLegibilityTest`,
failed because the filter was also applied to `:lib:snygg`, where no matching
test exists. Running the filter against `:app:testDebugUnitTest` directly
passed. This was command scoping, not a product or test regression.

No new source warning originates from the changed files. Pre-existing warnings
remain, including the known `BackupScreen.kt`, `RestoreScreen.kt`, and
`CrashUtility.kt` warnings observed during focused compilation. The full run
also reported existing Gradle/AGP deprecations, four clipboard string resources
without base values, and the Room nullable-list KSP warning.

## Not done

- No device validation or screenshot pass. No device is connected over ADB,
  and installing an APK was not authorized. Source and unit tests establish the
  data projection, editor path, preference bindings, and routes; final visual
  density and interaction remain device-verification items.
- No APK was installed, served, or published.
- No branch was pushed, merged, or otherwise changed remotely; `dev` was not
  modified.
- No subtype schema, persisted id, `SubtypeLayoutMap`, `qwerty_wide*` component
  id, bundled layout/language asset, or IME runtime behavior was changed.
- No `LayoutPack`, `LayoutPackRepository`, or `LayoutValidation` code or wiring
  was changed. Stage 6 retains ownership.
- The three demoted controls were not deleted or behaviorally altered. Numeric
  and phone-family subtype bindings were not added to the compact list summary;
  they remain in the editor.
