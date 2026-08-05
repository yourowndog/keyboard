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

All three back working code. `displayLanguageNamesIn` is read in five places including the subtype
list's own title. `displayKeyboardLabelsInSubtypeLanguage` is read and observed by
`FlorisImeService.kt:291,296`. The language pack manager fronts an extension subsystem spanning ten
files, including `HanShapeBasedLanguageProvider`. Their preferences and behavior must remain
unchanged and reachable.

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

## Exit condition

The settings tree contains no surface that reports the absence of something the user is currently
using, and the active layout binding can be read without opening an editor or exporting a backup.
No runtime behavior changes.
