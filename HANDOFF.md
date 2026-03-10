# Session Handoff — 2026-03-10

## Resume Command
```
cd ~/projects/keyboard-local && cc
> Read HANDOFF.md and continue where the previous session left off. I have ADB access now.
```

## What Was Done (3 commits on dev)

### Commit 1: `adcf2671` — Harvest review
- Synced 9,078 new harvest entries (since 2026-03-02)
- Added 17 new anti-corrections + updated 3 existing entries in PersonalPreferences.kt
- Added 8 dictionary words: cuz, bruh, nugs, dopesick, tomcat, deadhead, facetime, Kaylyn
- 51.5% autocorrect accuracy — nearly half of all corrections are wrong

### Commit 2: `3d8ae523` — PERSONAL_VOCAB
- Added `PERSONAL_VOCAB` set to `PersonalPreferences.kt` — words Sam types intentionally that should NEVER be corrected
- Case-insensitive check via `isPersonalVocab(typed)` → `PERSONAL_VOCAB.contains(typed.lowercase())`
- Current vocab: bc, rn, tf, lmk, ppl, msg, thx, sry, btw, imo, idk, omg, wtf, smh, ngl, tbh, fr, wdym, bday, pls, llms, cs, gen, config, ai, im, ya, def, tho, nah, ugh, oof, bruh, cuz, g, i
- Migrated 25+ abbreviations OUT of ANTI_CORRECTIONS into PERSONAL_VOCAB
- ANTI_CORRECTIONS now only contains genuine typo→bad-suggestion pairs (~30 entries)

### Commit 3: `434585e9` — Wire PERSONAL_VOCAB into fix()
- Added `isPersonalVocab()` check at very TOP of `SymSpellManager.fix()` — before CONTRACTION_SHORTCUTS and all hardcoded early returns
- Removed `"im"` from CONTRACTION_SHORTCUTS (now owned by PERSONAL_VOCAB)
- Removed hardcoded `"i"→"I"` single-char special case (PERSONAL_VOCAB handles it)
- Left `"s"→"a"`, `"ir"→"it"`, `"km"→"I'm"` hardcoded fixes in place (to be replaced by spatial cost model)

## Verified Working
- PERSONAL_VOCAB confirmed working after force-stop: "rn" no longer becomes "rnase"
- "tbe" → "the" autocorrect works (SymSpell edit distance 1, b↔h adjacent)
- Factory build succeeded, APK deployed

## CRITICAL OPEN BUGS — Debug With ADB

### Bug 1: Basic autocorrections missing entirely
- "tbis" → NO correction (should be "this")
- "agaib" → NO correction (should be "again")
- Both are edit distance 1 with adjacent keys. SymSpell SHOULD catch them.
- "tbe"→"the" DOES work, so SymSpell dictionary IS loaded
- Need logcat to diagnose: `adb logcat -s SymSpell CandidateScorer`

### Bug 2: Inconsistent corrections for same input
- "tbere" typed repeatedly gave: "the", "three", "there", "where", "there", "there", "where"
- This suggests scoring instability — possibly spatial cost + bigram context interaction

### Bug 3: MANUAL_FIX events still dark
- Zero MANUAL_FIX events in 9k+ entries
- Code was added in commit `8696906d` but keyboard hadn't been rebuilt until now
- Should start appearing with this build — verify in next harvest

### Bug 4: Password fields being logged
- Harvest log captures PASSWORD and PIN input types
- Security fix needed in HarvestManager.kt: skip logging when `inputType == PASSWORD`

## Planned Work (Not Yet Started)

### #3: Spatial Cost Model / Fat-Finger Fixes
- Three hardcoded hacks in SymSpellManager.fix() need to be replaced:
  - `if (lower == "s") return "a"` — s↔a adjacent key fat-finger
  - `if (lower == "ir") return "it"` — r↔t adjacent key fat-finger
  - `if (lower == "km") return "I'm"` — k↔i adjacent key fat-finger
- KeyboardLayout.kt already has Euclidean distance model (B↔H = 1.0)
- CandidateScorer.spatialCost() already uses it for scoring
- The gap: spatialCost only affects RANKING, not GENERATION — SymSpell generates candidates via edit distance, spatialCost just re-ranks them
- Root cause of missing corrections needs investigation with ADB logcat

### Space+Period Issue
- Typing space then period auto-deletes the space
- Sam sometimes wants a literal ". " (period with space before it)
- Need to find the auto-punctuation logic (likely in AbstractEditorInstance or KeyboardManager)

### Smart Bar Toggle
- Sam wants a button on the smart bar to toggle all corrections on/off
- Design discussion needed

## Key Files
- `PersonalPreferences.kt` — PERSONAL_VOCAB + ANTI_CORRECTIONS
- `CandidateScorer.kt` — scoring, spatial cost, bigram weighting
- `SymSpellManager.kt` — fix() autocorrect, suggest() suggestions, hardcoded typo fixes
- `KeyboardLayout.kt` — QWERTY coordinate model, keyDistance()
- `HarvestManager.kt` — event logging (password bug, MANUAL_FIX)
- `EditorInstance.kt` — MANUAL_FIX state machine

## Debug Commands (ADB)
```bash
# Watch autocorrect decisions live
adb logcat -s SymSpell CandidateScorer

# Filter for specific patterns
adb logcat | grep -E "SymSpell|CandidateScorer|PERSONAL|ANTI-CORRECTION"

# Test a specific word — type it in any text field and watch logcat
```
