# Settings console

The settings app is laid out by job and drawn as an LCARS console, so it reads
as part of the same device as the keyboard. The plan and the critique that
led to it are in the
[settings overhaul doc](https://claude.ai/artifact/X87AfgoGzXhrkD3YenAuH1).

## The map

Home shows seven panels. Route objects and deep-link paths kept their
FlorisBoard names, so old links still land on the right panel:

| Panel | Route (deep link) | Holds |
| --- | --- | --- |
| Look | `Settings.Theme` (`settings/theme`) | day/night theme, accent, keyboard opacity, backdrop, key skin, key label size, popups, incognito indicator |
| Sound & touch | `Settings.InputFeedback` (`settings/keyboard/input-feedback`) | long-press delay, key sounds, vibration |
| Layout | `Settings.Keyboard` (`settings/keyboard`) | profile, rows, space bar; links to size and spacing, per-key tuning, languages |
| Command strip | `Settings.Smartbar` (`settings/smartbar`) | strip on/off, suggestion display, phrase row |
| Typing | `Settings.Typing` (`settings/typing`) | suggestions, corrections, capitalization behaviour, spelling, dictionaries |
| Gestures & glide | `Settings.Gestures` (`settings/gestures`) | glide typing and trail, swipes, space and delete gestures |
| System | `Settings.Other` (`settings/other`) | settings language, physical keyboard, clipboard and emoji history, extensions, backup, diagnostics, about, uninstall |

Every preference control appears on exactly one screen.
`SettingsMapTest` fails when a control is added to a second screen, so move
a setting rather than copy it.

Home also has a search field over `SettingsSearchIndex`: each entry is a
label, its panel, the route it opens and extra keywords (synonyms and old
FlorisBoard names such as "smartbar"). Add an entry when you add a setting
people would look for by name.

## The console look

`app/console/Lcars.kt` owns the palette, the type (Chakra Petch for names,
Oxanium for values, loaded from the theme assets) and the frame:

- `FlorisAppTheme` always applies the LCARS colour scheme. The old Settings
  theme and accent preferences are no longer shown; their stored values are
  ignored.
- `FlorisScreen` draws `ConsoleHeader` (elbow, bar, title, end cap) and
  `ConsoleRail` instead of a Material top app bar. A screen picks its colour
  with `panel = ConsolePanel.X`; sub-screens use their panel's colour. A
  custom `navigationIcon` is drawn inside the elbow; otherwise the elbow is the
  back button.
- Home uses `ConsoleStatus` for the input-method state, a TEXT | CODE switch,
  readouts of the day/night theme and backdrop, and `ConsoleTile` blocks for
  the panels.

## Live keyboard

Screens that change what the keyboard looks or feels like set
`dockKeyboard = true`: Look, Sound & touch, Layout, size and spacing, and
per-key tuning. On entry the preview field takes focus, so the real
keyboard opens under the controls and every change shows on it at once. This
is the fallback from the plan; rendering the keyboard inside the settings
activity without the IME was not attempted.

## MENU on the keyboard

MENU on the command strip opens `QuickActionsOverflowPanel`, now three fixed
bands of quick-action tiles (`MenuConsoleBands`):

- EDIT: select all, copy, cut, paste, undo, redo
- CONSOLE: Text/Code, one-handed, autocorrect, incognito, Escape, hide keyboard
- SYSTEM: clipboard, language, settings

MENU draws as pressed while the panel is open. Tiles are ordinary quick
actions themed by the `smartbar-action-tile` rules; band titles use the
`smartbar-actions-overflow-customize-button` text style. A quick action with
no display name falls back to its key label instead of "!! invalid !!".

## Not done yet

Galleries for theme, font and sound pack (key skins already have one, see
[key skins](key-skins.md)) (sound needs SoundPool
playback, see [LCARS assets](lcars-assets.md)), inline sliders instead of
slider dialogs, changed-from-default markers, and per-band customisation of
MENU.
