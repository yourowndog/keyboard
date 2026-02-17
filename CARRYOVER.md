# OmniBoard Intelligence Upgrade — Agent Carryover Document
**Date:** 2026-02-17
**Branch:** `feature/whisper-bar`
**Last Commit:** `727656ed` — "Add phrase prediction, continuous spatial model, and personal phrase learning"
**Build Status:** Compiles and installs. Phrase row has a known bug (documented below with fix).

---

## WHAT JUST HAPPENED (TL;DR)

We implemented a three-phase intelligence upgrade to OmniBoard's autocorrect system:

1. **Phrase Prediction Row** — A second SmartBar row that shows multi-word phrase continuations (e.g., after "what's " → shows "up man", "going on", "the deal"). User taps to accept the whole phrase.
2. **Continuous Spatial Model** — Replaced binary adjacent/far key distance with Euclidean distance using QWERTY coordinates. Now 'r'→'e' costs ~1.0 instead of 2.0. Better fat-finger correction.
3. **Personal Phrase Learning** — New `PhraseTable.kt` singleton + `harvest_analyze.py` phrase extraction. Personal typing patterns get priority over generic bigram chains.

---

## 🚨 KNOWN BUG: PHRASE ROW NOT SHOWING

### Symptom
After typing "what's " (or any word with an apostrophe), the phrase prediction row does not appear. It may also not appear for other words.

### Root Cause (DIAGNOSED)
**Two bugs in NlpManager.kt:**

**Bug 1: `getPreviousWord()` rejects apostrophes (LINE 437)**
```kotlin
// CURRENT (BROKEN):
return lastWord.takeIf { it.isNotEmpty() && it.all { c -> c.isLetter() } }
// 'c.isLetter()' returns false for apostrophe!
// So "what's" → returns null → no phrase predictions
```

**Fix:**
```kotlin
// CORRECT:
return lastWord.takeIf { it.isNotEmpty() && it.all { c -> c.isLetter() || c == '\'' } }
```

Note: LatinLanguageProvider's `lastWordBefore()` (line 325) correctly includes apostrophes via `Regex("([A-Za-z']+)")`. That's why single-word next-word predictions work but phrase predictions don't — they use different previous-word extraction methods.

**Bug 2: Phrase prediction extracts prevWord from `editorInstance.activeContent` (via `getPreviousWord()`) instead of from the `content` parameter already passed to `suggest()`.**

The phrase prediction code at NlpManager.kt line 298 should extract the previous word from the `content` parameter (which is guaranteed to be the current editor state) rather than re-reading from `editorInstance.activeContent` (which may have a race condition). Here's the better approach — replace the phrase prediction block's prevWord extraction with:

```kotlin
// Extract previous word from content parameter (same approach as LatinLanguageProvider)
val textBefore = content.textBeforeSelection.toString()
val trimmedBefore = textBefore.trimEnd()
val prevWordMatch = Regex("([A-Za-z']+)[^A-Za-z']*$").find(trimmedBefore)
val prevWord = prevWordMatch?.groupValues?.getOrNull(1)
```

### Location of phrase prediction code in NlpManager.suggest()
File: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
Lines: ~295-335 (inside the `scope.launch` block, after the `suggestions` variable is populated)

### How to verify the fix
1. Apply the fix above
2. Type "what's " (with space) in a messaging app
3. A second row should animate in below the main suggestion row
4. It should show phrases like "up man", "going on", "wrong with"
5. Tapping a phrase should commit it with a trailing space

---

## WHAT HAS NOT BEEN DONE YET

### harvest_analyze.py has NOT been run
The personal phrase extraction pipeline exists but has never been executed. This means:
- `personal_phrases.tsv` does **not exist** in assets yet
- `PhraseTable` loads silently as null (this is fine — it falls back to BigramTable chaining)
- The harvest data (`usage_harvest.md`) has been accumulating on the device but hasn't been pulled or analyzed

### To run the full harvest → data cycle:
```bash
# 1. Pull harvest data from device (if not already local)
python3 harvest.py

# 2. Run the analyzer (generates personal_phrases.tsv + other outputs)
python3 harvest_analyze.py

# 3. Review harvest_summary.md for insights

# 4. Copy personal phrases into assets for PhraseTable
cp personal_phrases.tsv app/src/main/assets/ime/dict/personal_phrases.tsv

# 5. Also merge new bigrams if desired
cat bigrams_combined.tsv >> app/src/main/assets/ime/dict/final_mobile_bigrams.tsv
# Dedup:
sort -t$'\t' -k2 -nr app/src/main/assets/ime/dict/final_mobile_bigrams.tsv | \
  awk -F'\t' '!seen[$1]++' > /tmp/bigrams_clean.tsv && \
  mv /tmp/bigrams_clean.tsv app/src/main/assets/ime/dict/final_mobile_bigrams.tsv

# 6. Apply anti-corrections from anti_corrections.txt to PersonalPreferences.kt

# 7. Apply dictionary additions from dictionary_additions.txt to unified_dictionary.tsv

# 8. Rebuild and push
```

---

## FILES MODIFIED IN THIS UPGRADE

### Must-read files (the core changes):

| File | What Changed | Lines of Interest |
|------|-------------|-------------------|
| **`BigramTable.kt`** | Added `predictPhrases()` method | Lines 64-106: bigram chaining with frequency decay |
| **`NlpManager.kt`** | Added `phraseCandidatesFlow` + phrase generation in `suggest()` | Lines 136-155: new StateFlow; Lines 295-335: phrase prediction block (HAS BUG) |
| **`KeyboardLayout.kt`** | Replaced binary neighbors with coordinate-based Euclidean distance | Entire file rewritten (~103 lines). `QWERTY_POSITIONS`, `keyDistance()`, lazy `QWERTY_NEIGHBORS` |
| **`CandidateScorer.kt`** | Updated `spatialCost()` to use continuous distance | Lines 191-230: now calls `KeyboardLayout.keyDistance()` directly instead of checking neighbor map |
| **`Smartbar.kt`** | Added `SmartbarPhraseRow()` composable, integrated into all 3 layout modes | Lines 238-274: phrase row added to each placement mode; Lines 518-565: the `SmartbarPhraseRow` composable |
| **`SymSpellManager.kt`** | Added PhraseTable loading at init step 6b | Lines 195-201: try-catch guarded PhraseTable.load() |
| **`harvest_analyze.py`** | Added `extract_phrases()` method + `personal_phrases.tsv` output | Lines 157-203: phrase extraction; Lines 383-390: TSV output |

### New file created:

| File | Purpose |
|------|---------|
| **`PhraseTable.kt`** (`ime/nlp/shared/`) | Singleton for personal phrase predictions. Loads from `personal_phrases.tsv`. Keyed by 2-word lowercase context → list of continuations ranked by frequency. |

### Documentation updated:

| File | What |
|------|------|
| **`AUTOCORRECT_FLOW.md`** | Complete rewrite — now covers all 3 tiers (single-word, phrases, learning), spatial model details, file reference map, debugging checklist, developer guide |

---

## HOW THE PHRASE PREDICTION SYSTEM WORKS

### Data Flow
```
User types "what's " (space)
         │
NlpManager.suggest() called with blank composingText
         │
         ├─── PhraseTable.predictContinuation("said", "what's")
         │    (personal phrases — returns empty if no personal_phrases.tsv)
         │
         ├─── BigramTable.predictPhrases("what's")
         │    Chains: "what's"→"up"(500k)→"man"(50k) = "up man"
         │            "what's"→"wrong"(350k)→"with"(?) = "wrong with"
         │            "what's"→"going"(300k)→"on"(602) = "going on"
         │
         ▼
phraseCandidatesFlow updated → SmartbarPhraseRow animates in
         │
User taps "up man" → commitCompletion("up man") → "up man " inserted
```

### BigramTable.predictPhrases() Algorithm
1. Get top 3 followers for the previous word
2. For each follower, recursively chain by finding the best next follower
3. **Frequency decay threshold**: each link must have freq ≥ 10% of the INITIAL word's max follower freq
4. **Loop prevention**: a word can't appear twice in the same chain
5. **Minimum length**: only return chains with ≥2 words (otherwise it's just a next-word prediction)
6. **Max length**: stop at 6 words

### PhraseTable format (`personal_phrases.tsv`)
```
i'll be	there in a minute	8
what's up	man	15
see you	later	12
on my	way	9
```
Tab-separated: `context\tcontinuation\tfrequency`

---

## HOW THE SPATIAL MODEL WORKS

### Before (binary — KeyboardLayout.kt OLD)
```
keyDistance(a, b):
  same key → 0.0
  adjacent → 0.5  (checked via QWERTY_NEIGHBORS map)
  everything else → 2.0  (r→e same as r→m!)
```

### After (continuous — KeyboardLayout.kt NEW)
```
QWERTY_POSITIONS: each key has (x, y) coordinates with row stagger
  Row 0: no offset     (q=0,0  w=1,0  e=2,0 ...)
  Row 1: 0.5 offset    (a=0.5,1  s=1.5,1 ...)
  Row 2: 1.5 offset    (z=1.5,2  x=2.5,2 ...)

keyDistance(a, b) = √((x₁-x₂)² + (y₁-y₂)²)  clamped to [0.0, 2.0]
```

### Impact on CandidateScorer.spatialCost()
Old code checked `QWERTY_NEIGHBORS` map → binary 0.5 or 2.0
New code calls `KeyboardLayout.keyDistance()` → continuous 0.0-2.0

No other scoring constants needed retuning because the output range is the same [0, 2].

### QWERTY_NEIGHBORS is preserved
The `QWERTY_NEIGHBORS` map is now computed lazily from `QWERTY_POSITIONS` (threshold < 1.5). Any code that accesses `QWERTY_NEIGHBORS` still works. `isAdjacent()` also still works.

---

## THE FULL SYSTEM ARCHITECTURE (Current State)

```
USER INPUT (keystroke/space/tap)
     │
     ▼
AbstractEditorInstance
  - Composing buffer, phantom space, undo state
  - commitCompletion() handles single AND multi-word text identically
     │
     ▼
NlpManager (Central Orchestrator)
  - Two output flows:
    * activeCandidatesFlow  → CandidatesRow (main suggestions)
    * phraseCandidatesFlow  → SmartbarPhraseRow (multi-word phrases)  ← NEW
     │
     ├──── LatinLanguageProvider.suggest()
     │       │
     │       ├── [Blank input] → next-word predictions via BigramTable
     │       │
     │       ├── [Special] → "i"→"I", contractions (dont→don't)
     │       │
     │       └── [Normal word] →
     │             SymSpellManager.findPrefixCandidates()  (autocomplete)
     │             SymSpellManager.findCandidates()         (typo correction)
     │                   │
     │                   ▼
     │             NgramSuggestionEngine.rank()
     │               CandidateScorer.score()
     │                 - Edit distance (base)
     │                 - spatialCost() ← NOW CONTINUOUS EUCLIDEAN  ← UPGRADED
     │                 - Bigram context bonus
     │                 - Apostrophe/exact/user-dict bonuses
     │                 - Anti-correction filter
     │                 - Grammar blocking
     │                   │
     │                   ▼
     │             CasingUtils.applyPredictedCasing()
     │             Valid Word Immunity check
     │             Prepend raw typed word (iOS style)
     │
     ├──── Phrase Prediction Engine (inside NlpManager.suggest)  ← NEW
     │       │
     │       ├── PhraseTable.predictContinuation()  (personal first)
     │       └── BigramTable.predictPhrases()       (bigram chaining fallback)
     │
     ▼
SMARTBAR UI
  ┌─────────────────────────────────────────┐
  │ Main Row: [ typed ] [ fix ] [ complete ]│  ← activeCandidatesFlow
  ├─────────────────────────────────────────┤
  │ Phrase Row: [ up man ] [ going on ]     │  ← phraseCandidatesFlow (animated)
  └─────────────────────────────────────────┘
     │
     ▼
HarvestManager → usage_harvest.md → harvest_analyze.py
  → anti_corrections.txt, dictionary_additions.txt,
    bigrams_combined.tsv, personal_phrases.tsv  ← NEW
```

---

## CRITICAL FILE PATHS

### Kotlin Source (under `app/src/main/kotlin/dev/patrickgold/florisboard/`)
| File | Role |
|------|------|
| `ime/nlp/NlpManager.kt` | Central orchestrator, phrase flow, previous word extraction |
| `ime/nlp/latin/LatinLanguageProvider.kt` | Main suggestion pipeline entry point |
| `ime/nlp/SymSpellManager.kt` | Dictionary engine, prefix/edit lookups, init sequence |
| `ime/nlp/shared/BigramTable.kt` | Bigram data singleton, `predictNext()`, `predictPhrases()` |
| `ime/nlp/shared/PhraseTable.kt` | Personal phrase singleton (NEW) |
| `ime/nlp/shared/CandidateScorer.kt` | Unified penalty scorer, spatial cost, bigram scoring |
| `ime/nlp/shared/CasingUtils.kt` | Casing logic, contraction shortcuts |
| `ime/nlp/PersonalPreferences.kt` | Anti-corrections map |
| `ime/core/KeyboardLayout.kt` | QWERTY coordinates, Euclidean distance |
| `ime/smartbar/Smartbar.kt` | SmartBar UI: main row + phrase row + whisper bar |
| `ime/smartbar/CandidatesRow.kt` | Individual candidate items, tap/long-press handlers |
| `ime/editor/EditorInstance.kt` | `commitCompletion()` — commits any text string |

### Assets (under `app/src/main/assets/ime/dict/`)
| File | Format | Purpose |
|------|--------|---------|
| `unified_dictionary.tsv` | `word\tfrequency` | Main dictionary (~50k words) |
| `final_mobile_bigrams.tsv` | `word1 word2\tfrequency` | Bigram pairs (~5k+ entries) |
| `personal_phrases.tsv` | `context\tcontinuation\tfrequency` | Personal phrases (DOES NOT EXIST YET) |

### Scripts (project root)
| File | Purpose |
|------|---------|
| `harvest.py` | Pull harvest data from device |
| `harvest_analyze.py` | Analyze harvest data, generate all output files including `personal_phrases.tsv` |

### Documentation (project root)
| File | Purpose |
|------|---------|
| `AUTOCORRECT_FLOW.md` | **THE comprehensive technical reference** — architecture, pipeline, spatial model, phrase system, debugging, developer guide |
| `CLAUDE.md` | Build commands, module architecture, development workflow |
| `CARRYOVER.md` | This file — session context for agent continuity |

---

## IMMEDIATE TODO LIST (Priority Order)

### 1. Fix the Phrase Row Bug (5 minutes)
**File:** `NlpManager.kt` lines ~295-335
**Fix:** Replace the `getPreviousWord(subtype)` call in the phrase prediction block with direct extraction from `content` that includes apostrophes. See "KNOWN BUG" section above for exact code.

### 2. Run Harvest Analysis (10 minutes)
```bash
python3 harvest.py          # pull data from device
python3 harvest_analyze.py  # generates personal_phrases.tsv + other outputs
```
Then copy `personal_phrases.tsv` to `app/src/main/assets/ime/dict/`.

### 3. Apply Harvest Recommendations
- Review `harvest_summary.md`
- Add anti-corrections to `PersonalPreferences.kt`
- Merge `bigrams_combined.tsv` into `final_mobile_bigrams.tsv`
- Add words from `dictionary_additions.txt` to `unified_dictionary.tsv`

### 4. Test After Fix
- Type "what's " → verify phrase row appears with "up man", "going on"
- Type "going " → verify phrase row appears with "to be", "on" chains
- Type mid-word → verify phrase row hides
- Tap phrase → verify full phrase commits with trailing space
- Type "thr" → verify "the" appears (spatial model test)
- Type "wirh" → verify "with" appears (spatial model test)
- Normal autocorrect regression: "becuase"→"because", "dont"→"don't", "im"→"I'm"

### 5. Future Enhancements (When Ready)
- Lower the frequency decay threshold (currently 10%) if chains are too short
- Add casing to phrase predictions (currently all lowercase)
- Consider showing phrase count or visual indicator
- Automate the harvest → rebuild cycle (currently manual)
- Profile phrase prediction performance on low-end devices

---

## DESIGN PRINCIPLES (Don't Break These)

1. **Lower score = better candidate** — all scoring is penalty-based and additive
2. **Never auto-commit phrases** — `isEligibleForAutoCommit = false` always for phrases
3. **Personal > Generic** — PhraseTable before BigramTable chaining
4. **Fail silently** — every data load is try-catch guarded
5. **commitCompletion() is word-count-agnostic** — it's just `candidate.text.toString()`
6. **Spatial distance is physical** — Euclidean from real QWERTY coordinates
7. **The harvest system is the memory** — the keyboard learns through the data cycle
