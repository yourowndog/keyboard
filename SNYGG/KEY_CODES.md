# FlorisBoard Key Code Reference

These codes are used in layout definitions (`.json` files) and theme rules (Snygg selectors like `key[code=-5]`).

## Standard & Modifiers

| Name | Code | Description |
|---|---|---|
| UNSPECIFIED | 0 | Default / Null state |
| ENTER | 10 | Enter / Return |
| TAB | -14 | Tab key |
| ESCAPE | -15 | Escape key |
| SPACE | 32 | Standard Space bar |
| CTRL | -1 | Control Key |
| CTRL_LOCK | -2 | Control Lock |
| ALT | -3 | Alt Key |
| ALT_LOCK | -4 | Alt Lock |
| FN | -5 | Function Key |
| FN_LOCK | -6 | Function Lock |
| DELETE | -7 | Backspace |
| DELETE_WORD | -8 | Delete previous word |
| FORWARD_DELETE | -9 | Delete forward (Del) |
| FORWARD_DELETE_WORD | -10 | Delete next word |
| SHIFT | -11 | Shift (Caps) |
| CAPS_LOCK | -13 | Caps Lock (Permanent) |

## Navigation & Cursor Movement

| Name | Code | Description |
|---|---|---|
| ARROW_LEFT | -21 | Move cursor left |
| ARROW_RIGHT | -22 | Move cursor right |
| ARROW_UP | -23 | Move cursor up |
| ARROW_DOWN | -24 | Move cursor down |
| MOVE_START_OF_PAGE | -25 | Home / Start of Page |
| MOVE_END_OF_PAGE | -26 | End / End of Page |
| MOVE_START_OF_LINE | -27 | Start of Line |
| MOVE_END_OF_LINE | -28 | End of Line |

## Clipboard Operations

| Name | Code | Description |
|---|---|---|
| CLIPBOARD_COPY | -31 | Copy selection |
| CLIPBOARD_CUT | -32 | Cut selection |
| CLIPBOARD_PASTE | -33 | Paste from clipboard |
| CLIPBOARD_SELECT | -34 | Begin selection mode |
| CLIPBOARD_SELECT_ALL | -35 | Select All text |
| CLIPBOARD_CLEAR_HISTORY | -36 | Clear clipboard history |
| CLIPBOARD_CLEAR_FULL_HISTORY | -37 | Clear everything (Full) |
| CLIPBOARD_CLEAR_PRIMARY_CLIP | -38 | Clear primary clip only |

## Layout & View Switching

| Name | Code | Description |
|---|---|---|
| VIEW_CHARACTERS | -201 | Switch to Alphabet layout |
| VIEW_SYMBOLS | -202 | Switch to Symbols 1 layout |
| VIEW_SYMBOLS2 | -203 | Switch to Symbols 2 layout |
| VIEW_NUMERIC | -204 | Switch to Numeric layout |
| VIEW_NUMERIC_ADVANCED | -205 | Switch to Adv. Numeric |
| VIEW_PHONE | -206 | Switch to Phone Pad |
| VIEW_PHONE2 | -207 | Switch to Phone Pad 2 |
| TOGGLE_COMPACT_LAYOUT | -110 | Toggle One-Handed Mode |
| COMPACT_LAYOUT_TO_LEFT | -111 | Dock One-Handed Left |
| COMPACT_LAYOUT_TO_RIGHT | -112 | Dock One-Handed Right |
| SPLIT_LAYOUT | -113 | Split Keyboard |
| MERGE_LAYOUT | -114 | Merge/Full Keyboard |

## System & IME Actions

| Name | Code | Description |
|---|---|---|
| SETTINGS | -301 | Open FlorisBoard Settings |
| UNDO | -131 | Undo last action |
| REDO | -132 | Redo action |
| VOICE_INPUT | -233 | Trigger Voice/Dictation |
| IME_SHOW_UI | -231 | Force show keyboard |
| IME_HIDE_UI | -232 | Hide keyboard |
| SYSTEM_INPUT_METHOD_PICKER | -221 | Show Android Keyboard Picker |
| SYSTEM_PREV_INPUT_METHOD | -222 | Switch to Previous IME |
| SYSTEM_NEXT_INPUT_METHOD | -223 | Switch to Next IME |
| IME_SUBTYPE_PICKER | -224 | Switch Language Subtype |
| IME_PREV_SUBTYPE | -225 | Previous Language |
| IME_NEXT_SUBTYPE | -226 | Next Language |
| LANGUAGE_SWITCH | -227 | Cycle Languages (Globe) |

## Smartbar & Toggles

| Name | Code | Description |
|---|---|---|
| TOGGLE_SMARTBAR_VISIBILITY | -241 | Show/Hide Smartbar |
| TOGGLE_ACTIONS_OVERFLOW | -242 | Toggle Actions Menu |
| TOGGLE_ACTIONS_EDITOR | -243 | Toggle Clipboard/Cursor Row |
| TOGGLE_INCOGNITO_MODE | -244 | Toggle Incognito |
| TOGGLE_AUTOCORRECT | -245 | Toggle Autocorrect |

## Special Characters & Markers

| Name | Code | Description |
|---|---|---|
| NOOP | -999 | No Operation (Spacer/Placeholder) |
| DRAG_MARKER | -991 | Used internally for drag UI |
| MULTIPLE_CODE_POINTS | -902 | Marker for multi-char keys |
| PHONE_PAUSE | 44 | Phone Pause (,) |
| PHONE_WAIT | 59 | Phone Wait (;) |
| URI_COMPONENT_TLD | -255 | .com / Top Level Domain key |
| CURRENCY_SLOT_1 | -801 | Currency Slot 1 |
| CURRENCY_SLOT_2 | -802 | Currency Slot 2 |
| CURRENCY_SLOT_3 | -803 | Currency Slot 3 |
| CURRENCY_SLOT_4 | -804 | Currency Slot 4 |
| CURRENCY_SLOT_5 | -805 | Currency Slot 5 |
| CURRENCY_SLOT_6 | -806 | Currency Slot 6 |

## Asian / Width Specific

| Name | Code | Description |
|---|---|---|
| CHAR_WIDTH_SWITCHER | -9701 | Switch Character Width |
| CHAR_WIDTH_FULL | -9702 | Full Width Characters |
| CHAR_WIDTH_HALF | -9703 | Half Width Characters |
| KANA_SWITCHER | -9710 | Kana Switcher |
| KANA_HIRA | -9711 | Hiragana |
| KANA_KATA | -9712 | Katakana |
| KANA_HALF_KATA | -9713 | Half-width Katakana |
| KANA_SMALL | 12307 | Small Kana Marker |
| CJK_SPACE | 12288 | CJK (Wide) Space |
| HALF_SPACE | 8204 | ZWNJ / Half Space |
| KESHIDA | 1600 | Keshida (Arabic elongation) |

## JSON Format

Here is the raw JSON object if you wish to programmatically ingest this into your agent scripts.

```json
{
  "STANDARD": {
    "UNSPECIFIED": 0,
    "ENTER": 10,
    "TAB": 9,
    "ESCAPE": 27,
    "SPACE": 32
  },
  "MODIFIERS": {
    "CTRL": -1,
    "CTRL_LOCK": -2,
    "ALT": -3,
    "ALT_LOCK": -4,
    "FN": -5,
    "FN_LOCK": -6,
    "DELETE": -7,
    "DELETE_WORD": -8,
    "FORWARD_DELETE": -9,
    "FORWARD_DELETE_WORD": -10,
    "SHIFT": -11,
    "CAPS_LOCK": -13
  },
  "NAVIGATION": {
    "ARROW_LEFT": -21,
    "ARROW_RIGHT": -22,
    "ARROW_UP": -23,
    "ARROW_DOWN": -24,
    "MOVE_START_OF_PAGE": -25,
    "MOVE_END_OF_PAGE": -26,
    "MOVE_START_OF_LINE": -27,
    "MOVE_END_OF_LINE": -28
  },
  "CLIPBOARD": {
    "COPY": -31,
    "CUT": -32,
    "PASTE": -33,
    "SELECT": -34,
    "SELECT_ALL": -35,
    "CLEAR_HISTORY": -36,
    "CLEAR_FULL_HISTORY": -37,
    "CLEAR_PRIMARY": -38
  },
  "LAYOUT_SWITCHING": {
    "VIEW_CHARACTERS": -201,
    "VIEW_SYMBOLS": -202,
    "VIEW_SYMBOLS2": -203,
    "VIEW_NUMERIC": -204,
    "VIEW_NUMERIC_ADVANCED": -205,
    "VIEW_PHONE": -206,
    "VIEW_PHONE2": -207,
    "COMPACT_LAYOUT_TOGGLE": -110,
    "COMPACT_LAYOUT_LEFT": -111,
    "COMPACT_LAYOUT_RIGHT": -112,
    "SPLIT_LAYOUT": -113,
    "MERGE_LAYOUT": -114
  },
  "SYSTEM": {
    "SETTINGS": -301,
    "UNDO": -131,
    "REDO": -132,
    "VOICE_INPUT": -233,
    "SHOW_KEYBOARD": -231,
    "HIDE_KEYBOARD": -232,
    "SWITCH_LANGUAGE": -227,
    "SWITCH_IME": -221
  },
  "TOGGLES": {
    "SMARTBAR_VISIBILITY": -241,
    "ACTIONS_OVERFLOW": -242,
    "ACTIONS_EDITOR": -243,
    "INCOGNITO": -244,
    "AUTOCORRECT": -245
  },
  "SPECIAL": {
    "NOOP": -999,
    "DRAG_MARKER": -991,
    "URI_TLD": -255,
    "CURRENCY_SLOT_1": -801
  }
}
```
