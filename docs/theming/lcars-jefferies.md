# LCARS Jefferies

> Status: Canonical for what is implemented; the later phases are plans
> Last verified: 2026-11-10
> Verified against: `FlorisImeUi.kt`, `KeyCode.kt`, `SnyggBox.kt`,
> `FlorisAssetResolver.kt`, `ThemeManager.kt`, `ZipUtils.kt`,
> `TextKeyboardLayout.kt`, `QuickActionButton.kt`, the generated stylesheet
> schema, and the packaged theme assets
>
> **Nothing here has been device-validated.** The theme was authored and
> statically validated in an environment with no Android SDK. Appearance,
> legibility, and touch behavior are unconfirmed.

A theme targeting the design reference in
[`reference/`](reference/README.md). Measured palette and geometry live there;
this document covers how the design maps onto the codebase.

## Why the reference works

Three things do most of the work, and all three are reachable from a
stylesheet.

**Inverted value structure.** Most dark keyboards are dark caps on a dark
field, separated by a few percent of luminance. The reference is a true-black
field with light caps and near-black glyphs, so keys are defined by
figure/ground rather than by outlines. It can then afford to be almost
ornament-free and stay legible.

**A four-step value ladder** encodes function: `#9EA4BA` hint plates, `#6E748C`
ordinary keys, `#52586E` chrome (menu, delete), `#303749` recessed (voice,
shift housing). Glyphs are near-black on every tier except the darkest, where
they flip to `#9EA4BA`.

**Rationed accent.** Coral and cyan together are under 0.5% of the reference,
and only at mode boundaries: delete, voice, language, shift.

## What the theme engine can and cannot express

Verified against the implementation, not inferred.

### Reachable from a stylesheet

Palette, the value ladder, per-key accents, corner radii, the stadium shift and
Enter's asymmetric corner, outlined buttons, popup styling, and bundled custom
typefaces.

**Gutters** are reachable through `key`'s Snygg `margin`. `snyggMargin` applies
to the key's box, but `touchBounds` are computed separately in the keyboard
geometry pipeline, so theme margin shrinks the *visible* cap and leaves the
hitbox where it was. That separation is deliberate, and it is what makes the
reference's wide gutters safe to copy.

**Custom fonts** work for a built-in asset theme. `ThemeManager` loads an
extension through `ZipUtils.unzip`, which for an asset-backed extension calls
`context.assets.copyRecursively` into a real cache directory, and
`FlorisAssetResolver` resolves `flex:/` against that directory.

### Not reachable, and why

- `width`, `height`, `opacity` are not properties. Gradients have no safe value
  path. There is no animation.
- `background-image` is honored only on `window`, and only below API 30
  (`FlorisImeService.kt`, `supportsBackgroundImage = !ATLEAST_API30_R`), so
  textured keycaps are unavailable on the target device.
- There is no themable surface between `window` and `key`. `FlorisImeUi.Root`
  is declared but never queried, and only `window` paints. The reference's
  `#0C1014` bezel around a black field therefore needs a new registered
  element; the theme currently paints the field black and omits the bezel.
- Anything drawn *inside* a key that is not a glyph — the delete grille, the
  spacebar line art and dot run, the shift ring, the smartbar divider rules —
  has no Snygg expression at all.

### Value syntax that the schema rejects

Caught by `tools/theme/validate_stylesheets.py` while authoring:

- `rounded-corner` and `cut-corner` require exactly four values.
  `rounded-corner(5dp)` is invalid.
- `sp` sizes cannot begin with `0`: `0.5sp` is invalid, `1sp` is the minimum.
- `text-max-lines` is a string (`"1"`, `"none"`), not an integer.
- `padding` and `margin` values are space-separated, not comma-separated.
- The root object sets `additionalProperties: false` and allows only
  `$schema`, so no comment or provenance key can be added to a stylesheet.

## Current state

The theme is a stylesheet only: no Kotlin was changed for it. It is generated
from one authored base into four font variants — see
[`tools/theme/README.md`](../../tools/theme/README.md).

Registered as `lcars_jefferies` (Chakra Petch), `_oxanium`, `_saira`,
`_rajdhani` in the `org.florisboard.themes` extension, so the typeface can be
judged on device by switching themes rather than by rebuilding.

**Device verdict, 2026-11-11:** Sam approved the first two, Chakra Petch
(`lcars_jefferies`) and Oxanium (`lcars_jefferies_oxanium`). Saira Condensed and
Rajdhani were not approved. The choice between the two approved faces is still
open.

### Stacked hint plates

Letter keys in the character layout draw as a two-plate stack when the theme
gives the `key-plate` element a background: a plate showing the key's hint
(`TextKey.hintedLabel`, the symbol its long press types) above the cap. The
split is 39% plate to 61% cap of the key's existing visible bounds, from the
reference's 42px plate over a 66px cap; the gap is the two elements' margins.

This is render-only. Rows are not resized and touch bounds are unchanged, so a
tap anywhere on the plate or cap types the letter and a long press types the
plate symbol, exactly as before. Themes without a `key-plate` background keep
the corner `key-hint`. Raise the keyboard's row height if the caps feel short.

The hint is computed whenever symbol or number hints are enabled. The hidden
`keyboard__key_hints_visible` pref (no settings switch, default off) only
controls the classic corner hint. It used to gate the computation itself, which
left every plate empty on a default install.
Code: `TextKeyButton` and `isPlateCandidate` in `TextKeyboardLayout.kt`.

### Key proportions (Text profile)

The theme can only paint inside the bounds the geometry gives it, so the
reference's proportions come from the Text profile's geometry defaults in
`AppPrefs.Profile`: height 100%, alpha rows 135%, key spacing 16.5dp vertical
and 5dp horizontal. Coding keeps the stock defaults, and any value set in
Keyboard geometry settings wins. Measured with `tools/visual/measure.py` as
shares of the column pitch (reference / Text profile on the 1440px emulator):

| | reference | Text |
|---|---|---|
| keycap width, gap | 0.83, 0.17 | 0.83, 0.17 |
| plate / cap | 0.53 / 0.84 | 0.52 / 0.85 |
| row gap | 0.41 | 0.42 |
| row pitch | 1.86 | 1.86 |
| key height / width | 1.74 | 1.75 |

Before this the Text profile ran the stock 80% height, which made keys 1.12
times as tall as wide with a row gap a quarter of the reference's. Spacing is
visible gap only; touch bounds still meet, so a tap in a gap reaches a key.

### Ornament layer

`KeyOrnaments.kt` draws line art inside four keys' caps: the shift dial, the
delete grille, the spacebar dot run, and the Enter corner. A theme opts in
per key code by giving `key-ornament` a foreground. That foreground is the
accent, and `key-ornament`'s background is the structure colour, both
resolved through Snygg, so the art re-themes and no hex lives in Kotlin.

- **Shift housing:** a half stadium, `rounded-corner(50%,0%,0%,50%)`: round on
  the outer edge, straight on the inner edge facing Z, as in the reference.
- **Shift dial:** an empty ring when off, a half arc for one capital, and a
  full ring with a solid core for caps lock. It replaces the shift icon, so the
  Jefferies shift key no longer turns solid coral when pressed or in caps lock;
  the dial carries the state instead.
- **Delete grille:** a coral rule over a recessed grille in the top band, in
  line with the letter plates. The cells light when pressed, and the glyph moves
  to the lower right.
- **Spacebar:** machinery line art with a run of eight cyan dots. It replaces
  the language label. During a precise delete swipe (swipe left from delete)
  the run becomes a deletion gauge: dots light in the delete key's ornament
  accent (coral) from the right end toward the left, one per character (or
  word) selected, and past eight the bar and slab light too. Each step keeps
  the existing moving-swipe tick; filling the run adds one firmer detent.
  `DeleteSwipeReadout` carries the level from the touch controller to the
  spacebar, and it resets when the delete pointer lifts or cancels.
- **Enter:** an inner line with one oversized rounded corner, as in the
  reference. The label moves to the upper right.

Ornaments are static; motion is a later phase. They never change bounds or
touch behavior.

### Command strip smartbar

`SmartbarLayout.COMMAND_STRIP` ("Command strip" in Smartbar settings) is the
reference's fixed strip: `MENU | EMOJI | LANG ... VOICE`. It is a layout choice,
not a theme setting, so pick it in Settings, Smartbar, Layout.

- MENU opens the actions overflow (`TOGGLE_ACTIONS_OVERFLOW`), EMOJI the media
  panel, the third key (LANG in the reference) reads TEXT or CODE and switches
  between the Text and Coding profiles (`-308`, styled with the `-227` cyan
  rules; see `docs/keyboard/layout-pipeline.md`), VOICE starts voice input.
  Long-pressing the TEXT/CODE key switches language; long-pressing VOICE still
  opens voice history.
- While there are suggestions or inline autofill, they take the middle between
  the coral and cyan rules, and only MENU and VOICE stay.
- Command widths are shares of the whole bar (MENU 14%, EMOJI and TEXT/CODE 15%,
  VOICE 14%); the free middle takes exactly what is left. Labels are single
  line, 14sp, tracked 1sp (`smartbar-action-key`).
- Check visual changes here with the emulator loop in `tools/visual/`
  (`round.sh`) before installing on the phone; each round is compared with the
  reference and the previous round and added to the trajectory.
- Voice mode shows the existing `WhisperBar` and `VoiceVisualizer` unchanged.
- The buttons are ordinary quick actions drawn wide with text labels
  (`QuickActionButton(square = false, label = ...)`), styled by
  `smartbar-action-key[code=...]`. The coral and cyan bars and the half-stadium
  cap are `smartbar-command-rule` and `smartbar-command-cap`, each keyed by the
  `code` of the command they sit beside.
- Code: `SmartbarCommandStrip.kt`.

## Remaining phases

Tracked in `ROADMAP.md`. In short: stacked hint plates (a new `key-plate`
element plus row-height budgeting), an ornament layer for the artwork Snygg
cannot express, the smartbar relayout, and motion.
