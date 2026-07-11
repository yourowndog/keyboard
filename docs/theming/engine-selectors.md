# Snygg Engine and Selectors

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: generated stylesheet schema, `Snygg.kt`, `SnyggRule.kt`,
> `FlorisImeUi.kt`, `TextKeyboardLayout.kt`, and packaged themes

## Stylesheet rules

A stylesheet is a JSON object containing annotation rules such as `@defines`
and element rules such as:

```json
{
  "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
  "@defines": {
    "--surface": "#101018",
    "--text": "#F0F0F0"
  },
  "key": {
    "background": "var(--surface)",
    "foreground": "var(--text)"
  },
  "key[code=-15]:pressed": {
    "background": "#303048"
  }
}
```

The schema permits element names matching the general element grammar. That
does not mean every syntactically valid name is useful: an element affects the
UI only if a composable queries that name. `FlorisImeUi.entries` is the current
IME element inventory.

## Selectors

The parser supports:

- no state
- `:pressed`
- `:focus`
- `:hover`
- `:disabled`

Attributes use bracket syntax. Values may be integers, integer ranges, or
backtick-delimited strings:

```text
key[code=-15]
key[code=-26..-21]
key[shiftstate=`caps_lock`]
key[numberrowstate=`active`]
```

Snygg selectors are not CSS selectors. A rule names one element;
comma-separated element grouping such as `"key, smartbar"` is invalid. Share
values through `@defines` and write one rule per element. Integer attribute
lists and ranges are supported by the Snygg attribute grammar, but validate
unfamiliar forms against `SnyggRule.kt` before relying on an old example.

The key renderer supplies these attributes:

- `code`
- `mode`
- `shiftstate`
- `ctrlstate`: `none`, `active`, or `locked`
- `numberrowstate`: `none` or `active`
- `devrowstate`: `none` or `active`

Attributes are attached to every rendered key. A state attribute does not by
itself make the key use `:focus` or `:pressed`.

## Current toggle behavior

Ctrl, number-row toggle, and developer-row toggle are rendered with the
`:pressed` selector whenever active. The dedicated state attributes are also
present and can be used for more precise rules.

Consequences:

- A general `key:pressed` rule styles both finger-down presses and active
  toggles unless a more specific rule overrides it.
- Attribute rules can distinguish active from inactive keys.
- Ctrl locked versus active is available through `ctrlstate`, even though both
  currently receive the pressed selector.
- Documents claiming that `:focus` alone represents locked Ctrl do not describe
  the complete current renderer.

## Properties

The generated schema is authoritative. Current properties are:

- `background`, `foreground`
- `background-image`, `content-scale`
- `border-color`, `border-width`
- `font-family`, `font-size`, `font-style`, `font-weight`
- `letter-spacing`, `line-height`
- `margin`, `padding`
- `shadow-color`, `shadow-elevation`
- `shape`, `clip`
- `text-align`, `text-decoration-line`, `text-max-lines`, `text-overflow`

`border-style` is declared but has no concrete value encoder and is marked
unsupported in code.

Frequently documented but currently invalid properties include `width`,
`height`, and `opacity`.

Do not assume that a value type exists because an unfinished Kotlin class does.
Legacy documents advertised CSS-like gradients even though the active
parser/schema path does not make them a safe theme value. Use solid or
alpha-channel colors unless the current generated schema proves otherwise.

## Value gotchas

- `clip` uses `yes` or `no`.
- Hex colors accept `#RRGGBB` and `#RRGGBBAA`.
- Four-value shape arguments are ordered top-start, top-end, bottom-end,
  bottom-start.
- Padding uses one, two, or four `dp` values.
- Custom font references and asset URIs have strict Snygg syntax; use the schema.
- Variables must begin with `--` and resolve to a value accepted by the target
  property.

## Key codes in themes

Always confirm numeric codes in `KeyCode.kt`. Do not copy the embedded JSON
table from older Snygg manuals; it conflicts with current values for Escape,
Tab, and OmniBoard additions.
