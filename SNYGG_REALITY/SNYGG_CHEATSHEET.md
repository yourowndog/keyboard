# SNYGG Theming Cheatsheet

## Common Selectors

| Target | Selector |
| :--- | :--- |
| **All Keys** | `key` |
| **Pressed Key** | `key:pressed` |
| **Spacebar** | `key[code=32]` |
| **Enter Key** | `key[code=10]` |
| **Backspace** | `key[code=-4]` |
| **Shift Key** | `key[code=-11]` |
| **Shift (Active)** | `key[code=-11][shiftstate=`shifted`]` |
| **Caps Lock (Active)** | `key[code=-11][shiftstate=`caps_lock`]` |
| **Ctrl (Active)** | `key[code=-1][ctrlstate=`active`]` |
| **Ctrl (Locked)** | `key[code=-1][ctrlstate=`locked`]` |
| **Number Row Toggle (On)** | `key[code=-305][numberrowstate=`active`]` |
| **Dev Row Toggle (On)** | `key[code=-306][devrowstate=`active`]` |
| **All Alphabets** | `key[code=97..122]` |
| **Specific Keys** | `key[code=44,46]` (Comma, Period) |
| **Smartbar** | `smartbar` |
| **Suggestions** | `smartbar-candidate-word` |

## Valid Key Codes
*Source: `KeyCode.kt`*

### Standard Characters (Positive)
*   **a - z**: `97` .. `122`
*   **A - Z**: `65` .. `90`
*   **0 - 9**: `48` .. `57`
*   **Enter**: `10`
*   **Space**: `32`
*   **Tab**: `-14`
*   **Escape**: `-15`

### Modifiers & Function Keys (Negative)
*   **Backspace**: `-4` (or `-7`/`-9` for variants)
*   **Delete (Forward)**: `-9`
*   **Shift**: `-11`
*   **Caps Lock**: `-13`
*   **Ctrl**: `-1`
*   **Alt**: `-3`
*   **Meta/GUI**: `-2`
*   **Arrow Left**: `-21`
*   **Arrow Right**: `-22`
*   **Arrow Up**: `-23`
*   **Arrow Down**: `-24`

### Layout Switchers
*   **Char Layout**: `-201`
*   **Symbol Layout**: `-202`
*   **Symbol 2 Layout**: `-203`
*   **Num Layout**: `-204`
*   **Emoticon Layout**: `-231` (Check specific implementation)

### Toggle Keys
*   **Toggle Number Row**: `-305`
*   **Toggle Dev Row**: `-306`

## Common Variable Defines
Place these in `"@defines": { ... }` to reuse colors.

```json
"@defines": {
    "--primary": "#FF0000",
    "--bg": "#121212",
    "--key-shape": "rounded-corner(4dp)"
}
```

Usage:
```json
"key": {
    "background": "var(--bg)",
    "shape": "var(--key-shape)"
}
```

## ⚠️ Common Pitfalls

1.  **NO Comma Selectors:**
    *   ❌ `key[code=10], key[code=32]`
    *   ✅ `key[code=10,32]`

2.  **NO Width/Height:**
    *   Snygg styles *appearance*, not *layout*. Change key size in layout JSON files.

3.  **Padding Order:**
    *   `4dp 2dp 4dp 2dp` means **Start**, Top, **End**, Bottom.

4.  **Range Syntax:**
    *   Use `..` for ranges inside brackets: `key[code=48..57]` (Numbers).
