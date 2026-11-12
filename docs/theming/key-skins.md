# Key skins

> Status: Canonical (stub: the mechanism exists, no skin ships yet)
> Last verified: 2026-11-12 against `TextKeyboardLayout.kt`, `KeyRole.kt`

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

- No skin ships. The LCARS Jefferies theme is flat colour and draws its
  ornaments in code.
- There is no picker that previews skins as uniform swatches; a skin is
  chosen like any other theme.
- Mixing a skin with a separate colour theme is not supported: a skin is a
  complete theme.
