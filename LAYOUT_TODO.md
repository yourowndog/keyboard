# Layout Redesign TODO — Mod Row Toggle + Space Row Overhaul

## Goal
- Space row (comma/space/period/enter) is self-contained and always visible
- Long-press spacebar toggles the two mod rows on/off
- CTRL moves to bottom-right of mod row 2; emoji goes where CTRL was

---

## Phase 1 — Space Row: Add Enter, Fill Full Width

**Files:** `charactersMod/qwerty_wide_mod.json`, `charactersMod/qwerty_wide_default.json`

- [ ] Add `↵` (enter, code 10) to the end of mod row 0: `[,] [space] [.] [↵]`
- [ ] Set `flayWidthFactor` values so the row fills 100% width with no centering gap:
  - comma: 1.0, spacebar: 4.0, period: 1.0, enter: 1.3  (adjust to taste)
  - These keys should NOT respond to `modKeyWidthFactor` scaling (the row should always be full-width)
- [ ] Remove `↵` from the end of mod row 2
- [ ] Apply same change to `qwerty_wide_default.json` row 0

**TextKeyboard.kt consideration:**
The space row is currently treated as a pure mod row (all `isAlpha=false`).
With the new base-unitWidth approach, at `modKeyWidthFactor=1.0` it already fills
full width. The concern is if the user drops `modKeyWidthFactor` below 1.0 the whole
row shrinks and centers. Options:
  - A) Give the space row a dedicated "row type" that ignores the mod width slider
  - B) Accept that modKeyWidthFactor affects it (probably fine for now, revisit later)

---

## Phase 2 — Mod Row Key Swaps

**Files:** `charactersMod/qwerty_wide_mod.json`

- [ ] Row 1 (currently: `⇥ « ↑ » - ! " @ / CTRL`):
  - Replace `CTRL` (last position) with emoji switcher key
  - Emoji switcher code: `KeyCode.VIEW_MEDIA` (-202... actually check; may need new code)
  - Research: `KeyCode.kt` for existing emoji/media view code
- [ ] Row 2 (currently: `⎋ ← ↓ → ⚛ Σ λ Ψ paste ↵`):
  - Replace `↵` (last position) with `CTRL` (`{ "code": -1, "label": "CTRL", "type": "modifier" }`)

---

## Phase 3 — Long-Press Spacebar → Toggle Mod Rows

**Files:** `AppPrefs.kt`, `KeyboardManager.kt`, `LayoutManager.kt` (or `TextKeyboard.kt`)

### 3a — Preference
- [ ] Add `val modRowsVisible = boolean(key="keyboard__mod_rows_visible", default=true)` to `AppPrefs.kt` keyboard group

### 3b — Layout Filtering
Two options:
- **Option A (simpler):** In `TextKeyboard.kt layout()`, if `modRowsVisible=false`, skip rows where `r >= rowCount - bottomModRowCount`. Quick but doesn't change touch bounds for rows above.
- **Option B (cleaner):** In `LayoutManager.kt`, after building `computedArrangement`, drop the bottom `bottomModRowCount` rows if the pref is false. Then `bottomModRowCount=0`. Re-layout triggers naturally.

Recommend **Option B** — cleaner row count for height calculations.

### 3c — Long-Press Handler
- [ ] In `KeyboardManager.kt`, find where long-press events are dispatched
- [ ] Add case for `KeyCode.SPACE` long-press:
  ```kotlin
  KeyCode.SPACE -> {
      val current = prefs.keyboard.modRowsVisible.get()
      prefs.keyboard.modRowsVisible.set(!current)
      // trigger keyboard re-layout (same mechanism as number row toggle)
  }
  ```
- [ ] Confirm the re-layout mechanism (look at how `numberRow` toggle forces a rebuild)
- [ ] Prevent the long-press from also inserting a space character

---

## Phase 4 — Emoji Switcher Key (if not already covered by Phase 2)

- [ ] Identify correct KeyCode for switching to emoji/media panel
  - Likely `KeyCode.VIEW_MEDIA = -202` (already used as `⚛` system_gui key in row 2)
  - May need a different code if `⚛` and emoji are different
- [ ] Confirm the key renders with correct icon/label in `ComputingEvaluator.kt`

---

## Notes / Gotchas

- **Enter on space row means hiding mod rows never kills Enter** — that's the whole point of moving it up. ✓
- **Space row always visible** — even when mod rows are toggled off, user always has comma, space, period, enter.
- **flayWidthFactor for space is 5.0** (set in TextKey.kt based on `hasSlimSpaceRow`). With 4 keys total at factors [1.0, 5.0 (wait — see below), 1.0, 1.3], space dominates at 5/8.3 ≈ 60%. Adjust comma/enter factors to taste.
  - NOTE: `hasSlimSpaceRow = keyboard.bottomModRowCount >= 3`. With 2 mod rows (bottomModRowCount=2), space gets 5.0f. With the toggle active (0 mod rows), it still should get 5.0f — verify this doesn't break.
- **qwerty_wide_default.json** — this is the non-Fn-key variant. Apply same Phase 1 changes.
- **Do not use `placeholder` in mod row 0** unless you want ZXCVBNM merged in — the space row is intentionally placeholder-free (it completely replaces main row 3).

