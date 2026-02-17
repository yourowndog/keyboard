---
description: How to create and edit Snygg themes for OmniBoard/FlorisBoard
---

# SNYGG Theming Skill

Use this skill when editing or creating `.flex` theme packages.

## Quick Reference

**Package structure:**
```
my_theme.flex (ZIP renamed)
├── extension.json
└── stylesheets/
    └── <theme_id>.json
```

**Stylesheet structure:**
```json
{
  "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
  "@defines": { "--primary": "#cba6f7" },
  "key": { "background": "var(--primary)" }
}
```

## Valid Elements (most common)

`window`, `key`, `key:pressed`, `key-hint`, `key-popup-box`, `key-popup-element:focus`, `smartbar`, `smartbar-candidate-word`, `smartbar-action-key`, `clipboard-item`, `media-emoji-key`, `one-handed-panel`, `glide-trail`

## Valid Properties

| Property | Values |
|----------|--------|
| `background` | `#RRGGBB`, `rgba()`, `var(--name)`, `transparent` |
| `foreground` | Same as background |
| `shape` | `rectangle()`, `circle()`, `rounded-corner(tl,tr,br,bl)`, `cut-corner(tl,tr,br,bl)` |
| `font-size` | `"22sp"`, `"18sp"` |
| `font-family` | `"sans-serif"`, `"monospace"` |
| `font-weight` | `"normal"`, `"bold"` |
| `text-max-lines` | `"1"`, `"2"` |
| `text-overflow` | `"clip"`, `"ellipsis"` |
| `shadow-elevation` | `"0dp"`, `"2dp"` |
| `margin` / `padding` | `"4dp"`, `"2dp 4dp"` |

## Valid States

`:pressed`, `:focus`, `:disabled`

## Attribute Filters

- `key[code=10]` – ENTER key
- `key[code=-7]` – Backspace
- `key[code=-306]` – Dev row toggle
- `key[code=-11][shiftstate=`caps_lock`]` – Shift when caps is on

## DO NOT

- ❌ Invent selector names not in the reference
- ❌ Use CSS properties like `padding-left`
- ❌ Add `//` comments in JSON
- ❌ Use `:hover` or `:active` states

## Full Reference

See `/home/sam/projects/keyboard/SNYGG/SNYGG_REFERENCE.md` for complete docs.

## Workflow: Edit Theme

1. Unzip `.flex` to temp dir
2. Edit `stylesheets/<id>.json`
3. Repack: `cd <dir> && zip -r ../theme.flex .`
4. Install on device
