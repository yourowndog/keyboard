# Session Handoff — Space Row Redesign (Phase 1 Complete) — 2026-03-10

## Resume Command
```
cd ~/projects/keyboard-local && cc
> Test the APK and start Phase 2 (long-press spacebar toggle for mod rows)
```

---

## Session Summary: Space Row Layout Redesign

**Goal:** Redesign the keyboard space row to be centered with natural margins, immune to width sliders, and to include the Enter key.

**Result:** ✅ Phase 1 COMPLETE — Space row layout finalized and built.

---

## What Was Done (1 commit: `03c648fa`)

### Commit: Space row redesign
**Files changed:**
- `TextKeyboard.kt` — Added space row detection + layout immunity logic
- `TextKey.kt` — Already had `hasSlimSpaceRow >= 2` (spacebar gets 5.0f width factor)
- `qwerty_wide_mod.json` — Layout: row 0 is now `[,][space][.][↵]`, CTRL→emoji swap

**Implementation details:**
1. **Space row detection** via `row.any { it.computedData.code == 32 }` — identifies rows containing the SPACE key at layout time
2. **Base unit width** — space row uses `baseAlphaUnitWidth` instead of per-row calculation
3. **Slider immunity** — space row widthFactor fixed at `1.0f`, unaffected by either `alphaKeyWidthFactor` or `modKeyWidthFactor`
4. **Spacing** — uses `alphaSpacingH/V` for key gaps (matches alpha rows)
5. **Key arrangement:**
   - Row 0 (new): `[,][space][.][↵]` — 4-key space/punctuation row with Enter
   - Row 1: CTRL → emoji panel key (code `-212`, SentimentSatisfiedAlt icon)
   - Row 2: Enter → CTRL (CTRL moves to bottom-right)

**Factory build result:** ✅ SUCCESS — APK deployed to `http://142.93.94.124:8000/omni.apk`

---

## How It Works

### Width/Spacing Math
- Alpha rows: 10 units (e.g., QWERTY) → reference width `baseAlphaUnitWidth`
- Space row: ~8.3 units `[1 + 5 + 1 + 1.3]` → uses same `baseAlphaUnitWidth`
- Result: Space row is narrower than screen, so centering adds side margins automatically
- Immunity: `widthFactor=1.0f` for all space row keys → sliders don't affect it

### Key Codes
- Enter: code `10`, type `enter_editing`
- Emoji panel: code `-212`, type `system_gui` (already fully wired in codebase)
- CTRL: code `-1`, type `modifier`

---

## Verified Working
✅ Code compiles cleanly
✅ Factory build passed all tasks
✅ Emoji key already has icon + action handler (no new wiring needed)
✅ APK deployed successfully

---

## Next Steps (Pending)

### Phase 2: Long-Press Spacebar Toggle (Mod Rows)
- [ ] Add boolean preference in `AppPrefs.kt` (e.g., `modRowsVisible`)
- [ ] In `LayoutManager.kt`, filter bottom mod rows when pref is false
- [ ] In `KeyboardManager.kt`, wire long-press SPACE to toggle and request relayout
- [ ] Test show/hide animation

### Testing (Before commit)
- [ ] Visually test APK — space row should have margins, 4 keys, no gaps
- [ ] Test width sliders — alpha and mod rows should shrink independently, space row unchanged
- [ ] Test emoji panel button — smiley icon appears, opens emoji/media panel on tap
- [ ] Test CTRL position — should be bottom-right of mod rows
- [ ] Period long-press — should show only `!` and `?` (no `_`)

---

## Key Code Sections

**TextKeyboard.kt (lines 105–169):**
- Space row detection: `val isSpaceRow = !isAlphaRow && row.any { it.computedData.code == 32 }`
- Slider immunity: `widthFactor = when { ... isSpaceRow -> 1.0f ... }`
- Spacing: `mH = when { ... isSpaceRow -> alphaSpacingH ... }`

**qwerty_wide_mod.json:**
```json
Row 0: [,][space][.][↵]
Row 1: [⇥][«][↑][»][-][!]["][@][/][😊]  // emoji replaces CTRL
Row 2: [⎋][←][↓][→][⚛][Σ][λ][Ψ][↩][CTRL] // CTRL added
```

---

## File Paths
- `app/src/main/kotlin/.../ime/text/keyboard/TextKeyboard.kt` — layout logic
- `app/src/main/assets/ime/keyboard/.../charactersMod/qwerty_wide_mod.json` — layout JSON
- `app/src/main/kotlin/.../ime/text/key/KeyCode.kt` — code constants (reference: IME_UI_MODE_MEDIA = -212)
- `app/src/main/kotlin/.../ime/nlp/AppPrefs.kt` — preferences (for Phase 2)
- `app/src/main/kotlin/.../ime/keyboard/LayoutManager.kt` — row filtering (for Phase 2)
