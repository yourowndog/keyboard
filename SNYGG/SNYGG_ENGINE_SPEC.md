# SNYGG Engine v2 – Theming Specification for FlorisBoard / OmniBoard

> **Audience:** Human themers and system designers  
> **Goal:** Explain *exactly* how the Snygg theming engine works:  
> - What a `.flex` theme package is  
> - How `extension.json` and Snygg stylesheets relate  
> - What elements can be styled  
> - What properties exist and what they do  
> - How selectors, states, and key codes tie layout JSON to visual output

This document is the **source of truth** for creating themes for FlorisBoard/OmniBoard using the Snygg engine. It is intentionally verbose and redundant so that *no* behavior is left to guesswork.

---

## 0. High-Level Overview

The theming stack has three layers:

1. **Layout JSON**  
   - Files like `qwerty_wide.json`, `qwerty_wide_mod.json`.  
   - Declare *what keys exist* (rows, codes, labels, types).

2. **Snygg Stylesheets** (`stylesheets/<theme_id>.json`)  
   - Describe *how each element looks* (colors, shapes, fonts, etc.).  
   - Use selectors like `key`, `key:pressed`, `key[code=-7]`, etc.

3. **Theme Extension Package** (`.flex`)  
   - A **ZIP file with a different extension**.  
   - Root contains `extension.json` + `stylesheets/` folder with all Snygg stylesheets.

At runtime:

- The **layout engine** produces keys with codes, groups, and states.
- The **Snygg engine** evaluates rules in the stylesheet and computes final visual properties.
- The **`.flex` package** is how FlorisBoard discovers and loads themes.

---

## 1. Theme Extension Packages (`.flex`)

### 1.1 What is a `.flex` file?

A `.flex` file is literally:

- A **ZIP archive**, renamed to `.flex`.
- Root-level contents:

```text
<theme-pack>.flex (unzipped)
├── extension.json
└── stylesheets/
    ├── <theme_id_1>.json
    ├── <theme_id_2>.json
    └── ...
```

The keyboard does **not** care about the outer filename; it cares about:

* `extension.json` – the **manifest**.
* Files under `stylesheets/` – the **Snygg stylesheets**, one per theme ID.

### 1.2 `extension.json` – Manifest Schema

Real-world example (from `lcars.flex`):

```json
{
  "$": "ime.extension.theme",
  "meta": {
    "id": "com.catppuccin.florisboard",
    "version": "2.0.0",
    "title": "Catppuccin",
    "description": "🥀 Soothing pastel theme for FlorisBoard",
    "keywords": [
      "pastel catppuccin mocha latte frappe macchiato"
    ],
    "homepage": "https://github.com/catppuccin/florisboard",
    "issueTracker": "https://github.com/catppuccin/florisboard/issues",
    "maintainers": [
      "skinatro",
      "sgoudham"
    ],
    "license": "MIT"
  },
  "themes": [
    {
      "id": "lcars",
      "label": "LCARS",
      "authors": ["Gemini"],
      "isNight": true
    },
    {
      "id": "catppuccin_latte",
      "label": "Catppuccin Latte",
      "authors": ["skinatro", "sgoudham"]
    },
    {
      "id": "catppuccin_latte_borderless",
      "label": "Catppuccin Latte (Borderless)",
      "authors": ["skinatro", "sgoudham"]
    },
    ...
  ]
}
```

**Fields:**

* `"$": "ime.extension.theme"`

  * Required literal; identifies this as a theme extension manifest.

* `meta`

  * `id` – globally unique package ID (reverse domain).
  * `version` – semantic version string.
  * `title` – package name shown in UI.
  * `description` – human-readable description.
  * `keywords` – optional search terms.
  * `homepage` – optional URL.
  * `issueTracker` – optional URL.
  * `maintainers` – list of strings.
  * `license` – SPDX license identifier text.

* `themes` – **array of theme entries**. Each entry:

  * `id` – **MUST** match a stylesheet filename:

    ```text
    stylesheets/<id>.json
    ```

  * `label` – name shown in the theme picker.

  * `authors` – list of author strings.

  * `isNight` – optional boolean. If `true`, theme is considered dark/night. If omitted, treated as not explicitly night-themed.

> **Important:**
> A `.flex` does *not* need to contain both a day and night theme. It can hold:
>
> * One theme (e.g., `id: "lcars"`)
> * Many themes (e.g., Catppuccin variants)
>   The `isNight` flag is just metadata.

### 1.3 Packaging Workflow (Human)

For reference (human steps):

1. Create a folder:

   ```text
   my_theme/
     ├── extension.json
     └── stylesheets/
         └── my_theme_id.json
   ```

2. Zip **contents** of `my_theme`:

   * On desktop: select `extension.json` + `stylesheets/` → “Compress”.
   * This produces `Archive.zip`.

3. Rename:

   ```text
   Archive.zip → my_theme.flex
   ```

4. Install `my_theme.flex` into FlorisBoard.

Agents only need to **produce the contents**; humans assemble the `.flex`.

---

## 2. Snygg Stylesheets – File Structure

Each theme stylesheet is a JSON file:

```json
{
  "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
  "@defines": {
    "--primary": "#cba6f7",
    "--secondary": "#b4befe",
    "--background": "#11111b",
    "--surface": "#1e1e2e",
    "--surface-variant": "#313244",
    "--disabled": "#313244",
    "--popup-surface": "#45475a",
    "--focused-popup-surface": "#6c7086",
    "--drag-marker": "#b4befe",
    "--spacer-color": "rgba(205, 214, 244, 0.3)",
    "--one-hand-background": "#9399b2",
    "--one-hand-foreground": "#cba6f7",
    "--incognito-icon-color": "#cdd6f411",
    "--on-primary": "#181825",
    "--on-background": "#bac2de",
    "--on-background-disabled": "#bac2de48",
    "--on-surface": "#cdd6f4",
    "--on-surface-variant": "#a6adc8",
    "--shape": "rounded-corner(8dp, 8dp, 8dp, 8dp)",
    "--shape-variant": "rounded-corner(12dp, 12dp, 12dp, 12dp)"
  },

  "window": { ... },
  "key": { ... },
  "key:pressed": { ... },
  "key[code=10]": { ... },
  "key[code=10]:pressed": { ... },
  "key-popup-box": { ... },
  "key-popup-element:focus": { ... },
  "smartbar": { ... },
  ...
}
```

### 2.1 Top-Level Keys

* `"$": "$schema"` – always:

  ```json
  "https://schemas.florisboard.org/snygg/v2/stylesheet"
  ```

* `"@defines"` – **variable definitions** (theme palette, shapes, etc.).

* All other keys are **selectors** (`"key"`, `"key[code=10]"`, `"smartbar"`, etc.), each mapping to a dictionary of **properties**.

### 2.2 Defines vs Properties

* Keys in `"@defines"` starting with `--` are **variables**, not visual properties.

* Within rules, you reference them via:

  ```json
  "background": "var(--surface)"
  ```

* This allows changing the entire theme by editing a small set of variables.

---

## 3. Targets – What You Can Style

These are the main elements Snygg exposes (as seen in shipped themes):

### 3.1 Global / Keyboard Elements

* `window`
  The full background behind the keyboard area.

* `keyboard`
  The keyboard container. (In some themes, only `window` is used.)

* `key`
  **The main key surface**: letters, numbers, symbols, function keys.

* `key:pressed`
  The same key when pressed (finger down).

### 3.2 Popups & Hints

* `key-hint`
  Small hint text (e.g., long-press hints) in the corner of keys.

* `key-popup-box`
  The bubble container that appears on long-press.

* `key-popup-element`
  Individual key inside the popup.

* `key-popup-element:focus`
  The popup key currently “focused” or hovered/selected.

* `key-popup-extended-indicator`
  The “…” or visual marker that indicates more options are available.

### 3.3 Smartbar (Suggestion & Action Row)

* `smartbar`
  Overall smartbar background.

* `smartbar-shared-actions-toggle`
  The toggle arrow/button to expand actions.

* `smartbar-extended-actions-toggle`
  Additional toggle for extended actions.

* `smartbar-action-key`
  Single action buttons (undo, redo, clipboard, settings, etc.).

* `smartbar-action-tile`
  Larger action tiles/panels in extended menus.

* `smartbar-candidate-word`
  Predicted/autocomplete words.

* `smartbar-candidate-clip`
  Clipboard suggestions as chips.

* `smartbar-candidate-spacer`
  Spacer elements between candidates.

* `inline-autofill-chip`
  OS-provided autofill suggestion chips.

### 3.4 Clipboard UI

* `clipboard-header`
  Header of the clipboard overlay.

* `clipboard-content`
  Scrollable area containing clips.

* `clipboard-item`
  One clipboard entry.

* `clipboard-item-popup`
  Popup/long-press menu for a clipboard entry.

* `clipboard-clear-all-dialog`
  Confirmation dialog surface.

### 3.5 Emoji / Media

* `media-emoji-key`
  Emoji key tiles.

* `media-emoji-tab`
  Category icons (smileys, animals, etc.).

* `media-emoji-subheader`
  Section headers like “Smileys & People”.

### 3.6 System Indicators & Special Panels

* `incognito-mode-indicator`
  Visual indicator when incognito mode is active.

* `extracted-landscape-input-field`
  The dedicated text field in extracted/landscape mode.

* `one-handed-panel`
  Panel visible when in one-handed mode (compact layout).

* `glide-trail`
  The swiping trail line for glide typing.

> If a selector is not mentioned here but appears in a shipped stylesheet, you can treat it as a valid element. When in doubt, follow the existing example (see Document 3).

---

## 4. Properties – What You Can Change

These are the primary Snygg properties used in real themes.

### 4.1 Color and Appearance

* `background`

  * Type: color string or variable
  * Examples:

    * `"#1e1e2e"`
    * `"rgba(205, 214, 244, 0.3)"`
    * `"var(--surface)"`

* `foreground`

  * Color of text/icons on that element.

* `border-color`

  * Color of border if defined.

* `border-width`

  * Thickness of border (e.g., `"1dp"`, `"2dp"`).

* `shadow-elevation`

  * “Depth” of Material-style elevation.
  * Example: `"2dp"`.

* `opacity` *(supported in engine, sometimes used via RGBA colors)*

  * Transparency 0.0–1.0. Often handled implicitly by using RGBA in `background`/`foreground`.

* `clip`

  * Whether content is clipped to shape (e.g., `"true"` / `"false"` as strings in some themes).

### 4.2 Shape and Geometry

* `shape`

  * Describes the outline geometry.

  * Common patterns:

    * `"rectangle()"`
    * `"circle()"`
    * `"rounded-corner(4dp)"`
    * `"rounded-corner(8dp, 8dp, 8dp, 8dp)"`
    * `"rounded-corner(12dp, 12dp, 12dp, 12dp)"`
    * `"cut-corner(4dp)"`

  * Often set via defines:

    ```json
    "@defines": {
      "--shape": "rounded-corner(8dp, 8dp, 8dp, 8dp)"
    },
    "key": {
      "shape": "var(--shape)"
    }
    ```

* `margin`

  * Outer spacing around the element.
  * Can be one or multiple values:

    * `"4dp"`
    * `"2dp 4dp"` (vertical horizontal)
    * `"2dp 4dp 2dp 4dp"` (top right bottom left)

* `padding`

  * Inner spacing between element borders and content.

* `width`, `height` *(supported, rarely used on keys)*

  * Can force dimension for panels, chips, etc.

### 4.3 Typography

* `font-size`

  * Size of text.
  * Example: `"22sp"`.

* `font-family`

  * Examples: `"sans-serif"`, `"serif"`, `"monospace"`, or custom.

* `font-weight`

  * Either named or numeric:

    * `"normal"`, `"bold"`
    * `"400"`, `"700"`, etc.

* `font-style` *(supported in engine)*

  * `"normal"`, `"italic"`.

* `text-align`

  * `"start"`, `"center"`, `"end"`.

* `text-max-lines`

  * Limits text to a number of lines (e.g., `"1"`).

* `text-overflow`

  * `"clip"` – cut off overflow.
  * `"ellipsis"` – show “…” when truncated.

### 4.4 Special Theming Variables

These appear as `@defines` but are conceptually “special levers”:

* `--incognito-icon-color`
* `--glide-trail-color` *(can exist as a define or inferred from other colors)*
* `--drag-marker` (used in selection/drag UI)
* `--one-hand-background` / `--one-hand-foreground`
* `--popup-surface` / `--focused-popup-surface`
* `--spacer-color`
* `--shape`, `--shape-variant`
* `--pill-shape` (in some themes)

Define these once, then reference via `var(--name)`.

---

## 5. Selectors, States, and Conditions

### 5.1 Basic Selector Forms

The general pattern is:

```text
<element>
<element>:<state>
<element>[attribute=value]
<element>[attribute=value]:<state>
<element>[attribute=value1,value2,...]
```

Common cases:

* `key`
* `key:pressed`
* `key[code=10]` (Enter)
* `key[code=10]:pressed`
* `key[code=-201,-202,-203]` (multiple codes share rule)
* `key[code=-11][shiftstate=\`caps_lock`]`

### 5.2 States (Pseudo-Classes)

* `:pressed` – finger is down on the key.
* `:focus` – element is focused (e.g. controller/TV focus or popup selection).
* `:disabled` – control is disabled/unavailable.

Each state can be combined with attributes:

```json
"key[code=10]:pressed": {
  "background": "var(--primary)",
  "foreground": "var(--on-primary)"
}
```

### 5.3 Attribute Filters

Attributes come from the **keyboard engine**, based on layout JSON and runtime state.

Key ones relevant for theming:

* `[code=NUMBER]`

  * Directly matches a key whose **code** equals this value.
  * Example:

    * `key[code=-7]` → DELETE/BACKSPACE
    * `key[code=-21]` → ARROW_LEFT

* `[code=a,b,c]` (multi-code)

  * Matches any key where code is in the list.
  * Example:

    * `key[code=-201,-202,-203]` → layout switch keys (characters/symbols1/symbols2).

* `[group="name"]`

  * Matches all keys belonging to a group assigned by the layout engine.
  * Typical groups:

    * `default` – normal characters (letters, digits)
    * `modifier` – Shift, Ctrl, Alt, etc.
    * `navigation` – arrow keys, home, end
    * `enter` – enter/return
    * `space` – spacebar

* `[shiftstate=\`value`]`

  * Matches keys when shift state is in a specific mode:

    * `shifted` – Shift currently held.
    * `caps_lock` – Caps Lock active.

Example:

```json
"key[code=-11][shiftstate=\`caps_lock`]": {
  "background": "var(--primary-variant)"
}
```

---

## 6. Keyboard Layout Integration

The layout files (e.g., `qwerty_wide.json`) specify:

* Rows: arrays of key objects.
* Each key has fields like:

  * `"$"` – layout element type (`"auto_text_key"`, `"text_key"`, `"navigation"`, etc.).
  * `code` – integer key code.
  * `label` – visible text/icon string.
  * `type` – internal type hints (`"modifier"`, `"system_gui"`, `"placeholder"`, `"enter_editing"`…)

Example (`qwerty_wide.json`, first row):

```json
[
  { "$": "navigation", "code": -15, "label": "⎋" },
  { "$": "auto_text_key", "code": 113, "label": "q" },
  { "$": "auto_text_key", "code": 119, "label": "w" },
  ...
]
```

Example (`qwerty_wide_mod.json`):

```json
[
  [
    { "$": "navigation", "code": 0, "type": "placeholder" },
    ...
  ],
  [
    { "$": "text_key", "code": -202, "label": "⚛", "type": "system_gui" },
    ...
  ]
]
```

At runtime the engine attaches:

* A **code** (from `KeyCode.kt` table).
* A **group** (e.g., `default`, `modifier`, `navigation`) based on type and usage.
* A **state** (`pressed`, `focus`, etc.).

The Snygg engine then resolves selectors like:

* `key` – applies to **all** keys (fallback).
* `key[group="navigation"]` – applies to all nav keys.
* `key[code=-21]` – applies only to LEFT arrow.
* `key[code=-21]:pressed` – pressed state for LEFT arrow.

---

## 7. Global Key Code Reference (FlorisBoard)

These codes come from `KeyCode.kt` and are used in layout definitions and Snygg selectors.

### 7.1 Standard & Modifiers

| Name                | Code | Description           |
| ------------------- | ---- | --------------------- |
| UNSPECIFIED         | 0    | Default / Null state  |
| ENTER               | 10   | Enter / Return        |
| TAB                 | 9    | Tab key               |
| ESCAPE              | 27   | Escape key            |
| SPACE               | 32   | Standard Space bar    |
| CTRL                | -1   | Control Key           |
| CTRL_LOCK           | -2   | Control Lock          |
| ALT                 | -3   | Alt Key               |
| ALT_LOCK            | -4   | Alt Lock              |
| FN                  | -5   | Function Key          |
| FN_LOCK             | -6   | Function Lock         |
| DELETE              | -7   | Backspace             |
| DELETE_WORD         | -8   | Delete previous word  |
| FORWARD_DELETE      | -9   | Delete forward (Del)  |
| FORWARD_DELETE_WORD | -10  | Delete next word      |
| SHIFT               | -11  | Shift (Caps)          |
| CAPS_LOCK           | -13  | Caps Lock (Permanent) |

### 7.2 Navigation & Cursor Movement

| Name               | Code | Description          |
| ------------------ | ---- | -------------------- |
| ARROW_LEFT         | -21  | Move cursor left     |
| ARROW_RIGHT        | -22  | Move cursor right    |
| ARROW_UP           | -23  | Move cursor up       |
| ARROW_DOWN         | -24  | Move cursor down     |
| MOVE_START_OF_PAGE | -25  | Home / Start of Page |
| MOVE_END_OF_PAGE   | -26  | End / End of Page    |
| MOVE_START_OF_LINE | -27  | Start of Line        |
| MOVE_END_OF_LINE   | -28  | End of Line          |

### 7.3 Clipboard Operations

| Name                         | Code | Description             |
| ---------------------------- | ---- | ----------------------- |
| CLIPBOARD_COPY               | -31  | Copy selection          |
| CLIPBOARD_CUT                | -32  | Cut selection           |
| CLIPBOARD_PASTE              | -33  | Paste from clipboard    |
| CLIPBOARD_SELECT             | -34  | Begin selection mode    |
| CLIPBOARD_SELECT_ALL         | -35  | Select All text         |
| CLIPBOARD_CLEAR_HISTORY      | -36  | Clear clipboard history |
| CLIPBOARD_CLEAR_FULL_HISTORY | -37  | Clear everything (Full) |
| CLIPBOARD_CLEAR_PRIMARY_CLIP | -38  | Clear primary clip only |

### 7.4 Layout & View Switching

| Name                    | Code | Description                |
| ----------------------- | ---- | -------------------------- |
| VIEW_CHARACTERS         | -201 | Switch to Alphabet layout  |
| VIEW_SYMBOLS            | -202 | Switch to Symbols 1 layout |
| VIEW_SYMBOLS2           | -203 | Switch to Symbols 2 layout |
| VIEW_NUMERIC            | -204 | Switch to Numeric layout   |
| VIEW_NUMERIC_ADVANCED   | -205 | Switch to Adv. Numeric     |
| VIEW_PHONE              | -206 | Switch to Phone Pad        |
| VIEW_PHONE2             | -207 | Switch to Phone Pad 2      |
| TOGGLE_COMPACT_LAYOUT   | -110 | Toggle One-Handed Mode     |
| COMPACT_LAYOUT_TO_LEFT  | -111 | Dock One-Handed Left       |
| COMPACT_LAYOUT_TO_RIGHT | -112 | Dock One-Handed Right      |
| SPLIT_LAYOUT            | -113 | Split Keyboard             |
| MERGE_LAYOUT            | -114 | Merge/Full Keyboard        |

### 7.5 System & IME Actions

| Name                       | Code | Description                  |
| --------------------------- | ---- | ---------------------------- |
| SETTINGS                   | -301 | Open FlorisBoard Settings    |
| UNDO                       | -131 | Undo last action             |
| REDO                       | -132 | Redo action                  |
| VOICE_INPUT                | -233 | Trigger Voice/Dictation      |
| IME_SHOW_UI                | -231 | Force show keyboard          |
| IME_HIDE_UI                | -232 | Hide keyboard                |
| SYSTEM_INPUT_METHOD_PICKER | -221 | Show Android Keyboard Picker |
| SYSTEM_PREV_INPUT_METHOD   | -222 | Switch to Previous IME       |
| SYSTEM_NEXT_INPUT_METHOD   | -223 | Switch to Next IME           |
| IME_SUBTYPE_PICKER         | -224 | Switch Language Subtype      |
| IME_PREV_SUBTYPE           | -225 | Previous Language            |
| IME_NEXT_SUBTYPE           | -226 | Next Language                |
| LANGUAGE_SWITCH            | -227 | Cycle Languages (Globe key)  |

### 7.6 Smartbar & Toggles

| Name                       | Code | Description                 |
| --------------------------- | ---- | --------------------------- |
| TOGGLE_SMARTBAR_VISIBILITY | -241 | Show/Hide Smartbar          |
| TOGGLE_ACTIONS_OVERFLOW    | -242 | Toggle Actions Menu         |
| TOGGLE_ACTIONS_EDITOR      | -243 | Toggle Clipboard/Cursor Row |
| TOGGLE_INCOGNITO_MODE      | -244 | Toggle Incognito            |
| TOGGLE_AUTOCORRECT         | -245 | Toggle Autocorrect          |

### 7.7 Special Characters & Markers

| Name                 | Code | Description                       |
| -------------------- | ---- | --------------------------------- |
| NOOP                 | -999 | No Operation (Spacer/Placeholder) |
| DRAG_MARKER          | -991 | Used internally for drag UI       |
| MULTIPLE_CODE_POINTS | -902 | Marker for multi-char keys        |
| PHONE_PAUSE          | 44   | Phone Pause (`,`)                 |
| PHONE_WAIT           | 59   | Phone Wait (`;`)                  |
| URI_COMPONENT_TLD    | -255 | `.com` / TLD key                  |
| CURRENCY_SLOT_1      | -801 | Currency Slot 1                   |
| CURRENCY_SLOT_2      | -802 | Currency Slot 2                   |
| CURRENCY_SLOT_3      | -803 | Currency Slot 3                   |
| CURRENCY_SLOT_4      | -804 | Currency Slot 4                   |
| CURRENCY_SLOT_5      | -805 | Currency Slot 5                   |
| CURRENCY_SLOT_6      | -806 | Currency Slot 6                   |

### 7.8 Asian / Width Specific

| Name                | Code  | Description                 |
| ------------------- | ----- | --------------------------- |
| CHAR_WIDTH_SWITCHER | -9701 | Switch Character Width      |
| CHAR_WIDTH_FULL     | -9702 | Full Width Characters       |
| CHAR_WIDTH_HALF     | -9703 | Half Width Characters       |
| KANA_SWITCHER       | -9710 | Kana Switcher               |
| KANA_HIRA           | -9711 | Hiragana                    |
| KANA_KATA           | -9712 | Katakana                    |
| KANA_HALF_KATA      | -9713 | Half-width Katakana         |
| KANA_SMALL          | 12307 | Small Kana Marker           |
| CJK_SPACE           | 12288 | CJK (Wide) Space            |
| HALF_SPACE          | 8204  | ZWNJ / Half Space           |
| KESHIDA             | 1600  | Keshida (Arabic elongation) |

---

## 8. Selector Specificity & Precedence

When multiple rules apply to the same key, the Snygg engine resolves them by **specificity**, similar to CSS:

1. **Code-specific selectors** (most specific)

   * `key[code=-7]`
   * `key[code=-7]:pressed`

2. **Group selectors**

   * `key[group="navigation"]`
   * `key[group="modifier"]`

3. **Element selectors**

   * `key`
   * `key:pressed`

4. **Global / default behavior** (if no rule is defined)

**Rule:**

> If a key matches both `key[group="navigation"]` and `key[code=-21]`, the properties defined under `key[code=-21]` **override** the group-level ones.

This is crucial for designing:

* A default style (all keys)
* A group style (all navigation keys)
* A single unique style (just Escape or just the Left arrow)

---

## 9. Putting It Together – Example Mapping

Suppose your layout defines:

```json
{ "$": "navigation", "code": -21, "label": "←" }
```

Runtime:

* Element: `key`
* Code: `-21` (ARROW_LEFT)
* Group: `"navigation"` (assigned by engine)
* States: `pressed`, `focus`, etc.

Relevant selectors:

* `key` – applies to all keys.
* `key[group="navigation"]` – applies to all nav keys (left, right, up, down, home, end).
* `key[code=-21]` – applies only to the left arrow.
* `key[code=-21]:pressed` – pressed state for left arrow.

By setting rules at each layer, you can control:

* Global look for all keys (`key`).
* Distinct visual treatment for navigation cluster (`key[group="navigation"]`).
* A unique style for just one special navigation key (`key[code=-21]`).

```