# SNYGG EXAMPLE THEME – FULLY WORKED REFERENCE  
Example: **Catppuccin Mocha** theme pack from `lcars.flex`

This document is a **worked example** of a real, production Snygg theme:

- It shows the **actual manifest** (`extension.json`) used in the `.flex` pack.
- It shows the **full Snygg stylesheet** for `catppuccin_mocha`.
- It **maps selectors and properties to behavior** on the keyboard:  
  - “This selector = this part of the UI.”  
  - “This property = this visual effect.”  
  - “This key code = this physical key.”

Use this file as:

- A **teaching artifact** for humans.
- A **ground-truth reference** for agents (they can pattern-match against this).

---

## 1. Theme Pack Manifest (`extension.json`)

This is the manifest that lives at the root of the `.flex` archive.  
FlorisBoard uses this file to:

- Identify the extension (`meta.id`, `meta.title`).
- List all themes in the pack (`themes` array).
- Connect each theme entry to a stylesheet via `id`.

> **Important:**  
> `themes[i].id` must match a file in `stylesheets/` named `id + ".json"`.

### 1.1 Raw `extension.json` (copy-pasteable)

```json
{
  "$": "ime.extension.theme",
  "meta": {
    "id": "org.florisboard.themes",
    "version": "0.1.0",
    "title": "Catppuccin",
    "description": "Catppuccin theme pack",
    "keywords": [
      "catppuccin"
    ],
    "homepage": "https://github.com/catppuccin",
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
      "authors": [
        "Gemini"
      ],
      "isNight": true
    },
    {
      "id": "catppuccin_latte",
      "label": "Catppuccin Latte",
      "authors": [
        "skinatro",
        "sgoudham"
      ],
      "isNight": false
    },
    {
      "id": "catppuccin_latte_borderless",
      "label": "Catppuccin Latte (Borderless)",
      "authors": [
        "skinatro",
        "sgoudham"
      ],
      "isNight": false
    },
    {
      "id": "catppuccin_frappe",
      "label": "Catppuccin Frappe",
      "authors": [
        "skinatro",
        "sgoudham"
      ]
    },
    {
      "id": "catppuccin_frappe_borderless",
      "label": "Catppuccin Frappe (Borderless)",
      "authors": [
        "skinatro",
        "sgoudham"
      ]
    },
    {
      "id": "catppuccin_macchiato",
      "label": "Catppuccin Macchiato",
      "authors": [
        "skinatro",
        "sgoudham"
      ]
    },
    {
      "id": "catppuccin_macchiato_borderless",
      "label": "Catppuccin Macchiato (Borderless)",
      "authors": [
        "skinatro",
        "sgoudham"
      ]
    },
    {
      "id": "catppuccin_mocha",
      "label": "Catppuccin Mocha",
      "authors": [
        "skinatro",
        "sgoudham"
      ]
    },
    {
      "id": "catppuccin_mocha_borderless",
      "label": "Catppuccin Mocha (Borderless)",
      "authors": [
        "skinatro",
        "sgoudham"
      ]
    }
  ]
}

1.2 How This Connects to Snygg

For this example:

Theme ID: "catppuccin_mocha"

Stylesheet filename: stylesheets/catppuccin_mocha.json


FlorisBoard will:

1. Load extension.json.


2. See this theme entry:

{
  "id": "catppuccin_mocha",
  "label": "Catppuccin Mocha",
  "authors": ["skinatro", "sgoudham"]
}


3. Then load stylesheets/catppuccin_mocha.json and apply its rules.



From an agent’s perspective:

If you define a theme with id: "my_theme", you must produce a stylesheet file stylesheets/my_theme.json using the Snygg schema.



---

2. Full Snygg Stylesheet – catppuccin_mocha.json

This is the actual theme file for "catppuccin_mocha" extracted from the .flex file and pretty-printed for readability.

2.1 Raw Stylesheet (copy-pasteable)

{
  "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
  "@defines": {
    "--primary": "#cba6f7",
    "--secondary": "#b4befe",
    "--primary-variant": "#af77f3",
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
  "window": {
    "background": "var(--background)",
    "foreground": "var(--on-background)",
    "clip": "no"
  },
  "key": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "font-size": "22sp",
    "shadow-elevation": "2dp",
    "shape": "var(--shape)",
    "text-max-lines": "1"
  },
  "key:pressed": {
    "background": "var(--surface-variant)",
    "foreground": "var(--on-surface)"
  },
  "key[code=10]": {
    "background": "var(--primary)",
    "foreground": "var(--background)"
  },
  "key[code=10]:pressed": {
    "background": "var(--primary-variant)",
    "foreground": "var(--background)"
  },
  "key[code=32]": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface-variant)",
    "font-size": "12sp",
    "text-overflow": "ellipsis"
  },
  "key[code=-201,-202,-203]": {
    "font-size": "18sp"
  },
  "key[code=-204,-205]": {
    "font-size": "12sp"
  },
  "key[code=-205]": {
    "text-max-lines": "2"
  },
  "key[code=-11][shiftstate=`caps_lock`]": {
    "foreground": "var(--secondary)"
  },
  "key-hint": {
    "background": "transparent",
    "foreground": "var(--on-surface-variant)",
    "font-family": "monospace",
    "font-size": "12sp",
    "padding": "0dp 1dp 2dp 0dp",
    "text-max-lines": "1"
  },
  "key-popup-box": {
    "background": "var(--popup-surface)",
    "foreground": "var(--on-surface)",
    "font-size": "22sp",
    "shape": "var(--shape)",
    "shadow-elevation": "2dp"
  },
  "key-popup-element:focus": {
    "background": "var(--focused-popup-surface)",
    "shape": "var(--shape)"
  },
  "key-popup-extended-indicator": {
    "font-size": "16sp"
  },
  "smartbar": {
    "font-size": "18sp"
  },
  "smartbar-shared-actions-toggle": {
    "background": "var(--surface-variant)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "smartbar-extended-actions-toggle": {
    "background": "var(--surface-variant)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "smartbar-action-key": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "smartbar-action-key:disabled": {
    "foreground": "var(--on-background-disabled)"
  },
  "smartbar-action-tile": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)",
    "shadow-elevation": "1dp"
  },
  "smartbar-action-tile-icon": {
    "font-size": "18sp"
  },
  "smartbar-action-tile:disabled": {
    "foreground": "var(--on-background-disabled)"
  },
  "smartbar-actions-overflow-customize-button": {
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "smartbar-actions-editor": {
    "background": "var(--background)"
  },
  "smartbar-actions-editor-header": {
    "foreground": "var(--on-surface)"
  },
  "smartbar-actions-editor-header-button": {
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "smartbar-actions-editor-subheader": {
    "foreground": "var(--on-surface-variant)"
  },
  "smartbar-actions-editor-tile-grid": {
    "background": "var(--background)"
  },
  "smartbar-actions-editor-tile": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)",
    "shadow-elevation": "1dp"
  },
  "smartbar-actions-editor-tile[code=-999]": {
    "background": "transparent",
    "foreground": "var(--on-surface-variant)"
  },
  "smartbar-actions-editor-tile[code=-999]:pressed": {
    "background": "transparent",
    "foreground": "var(--on-surface)"
  },
  "smartbar-actions-editor-tile-icon": {
    "font-size": "18sp"
  },
  "smartbar-actions-editor-tile-label": {
    "font-size": "12sp",
    "text-max-lines": "2"
  },
  "smartbar-candidate-word": {
    "background": "transparent",
    "foreground": "var(--on-surface)"
  },
  "smartbar-candidate-clip": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "smartbar-candidate-spacer": {
    "background": "var(--spacer-color)"
  },
  "inline-autofill-chip": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "clipboard-header": {
    "foreground": "var(--on-background)",
    "font-size": "16sp"
  },
  "clipboard-header-button": {
    "margin": "4dp",
    "shape": "circle()"
  },
  "clipboard-header-button:disabled": {
    "foreground": "var(--on-background-disabled)"
  },
  "clipboard-header-text": {
    "text-max-lines": "1",
    "text-overflow": "ellipsis"
  },
  "clipboard-subheader": {
    "font-size": "14sp",
    "margin": "6dp"
  },
  "clipboard-content": {
    "padding": "10dp"
  },
  "clipboard-item": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "font-size": "14sp",
    "margin": "4dp",
    "padding": "12dp 8dp",
    "shape": "var(--shape-variant)",
    "shadow-elevation": "2dp"
  },
  "clipboard-item-popup": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "font-size": "14sp",
    "shape": "var(--shape-variant)",
    "shadow-elevation": "1dp"
  },
  "clipboard-item-popup-button": {
    "background": "transparent",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape-variant)"
  },
  "clipboard-clear-all-dialog": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape-variant)",
    "shadow-elevation": "1dp"
  },
  "clipboard-clear-all-dialog-message": {
    "padding": "16dp"
  },
  "clipboard-clear-all-dialog-buttons": {
    "padding": "4dp"
  },
  "clipboard-clear-all-dialog-button": {
    "background": "transparent",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape-variant)"
  },
  "clipboard-history-disabled-title": {
    "font-weight": "bold"
  },
  "clipboard-history-disabled-message": {
    "padding": "0dp 4dp 0dp 8dp"
  },
  "clipboard-history-disabled-button": {
    "background": "var(--primary)",
    "foreground": "var(--on-primary)",
    "shape": "rounded-corner(24dp, 24dp, 24dp, 24dp)"
  },
  "clipboard-history-locked-title": {
    "font-weight": "bold",
    "text-align": "center"
  },
  "clipboard-history-locked-message": {
    "padding": "0dp 4dp 0dp 0dp",
    "text-align": "center"
  },
  "clipboard-history-locked-button": {
    "background": "var(--primary)",
    "foreground": "var(--on-primary)",
    "shape": "rounded-corner(24dp, 24dp, 24dp, 24dp)"
  },
  "extracted-landscape-input-field": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "font-size": "18sp",
    "padding": "12dp",
    "shape": "var(--shape-variant)",
    "shadow-elevation": "2dp"
  },
  "extracted-landscape-input-action": {
    "background": "var(--primary)",
    "foreground": "var(--on-primary)",
    "shape": "var(--shape)"
  },
  "glide-trail": {
    "foreground": "var(--secondary)"
  },
  "incognito-mode-indicator": {
    "foreground": "var(--incognito-icon-color)"
  },
  "media-emoji-subheader": {
    "foreground": "var(--on-surface-variant)",
    "font-size": "14sp"
  },
  "media-emoji-key": {
    "background": "transparent",
    "foreground": "var(--on-surface)",
    "font-size": "22sp",
    "shape": "var(--shape)"
  },
  "media-emoji-key:pressed": {
    "background": "var(--surface-variant)",
    "foreground": "var(--on-surface)"
  },
  "media-emoji-key-popup-box": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "font-size": "22sp",
    "shape": "var(--shape)",
    "shadow-elevation": "2dp"
  },
  "media-emoji-key-popup-element:focus": {
    "background": "var(--focused-popup-surface)",
    "shape": "var(--shape)"
  },
  "media-emoji-tab": {
    "background": "transparent",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "media-emoji-tab:focus": {
    "background": "var(--surface-variant)"
  },
  "media-bottom-row-button": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape)"
  },
  "media-emoji-key-popup-extended-indicator": {
    "font-size": "16sp"
  },
  "one-handed-panel": {
    "background": "var(--one-hand-background)",
    "foreground": "var(--one-hand-foreground)"
  },
  "subtype-panel": {
    "background": "var(--surface)",
    "foreground": "var(--on-surface)",
    "shape": "var(--shape-variant)",
    "shadow-elevation": "2dp"
  },
  "subtype-panel-header": {
    "font-weight": "bold",
    "padding": "12dp"
  }
}


---

3. Annotated Walkthrough – What Each Selector Actually Hits

Now the “teacher mode”: what each piece means in terms of UI and behavior.

You already have:

Key code table – what each code means.

Engine spec – legal selectors, properties, etc.


Here we connect them.


---

3.1 @defines – The Palette & Shape System

"@defines": {
  "--primary": "#cba6f7",
  "--secondary": "#b4befe",
  "--primary-variant": "#af77f3",
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
}

Conceptually:

All colors and shapes are defined once here, then reused.

This gives you a single point of control:

Change --surface → all key backgrounds update.

Change --shape → all pill corners change.



Notable variables:

--primary / --primary-variant:
Used for special keys (like Enter) and call-to-action buttons.

--background:
Used for the overall keyboard backdrop (window.background).

--surface / --surface-variant:
Used for key faces and pressed states.

--on-* values:
Foreground colors (text/icons) that sit on top of those surfaces.

--shape / --shape-variant:
Standard pill radii, used across keys, tiles, panels.



---

3.2 The Global Container – window

"window": {
  "background": "var(--background)",
  "foreground": "var(--on-background)",
  "clip": "no"
}

Selector: window = the entire IME UI backdrop.

background: sets the whole keyboard area behind keys.

foreground: default text/icon color for elements inheriting from window.

clip: "no": tells the engine not to clip child content to window bounds (useful when drawing shadows beyond edges).



---

3.3 Baseline Key Styling – key and key:pressed

"key": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "font-size": "22sp",
  "shadow-elevation": "2dp",
  "shape": "var(--shape)",
  "text-max-lines": "1"
},
"key:pressed": {
  "background": "var(--surface-variant)",
  "foreground": "var(--on-surface)"
}

Selector: key = all keys by default:

Letters

Numbers

Most function keys


background: --surface sets the key face color.

foreground: --on-surface sets letter/icon color.

font-size: 22sp – large and readable.

shadow-elevation: 2dp – subtle drop shadow for 3D feel.

shape: --shape – uniform pill shape.

text-max-lines: 1: ensures labels don’t wrap.

key:pressed:

When your finger is down, the key’s background changes to --surface-variant.

Foreground stays --on-surface.



This pair is the global lever for all keys:

Any more specific key[...] rule overrides parts of this.



---

3.4 Special Keys by Code

Now we override the baseline for specific keys using key[code=...].

3.4.1 Enter Key – key[code=10]

"key[code=10]": {
  "background": "var(--primary)",
  "foreground": "var(--background)"
},
"key[code=10]:pressed": {
  "background": "var(--primary-variant)",
  "foreground": "var(--background)"
}

Code 10 = ENTER (from the key code table).

This makes Enter:

Normal: primary background / background text (bright accent).

Pressed: primary-variant background (slightly shifted hue) / same text color.



So when you hit Enter on the board, you see a distinctive accent key.

3.4.2 Space Key – key[code=32]

"key[code=32]": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface-variant)",
  "font-size": "12sp",
  "text-overflow": "ellipsis"
}

Code 32 = SPACE.

Uses same surface as other keys, but:

Foreground uses on-surface-variant (slightly dimmer).

Smaller font-size: 12sp – space label / hints are small.

text-overflow: "ellipsis" – if label is long (e.g., language name), it truncates with ….



3.4.3 Layout Switchers – key[code=-201,-202,-203]

"key[code=-201,-202,-203]": {
  "font-size": "18sp"
}

Codes:

-201 → VIEW_CHARACTERS

-202 → VIEW_SYMBOLS

-203 → VIEW_SYMBOLS2


All three share a style:

Slightly smaller text than letters (18sp instead of 22sp).


This is a code list selector: a single rule matching multiple keys.


3.4.4 Numeric Layout Switchers – key[code=-204,-205] and key[code=-205]

"key[code=-204,-205]": {
  "font-size": "12sp"
},
"key[code=-205]": {
  "text-max-lines": "2"
}

Codes:

-204 → VIEW_NUMERIC

-205 → VIEW_NUMERIC_ADVANCED


First rule shrinks text for both numeric toggles.

Second rule (key[code=-205]) adds a second constraint:

text-max-lines: "2" – allows the “advanced” label to wrap.



This is an example of specific override:

key[code=-205] wins over key[code=-204,-205] for overlapping properties.


3.4.5 Shift in Caps Lock – key[code=-11][shiftstate=\caps_lock`]`

"key[code=-11][shiftstate=`caps_lock`]": {
  "foreground": "var(--secondary)"
}

Code -11 = SHIFT.

Attribute filter shiftstate=\caps_lock`` = “Shift key while Caps Lock is active.”

Makes the Shift icon use secondary color when Caps Lock is latched.


Net effect:
You visually distinguish “Shift pressed once” vs “Caps Lock on” using color.


---

3.5 Key Hints & Popups

3.5.1 key-hint – Small Corner Labels

"key-hint": {
  "background": "transparent",
  "foreground": "var(--on-surface-variant)",
  "font-family": "monospace",
  "font-size": "12sp",
  "padding": "0dp 1dp 2dp 0dp",
  "text-max-lines": "1"
}

Targets the little hints on keys (e.g., 1 above Q).

Transparent background; dimmer text color.

monospace for code-y look.

Small font, padded into corner.


3.5.2 Popups – key-popup-box, key-popup-element:focus, key-popup-extended-indicator

"key-popup-box": {
  "background": "var(--popup-surface)",
  "foreground": "var(--on-surface)",
  "font-size": "22sp",
  "shape": "var(--shape)",
  "shadow-elevation": "2dp"
},
"key-popup-element:focus": {
  "background": "var(--focused-popup-surface)",
  "shape": "var(--shape)"
},
"key-popup-extended-indicator": {
  "font-size": "16sp"
}

key-popup-box – the bubble that appears on long-press.

key-popup-element:focus – the current selection under your finger in that popup.

key-popup-extended-indicator – usually the “…” or tiny mark indicating more keys are available.


This trio controls the entire long-press experience:
colors, shapes, focus behavior, and indicator size.


---

3.6 Smartbar – Top Prediction Row

Selectors starting with smartbar- handle predictions and actions above the keys.

3.6.1 Base Smartbar

"smartbar": {
  "font-size": "18sp"
}

Sets default font size for content in the bar.


3.6.2 Toggles

"smartbar-shared-actions-toggle": {
  "background": "var(--surface-variant)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
},
"smartbar-extended-actions-toggle": {
  "background": "var(--surface-variant)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
}

These are buttons that open extended actions.

Styled as pill buttons with muted surface variant.


3.6.3 Action Keys & Tiles

"smartbar-action-key": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
},
"smartbar-action-key:disabled": {
  "foreground": "var(--on-background-disabled)"
},
"smartbar-action-tile": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)",
  "shadow-elevation": "1dp"
},
"smartbar-action-tile-icon": {
  "font-size": "18sp"
},
"smartbar-action-tile:disabled": {
  "foreground": "var(--on-background-disabled)"
}

Action keys = small buttons (undo, redo, settings).

Tiles = larger rectangular items (clipboard tiles, etc.).

Disabled state dims the foreground using --on-background-disabled.


3.6.4 Smartbar Editor – Customizing Actions

"smartbar-actions-editor": {
  "background": "var(--background)"
},
"smartbar-actions-editor-header": {
  "foreground": "var(--on-surface)"
},
"smartbar-actions-editor-header-button": {
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
},
"smartbar-actions-editor-subheader": {
  "foreground": "var(--on-surface-variant)"
},
"smartbar-actions-editor-tile-grid": {
  "background": "var(--background)"
},
"smartbar-actions-editor-tile": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)",
  "shadow-elevation": "1dp"
},
"smartbar-actions-editor-tile[code=-999]": {
  "background": "transparent",
  "foreground": "var(--on-surface-variant)"
},
"smartbar-actions-editor-tile[code=-999]:pressed": {
  "background": "transparent",
  "foreground": "var(--on-surface)"
},
"smartbar-actions-editor-tile-icon": {
  "font-size": "18sp"
},
"smartbar-actions-editor-tile-label": {
  "font-size": "12sp",
  "text-max-lines": "2"
}

Key detail:

code = -999 = NOOP / spacer tile (from key code table).

Here, used as a placeholder tile in the editor grid.

Transparent background differentiates empty slots from real actions.




---

3.7 Smartbar Candidates & Autofill Chips

"smartbar-candidate-word": {
  "background": "transparent",
  "foreground": "var(--on-surface)"
},
"smartbar-candidate-clip": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
},
"smartbar-candidate-spacer": {
  "background": "var(--spacer-color)",
  "height": "1dp"
},
"inline-autofill-chip": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
}

Candidate words – text only, transparent background.

Clipboard candidate – pill with surface background.

Spacer – vertical divider using --spacer-color.

Autofill chip – pill shaped suggestion for autofill.



---

3.8 Clipboard UI

Everything that starts with clipboard- controls the clipboard manager screens.

Examples:

clipboard-header, clipboard-header-button, clipboard-header-text

clipboard-subheader

clipboard-content

clipboard-item, clipboard-item-popup, clipboard-item-popup-button

clipboard-clear-all-dialog, clipboard-clear-all-dialog-*

clipboard-history-disabled-*, clipboard-history-locked-*


Patterns:

Headers use bigger font + bold for emphasis.

Buttons often use shape-variant (bigger pills) and primary background for “enable/confirm” actions.

Messages use padding and sometimes text-align: "center".


The main idea:

> Clipboard “cards” feel like a cohesive sub-UI, but still inherit the same palette and shapes as keys.




---

3.9 Extracted Text, Glide Trail, Incognito

"extracted-landscape-input-field": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "font-size": "18sp",
  "padding": "12dp",
  "shape": "var(--shape-variant)",
  "shadow-elevation": "2dp"
},
"extracted-landscape-input-action": {
  "background": "var(--primary)",
  "foreground": "var(--on-primary)",
  "shape": "var(--shape)"
},
"glide-trail": {
  "foreground": "var(--secondary)"
},
"incognito-mode-indicator": {
  "foreground": "var(--incognito-icon-color)"
}

Extracted field – full-width text box for landscape input.

Glide trail uses secondary color – matches the theme accent.

Incognito indicator uses a very transparent color (--incognito-icon-color).



---

3.10 Emoji / Media Panel

"media-emoji-subheader": {
  "foreground": "var(--on-surface-variant)",
  "font-size": "14sp"
},
"media-emoji-key": {
  "background": "transparent",
  "foreground": "var(--on-surface)",
  "font-size": "22sp",
  "shape": "var(--shape)"
},
"media-emoji-key:pressed": {
  "background": "var(--surface-variant)",
  "foreground": "var(--on-surface)"
},
"media-emoji-key-popup-box": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "font-size": "22sp",
  "shape": "var(--shape)",
  "shadow-elevation": "2dp"
},
"media-emoji-key-popup-element:focus": {
  "background": "var(--focused-popup-surface)",
  "shape": "var(--shape)"
},
"media-emoji-tab": {
  "background": "transparent",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
},
"media-emoji-tab:focus": {
  "background": "var(--surface-variant)"
},
"media-bottom-row-button": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape)"
},
"media-emoji-key-popup-extended-indicator": {
  "font-size": "16sp"
}

Same pattern as normal keys / popups, but used for emojis & category tabs.


---

3.11 One-handed & Subtype Panel

"one-handed-panel": {
  "background": "var(--one-hand-background)",
  "foreground": "var(--one-hand-foreground)"
},
"subtype-panel": {
  "background": "var(--surface)",
  "foreground": "var(--on-surface)",
  "shape": "var(--shape-variant)",
  "shadow-elevation": "2dp"
},
"subtype-panel-header": {
  "font-weight": "bold",
  "padding": "12dp"
}

one-handed-panel – the shaded background when keyboard is shrunk to one side.

subtype-panel – panel that shows available language layouts.

subtype-panel-header – bold header with padding.



---

4. How to Generalize This Example

If you’re designing your own theme (or instructing an agent), this file demonstrates:

1. Variables first.
Palette and shapes live in @defines.


2. Baseline selectors.

window, key, key:pressed, key-hint, key-popup-*.



3. Special cases by code.

key[code=10] (Enter)

key[code=32] (Space)

Layout toggles: key[code=-201,-202,-203], key[code=-204,-205]

key[code=-11][shiftstate=\caps_lock`]`



4. Sub-UI clusters.

smartbar-*, clipboard-*, media-*, subtype-*, one-handed-panel.



5. Specific override precedence.

Generic key → group/attribute rules → code-specific overrides.




You can hand this document to an agent along with the Engine Spec and Key Code table, and it has everything needed to:

Understand how a real theme is wired.

Produce new themes that follow the same patterns without guessing.



---
