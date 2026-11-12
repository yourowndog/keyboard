# Keyboard backdrops

> Status: Canonical (first pass: picker, built-ins, imports, private assets; not yet device-validated)
> Last verified: 2026-11-12 against `ime/theme/backdrop/` and `TextInputLayout.kt`

A backdrop is an image or drawing behind the key field, chosen in
**Settings → Theme → Keyboard backdrop** independently of the theme. Keys stay
opaque, so the backdrop shows only through the gutters between keys and plates.
That keeps it decorative: it can never cost legibility. This is the pattern the
Sci-Fi Keyboard app uses (its Background and Animation galleries put a
full-bleed image or loop behind opaque LCARS keys); see
[LCARS assets](lcars-assets.md) for the study.

## Where it draws

`KeyboardBackdrop` sits first in the `Box` that holds `TextKeyboardLayout` in
`TextInputLayout.kt`, so the layering from back to front is:

1. `window` and `keyboard-chassis` colours from the theme
2. the backdrop, at the chosen strength (alpha)
3. the incognito indicator, then the keys, plates and ornaments

The smartbar paints its own background and covers the backdrop above the keys.
The backdrop has no pointer input and never changes layout or touch geometry.
With no backdrop selected it composes nothing, so the keyboard renders exactly
as it did before backdrops existed.

## Sources

The preference `theme__backdrop_id` stores a `BackdropId` string. It is always
parsed through `BackdropId.parse`, which never throws and maps anything unknown
(a removed file, a builtin from a newer build, a path that would escape its
folder) to no backdrop.

| Stored value | Source | Ships in the repo? |
| --- | --- | --- |
| *(blank)* | none: the theme's own background | n/a |
| `builtin:grid`, `builtin:starfield`, `builtin:scanlines` | drawn in code, original line art | yes |
| `asset:<file>` | `assets/backdrops/` packed from the gitignored `private-assets/backdrops/` | **no** |
| `file:<file>` | imported through the picker into `files/backdrops/` | no |

Built-in ids are written to preferences and backups, so they are never renamed
(`BackdropIdTest.builtinIdsAreStable`).

Imports accept PNG, JPEG, WebP and GIF up to 8 MB. The picker copies the file
into app storage under a sanitised, unique name, checks that Android can decode
it, and selects it. A failed import leaves nothing behind.

## Settings

| Row | Preference | Default |
| --- | --- | --- |
| Backdrop | `theme__backdrop_id` | blank (none) |
| Import image… | adds a `file:` backdrop and selects it | |
| Backdrop strength | `theme__backdrop_opacity`, 0-100 | 60 |
| Animate backdrop | `theme__backdrop_animate` | on |

Animated GIF and WebP backdrops play through Coil's animated decoder when
"Animate backdrop" is on, and show their first frame when it is off. Images are
cropped to fill (`ContentScale.Crop`), centred.

## Not there yet

See [LCARS assets](lcars-assets.md) for the plan. In short: a thumbnail
gallery instead of a list, deleting imports, per-profile backdrops (Text vs
Code), letting a theme name a default backdrop, pausing animation while typing
fast, and device validation of animated loops for frame cost and battery.
