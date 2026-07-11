# Theme Coverage Checklist

> Status: Canonical validation checklist  
> Last verified: 2026-07-11  
> Coverage source: `FlorisImeUi.entries` and current Compose queries

Use this as a visual/runtime checklist, not as a promise that every theme needs
a unique rule for every element. Inherited styling is acceptable when it is
intentional and legible.

## Keyboard

- Window/root background and clipping
- Alpha, number, punctuation, modifier, navigation, command, view, and space keys
- Finger-down pressed state
- Ctrl active and locked states
- Number/developer row active states
- Disabled keys
- Key hints
- Popup box, focused popup element, and extended indicator
- Glide trail and incognito indicator

## Smartbar

- Shared and extended action rows/toggles
- Action keys and disabled state
- Action tiles, icons, and labels
- Overflow and customize button
- Candidate word, secondary text, pressed state, clip, icon, and spacer
- Actions editor, header, subheader, grid, normal/disabled/drag tiles
- Inline autofill chip

## Clipboard

- Header, buttons, title, and disabled button state
- Subheader, content, filter row/chips, and active filtering
- Grid, text/image items, descriptions, timestamps
- Item popup and actions
- Clear-all dialog and buttons
- Disabled and locked empty states

## Media

- Media container and bottom row/buttons
- Emoji section headers, keys, pressed state, and tabs/focused tab
- Emoji popup, focused popup element, and extended indicator

## Auxiliary surfaces

- One-handed panel and buttons
- Subtype panel, header, list, item icon, and text
- Extracted landscape layout, field, and action

## Runtime checks

- No black-on-black or invisible icons/text
- Pressed feedback is visible at typing speed
- Latched toggles remain distinguishable after finger release
- Popup focus is distinguishable from the popup background
- Disabled content looks disabled but remains readable
- Theme remains coherent when switching keyboard modes
- Clipboard/media/action panels do not fall back to jarring defaults
- Process restart/reselection does not change the intended appearance

