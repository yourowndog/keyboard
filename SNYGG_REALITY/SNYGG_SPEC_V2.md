# SNYGG Engine V2 Specification

> **Status:** CANONICAL / GROUND TRUTH
> **Based on:** `stylesheet.schema.json` and `SnyggRule.kt` (Source Code)
> **Correction Date:** 2025-12-05

This document describes the **actual** behavior of the Snygg styling engine used in FlorisBoard. It supersedes all previous guides.

---

## 1. The Rule Structure

A Snygg stylesheet is a JSON object. Keys are **Selectors**, and values are **Property Sets**.

### 1.1 Selector Syntax
Snygg uses a strict, single-element regex for selectors. **It is NOT CSS.**

**Syntax:** `element[attributes]:state`

| Component | Required? | Description |
| :--- | :--- | :--- |
| **Element** | **Yes** | The base UI component name (e.g., `key`, `smartbar`, `window`). |
| **Attributes** | No | Brackets `[...]` filtering the element. |
| **State** | No | Colon `:` followed by interaction state (e.g., `:pressed`). |

### 1.2 CRITICAL: Grouping Rules
**❌ DO NOT use comma-separated selectors:**
```json
"key[code=10], key[code=32]": { ... } // INVALID - Will be ignored or crash
```

**✅ DO use comma-separated attribute values:**
```json
"key[code=10,32]": { ... } // VALID - Matches code 10 OR 32
```

**✅ DO use ranges for integer attributes:**
```json
"key[code=97..122]": { ... } // VALID - Matches all lowercase a-z
```

---

## 2. Selectors & Attributes

### 2.1 Valid Elements
Derived from usage and schema:
- `window` (Background behind everything)
- `keyboard` (Container for keys)
- `key` (The actual key buttons)
- `key-hint` (Small labels on keys)
- `key-popup-box` (Long-press container)
- `key-popup-element` (Items inside long-press)
- `smartbar` (Top suggestion bar)
- `smartbar-key` (Action buttons in smartbar)
- `smartbar-candidate-word` (Suggestions)
- `media-emoji-key` (Emoji grid items)
- `one-handed-panel`
- `glide-trail`

### 2.2 Attributes
Attributes are key-value pairs inside `[...]`.
- **`code`**: The internal integer key code (see Reference).
- **`group`**: The layout definition group (e.g., `default`, `modifier`, `enter`).
- **`shiftstate`**: Current shift status (`shifted`, `caps_lock`).

### 2.3 States
- `:pressed`
- `:focus`
- `:hover`
- `:disabled`

---

## 3. Style Properties

These are the **only** properties strictly defined in `stylesheet.schema.json`. **Do not** use properties like `width` or `height` on keys (layout handles that).

### 3.1 Colors & Backgrounds
| Property | Value Type | Description |
| :--- | :--- | :--- |
| `background` | `Color` | Fill color. |
| `foreground` | `Color` | Text/Icon color. |
| `background-image` | `URI` | `url("...")` (Rarely used). |
| `shadow-color` | `Color` | Color of the shadow. |
| `shadow-elevation` | `Dp` | Size of shadow (e.g., `2dp`). |
| `border-color` | `Color` | Border stroke color. |

### 3.2 Geometry & Borders
| Property | Value Type | Description |
| :--- | :--- | :--- |
| `shape` | `Shape` | The outline shape (rounded, cut, etc.). |
| `border-width` | `Dp` | Thickness of border (e.g., `1dp`). |
| `border-style` | `String` | Solid/Dashed (Implementation dependent). |
| `clip` | `yes/no` | Clip content to shape. |

### 3.3 Spacing
| Property | Value Type | Description |
| :--- | :--- | :--- |
| `padding` | `DpList` | Inner spacing. Order: `Start Top End Bottom`. |
| `margin` | `DpList` | Outer spacing. Order: `Start Top End Bottom`. |

> **Note on Order:** Unlike CSS (Top/Right/Bottom/Left), Snygg uses `Start/Top/End/Bottom` to support RTL layouts.

### 3.4 Typography
| Property | Value Type | Description |
| :--- | :--- | :--- |
| `font-family` | `String` | `sans-serif`, `serif`, `monospace`, or custom. |
| `font-size` | `Sp` | Text size (e.g., `18sp`). |
| `font-style` | `String` | `normal` or `italic`. |
| `font-weight` | `String` | `normal`, `bold`, `100`..`900`. |
| `letter-spacing` | `Sp` | Spacing between characters. |
| `text-align` | `Enum` | `start`, `end`, `center`, `justify`. |
| `text-max-lines` | `Int` | Max lines before truncation. |

---

## 4. Value Types

### 4.1 Colors
*   **Hex:** `#RRGGBB` or `#RRGGBBAA`
*   **RGBA:** `rgba(255, 0, 0, 1.0)`
*   **Variables:** `var(--my-color)`
*   **Transparent:** `transparent`
*   **System (Material You):** `dynamic-light-color(primary)`, `dynamic-dark-color(surface)`

### 4.2 Shapes
*   `rectangle()`
*   `circle()`
*   `rounded-corner(4dp)` (All corners)
*   `rounded-corner(4dp, 4dp, 4dp, 4dp)` (TL, TR, BR, BL - *Wait, check code*)
    *   **Correction:** Code uses `TopStart, TopEnd, BottomEnd, BottomStart`.
*   `cut-corner(4dp)`
*   **Percentages:** `rounded-corner(50%)` is valid.

### 4.3 Dimensions
*   **Dp:** `4dp`, `0.5dp` (Density-independent pixels)
*   **Sp:** `14sp` (Scale-independent pixels for text)

---

## 5. Specificity Logic

When multiple rules match a key, specificity determines the winner. The comparison order in `SnyggRule.kt` is:

1.  **Element Name:** Alphabetical sort (Only useful if elements differ, which they usually don't for the same object).
2.  **Selector (State):** Presence of state (`:pressed`) > No state.
3.  **Attributes:**
    *   Count of attributes.
    *   Then specific attribute key comparison.
    *   Then value size comparison.

**Practical Rule of Thumb:**
`key[code=10]:pressed` > `key[code=10]` > `key:pressed` > `key`

**Warning:** `key[group=enter]` vs `key[code=10]`. Both have 1 attribute. Specificity might depend on alphabetical order of `code` vs `group`. **Always use explicit overrides if unsure.**

