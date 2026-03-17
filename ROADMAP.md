# OmniBoard Roadmap

Features planned for upcoming development, roughly ordered by complexity.

---

## 1. Hide Keyboard Button in Smartbar

**What:** Add a "hide keyboard" quick-action button to the smartbar so Sam can dismiss the keyboard and see the full screen without tapping outside an input field.

**Why:** Essential usability — reading content while a text field is focused is currently awkward.

**Approach:**
- Add a new `QuickActionButton` with code `KeyCode.VIEW_HIDE_KEYBOARD` (or trigger `FlorisImeService.requestHideSelf()`)
- Add it to the smartbar quick-actions pool in `Smartbar.kt`
- Give it a reasonable icon (arrow-down or keyboard-hide vector)

**Files:** `Smartbar.kt`, `QuickActionButton.kt`, `FlorisImeUi.kt`

---

## 2. Phrase Prediction Row Toggle in Smartbar

**What:** The "phrase prediction row" (currently a settings toggle in Smartbar settings) should also be toggleable directly from the smartbar via a quick-action button — like a mode switch.

**Why:** Sam wants to flip it on/off mid-session without going into settings.

**Approach:**
- Read `prefs.smartbar.showPhraseRow` (or equivalent pref)
- Add a smartbar button that toggles that pref in-place and triggers recompose
- Button should visually reflect current state (active/inactive)

**Files:** `Smartbar.kt`, `AppPrefs.kt`, `SmartbarScreen.kt`

---

## 3. Fix Smartbar Action Reordering / Customization

**What:** The "Customize Actions" overflow in the smartbar is broken — the microphone is sticky but other actions can't be reordered or toggled on/off.

**Why:** Sam can't control which quick-action buttons are visible.

**Approach:**
- Audit the drag-reorder and toggle logic in `Smartbar.kt` / quickaction layer
- Likely a state persistence or LazyRow key bug — the list updates but doesn't save or re-render correctly
- Fix persistence so changes survive keyboard restarts

**Files:** `Smartbar.kt`, `QuickActionButton.kt`, prefs serialization

---

## 4. True Home/End Navigation (Fix « » Keys)

**What:** The `«` (code -27) and `»` (code -28) keys currently behave like reverse-tab / forward-tab rather than moving to the start/end of the text field. They should jump to start-of-field or start-of-line.

**Why:** Sam needs actual positional navigation, not focus cycling.

**Approach:**
- In `KeyboardManager.kt` key event handling, map codes -27/-28 to `InputConnection.performContextMenuAction(android.R.id.selectAll)` or direct `setSelection(0)` / `setSelection(length)` calls
- Verify against `AbstractEditorInstance` selection API — `setSelection(start, end)` should be available
- May need new KeyCode constants (e.g. `MOVE_START_OF_FIELD`, `MOVE_END_OF_FIELD`) to distinguish from existing line-start/end

**Files:** `KeyboardManager.kt`, `EditorInstance.kt`, `KeyCode.kt`

---

## 5. Transparent / Frosted Background Effect

**What:** A bottom-offset bug accidentally revealed that the keyboard container background can be made transparent/semi-transparent, showing app content through the lower rows. Sam wants this as a real feature — controllable keyboard background transparency or frosted-glass effect.

**Why:** Aesthetic + practical — see more of the screen while typing.

**Approach:**
- The bug was triggered by `FlorisImeSizing` bottom offset shifting the keyboard window up while the background container stayed anchored — net effect: background didn't cover the bottom rows
- To make it a feature: expose a `backgroundAlpha` or `backgroundBlur` parameter in the snygg theme system (or directly in `FlorisImeService` window flags)
- Two sub-approaches:
  - **Alpha only:** Set `window.decorView.background` alpha or use a Compose `Modifier.alpha()` on the keyboard surface — simple, works immediately
  - **True blur/frosted glass:** Requires `RenderEffect.createBlurEffect` (API 31+) or a `BlurMaskFilter` — more complex but achievable on S25 Ultra
- Add a theme property (e.g. `keyboard-background-alpha: 0.85`) to snygg stylesheets so it's theme-controlled
- Consider adding a slider in keyboard appearance settings

**Files:** `FlorisImeService.kt`, `FlorisImeSizing.kt`, snygg stylesheet system, theme settings UI

---

## 6. Independent Space Row (Architecture Refactor)

**What:** The spacebar row should be its own independently-controllable JSON row, not baked into the mod rows or scaled with them. This allows: independent height, independent padding above/below the spacebar, independent key sizing, and true layout flexibility.

**Why:** The current system merges the space row into mod-row scaling, making it impossible to e.g. add padding above the spacebar, shrink the mod keys without shrinking the space row, or treat the spacebar as a distinct layout zone.

**Scope:** This is the largest change on this list — it touches the core layout computation pipeline.

**Approach:**
- Currently: `qwerty_wide.json` (characters) + `qwerty_wide_mod.json` (mod rows) are combined; the space row is row 1 of the mod file and gets `isSpaceRow = true` detection in `TextKeyboard.kt`
- Target: Introduce a third JSON slot — `charactersMod/space/` (or a `spaceRow` field) — that is loaded independently and rendered as its own row with its own height factor, padding, and scaling rules
- `LayoutManager.kt` would need to load/combine three row sources instead of two
- `TextKeyboard.kt` layout algorithm would need to distinguish three row classes: alpha rows, space row, mod rows — each with their own `rowHeightFactor`
- Enables: `spaceRowHeightFactor`, padding above/below space row, space row immune to `modKeyWidthFactor`

**Files:** `LayoutManager.kt`, `KeyboardLayout.kt`, `TextKeyboard.kt`, `TextKeyboardLayout.kt`, layout JSON schema, `FlorisImeSizing.kt`

**Tokens/risk:** High. Plan carefully before starting — consider doing on a feature branch.

---

## 7. Key Padding "Smoosh" — Redistribute Space to Neighbors

**What:** When a key has `flayPaddingLeft/Right` applied (currently ESC and Σ), the visual inset should cause neighboring keys to grow into that reclaimed space rather than leaving a visual gap. Currently the padding just shrinks the rendered key while the touch boundary stays the same allocated width — neighbors don't see the freed space.

**Why:** The current padding approach works for the alignment fix but isn't a general-purpose "make this key visually smaller" tool. True smooshing would let you say "give ESC less visual weight" and have the adjacent key automatically fill in.

**Approach:**
- During layout, after computing `visibleBounds` for a key with padding, propagate the reclaimed pixel width to the immediately adjacent key's `visibleBounds.right` (left neighbor) or `visibleBounds.left` (right neighbor)
- Alternatively: instead of padding the key itself, allocate a zero-width invisible spacer key on the outer edge — the real key takes its normal 1.00f width and the spacer absorbs the extra 0.25f
- The spacer approach is cleaner architecturally and avoids patching the visibleBounds propagation loop

**Files:** `TextKeyboard.kt`, `TextKey.kt`, possibly layout JSON schema (for explicit spacer keys)

---

## 8. Theme Change Logging System

**What:** Any edits to theme/snygg stylesheet files should be automatically logged to a persistent change log (similar to how `usage_harvest.md` logs typing events). This way, when an agent implements theme work in a session, it doesn't get lost — the next agent can read the log and bake changes into the actual default theme pack.

**Why:** Theme tweaks made during sessions currently evaporate if they're only in `.flex` scratch files or never merged back into the asset stylesheets. A harvest-style log gives a paper trail.

**Approach:**
- Create `theme_changelog.md` at the repo root — append-only, one entry per change
- Format: `[date] [file] [property changed] [old value] → [new value] [why]`
- Could be maintained manually by convention or hooked into a write-watcher script
- Agent instructions (in CLAUDE.md) should mandate writing a log entry whenever a theme file is edited
- Periodic "bake-in" task: review log, apply approved entries to the canonical stylesheet assets, clear the log

**Files:** `theme_changelog.md` (new), `CLAUDE.md` (add mandate), snygg asset stylesheets

---

## 9. Tmux Prefix Key (Ctrl+B Combo Key)

**What:** Add a dedicated key to the mod row (or smartbar) that fires `Ctrl+B` as a single tap — the tmux prefix. This lets Sam trigger tmux commands without holding CTRL and tapping B separately.

**Why:** Sam uses tmux heavily. The current CTRL key requires two-tap combos. A single-tap tmux-prefix key would be a significant workflow improvement in terminal sessions.

**Approach:**
- Define a new `KeyCode` constant, e.g. `TMUX_PREFIX = -400` (or similar unused negative code)
- In `KeyboardManager.kt`, handle that code by sending `Ctrl+B` as a key chord: `sendDownUpKeyEvents(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)`
- Add the key to `qwerty_wide_mod.json` (row 3, perhaps replacing or alongside ⚛) — or make it a smartbar quick-action button
- Alternatively: generalize to a "chord key" concept — a key defined as `"chord": [-1, 98]` (CTRL + 'b') that any layout can use, making this reusable for other tmux/terminal combos

**Files:** `KeyCode.kt`, `KeyboardManager.kt`, `qwerty_wide_mod.json`, possibly layout JSON schema for chord key type

---

## Priority Order (suggested)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 3 | Fix smartbar action reordering | Low | High |
| 4 | True home/end nav | Low | Medium |
| 1 | Hide keyboard button | Low | High |
| 9 | Tmux prefix key (Ctrl+B) | Low | High |
| 2 | Phrase prediction toggle button | Low | Medium |
| 8 | Theme change logging | Low | Medium |
| 5 | Transparent background | Medium | High |
| 7 | Padding smoosh (redistribute to neighbors) | Medium | Medium |
| 6 | Independent space row | Very High | Very High |
