# Handover: Sticky/Lock Visual States for Ctrl, Toggle Number Row, Toggle Dev Row

## Goal

Three special keys need persistent visual feedback showing their active/locked state AFTER the finger lifts:

- **Ctrl** (`KeyCode.CTRL`, code `-1`): single-tap = "active" (sticky), double-tap = "locked" (persistent)
- **Toggle Number Row** (`KeyCode.TOGGLE_NUMBER_ROW`, code `-305`): lit when number row is visible
- **Toggle Dev Row** (`KeyCode.TOGGLE_DEV_ROW`, code `-306`): lit when dev row is visible

Currently, none of these show any visual change after the finger lifts. The standard `:pressed` finger-down flash works fine — it's the persistent post-release state that's broken.

## What Was Done

### 1. Added Snygg attribute constants (`FlorisImeUi.kt`)

```
app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisImeUi.kt
```

Added to `FlorisImeUi.Attr` object (~line 390):
```kotlin
const val CtrlState = "ctrlstate"
const val NumberRowState = "numberrowstate"
const val DevRowState = "devrowstate"
```

### 2. Wired attributes into key rendering (`TextKeyboardLayout.kt`)

```
app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt
```

In `TextKeyButton()` (~line 410), the attributes map now includes:
```kotlin
FlorisImeUi.Attr.CtrlState to when {
    evaluator.state.isCtrlLocked -> "locked"
    evaluator.state.isCtrlPressed -> "active"
    else -> "none"
},
FlorisImeUi.Attr.NumberRowState to if (numberRowEnabled) "active" else "none",
FlorisImeUi.Attr.DevRowState to if (devRowEnabled) "active" else "none",
```

Where `numberRowEnabled` / `devRowEnabled` come from `prefs.keyboard.numberRow.observeAsState()` (Compose state observation).

The old DEBUG hacks were removed from the selector:
```kotlin
// REMOVED:
// key.computedData.code == KeyCode.CTRL && evaluator.state.isCtrlPressed -> SnyggSelector.FOCUS
// key.computedData.code == KeyCode.TOGGLE_NUMBER_ROW -> SnyggSelector.PRESSED // DEBUG
// key.computedData.code == KeyCode.TOGGLE_DEV_ROW -> SnyggSelector.PRESSED // DEBUG
```

### 3. Added theme selectors to all 4 LCARS stylesheets

Example from `lcars_neon.json` (currently has DEBUG hardcoded colors):
```json
"key[code=-1][ctrlstate=`active`]": {
    "background": "#00FF00",
    "foreground": "#000000"
},
"key[code=-305][numberrowstate=`active`]": {
    "background": "#FF0000",
    "foreground": "#FFFFFF"
}
```

All 4 LCARS themes were updated: `lcars.json`, `lcars_neon.json`, `lcars_tactical.json`, `lcars_sickbay.json`.

### 4. Updated docs

`SNYGG/SNYGG_REFERENCE.md` and `SNYGG_REALITY/SNYGG_CHEATSHEET.md` updated with new attributes.

## What Was Tested

- Built and installed via factory remote (`git push factory dev`)
- Tested on Neon theme with hardcoded bright colors (#FF0000, #00FF00, #FF00FF)
- **Result: NONE of the colors appeared.** The toggle keys and Ctrl key show only the standard `:pressed` flash and nothing persistent.

## What Was Verified (Code-Level)

All of these were traced through and appear correct:

1. **Snygg regex parsing** — Python simulation confirms `key[code=-305][numberrowstate=`active`]` parses correctly into attributes `{code: ["-305"], numberrowstate: ["active"]}`

2. **Snygg attribute matching** — `SnyggAttributes.isMatchForQuery()` in `SnyggTheme.kt:166` does `query[attrKey]?.toString()` and checks `contains()`. String values like `"active"` match backtick-stripped values from the theme JSON.

3. **Snygg style cascade** — `SnyggTheme.query()` iterates ALL matching rules sorted by specificity. Rules with more attributes sort after (override) rules with fewer. So `key[code=-305][numberrowstate=`active`]` (2 attrs) overrides `key[code=-305,-306]` (1 attr).

4. **Snygg selector matching** — Rules with no `:pressed`/`:focus` suffix have `SnyggSelector.NONE`, which matches any query selector per `SnyggSelector.isMatchForQuery()`.

5. **Ctrl state sources exist** — `activeState.isCtrlPressed` and `isCtrlLocked` are real boolean flags in `KeyboardState.kt` (bit flags in `rawValue`). `handleCtrlDown()` in `KeyboardManager.kt:803` sets them.

6. **Toggle pref sources exist** — `prefs.keyboard.numberRow` and `prefs.keyboard.devRow` are boolean prefs toggled by `KeyboardManager.kt:1172-1177`.

7. **Evaluator update chain** — `KeyboardManager.kt:363`: `activeState.collectLatestIn(scope) { updateActiveEvaluators() }`. When `activeState` changes, a new evaluator is created with a fresh `activeState.snapshot()` and emitted to `_activeEvaluator` StateFlow. `TextInputLayout.kt:55` collects this: `val evaluator by keyboardManager.activeEvaluator.collectAsState()`.

8. **`shiftstate` works with same mechanism** — `InputShiftState.CAPS_LOCK.toString()` returns `"caps_lock"` which matches theme selector `key[code=-11][shiftstate=`caps_lock`]`. This uses the exact same attribute matching path.

## Potential Issues / Things NOT Yet Verified

### Theory 1: Theme files aren't being loaded from assets (MOST LIKELY)
Maybe the Neon theme stylesheet loaded at runtime is NOT the one from `assets/ime/theme/`. FlorisBoard's extension system may cache/install themes to app storage on first run, and subsequent APK installs may not overwrite the cached copy.

**How to test:** Add a completely new obviously-wrong rule like `"key": { "background": "#FF0000" }` as the FIRST rule in `lcars_neon.json`. If ALL keys don't turn red on install, the file isn't being loaded and there's a caching issue. You may need to clear app data or uninstall/reinstall.

### Theory 2: `shiftstate` doesn't actually work either
We ASSUMED `shiftstate` attribute selectors work because they exist in themes. But maybe they DON'T work and the caps lock indicator uses a different mechanism entirely (like Compose-side logic outside Snygg).

**How to test:** Add `"key[code=-11][shiftstate=`caps_lock`]": { "background": "#FF0000" }` to a theme and verify caps lock actually turns the shift key red.

### Theory 3: Evaluator snapshot timing
`evaluator.state` is a **snapshot** (frozen copy) created in `updateActiveEvaluators()`. The flow chain is async (`scope.launch`). Maybe the evaluator snapshot never captures the updated Ctrl state, or the Compose recomposition fires before the new evaluator is ready.

**How to test:** Add logging in `updateActiveEvaluators()` printing `state.isCtrlPressed` to verify the snapshot captures the correct value after Ctrl is pressed.

### Theory 4: The attribute map values aren't invalidating rememberQuery cache
`SnyggUi.kt:254` uses `remember(this, elementName, attributes, mergedSelector, ...)`. If the `attributes` map identity doesn't trigger cache invalidation despite content changes, stale results would be returned.

**How to test:** Temporarily replace `remember(...)` with just `query(...)` in `rememberQuery()` to disable caching entirely.

### Theory 5: Rule parsing silently fails
`SnyggElementRule.fromOrNull()` might return `null` for the new selectors, causing them to be silently dropped during theme compilation. The regex looks correct in theory, but edge cases are possible.

**How to test:** Add logging in `SnyggStylesheet` deserialization or `SnyggTheme.compileFrom()` to print all parsed rule strings and verify attribute selectors appear.

## Key Files

| File | Purpose |
|------|---------|
| `app/.../ime/theme/FlorisImeUi.kt` | `Attr` constants (lines 390-396) |
| `app/.../ime/text/keyboard/TextKeyboardLayout.kt` | `TextKeyButton()` attributes + selector (lines 404-430) |
| `lib/snygg/src/.../SnyggRule.kt` | Attribute parsing regex (line 421), `SnyggAttributes.from()` (line 423), `compareTo()` (line 242) |
| `lib/snygg/src/.../SnyggTheme.kt` | `query()` cascade (line 63), `isMatchForQuery()` (line 166), `rememberQuery()` lives in SnyggUi.kt (line 244) |
| `lib/snygg/src/.../ui/SnyggUi.kt` | `rememberQuery()` with `remember()` cache (line 254), `ProvideSnyggStyle()` (line 194) |
| `lib/snygg/src/.../ui/SnyggBox.kt` | `SnyggBox()` composable that TextKeyButton uses (line 58) |
| `app/.../ime/keyboard/KeyboardManager.kt` | `handleCtrlDown()` (line 803), toggle handlers (line 1172), `updateActiveEvaluators()` (line 390), `activeState` flow collection (line 363) |
| `app/.../ime/keyboard/KeyboardState.kt` | `isCtrlPressed`/`isCtrlLocked` flags (line 236), `ObservableKeyboardState` with StateFlow (line 249), `snapshot()` (line 110) |
| `app/.../ime/keyboard/ComputingEvaluator.kt` | Interface defining `state: KeyboardState` (line 64), placeholder (line 88) |
| `app/.../ime/text/TextInputLayout.kt` | `evaluator` collected as Compose state from `keyboardManager.activeEvaluator.collectAsState()` (line 55) |
| `app/.../assets/ime/theme/com.brokentooth.lcars/stylesheets/*.json` | Theme stylesheet JSON files |
| `app/.../assets/ime/theme/com.brokentooth.lcars/extension.json` | Theme extension manifest |

## Current State of the Code

Branch `dev` with 2 relevant commits:
1. `6a33c4d7` — main implementation (attributes, theme selectors, docs, bigrams, smartbar/clipboard selectors)
2. `a01c3d5c` — debug commit with hardcoded bright colors in `lcars_neon.json`

The debug colors (#FF0000, #00FF00, #FF00FF) are still in `lcars_neon.json`. The Kotlin code is clean (no debug artifacts).

## Recommended Next Steps

1. **Test Theory 1 first** (theme caching) — it's the easiest and would explain everything. If the asset JSON files aren't being read at runtime, nothing we do in them matters. Try clearing app data or adding a rule that would break ALL keys visually.

2. **Test Theory 2** — verify `shiftstate` actually works as a visual indicator. If it doesn't, the whole Snygg string-attribute mechanism may be non-functional and we'd need a different approach (e.g., going back to pseudo-selectors like `:focus`).

3. **If themes ARE loading** — add logging in `SnyggTheme.query()` and `isMatchForQuery()` to trace whether the attribute selectors are being evaluated and whether they match.

4. **Nuclear option** — if Snygg attribute matching is fundamentally broken, revert to using the selector-based approach: map active states to `:focus` selector in the Kotlin code and use `:focus` pseudo-selectors in themes. This is what the old Ctrl hack did and it worked.

## Build & Test

```bash
# Build and deploy
git push factory dev

# APK URL after build
http://142.93.94.124:8000/omni.apk

# Remotes
factory = ssh://silo@beksinski/home/silo/git/omniboard.git  (build server)
origin = https://github.com/yourowndog/keyboard.git
vault = /data/data/com.termux/files/home/vault/projects/keyboard

# No ADB available — testing is on-device in Termux
```
