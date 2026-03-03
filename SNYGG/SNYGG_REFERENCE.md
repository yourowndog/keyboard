# SNYGG Theming Reference

> **The Authoritative Guide** – Derived from working themes and engine source code.

This document is the single source of truth for Snygg theming. Use it instead of the older docs (`SNYGG_AGENT_MANUAL.md`, `SNYGG_ENGINE_SPEC.md`, `SNYGG_EXAMPLE_THEME.md`). Those are deprecated.

---

## Quick Start

A Snygg theme lives inside a `.flex` file (a renamed ZIP) with this structure:

```
my_theme.flex (ZIP archive)
├── extension.json
└── stylesheets/
    └── my_theme_id.json
```

**Packaging:** Zip the contents (not the folder itself), rename `.zip` → `.flex`, install via FlorisBoard.

---

## 1. Manifest: `extension.json`

```json
{
  "$": "ime.extension.theme",
  "meta": {
    "id": "com.yourname.theme",
    "version": "1.0.0",
    "title": "My Theme",
    "description": "Short description",
    "maintainers": ["Your Name"],
    "license": "MIT"
  },
  "themes": [
    {
      "id": "my_theme_id",
      "label": "My Theme",
      "authors": ["Your Name"],
      "isNight": true
    }
  ]
}
```

**Rules:**
- `"$"` must be `"ime.extension.theme"`
- Each `themes[i].id` **must** match a file: `stylesheets/<id>.json`
- `isNight` is optional (true = dark theme)

---

## 2. Stylesheet Structure

```json
{
  "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
  "@defines": {
    "--primary": "#cba6f7",
    "--surface": "#1e1e2e",
    "--on-surface": "#cdd6f4",
    "--shape": "rounded-corner(8dp, 8dp, 8dp, 8dp)"
  },
  "window": { "background": "var(--surface)" },
  "key": { "background": "var(--surface)", "foreground": "var(--on-surface)" },
  "key:pressed": { "background": "var(--primary)" }
}
```

**Rules:**
- `$schema` is required
- `@defines` holds variables (referenced via `var(--name)`)
- All other keys are **selectors**

---

## 3. Valid Element Selectors

These are the **only** valid element names (extracted from working themes):

### Core Keyboard
| Selector | What It Styles |
|----------|----------------|
| `window` | Full keyboard background |
| `key` | All key faces (baseline) |
| `key-hint` | Corner hints (e.g., "1" above "Q") |
| `key-popup-box` | Long-press popup container |
| `key-popup-element` | Individual popup key |
| `key-popup-extended-indicator` | "..." more options indicator |

### Smartbar
| Selector | What It Styles |
|----------|----------------|
| `smartbar` | Smartbar container |
| `smartbar-shared-actions-toggle` | Expand arrow |
| `smartbar-extended-actions-toggle` | Extended toggle |
| `smartbar-action-key` | Action buttons (undo, redo, etc.) |
| `smartbar-action-tile` | Larger action tiles |
| `smartbar-action-tile-icon` | Icon inside tile |
| `smartbar-actions-overflow-customize-button` | Overflow customize button |
| `smartbar-actions-editor` | Editor panel |
| `smartbar-actions-editor-header` | Editor header |
| `smartbar-actions-editor-header-button` | Header buttons |
| `smartbar-actions-editor-subheader` | Sub-header text |
| `smartbar-actions-editor-tile` | Editor tiles |
| `smartbar-actions-editor-tile-grid` | Tile grid container |
| `smartbar-candidate-word` | Suggestion words |
| `smartbar-candidate-clip` | Clipboard suggestion chips |
| `smartbar-candidate-clip-icon` | Icon in clip |
| `smartbar-candidate-spacer` | Divider between candidates |

### Clipboard
| Selector | What It Styles |
|----------|----------------|
| `clipboard-header` | Clipboard header |
| `clipboard-header-button` | Header buttons |
| `clipboard-header-text` | Header text |
| `clipboard-subheader` | Section subheader |
| `clipboard-content` | Scrollable content area |
| `clipboard-item` | Individual clip entry |
| `clipboard-item-popup` | Long-press menu |
| `clipboard-item-popup-action` | Popup action button |
| `clipboard-clear-all-dialog` | Clear confirmation dialog |
| `clipboard-clear-all-dialog-message` | Dialog message |
| `clipboard-clear-all-dialog-buttons` | Button container |
| `clipboard-clear-all-dialog-button` | Individual button |
| `clipboard-history-disabled-title` | Disabled state title |
| `clipboard-history-disabled-message` | Disabled message |
| `clipboard-history-disabled-button` | Enable button |
| `clipboard-history-locked-title` | Locked state title |
| `clipboard-history-locked-message` | Locked message |

### Emoji / Media
| Selector | What It Styles |
|----------|----------------|
| `media-emoji-key` | Emoji key |
| `media-emoji-key-popup-box` | Emoji popup container |
| `media-emoji-key-popup-element` | Popup emoji |
| `media-emoji-key-popup-extended-indicator` | More emojis indicator |
| `media-emoji-tab` | Category tab |
| `media-emoji-subheader` | Section header |
| `media-bottom-row-button` | Bottom row buttons |

### Special Panels
| Selector | What It Styles |
|----------|----------------|
| `one-handed-panel` | One-handed mode panel |
| `subtype-panel` | Language/subtype picker |
| `subtype-panel-header` | Panel header |
| `extracted-landscape-input-layout` | Landscape input container |
| `extracted-landscape-input-field` | Text field |
| `extracted-landscape-input-action` | Action button |
| `glide-trail` | Swipe typing trail |
| `incognito-mode-indicator` | Incognito icon |

---

## 4. States (Pseudo-Classes)

Append to any element selector:

| State | When It Applies |
|-------|-----------------|
| `:pressed` | Finger is down |
| `:focus` | Element is focused (popup selection, controller) |
| `:disabled` | Element is disabled |

**Examples:**
```json
"key:pressed": { "background": "#ff0000" },
"smartbar-action-key:disabled": { "foreground": "#666666" }
```

---

## 5. Attribute Filters

Filter elements by attributes:

### By Key Code
```json
"key[code=10]": { ... },           // ENTER
"key[code=-7]": { ... },           // DELETE
"key[code=-201,-202,-203]": { ... } // Multiple codes
```

### By Shift State
```json
"key[code=-11][shiftstate=`caps_lock`]": { "foreground": "#ff0000" }
```

Valid `shiftstate` values: `shifted`, `caps_lock`

### Combining
```json
"key[code=10]:pressed": { "background": "var(--primary)" }
```

### Available Attributes
| Attribute | Used On | Values |
|-----------|---------|--------|
| `code` | `key`, `smartbar-action-*` | Key codes (-306, 10, etc.) |
| `mode` | Various | Keyboard mode identifiers |
| `shiftstate` | `key` | `shifted`, `caps_lock` |
| `ctrlstate` | `key` | `none`, `active`, `locked` |
| `numberrowstate` | `key` | `none`, `active` |
| `devrowstate` | `key` | `none`, `active` |

---

## 5a. Toggle State Attributes

All toggle-state attributes are wired up in `TextKeyboardLayout.kt` and passed to every key's attribute map. Theme selectors match against these values.

### How It Works
1. State attributes (`shiftstate`, `ctrlstate`, `numberrowstate`, `devrowstate`) are passed when drawing keys
2. Theme selectors like `key[code=-1][ctrlstate=`locked`]` match when the attribute value equals the selector value
3. More specific selectors (with more attributes) take priority over less specific ones

### Ctrl Key (`code=-1`)
- **`ctrlstate=none`** — default, no Ctrl active
- **`ctrlstate=active`** — single-tap sticky Ctrl (clears after next key)
- **`ctrlstate=locked`** — double-tap locked Ctrl (stays until toggled off)

```json
"key[code=-1][ctrlstate=`active`]": {
  "background": "var(--secondary)",
  "foreground": "var(--on-primary)"
},
"key[code=-1][ctrlstate=`locked`]": {
  "background": "var(--primary-variant)",
  "foreground": "var(--secondary)"
}
```

### Toggle Number Row (`code=-305`)
- **`numberrowstate=none`** — number row hidden
- **`numberrowstate=active`** — number row visible

```json
"key[code=-305][numberrowstate=`active`]": {
  "background": "var(--secondary)",
  "foreground": "var(--on-primary)"
}
```

### Toggle Dev Row (`code=-306`)
- **`devrowstate=none`** — dev row hidden
- **`devrowstate=active`** — dev row visible

```json
"key[code=-306][devrowstate=`active`]": {
  "background": "var(--secondary)",
  "foreground": "var(--on-primary)"
}
```

### Source
- Attributes defined in `FlorisImeUi.Attr` (`FlorisImeUi.kt`)
- State wired in `TextKeyButton` (`TextKeyboardLayout.kt`)
- `ctrlstate` reads from `evaluator.state.isCtrlPressed` / `isCtrlLocked`
- `numberrowstate` reads from `prefs.keyboard.numberRow`
- `devrowstate` reads from `prefs.keyboard.devRow`

---

## 6. Properties

### Color & Appearance
| Property | Values | Description |
|----------|--------|-------------|
| `background` | `#RRGGBB`, `#RRGGBBAA`, `rgba(r,g,b,a)`, `var(--name)`, `transparent` | Fill color |
| `foreground` | Same as above | Text/icon color |
| `shadow-elevation` | `"0dp"`, `"2dp"`, etc. | Drop shadow depth |
| `clip` | `"yes"`, `"no"` | Clip children to bounds |

### Shape
| Property | Values | Description |
|----------|--------|-------------|
| `shape` | See shapes below | Element outline |

### Typography
| Property | Values | Description |
|----------|--------|-------------|
| `font-size` | `"18sp"`, `"22sp"` | Text size |
| `font-family` | `"sans-serif"`, `"monospace"`, `"serif"` | Font |
| `font-weight` | `"normal"`, `"bold"`, `"400"`-`"900"` | Weight |
| `font-style` | `"normal"`, `"italic"` | Style |
| `text-align` | `"start"`, `"center"`, `"end"` | Alignment |
| `text-max-lines` | `"1"`, `"2"` | Max lines |
| `text-overflow` | `"clip"`, `"ellipsis"` | Overflow behavior |

### Spacing
| Property | Values | Description |
|----------|--------|-------------|
| `margin` | `"4dp"`, `"2dp 4dp"`, `"2dp 4dp 2dp 4dp"` | Outer spacing |
| `padding` | Same as margin | Inner spacing |

---

## 7. Shape Values

| Shape | Syntax | Example |
|-------|--------|---------|
| Rectangle | `rectangle()` | `"shape": "rectangle()"` |
| Circle | `circle()` | `"shape": "circle()"` |
| Rounded corners | `rounded-corner(tl, tr, br, bl)` | `"shape": "rounded-corner(8dp, 8dp, 8dp, 8dp)"` |
| Cut corners | `cut-corner(tl, tr, br, bl)` | `"shape": "cut-corner(4dp, 4dp, 4dp, 4dp)"` |

**Note:** Corner values can be `dp` units or percentages (`8%`).

---

## 8. Common Variables

Define once in `@defines`, use everywhere:

```json
"@defines": {
  "--primary": "#cba6f7",
  "--primary-variant": "#af77f3",
  "--secondary": "#b4befe",
  "--background": "#11111b",
  "--surface": "#1e1e2e",
  "--surface-variant": "#313244",
  "--popup-surface": "#45475a",
  "--focused-popup-surface": "#6c7086",
  "--on-primary": "#181825",
  "--on-background": "#bac2de",
  "--on-surface": "#cdd6f4",
  "--on-surface-variant": "#a6adc8",
  "--on-background-disabled": "#bac2de48",
  "--shape": "rounded-corner(8dp, 8dp, 8dp, 8dp)",
  "--shape-variant": "rounded-corner(12dp, 12dp, 12dp, 12dp)",
  "--pill-shape": "rounded-corner(24dp, 24dp, 24dp, 24dp)",
  "--spacer-color": "rgba(205, 214, 244, 0.3)",
  "--drag-marker": "#b4befe",
  "--one-hand-background": "#9399b2",
  "--one-hand-foreground": "#cba6f7",
  "--incognito-icon-color": "#cdd6f411"
}
```

---

## 9. Key Codes Reference

### Modifiers & Core
| Code | Name | Description |
|------|------|-------------|
| 0 | UNSPECIFIED | Default/null |
| 10 | ENTER | Enter key |
| 9 | TAB | Tab key |
| 27 | ESCAPE | Escape |
| 32 | SPACE | Spacebar |
| -1 | CTRL | Control |
| -7 | DELETE | Backspace |
| -11 | SHIFT | Shift |
| -13 | CAPS_LOCK | Caps Lock |

### Navigation
| Code | Name | Description |
|------|------|-------------|
| -21 | ARROW_LEFT | ← |
| -22 | ARROW_RIGHT | → |
| -23 | ARROW_UP | ↑ |
| -24 | ARROW_DOWN | ↓ |
| -25 | MOVE_START_OF_PAGE | Home |
| -26 | MOVE_END_OF_PAGE | End |

### Layout Switching
| Code | Name | Description |
|------|------|-------------|
| -201 | VIEW_CHARACTERS | ABC layout |
| -202 | VIEW_SYMBOLS | Symbols 1 |
| -203 | VIEW_SYMBOLS2 | Symbols 2 |
| -204 | VIEW_NUMERIC | Numeric |
| -205 | VIEW_NUMERIC_ADVANCED | Advanced numeric |

### System Actions
| Code | Name | Description |
|------|------|-------------|
| -233 | VOICE_INPUT | Voice/Dictation |
| -301 | SETTINGS | Open settings |
| -305 | TOGGLE_NUMBER_ROW | Toggle number row |
| -306 | TOGGLE_DEV_ROW | Toggle dev row |
| -999 | NOOP | No operation (spacer) |
| -991 | DRAG_MARKER | Drag UI marker |

### Clipboard
| Code | Name |
|------|------|
| -31 | CLIPBOARD_COPY |
| -32 | CLIPBOARD_CUT |
| -33 | CLIPBOARD_PASTE |
| -35 | CLIPBOARD_SELECT_ALL |

---

## 10. Selector Specificity

When multiple rules match, more specific wins:

1. **Code + State:** `key[code=10]:pressed`
2. **Code only:** `key[code=10]`
3. **Element + State:** `key:pressed`
4. **Element only:** `key`

---

## 11. Rules & Gotchas

### DO
- ✅ Use JSON only (no CSS, no XML, no comments)
- ✅ Define variables in `@defines`
- ✅ Reference variables with `var(--name)`
- ✅ Always include `key:pressed` for visual feedback
- ✅ Test with actual keyboard – some selectors are unused

### DON'T
- ❌ Invent selector names not listed here
- ❌ Use CSS properties like `padding-left` (use `padding` shorthand)
- ❌ Add `//` comments (JSON doesn't support them)
- ❌ Use `hover` or `active` states (mobile has no hover)

---

## 12. Minimal Working Theme

```json
{
  "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
  "@defines": {
    "--bg": "#1a1a2e",
    "--key-bg": "#16213e",
    "--key-fg": "#e94560",
    "--accent": "#0f3460",
    "--shape": "rounded-corner(6dp, 6dp, 6dp, 6dp)"
  },
  "window": {
    "background": "var(--bg)"
  },
  "key": {
    "background": "var(--key-bg)",
    "foreground": "var(--key-fg)",
    "shape": "var(--shape)",
    "font-size": "22sp"
  },
  "key:pressed": {
    "background": "var(--accent)"
  },
  "key[code=10]": {
    "background": "var(--key-fg)",
    "foreground": "var(--bg)"
  },
  "key-hint": {
    "foreground": "var(--accent)",
    "font-size": "10sp"
  },
  "key-popup-box": {
    "background": "var(--key-bg)",
    "foreground": "var(--key-fg)",
    "shape": "var(--shape)"
  },
  "smartbar": {
    "background": "var(--bg)"
  },
  "smartbar-candidate-word": {
    "foreground": "var(--key-fg)"
  }
}
```

---

## Deprecated Documents

The following files are **superseded by this document** and should not be used:
- `SNYGG/SNYGG_AGENT_MANUAL.md`
- `SNYGG/SNYGG_ENGINE_SPEC.md`
- `SNYGG/SNYGG_EXAMPLE_THEME.md`
- `SNYGG/KEY_CODES.md`

*Consider archiving or deleting them to avoid confusion.*
