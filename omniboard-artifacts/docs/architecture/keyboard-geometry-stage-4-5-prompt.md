# Stage 4.5 — Settings legibility pass

## Framing

This is a legibility pass, not a subtraction pass. That distinction is the point of the stage.

An audit of the Languages and Layouts surface found that almost nothing on it is dead code. The
controls that look like clutter are functioning features that this product does not use, and the
reason the screen reads as empty is a genuine defect, not styling. Deleting things would therefore
buy very little. Rendering the truth buys a lot.

Scope is the settings surface only. No subtype semantics, no persistence format, no
`qwerty_wide*` component ids, no geometry.

## The defect this stage exists to fix

`SubtypeManager.kt:54,87` falls back to `Subtype.DEFAULT` — id `-1`, en-US, `qwerty_wide` — whenever
no subtype has been configured. That fallback is a real, active subtype: it is what the keyboard is
running on right now.

`LocalizationScreen.kt:119` checks `subtypes.isEmpty()` and renders a "no subtypes configured"
warning card. The implicit default is not in `subtypesFlow`, so it never appears.

The result is a settings screen that reports nothing is configured while the user is typing on the
thing it is failing to report. "Add subtype" does not reveal the active subtype; it creates a second
one beside an invisible first. This is the whole of the reported illegibility.

## What a subtype is

Needed because the screen never explains it and the fix depends on it. `Subtype` (`Subtype.kt:47`)
binds a language to everything that varies by language:

| Field | Decides |
| --- | --- |
| `primaryLocale`, `secondaryLocales` | Which language(s) the entry covers |
| `layoutMap` | Eight layout slots: characters, symbols, symbols2, numeric, numericAdvanced, numericRow, phone, phone2 |
| `composer` | How keystrokes combine into characters (dead keys, accents) |
| `currencySet` | Which currency symbols appear |
| `punctuationRule` | Auto-space behavior around punctuation |
| `popupMapping` | What long-press popups offer |
| `nlpProviders` | Which spellcheck and suggestion engines run |

`Subtype.DEFAULT` (`Subtype.kt:73`) pins `layoutMap.characters = extCoreLayout("qwerty_wide")`. This
is why Stage 4 renamed the QWERTY Wide *labels* but kept the `qwerty_wide*` ids: they are the join
key between the language list and the layout assets, and they are persisted inside user subtypes.

So "add subtype" is really "bind a language to eight layouts and five behavior modules," presented
as one dropdown list. The screen is illegible because it is eight-dimensional and renders as one.

## Surface inventory

| File | Lines | Role |
| --- | --- | --- |
| `settings/localization/SubtypeEditorScreen.kt` | 576 | The add/edit flow; the only place the eight slots are visible today |
| `settings/localization/LanguagePackManagerScreen.kt` | 215 | Fronts the language pack extension subsystem |
| `settings/localization/LocalizationScreen.kt` | 190 | The screen this stage restructures |
| `settings/localization/SelectLocaleScreen.kt` | 150 | Locale picker, reads `displayLanguageNamesIn` |
| `app/layoutbuilder/LayoutBuilderScreen.kt` | 195 | Obsolete builder UI; unrouted from Settings here |

## Required changes

**1. Surface the active subtype.**

The list must show whatever the keyboard is actually running on, including the implicit default.
Replace the empty-state warning with the resolved active subtype, marked as a default rather than
presented as user-created. The existing summary already renders characters, symbols, and currency
labels — keep it and extend it toward the rest of `SubtypeLayoutMap` where it fits without crowding.

Editing an implicit default must materialize it as a real subtype rather than silently mutating a
constant.

**2. Collapse the submenu.**

Selecting Layouts from the main settings list goes straight to the subtype list. A subtype list is
what the section is for; it must not be a group nested inside a screen of unrelated toggles.

**3. Rename Languages and Layouts to Layouts.**

This product ships one language. The label should say what the screen does. String resources only —
route names and persisted ids stay as they are.

**4. Move the layout builder to Devtools only.**

Remove `HomeScreen.kt:129`, `Routes.kt:318`, and the `Settings.LayoutBuilder` object at
`Routes.kt:148`. Keep `Devtools.LayoutBuilder` (`Routes.kt:238,355`) and `DevtoolsScreen.kt:168`.

`LayoutBuilderScreen` has no references from the IME runtime and is safe to unroute. Do **not**
touch `LayoutPack`, `LayoutPackRepository`, or `LayoutValidation`: they are wired into
`FlorisApplication` and the `KeyboardManager` constructor. They are runtime-inert —
`loadInitialLayout` returns an empty pack on purpose and `setLayout` has no caller outside the
builder — but they are compile-time load-bearing, and Stage 6 decides their fate.

**5. Demote, do not delete, the three unused controls.**

Move to Devtools rather than removing:

- Display language names in system locale (`displayLanguageNamesIn`)
- Display keyboard labels in subtype language (`displayKeyboardLabelsInSubtypeLanguage`)
- Manage installed language packs

All three back working code. Their preferences and behavior must remain unchanged and reachable.

`displayLanguageNamesIn` has five readers, one of which is the subtype list's own title:

- `LocalizationScreen.kt:102,127,139`
- `SelectLocaleScreen.kt:67,72`
- `settings/advanced/OtherScreen.kt:139,141`
- `devtools/AndroidLocalesScreen.kt:82,94`
- declared at `AppPrefs.kt:773`

`displayKeyboardLabelsInSubtypeLanguage` is read and observed at `FlorisImeService.kt:291,296`, where
it syncs key labels to the subtype language; declared at `AppPrefs.kt:777`.

The language pack manager fronts an extension subsystem spanning ten files, including
`lib/ext/ExtensionManager.kt`, `ime/nlp/LanguagePackExtension.kt`, and
`ime/nlp/han/HanShapeBasedLanguageProvider.kt` — the Chinese shape-based input path.

Demote these **last**, after the new list is visible, in case the new list makes one of them worth
keeping in the main flow.

## Non-goals

- No change to subtype persistence, `SubtypeLayoutMap`, or component ids.
- No pruning of bundled layouts or languages. That is Stage 9, behind a reference audit.
- No redesign of the subtype editor. Unburying subtypes into a type menu is Stage 8, once the
  Stage 6 layout contract is settled.
- No change to `LayoutPack*`.

## Tests

- The active subtype appears in the list on a fresh install with no subtypes configured.
- The list still renders correctly with one, several, and many configured subtypes.
- Editing the implicit default produces a real persisted subtype and does not disturb the others.
- Demoted preferences retain their values and remain settable from Devtools.
- The layout builder is unreachable from Settings and reachable from Devtools.

## Suggested order

1. **Change 4** — zero risk, gets the builder out of the main menu immediately.
2. **Change 1** — the only item that is real work, and the only one that changes what the screen can
   tell you. Surfacing the implicit default requires deciding how a synthetic entry behaves when
   tapped; that is a design call, not a refactor.
3. **Changes 2 and 3** together — one restructure of the same screen.
4. **Change 5** last, and reversible by construction, in case the new list makes one of the demoted
   controls worth keeping in the main flow.

## Exit condition

The settings tree contains no surface that reports the absence of something the user is currently
using, and the active layout binding can be read without opening an editor or exporting a backup.
No runtime behavior changes.
