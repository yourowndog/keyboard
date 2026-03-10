# OmniBoard Ergonomics TODO

## 1. Dynamic Alpha Key Width
- [x] Add `alphaKeyWidth` preference (80% - 140%, default 100%) in `AppPrefs.kt`.
- [x] Add Slider UI in `KeyboardScreen.kt`.
- [x] Update `TextKey.kt` to apply width factor to A-Z keys. (Added `isAlpha` property)
- [x] Ensure row reflow and centering in `TextKeyboard.kt`. (Updated `layout` method)

## 2. Split Spacing Groups
- [x] Add `alphaSpacingHorizontal`, `alphaSpacingVertical` in `AppPrefs.kt`.
- [x] Add `modeSpacingHorizontal`, `modeSpacingVertical` in `AppPrefs.kt`.
- [x] Add Slider UIs in `KeyboardScreen.kt`.
- [x] Update layout logic to apply specific spacing based on key classification (Alpha vs Mode). (Updated `TextKeyboardLayout.kt`)

## 3. Hitbox Expansion
- [x] Expand touch target width for Alpha keys in `TextKey.kt`. (Actually implemented in `TextKeyboardLayout.kt` post-layout)
- [x] Ensure no visual change or overlap.

## 4. Period Key Popup
- [x] Modify `qwerty_default.json` to add long-press popup to `.`.
- [x] Include `?`, `!`, `…`, `,` in the popup.
