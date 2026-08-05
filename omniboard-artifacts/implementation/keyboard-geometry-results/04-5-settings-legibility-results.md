# Stage 04.5 Results — Settings Legibility Pass

Implementation commits on `keygeo-phase3-normalization`: `79f135d3` for the
original pass and `4ceea69b` for the live-test recovery, based on `5f31b4a5`.

## Outcome

Settings now reports the layout binding the keyboard actually uses. On an
install with no persisted subtypes, **Layouts** shows the resolved
`Subtype.DEFAULT` entry, labels it **Default**, and retains the existing compact
layout/currency summary. The summary now also includes the secondary Symbols2
binding.

The primary settings entry opens that subtype list directly. Layout Builder is
no longer routed from Settings and remains available from Devtools. The three
working but unused localization controls were moved intact to Devtools.

Live testing then exposed a pre-existing broken selectable component:
`qwerty_wide_full` was registered but had no arrangement asset. A cold process
start logged `FileNotFoundException` for
`layouts/characters/qwerty_wide_full.json`, after which only the surviving
number and modifier rows rendered. The compatibility component now explicitly
resolves to the shipped QWERTY Wide arrangement and coding modifier. Every
subtype editor also has an always-available **Factory default** recovery card.

No subtype persistence format, `SubtypeLayoutMap`, component id, layout-pack
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

**Factory recovery repairs in place.** The editor loads an exact copy of
`Subtype.DEFAULT` but keeps the id of the configured subtype being edited.
Nothing is persisted until Save. This avoids both failure modes observed in the
field: attempting to reconstruct all component bindings by hand and creating a
second subtype beside the broken active one.

**The broken coding component remains identity-compatible.** The persisted
`qwerty_wide_full` id was not renamed or migrated. Its extension metadata now
uses `arrangementFile = layouts/characters/qwerty_wide.json` and the existing
`qwerty_wide_mod` modifier, and the bundled layout extension version was bumped
from `0.1.7` to `0.1.8` so an installed build refreshes the corrected metadata.
This avoids duplicating a layout asset that could drift from the shipped
QWERTY Wide source.

**The live diagnosis did not mutate subtype data.** The device preferences were
read, the failure was reproduced in a disposable Chrome search field, and an
app-process restart established that the result was not stale cache. The
restart temporarily caused Android to select Samsung Keyboard; OmniBoard was
immediately restored as the selected IME. No subtype, preference, app data, or
datastore file was cleared or rewritten.

The closest canonical current-behavior guide,
`docs/keyboard/layout-pipeline.md`, now describes the Layouts screen, implicit
default materialization, Devtools ownership, and untouched layout-pack wiring.

## Tests

`SettingsLegibilityTest` — 8 tests, all passing. `LayoutAssetDiagnosticTest` —
7 tests, all passing.

Coverage against the stage contract:

| Required | Test/evidence |
| --- | --- |
| Active subtype appears on a fresh install with no configured subtypes | `fresh install presents the active implicit subtype` asserts one projected entry containing `Subtype.DEFAULT` and marked implicit. |
| One, several, and many configured subtypes render correctly | `configured subtype lists preserve one several and many entries` covers lists of 1, 3, and 50 entries, preserving order and marking none implicit. |
| Editing the implicit default produces a real persisted subtype without disturbing others | `implicit default opens prefilled and saves through the add path` covers editor routing; `materializing implicit default appends a real id without disturbing others` covers id assignment and list preservation; `materialization keeps duplicate protection` covers the existing guard. Production `addSubtype` persists the helper's result through the unchanged writer. |
| Factory recovery is always safe for both add and edit flows | `factory default recovery preserves the subtype being edited` proves an add flow receives exact `Subtype.DEFAULT` and an edit flow receives the same bindings while retaining its real id. |
| The `qwerty_wide_full` compatibility component resolves to packaged assets | `qwerty wide full resolves to the shipped coding layout` parses the extension metadata, verifies the explicit arrangement file exists, and pins the coding modifier id. The built APK was also inspected and contains the corrected metadata. |
| Demoted preferences retain values and remain settable from Devtools | `localization controls retain their bindings in Devtools only` verifies the same two `PreferenceData` objects, enum entries, and existing language-pack action are bound in Devtools and no control remains on the primary Layouts screen. No localization preference key or declaration changed. |
| Layout Builder is unreachable from Settings and reachable from Devtools | `layout builder is routed from Devtools only` checks Home, both route declarations, the nav graph, and the Devtools entry. |

Commands and exact results:

- `./gradlew --no-daemon :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.localization.SettingsLegibilityTest`
  — **BUILD SUCCESSFUL**; all 7 focused tests passed.
- `./gradlew --no-daemon assembleDebug testDebugUnitTest` — **BUILD SUCCESSFUL
  in 46s**; 218 actionable tasks: 39 executed, 179 up-to-date.
- Repair-focused command covering `SettingsLegibilityTest` and
  `LayoutAssetDiagnosticTest` — **BUILD SUCCESSFUL in 59s**; 113 actionable
  tasks: 22 executed, 91 up-to-date.
- Post-repair `./gradlew --no-daemon assembleDebug testDebugUnitTest` — **BUILD
  SUCCESSFUL in 20s**; 218 actionable tasks: 17 executed, 201 up-to-date.

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

- The original Stage 4.5 APK was installed and the settings changes were
  visually validated. The repair APK from `4ceea69b` is hosted privately at
  `http://100.66.224.88:8045/OmniBoard-stage-4.5-repair-debug.apk` but has not
  been installed; installation still requires separate authorization. Its
  Factory default card and corrected keyboard rendering therefore remain
  live-test items. The APK was not published to GitHub or another public host.
- No branch was pushed, merged, or otherwise changed remotely; `dev` was not
  modified.
- No subtype schema, persisted subtype, `SubtypeLayoutMap`, or `qwerty_wide*`
  component id was changed. The device still contains the two subtypes created
  during the failed Arabic-to-English recovery attempt.
- No `LayoutPack`, `LayoutPackRepository`, or `LayoutValidation` code or wiring
  was changed. Stage 6 retains ownership.
- The three demoted controls were not deleted or behaviorally altered. Numeric
  and phone-family subtype bindings were not added to the compact list summary;
  they remain in the editor.
- The proposed single-page subtype editor with a subtype selector at the top was
  not implemented in this recovery. It remains a separate UX change rather than
  part of the narrow data-loss escape hatch.
- The known missing `symbols2/western_wide.json` default was not repaired; it is
  independent of the missing character rows reproduced here.
