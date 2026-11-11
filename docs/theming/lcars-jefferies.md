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

## Remaining phases

Tracked in `ROADMAP.md`. In short: stacked hint plates (a new `key-plate`
element plus row-height budgeting), an ornament layer for the artwork Snygg
cannot express, the smartbar relayout, and motion.
