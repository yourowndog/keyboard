# Key skins

> Status: Canonical
> Last verified: 2026-11-12 against `TextKeyboardLayout.kt`, `KeyRole.kt`, `ime/theme/keyskin/`

There are two ways to skin keys. **Skin packs** (below, first) are picked from a
gallery in Settings → Theme → Key skin and sit on top of whatever theme is
active; they are how the Sci-Fi Keyboard's textures reach OmniBoard. **Theme
extensions** (the contract further down) are a full Snygg theme that assigns
`background-image` per slot.

## Skin packs (Settings → Theme → Key skin)

A pack is a folder of textures under `assets/keyskins/<pack>/`, one file per
slot, named `<slot>@<dpi>[.9].png`:

| Slot file stem | Drawn on |
| --- | --- |
| `key`, `key_pressed` | every other key (letters, digits, symbols, Ctrl, `?123`...) |
| `space`, `enter`, `delete` | those keys |
| `shift`, `shift_on` | shift and caps lock, unshifted / shifted |
| `arrow_left`, `arrow_right`, `arrow_up`, `arrow_down` | the cursor keys |
| `popup` | reserved for key popups (not drawn yet) |

- `@<dpi>` is the density the texture was drawn for (640 = xxxhdpi); the
  keyboard scales it to the screen so a bevel keeps its physical size.
- `.9.png` marks a **compiled nine-patch** (the `npTc` chunk Android's resource
  compiler writes). Android decodes the chunk from assets, and the corners stay
  fixed while the middle stretches, so one texture fits a narrow key and the
  spacebar. Plain PNGs are stretched.
- A missing slot falls back to `key`. A missing `key_pressed` darkens `key`.
- **Special-key textures carry their own art** (the DELETE grille, the ENTER
  elbow, the shift lamp, `SPACE`/`UP` lettering), so the keyboard draws no
  label or icon on space, enter, delete, shift and the arrows when a pack
  supplies them. Character keys keep their labels and corner hints.
- A skinned key is one surface: the LCARS hint plate split and the key
  ornaments (delete grille, space dot run) give way to the texture, and hints
  move to the corner (if corner hints are on).

**Swatches** (`assets/keyskins/swatches/<name>@<dpi>.png`) are single textures,
like the Sci-Fi Keyboard's Keys tab. A swatch skins only character keys
(letters, digits, symbols); every other key keeps the theme's own surface, so
special keys stay distinct from the field of letters.

The choice is stored in `prefs.theme.keySkinId` (`""`, `pack:<folder>`,
`swatch:<file>`), parsed by `KeySkinId.parse`, which reads anything unknown or
unsafe as "no skin". Code: `ime/theme/keyskin/` (ids, slot mapping, decoding,
drawing) and `app/settings/theme/KeySkinGallery.kt` (the gallery draws each
pack as a miniature keyboard). The texture replaces the key's Snygg shadow,
border and background through `SnyggBox(backgroundOverride = ...)`.

The repository ships no packs. Sam's personal builds stage the Sci-Fi
Keyboard's 20 packs (`sfk-01`..`sfk-20`) and 40 swatches through
`private-assets/keyskins/`; see [LCARS assets](lcars-assets.md). Packs 02-04 are
LCARS: 02 and 03 are labelled pills, and 04 is the control-panel set (shift
lamp, DELETE grille, ENTER elbow, spacebar readout) that matches the
[reference](reference/) anatomy.

## Theme-extension skins

A key skin swaps the *texture* of every key while the keyboard keeps its
geometry and always draws each key's labels. Skins are designed against a
fixed inventory of slots, so an artist paints "the enter key" or "a letter cap
with its plate" once and every layout reuses it. The model is the one
consumer sci-fi keyboards use: one fixed template, many interchangeable
textures, and the character and its hint always rendered by the keyboard
rather than baked into the art.

## The contract

A skin is a Snygg theme extension (a `.flex` archive imported through
Settings → Theme). Its stylesheet assigns a `background-image` to each slot and
ships the images inside the extension. Images resolve through `flex:/` paths
relative to the extension, so nothing private ever enters the APK or this
repository.

| Slot | Selector | What it covers |
| --- | --- | --- |
| Letter cap | `key[role=`alpha`]` | the lower, larger part of a stacked letter key, or the whole key on unplated layouts |
| Hint plate | `key-plate` | the upper tab holding the number or symbol; only drawn when the theme gives `key-plate` a background |
| Number cap | `key[role=`number`]` | digits on the number row and numeric layouts |
| Symbol cap | `key[role=`symbol`]` | punctuation and symbols |
| Space | `key[role=`space`]` | the spacebar, which is much wider than a cap |
| Enter | `key[role=`enter`]` | |
| Delete | `key[role=`delete`]` | backspace and the word or forward deletes |
| Shift / caps lock | `key[role=`shift`]`, `key[role=`capslock`]` | |
| Coding cluster | `key[role=`modifier`]` | Ctrl, Alt, Fn, Tab, Esc, tmux prefix, arrows and line motions |
| Layout switch | `key[role=`layout`]` | `?123`, `ABC` and the other view switches |
| Profile | `key[role=`profile`]` | the Text/Code toggle and the language key |
| Voice, clipboard, command | `key[role=`voice`]`, `key[role=`clipboard`]`, `key[role=`command`]` | |

Roles come from [`KeyRole`](../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/key/KeyRole.kt).
The ids are written into stylesheets, so they are never renamed. The same
classification picks key sounds (see [sound](../architecture/sound.md)), so a
key's texture and its click always agree on what the key is.

Every slot also accepts the `:pressed` selector and the state attributes from
[engine selectors](engine-selectors.md) (`shiftstate`, `ctrlstate`, and so on),
which is how a skin gives Ctrl-locked or caps-on its own texture.

## Rules for skin art

- The keyboard draws the labels. Never bake a letter or a symbol into a
  texture: layouts, shift state and the Text/Code profiles all change them.
- Leave the label zones calm. On a plated letter key the plate is the top 39%
  (`KEY_PLATE_WEIGHT`) and the cap the rest; the glyph sits centred in each.
- Keys vary in width (space, enter, the coding cluster) and height (the Text
  and Code profiles size rows differently). Pick `content-scale` per slot:
  `fill-bounds` for textures that stretch cleanly, `crop` for patterns.
- The key's `shape` clips the image, so silhouette (rounded, chamfered,
  stadium) stays a stylesheet decision rather than part of the bitmap.
- Use PNG with alpha. Images are decoded once and cached; keep each one near
  the key's on-screen size (about 140×170 px for a letter cap on icarion).

## What is not there yet

- No theme-extension skin ships. The LCARS Jefferies theme is flat colour and
  draws its ornaments in code.
- Skin packs do not yet tint label colours to suit the texture: labels use the
  theme's key foreground, which can be low-contrast on a light texture.
- The `popup` slot is extracted but not drawn; popups keep the theme's pill.
- Theme-extension images only scale; they do not stretch by region. Skin packs
  (above) do support compiled nine-patches; a Snygg slice property would bring
  the same to theme extensions. Until then, give wide slots (space, enter, the
  coding cluster) their own images drawn at their real proportions.
