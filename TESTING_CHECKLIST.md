# Dev Branch Testing Checklist (2026-03-03)

Everything unstaged on `dev` since last commit. Build first, then work through these.

---

## 1. Ctrl/Toggle Visual States (Core Change)
**Files:** `FlorisImeUi.kt`, `TextKeyboardLayout.kt`

Old debug hacks removed (`:pressed` forced on toggles, `:focus` for Ctrl). Now uses real Snygg attributes: `ctrlstate`, `numberrowstate`, `devrowstate`.

### Ctrl key
- [ ] Single-tap Ctrl — highlights immediately in theme accent
- [ ] Type a letter — Ctrl clears back to default (sticky, not locked)
- [ ] Double-tap Ctrl — distinct "locked" styling (glow/shadow in Neon/Tactical/Sickbay, color swap in Classic)
- [ ] Type several keys while locked — stays highlighted
- [ ] Tap Ctrl again — clears to default

### Number Row toggle (-305)
- [ ] Default state: no highlight
- [ ] Tap to enable: key lights up in accent color, row appears
- [ ] Tap to disable: key returns to default, row hides

### Dev Row toggle (-306)
- [ ] Same as number row — lit when active, default when off

### Regression check
- [ ] Toggle keys are NOT permanently highlighted when rows are off (old debug artifact)
- [ ] `:pressed` (finger-down flash) still works on all keys including these three

---

## 2. Theme-Specific Toggle Colors

Test Ctrl active/locked + both toggle active states in each theme:

| Theme | Active Color | Locked Extra |
|-------|-------------|-------------|
| **Classic** | `--secondary` (cyan) | `--primary-variant` bg + cyan text |
| **Neon** | `--cyan-bright` + border | + 12dp cyan glow |
| **Tactical** | `--tac-amber-gold` + border | + 8dp amber glow |
| **Sickbay** | `--med-accent-teal` | + 4dp teal shadow |

- [ ] Classic
- [ ] Neon
- [ ] Tactical
- [ ] Sickbay

---

## 3. Smartbar / Clipboard / Media Visibility (Classic, Neon, Tactical)
**Files:** `lcars.json`, `lcars_neon.json`, `lcars_tactical.json`

These themes got full selector coverage for areas that were previously black-on-black.

- [ ] **Smartbar suggestions** — visible text, not invisible/black-on-black
- [ ] **Suggestion pressed state** — different background on tap
- [ ] **Clipboard panel** — header, items, pin/delete icons all visible
- [ ] **Emoji panel** — emoji grid visible, category tabs visible, active tab highlighted
- [ ] **Smartbar actions editor** — panel background, header, section labels all readable

Test in: Classic, Neon, Tactical (Sickbay didn't get these additions)

---

## 4. Bigram Changes
**File:** `final_mobile_bigrams.tsv`

Re-ranked from harvest data + 14 new low-frequency entries.

- [ ] Type "you " — "know" ranks high
- [ ] Type "and " — "then" appears
- [ ] Type "it's " — "like" shows up
- [ ] General suggestion quality not regressed

---

## 5. Sanity
- [ ] Keyboard doesn't crash on launch
- [ ] Switching between all 4 LCARS themes: no crash
- [ ] Autocorrect still works
- [ ] Snygg docs updated (SNYGG_REFERENCE.md, SNYGG_CHEATSHEET.md) — no action needed, just FYI
