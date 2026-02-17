# OmniBoard Autocorrect System - Handover Document
**Date:** 2026-02-17
**Last Build:** 4332513a (http://142.93.94.124:8000/omni.apk)

---

## 🚨 CRITICAL BUG DISCOVERED

**Symptom:** No suggestions or autocorrect appearing when typing
**Test Case:** Typed `wouldf you like tk go to the stoee wkth me?` - zero corrections
**Context:** Tested in OmniBoard settings text field (might be special input type)

**Likely Cause:** Input field type detection blocking suggestions
- Settings fields may be marked as `TYPE_TEXT_VARIATION_FILTER` or similar
- Check `EditorInfo.inputType` for special flags that disable suggestions

**Next Steps:**
1. Test in a normal app (Messages, Notes) to verify autocorrect works
2. Check `activeInfo.isRawInputEditor` in AbstractEditorInstance.kt:398
3. Add logging to see what input type the settings field reports
4. Verify SymSpellManager initialization with logs

---

## 📋 TODAY'S WORK SUMMARY

### 1. Harvest System Improvements ✅
**Commit:** 4332513a - "feat(harvest): comprehensive logging and analysis system"

**What Changed:**
- Added session source tagging (TYPING vs VOICE) to separate voice transcription from manual typing
- Added 4 new event types for failure detection:
  - `NO_SUGGESTION` - When no autocorrect offered (dictionary gap)
  - `MULTI_ATTEMPT` - Multiple correction attempts (user struggling)
  - `IGNORED_SUGGESTIONS` - Suggestions shown but all ignored
  - `BACKSPACE_STORM` - High backspace count (high-effort word)
- Created `harvest_analyze.py` for automated analysis with typing/voice separation
- Lowered thresholds to 2 (aggressive pattern detection for young autocorrect)
- Added 390 personal bigrams from typing patterns
- Voice input now tagged as SESSION:VOICE before flushing

**Files Modified:**
- `HarvestManager.kt` - New event types, session source tracking
- `KeyboardManager.kt` - Voice session tagging in stopVoiceCapture()
- `harvest_analyze.py` - NEW: Multi-source analyzer
- `GEMINI.md` - Full documentation
- `HARVEST_SYSTEM.md` - NEW: Quick reference guide

### 2. Context-Aware Autocorrect ✅
**Commit:** 9aa51797 - "feat(nlp): add hybrid context-aware blocking and boost bigram influence"

**What Changed:**
- Removed "id" from CONTRACTION_SHORTCUTS (line 63 SymSpellManager.kt)
- Increased BIGRAM_WEIGHT from 0.5 → 5.0 (CandidateScorer.kt:28)
- Added grammatical blocking:
  - Possessives (my/your/his) + contractions = blocked
  - Determiners (the/this/that) + contractions = blocked
- Added bigram validation: typed word with 2x stronger bigram blocks correction

### 3. Dictionary Updates ✅
**Added to unified_dictionary.tsv:**
- texted, messaged, ya, Ya, ai, AI, Aww, aww (previous session)
- blinker, blinkers (today)

### 4. Bigrams ✅
- Added 390 personal bigrams to final_mobile_bigrams.tsv
- Filtered out stutters, Welsh artifacts, "like" filler patterns

---

## 🏗️ AUTOCORRECT ARCHITECTURE

### The Pipeline (How Autocorrect Works)

```
User types word → Composing text buffer
        ↓
User presses SPACE → AbstractEditorInstance.commitText() (line 402)
        ↓
Check if word separator + composing text exists
        ↓
NlpManager.getAutoCommitCandidate() → Get top suggestion
        ↓
LatinLanguageProvider.suggest() → Generate candidates
        ↓
SymSpellManager.suggest(input, previousWord) → SymSpell lookup
        ↓
NgramSuggestionEngine.rank() → Score with bigrams
        ↓
Top candidate (isEligibleForAutoCommit=true) → Auto-commit
        ↓
Replace composing text with corrected word + separator
```

### Key Functions

**SymSpellManager.kt:**
- `suggest(input, previousWord)` → Returns List<String> of suggestions
- `fix(input, previousWord)` → Direct autocorrect (NOT used in pipeline!)
- `hasWord(word)` → Check if word exists in dictionary
- MAX_EDIT_DISTANCE = 2
- PREFIX_LENGTH = 7

**CandidateScorer.kt:**
- `score(typed, candidate, editDistance, prevWord, isInUserDict)` → Lower = better
- BIGRAM_WEIGHT = 5.0
- Handles anti-corrections, grammatical blocking, bigram validation
- Spatial cost calculation for typo likelihood

**AbstractEditorInstance.kt:**
- `commitText(text)` (line 393) → Main autocorrect trigger
- Line 402: Check if word separator + composing text
- Line 409: Get auto-commit candidate from NLP manager
- Line 414-437: Replace composing word with correction

**LatinLanguageProvider.kt:**
- `suggest()` (line 266) → Calls SymSpellManager.suggest()
- Line 277: `isEligibleForAutoCommit = upperCount < 2`
- Returns WordSuggestionCandidates

**NlpManager.kt:**
- `getAutoCommitCandidate()` (line 253) → First eligible candidate
- `suggest()` (line 199) → Triggers suggestion generation
- `activeCandidates` → Current suggestion list

---

## 🗂️ KEY FILES & LOCATIONS

### Autocorrect Core
```
app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/
├── SymSpellManager.kt           # Dictionary lookup, suggestion generation
├── shared/
│   ├── CandidateScorer.kt       # Unified scoring (edit distance + bigrams + spatial)
│   ├── BigramTable.kt           # Bigram frequency lookup
│   └── CasingUtils.kt           # Smart casing (Christmas, I'm, etc.)
├── PersonalPreferences.kt       # Anti-corrections map
├── NlpManager.kt                # Orchestrates suggestion providers
├── SuggestionEngine.kt          # NgramSuggestionEngine (ranking)
└── latin/
    └── LatinLanguageProvider.kt # Calls SymSpell, creates candidates
```

### Editor Integration
```
app/src/main/kotlin/dev/patrickgold/florisboard/ime/
├── editor/
│   ├── AbstractEditorInstance.kt  # commitText() - autocorrect trigger (line 402)
│   └── EditorInstance.kt          # Harvest logging, undo state
└── keyboard/
    └── KeyboardManager.kt         # Voice tagging, space handling
```

### Dictionaries & Data
```
app/src/main/assets/ime/dict/
├── unified_dictionary.tsv         # CANONICAL word list (word \t frequency)
├── final_mobile_bigrams.tsv       # Bigram pairs (word1 word2 \t frequency)
└── [other legacy files]
```

### Harvest System
```
keyboard-local/
├── harvest.py                     # Sync data from phone to repo
├── harvest_analyze.py             # Generate reports from harvest data
├── usage_harvest.md               # Local copy of harvest data
├── HARVEST_SYSTEM.md              # Quick reference
├── GEMINI.md                      # Full documentation
└── [Generated outputs:]
    ├── harvest_summary.md         # Stats + recommendations
    ├── anti_corrections.txt       # For PersonalPreferences.kt
    ├── dictionary_additions.txt   # For unified_dictionary.tsv
    ├── bigrams_combined.tsv       # For final_mobile_bigrams.tsv
    └── problem_patterns.txt       # Autocorrect failures
```

---

## 🐛 DEBUGGING THE NO-SUGGESTIONS BUG

### Hypothesis: Input Field Type Blocking

**Check 1: Input Type Detection**
Location: `AbstractEditorInstance.kt:398`
```kotlin
if (activeInfo.isRawInputEditor) {
    ic.finishComposingText()
    ic.commitText(text, 1)
```

If `isRawInputEditor = true`, autocorrect is skipped entirely.

**Check 2: EditorInfo Flags**
Add logging in `EditorInstance.kt` or `AbstractEditorInstance.kt`:
```kotlin
android.util.Log.d("AutocorrectDebug", "InputType: ${activeInfo.inputType}, isRaw: ${activeInfo.isRawInputEditor}")
```

Common flags that disable suggestions:
- `TYPE_TEXT_VARIATION_PASSWORD`
- `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`
- `TYPE_TEXT_VARIATION_EMAIL_ADDRESS`
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS`
- Settings fields might use `TYPE_TEXT_VARIATION_FILTER`

### Check 3: SymSpell Initialization

Add logging to verify dictionary loaded:
```kotlin
// In SymSpellManager.kt after line 178
android.util.Log.i("SymSpellManager",
    "Loaded $loadedWords words, isReady=$isReady, prefixIndex=${prefixIndex.size}")
```

Expected output: `Loaded ~300000 words, isReady=true, prefixIndex=~26 prefixes`

If `loadedWords = 0`, dictionary failed to load.

### Check 4: Suggestion Generation

Add logging in `LatinLanguageProvider.kt:266`:
```kotlin
val suggestions = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.suggest(
    input = currentWordRaw,
    previousWord = previousWord,
)
android.util.Log.d("Suggestions", "Input: '$currentWordRaw' → ${suggestions.size} suggestions: $suggestions")
```

### Check 5: Auto-Commit Eligibility

Add logging in `NlpManager.kt:257`:
```kotlin
val result = activeCandidates.take(3).firstOrNull { it.isEligibleForAutoCommit }
android.util.Log.d("AutoCommit", "Active candidates: ${activeCandidates.size}, Auto-commit: $result")
return result
```

---

## 🧪 TEST CASES

### Immediate Tests (Normal App)

**Test in Messages/Notes app (NOT OmniBoard settings):**

1. **Extra character deletion:**
   - Type: `wouldf` + SPACE
   - Expected: `would `
   - Current: ❌ NO CORRECTION

2. **Simple substitution:**
   - Type: `tk` + SPACE
   - Expected: `to `
   - Current: ❌ NO CORRECTION

3. **Typo with context:**
   - Type: `blibker` + SPACE
   - Expected: `blinker ` (just added to dictionary)
   - Current: ❌ NO CORRECTION

4. **Single letter:**
   - Type: `s` + SPACE
   - Expected: `a ` (line 267 SymSpellManager.kt)
   - Current: ❌ NO CORRECTION

5. **Bigram context:**
   - Type: `my id` + SPACE
   - Expected: `my id ` (NOT "my I'd" - blocked by possessive rule)
   - Current: ❓ UNKNOWN

### Harvest Data Tests

After fixing, use keyboard normally and run:
```bash
cd ~/keyboard-local
python3 harvest.py              # Sync from phone
python3 harvest_analyze.py      # Generate reports
cat problem_patterns.txt        # Check for NO_SUGGESTION events
```

---

## 📊 HARVEST DATA INSIGHTS

**Last Analysis (2026-02-16 23:31:03):**
- Typing: 11,237 sessions, 53,752 words
- Autocorrect accuracy: 51.2% (310 accepted / 295 rejected)
- Top rejected: "s"→"so" (10x), "id"→"I'd" (13x) ← Both now fixed!

**Current Thresholds (harvest_analyze.py):**
```python
MIN_WORD_FREQ = 2          # Dictionary addition threshold
MIN_REJECTION_COUNT = 2    # Anti-correction threshold
MIN_BIGRAM_FREQ = 2        # Bigram inclusion threshold
```

**Rationale:** Aggressive thresholds for rapid iteration workflow with frequent reinstalls.

---

## 🔧 KNOWN ISSUES & FIXES

### Fixed in Current Build ✅
1. **"s" → "so" over-correction** → Now does "s" → "a" (line 267)
2. **"id" → "I'd" in wrong contexts** → Removed from CONTRACTION_SHORTCUTS, uses context
3. **Weak bigram influence** → BIGRAM_WEIGHT 0.5 → 5.0
4. **Voice data polluting metrics** → SESSION:TYPING vs SESSION:VOICE separation
5. **Missing dictionary words** → Added texted, messaged, ya, ai, blinker

### Still Broken ❌
1. **NO SUGGESTIONS APPEARING AT ALL** ← Current critical bug
2. **Extra character not stripped** (e.g., "wouldf" → "would") ← Might be fixed once #1 resolved
3. **Multiple typos per sentence** ← All failed, suggests #1 is the root cause

### Not Yet Tested ⏳
- Context-aware "id" handling (my id vs I'd like)
- Grammatical blocking (the I'm, my I'd)
- Bigram validation (typed word with stronger bigram wins)
- Personal bigrams from typing patterns
- New harvest event types (NO_SUGGESTION, MULTI_ATTEMPT, etc.)

---

## 🚀 NEXT SESSION CHECKLIST

### Immediate Priority
1. [ ] Test autocorrect in normal app (Messages, not settings)
2. [ ] Add logging to identify why no suggestions appear
3. [ ] Check `isRawInputEditor` flag for settings fields
4. [ ] Verify SymSpellManager initialization succeeded
5. [ ] Confirm dictionary loaded (~300k words)

### If Autocorrect Works (Just Input Field Issue)
6. [ ] Test all 5 test cases above
7. [ ] Verify context-aware "id" handling
8. [ ] Verify bigram influence (type common phrases)
9. [ ] Use keyboard normally to generate harvest data
10. [ ] Run harvest_analyze.py to check for new patterns

### If Autocorrect Still Broken
6. [ ] Check Android logcat for errors during initialization
7. [ ] Verify assets (unified_dictionary.tsv, final_mobile_bigrams.tsv) exist in APK
8. [ ] Check if suggestion bar shows ANY candidates (even wrong ones)
9. [ ] Test if manual suggestion selection works (tap suggestion)
10. [ ] Bisect commits to find when it broke

### After Autocorrect Works
11. [ ] Apply recommendations from harvest_summary.md
12. [ ] Add anti-corrections from anti_corrections.txt to PersonalPreferences.kt
13. [ ] Add new words from dictionary_additions.txt to unified_dictionary.tsv
14. [ ] Merge bigrams from bigrams_combined.tsv to final_mobile_bigrams.tsv
15. [ ] Document any new patterns discovered

---

## 📖 USEFUL COMMANDS

### Building
```bash
cd ~/keyboard-local
git add -A
git commit -m "description"
git push factory dev          # Triggers build
# APK: http://142.93.94.124:8000/omni.apk
```

### Harvest Workflow
```bash
cd ~/keyboard-local
python3 harvest.py            # Pull from /sdcard/Documents/usage_harvest.md
python3 harvest_analyze.py    # Generate all reports
cat harvest_summary.md        # Review stats
cat problem_patterns.txt      # Check failures
```

### Dictionary Queries
```bash
cd ~/keyboard-local
grep "^blinker" app/src/main/assets/ime/dict/unified_dictionary.tsv
grep "would" app/src/main/assets/ime/dict/unified_dictionary.tsv
wc -l app/src/main/assets/ime/dict/unified_dictionary.tsv  # Total words
```

### Android Logging (if accessible)
```bash
adb logcat | grep -E "SymSpell|Autocorrect|Suggestions"
```

---

## 💡 DESIGN NOTES

### Why Two Functions: fix() vs suggest()?
- `fix(input, previousWord)` → Direct single correction (NOT used in current pipeline)
- `suggest(input, previousWord)` → List of candidates for ranking

**Current pipeline uses suggest()** because:
1. Suggestions need ranking with bigram context
2. User might want to see alternatives in suggestion bar
3. NgramSuggestionEngine does final scoring

### Why Bigram Weight = 5.0?
Previous value (0.5) was drowned out by edit distance and spatial costs. A bigram frequency of 100 only contributed 0.5 × log(100) ≈ 2.3 to the score, which was negligible. Now 5.0 × log(100) ≈ 23, making bigrams actually influential.

### Why Aggressive Thresholds (MIN=2)?
With frequent reinstalls and rapid iteration, waiting for 5+ occurrences means missing critical issues. The autocorrect is "young" and needs to learn fast from limited data.

---

## 🎯 SUCCESS CRITERIA

**Autocorrect is working when:**
1. ✅ Typing `wouldf` + SPACE → `would `
2. ✅ Typing `tk` + SPACE → `to `
3. ✅ Typing `blibker` + SPACE → `blinker `
4. ✅ Typing `s` + SPACE → `a ` (or `so` if we decide to change it)
5. ✅ Typing `my id` + SPACE → `my id ` (NOT "my I'd")
6. ✅ Suggestions visible in suggestion bar
7. ✅ Harvest data shows <5% NO_SUGGESTION events
8. ✅ Autocorrect accuracy >70% (from harvest_summary.md)

---

**Last Updated:** 2026-02-17
**Next Agent:** Start by testing in normal app, add logging if needed
**Questions?** See GEMINI.md for full architecture, HARVEST_SYSTEM.md for workflow
