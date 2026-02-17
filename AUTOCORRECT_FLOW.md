# OmniBoard Intelligence System
## Autocorrect, Prediction & Learning Pipeline

**Last Updated:** 2026-02-17 (Intelligence Upgrade: Phrase Prediction + Spatial Model + Personal Phrases)

---

## 1. System Anatomy

OmniBoard's NLP pipeline has three tiers that work in concert:

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER INPUT                               │
│                   (keystroke / space / tap)                      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              AbstractEditorInstance                              │
│  Manages composing buffer, phantom space, undo state            │
│  commitCompletion() → commits ANY text (single or multi-word)   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      NlpManager                                 │
│  Central orchestrator. Coordinates all providers.               │
│                                                                 │
│  Two output flows:                                              │
│    activeCandidatesFlow  → main suggestion row (single words)   │
│    phraseCandidatesFlow  → phrase prediction row (multi-word)   │
└──────────────┬────────────────────────────┬─────────────────────┘
               │                            │
               ▼                            ▼
┌──────────────────────────┐  ┌─────────────────────────────────┐
│  LatinLanguageProvider   │  │     Phrase Prediction Engine     │
│  (single-word pipeline)  │  │  (runs inside NlpManager.suggest)│
│                          │  │                                 │
│  ┌────────────────────┐  │  │  1. PhraseTable (personal)      │
│  │ SymSpellManager    │  │  │     "i'll be" → "there soon"   │
│  │ (The Retriever)    │  │  │                                 │
│  │ - Prefix lookup    │  │  │  2. BigramTable.predictPhrases  │
│  │ - Edit-dist lookup │  │  │     (chained bigram fallback)   │
│  └────────┬───────────┘  │  │     "what's"→"up"→"man"        │
│           ▼              │  └──────────────┬──────────────────┘
│  ┌────────────────────┐  │                 │
│  │ NgramSuggestion    │  │                 │
│  │ Engine (The Judge) │  │                 │
│  │ - CandidateScorer  │  │                 │
│  │ - Spatial model    │  │                 │
│  │ - Bigram context   │  │                 │
│  └────────┬───────────┘  │                 │
│           ▼              │                 │
│  ┌────────────────────┐  │                 │
│  │ CasingUtils        │  │                 │
│  │ (The Caser)        │  │                 │
│  └────────────────────┘  │                 │
└──────────┬───────────────┘                 │
           │                                 │
           ▼                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                         SMARTBAR UI                             │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Main Row (CandidatesRow)                                  │  │
│  │  [ typed ] [ correction ] [ completion ]                  │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Phrase Row (SmartbarPhraseRow) — animated show/hide       │  │
│  │  [ up man ]  [ going on ]  [ the deal ]                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Tap main row → commits single word + space                     │
│  Tap phrase row → commits full phrase + space                   │
│  Both go through commitCompletion() — it's just a string        │
└─────────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────┐
│                     HarvestManager                              │
│  Logs: ACCEPTED, REJECTED, INSISTED, NO_SUGGESTION, etc.       │
│  Feeds into: harvest_analyze.py → dictionary/bigram/phrase data │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Initialization Sequence

Components load asynchronously during startup. The order matters.

| Step | Component | What Loads | Asset File |
|------|-----------|-----------|------------|
| 1 | FlorisImeService | Starts IME service | — |
| 2 | NlpManager.preload() | Triggers provider init | — |
| 3 | SymSpellManager.init() | SymSpell engine + dictionary | `ime/dict/unified_dictionary.tsv` |
| 4 | SymSpell bigrams | Bigram data into SymSpell holder | `ime/dict/final_mobile_bigrams.tsv` |
| 5 | **BigramTable.load()** | Shared singleton for context scoring | `ime/dict/final_mobile_bigrams.tsv` |
| 6 | **PhraseTable.load()** | Personal phrases (silent fail if missing) | `ime/dict/personal_phrases.tsv` |
| 7 | User Overrides | Force-inject personal words (Claira, GPU, etc.) | Hardcoded in SymSpellManager |
| 8 | Prefix Index | Build 1-3 char prefix map for autocomplete | Built from loaded dictionary |
| 9 | NgramSuggestionEngine | Load unigram log-frequencies for ranking | `ime/dict/unified_dictionary.tsv` |

**Crash Protection:** Every loading step is wrapped in try-catch. A failure in step 6 (PhraseTable) doesn't affect steps 5 or 7. Learned the hard way — a single corrupted bigram entry once killed the entire autocorrect pipeline.

---

## 3. The Single-Word Pipeline (Detail)

Triggered every time the user types a character. Runs in `LatinLanguageProvider.suggest()`.

### 3.1 Early Returns & Shortcuts

Before the engine runs, we check for special cases:

| Input | Result | Why |
|-------|--------|-----|
| Blank (just hit space) | Next-word predictions from BigramTable | User finished a word, predict what comes next |
| `i` | Force to `I` | SymSpell doesn't return single-char candidates |
| `dont`, `im`, `wont` | `don't`, `I'm`, `won't` | Contraction shortcuts bypass engine entirely |

### 3.2 Candidate Retrieval (The Retriever)

Two parallel lookups in SymSpellManager:

1. **findPrefixCandidates(typed, prevWord)** — autocomplete candidates (words starting with typed prefix)
2. **findCandidates(typed)** — typo correction candidates (edit distance ≤ 2)

Results are merged and deduplicated by lowercase term.

### 3.3 Candidate Scoring (The Judge)

Each candidate gets a **penalty score** (lower = better) from `CandidateScorer.score()`:

```
Score = editDistance
      + spatialCost(typed, candidate)      ← Euclidean keyboard distance
      - BIGRAM_WEIGHT × bigramBonus        ← context reward (×5.0)
      + bigramNoPenalty                     ← +0.2 if no bigram hit
      + apostropheBonus                     ← -20.0 exact, -10.0 typo
      + exactMatchBonus                     ← -100.0 if perfect input
      + userDictBonus                       ← -1000.0 for personal words
      - frequency × 0.1                    ← log-frequency reward
```

**Filters that reject candidates entirely (CULLED_SCORE = Double.MAX_VALUE):**

| Filter | Example | Why |
|--------|---------|-----|
| Anti-corrections | `"hell" → "he'll"` | User rejected this correction ≥2 times |
| Grammar blocking | `"my I'd"`, `"the I'm"` | Possessives/determiners can't precede contractions |
| Bigram validation | `"you well"` when typed `"well"` has 2× stronger bigram than candidate | Typed word fits context better |

### 3.4 The Spatial Model (Continuous Euclidean Distance)

**Before (binary):** Every non-adjacent key mismatch cost exactly 2.0.
- `r→t` (right next door): 0.5
- `r→e` (one key over, same row): **2.0** (wrong! should be close)
- `r→m` (opposite corner): **2.0** (same penalty as r→e!)

**After (continuous):** Real physical distance on the QWERTY layout.

```
QWERTY layout with stagger offsets:

Row 0: q(0,0) w(1,0) e(2,0) r(3,0) t(4,0) y(5,0) u(6,0) i(7,0) o(8,0) p(9,0)
Row 1:  a(0.5,1) s(1.5,1) d(2.5,1) f(3.5,1) g(4.5,1) h(5.5,1) j(6.5,1) k(7.5,1) l(8.5,1)
Row 2:    z(1.5,2) x(2.5,2) c(3.5,2) v(4.5,2) b(5.5,2) n(6.5,2) m(7.5,2)

Distance = √((x₁-x₂)² + (y₁-y₂)²)  clamped to [0.0, 2.0]
```

| Pair | Old Cost | New Cost | Notes |
|------|----------|----------|-------|
| `r→t` | 0.5 | 1.0 | Adjacent same row |
| `r→e` | **2.0** | **1.0** | Same row, one key over — NOW CAUGHT! |
| `r→g` | **2.0** | **1.8** | Diagonal cross-row |
| `r→m` | **2.0** | **2.0** | Far — capped at max |
| `t→y` | 0.5 | 1.0 | Adjacent same row |
| `f→g` | 0.5 | 1.0 | Adjacent same row |
| `a→z` | 0.5 | 1.4 | Adjacent cross-row (diagonal) |

**Impact:** Typos like `thr→the`, `wirh→with` now get low spatial cost and are caught reliably.

### 3.5 Casing & Auto-Commit

After ranking, `applyPredictedCasing()` matches the user's input casing:
- Sentence start → capitalize first letter
- ALL CAPS input → ALL CAPS output
- `i → I` always

**Auto-commit logic:**
```kotlin
shouldCommit = isChange && (!isInputValidWord || isCasingFix)
```
- Typos get corrected automatically on space
- Valid words are NEVER auto-corrected to different words
- Casing fixes (monday → Monday) always commit

### 3.6 Final Assembly

The raw typed word is prepended as the first suggestion (iOS/Gboard style).
When the user taps their own typed word, it's logged as INSISTED — a strong signal for dictionary learning.

---

## 4. The Phrase Prediction Pipeline (NEW)

Triggered when composing text is blank (user just pressed space after a word).

### 4.1 Flow

```
User types "what's " (space)
         │
         ▼
NlpManager.suggest() detects blank composing text
         │
         ├─── Check PhraseTable (personal phrases)
         │    Key: last 2 words ("i'll be") → "there soon" (8x freq)
         │
         ├─── Check BigramTable.predictPhrases() (generic chains)
         │    "what's" → top follower "up" → top follower "man" → "up man"
         │    "what's" → "going" → "on" → "going on"
         │
         ▼
Merge: Personal first, then bigram chains. Dedup. Max 3 phrases.
         │
         ▼
phraseCandidatesFlow updated → SmartbarPhraseRow animates in
```

### 4.2 Bigram Chaining Algorithm

```
predictPhrases(prev="what's", maxPhrases=3, maxWords=6):

1. Get top followers for "what's": ["up", "going", "the", "your", "wrong"]
2. For each starter (up to maxPhrases=3):

   Chain "up":
     "up" → best follower meeting threshold → "man" (freq > 10% of initial max)
     "man" → best follower → none above threshold
     Result: "up man" ✓ (2+ words)

   Chain "going":
     "going" → "on" (high freq)
     "on" → "the" (drops below threshold)
     Result: "going on" ✓

   Chain "the":
     "the" → "same" → "time"
     Result: "the same time" ✓

3. Filter: only include chains with ≥2 words
4. Return: ["up man", "going on", "the same time"]
```

**Frequency decay threshold:** Each link in the chain must have freq > 10% of the initial word's max follower frequency. This prevents nonsensical chains like "the of and to a".

**Loop prevention:** A word can't appear twice in the same chain.

### 4.3 PhraseTable (Personal Phrases)

Takes priority over bigram chaining. Loaded from `personal_phrases.tsv`.

| Context | Continuation | Freq | Source |
|---------|-------------|------|--------|
| `i'll be` | `there in a minute` | 8 | Harvest trigrams |
| `what's up` | `man` | 15 | Session n-grams |
| `see you` | `later` | 12 | Session n-grams |
| `on my` | `way` | 9 | Session n-grams |

**Key design:** 2-word context (lowercased, space-joined) maps to continuation strings. Personal phrases are hyper-specific to the user — they reflect actual typing patterns, not generic language statistics.

### 4.4 UI Behavior

- **Phrase row appears** when user hits space and phrase predictions exist
- **Phrase row hides** when user starts typing a new word (composing text non-blank)
- **AnimatedVisibility** with vertical slide animation (200ms, matching main row)
- **Tap to accept** — commits full phrase + trailing space via `commitCompletion()`
- **Never auto-commits** — all phrase candidates have `isEligibleForAutoCommit = false`
- **Italic text** — visual distinction from main suggestion row

---

## 5. The Learning Pipeline (Harvest → Data → Rebuild)

### 5.1 Data Collection (Automatic)

HarvestManager silently logs every autocorrect interaction:

| Event | What It Captures | Signal |
|-------|-----------------|--------|
| ACCEPTED | `typed → correction` + trigram context | Autocorrect working well |
| REJECTED | `typed ← correction (reverted)` | Bad correction — candidate for anti-correction |
| INSISTED | User tapped their own typed word | Word should be in dictionary |
| NO_SUGGESTION | No candidates shown | Dictionary gap |
| MULTI_ATTEMPT | Multiple correction attempts | User struggling |
| BACKSPACE_STORM | High backspace count per word | Autocorrect fighting user |
| SESSION:TYPING | Full sentence text | Bigram/phrase extraction |
| SESSION:VOICE | Whisper transcription text | Natural speech patterns |

### 5.2 Analysis (Manual)

```bash
# Pull harvest data from device
python3 harvest.py

# Run analysis
python3 harvest_analyze.py
```

**Outputs:**

| File | Purpose | Feeds Into |
|------|---------|-----------|
| `harvest_summary.md` | Statistics & recommendations | Human review |
| `anti_corrections.txt` | Frequently rejected corrections | PersonalPreferences.kt |
| `dictionary_additions.txt` | Words with no suggestions | unified_dictionary.tsv |
| `bigrams_combined.tsv` | New word pairs | final_mobile_bigrams.tsv |
| **`personal_phrases.tsv`** | **Personal phrase predictions** | **PhraseTable (NEW)** |
| `problem_patterns.txt` | Autocorrect failures | Debugging |

### 5.3 Data Injection (Manual)

```bash
# 1. Copy anti-corrections into PersonalPreferences.kt
#    Format: "typed" to listOf("blocked_correction")

# 2. Merge bigrams into the master file
cat bigrams_combined.tsv >> app/src/main/assets/ime/dict/final_mobile_bigrams.tsv
# Then sort and dedup:
sort -t$'\t' -k2 -nr app/src/main/assets/ime/dict/final_mobile_bigrams.tsv | \
  awk -F'\t' '!seen[$1]++' > /tmp/bigrams_clean.tsv && \
  mv /tmp/bigrams_clean.tsv app/src/main/assets/ime/dict/final_mobile_bigrams.tsv

# 3. Add dictionary words
cat dictionary_additions.txt >> app/src/main/assets/ime/dict/unified_dictionary.tsv

# 4. Copy personal phrases for PhraseTable
cp personal_phrases.tsv app/src/main/assets/ime/dict/personal_phrases.tsv

# 5. Rebuild and push
./gradlew assembleDebug
```

---

## 6. Critical Variables & State

| Variable | Location | Role | Failure Mode |
|----------|----------|------|-------------|
| `isReady` | SymSpellManager | `true` only if dictionary loaded | No autocorrect at all |
| `ngramEngine` | LatinLanguageProvider | Null if unigram loading failed | Falls back to legacy suggest() |
| `BigramTable.get()` | BigramTable | Null if bigram file missing/corrupt | No context scoring, no phrase chains |
| `PhraseTable.get()` | PhraseTable | Null if personal_phrases.tsv missing | Falls back to bigram chaining only |
| `QWERTY_POSITIONS` | KeyboardLayout | Key coordinate map | Spatial scoring defaults to 2.0 for unknown chars |
| `activeCandidatesFlow` | NlpManager | Main suggestion row data | Empty = no suggestions shown |
| `phraseCandidatesFlow` | NlpManager | Phrase prediction row data | Empty = phrase row hidden |

---

## 7. File Reference Map

| Component | File | Key Functions |
|-----------|------|--------------|
| **Orchestrator** | `NlpManager.kt` | `suggest()`, `phraseCandidatesFlow`, `getPreviousWord()` |
| **Engine** | `LatinLanguageProvider.kt` | `suggest()` — the main pipeline entry point |
| **Retriever** | `SymSpellManager.kt` | `findCandidates()`, `findPrefixCandidates()`, `nextWordPredictions()` |
| **Scorer** | `CandidateScorer.kt` | `score()`, `spatialCost()`, `bigramScore()` |
| **Spatial Model** | `KeyboardLayout.kt` | `QWERTY_POSITIONS`, `keyDistance()`, `isAdjacent()` |
| **Bigrams** | `BigramTable.kt` | `bonus()`, `predictNext()`, `predictPhrases()` |
| **Personal Phrases** | `PhraseTable.kt` | `predictContinuation()`, `load()` |
| **Casing** | `CasingUtils.kt` | `applyPredictedCasing()`, `CONTRACTION_SHORTCUTS` |
| **Anti-Corrections** | `PersonalPreferences.kt` | `isAntiCorrection()` |
| **Editor** | `EditorInstance.kt` | `commitCompletion()` — handles single AND multi-word commits |
| **UI: Main Row** | `CandidatesRow.kt` | Tap-to-commit, long-press-to-add-to-dict |
| **UI: Phrase Row** | `Smartbar.kt` | `SmartbarPhraseRow()` — animated multi-word prediction row |
| **Harvest Logger** | `HarvestManager.kt` | `logAccepted()`, `logRejected()`, `logInsisted()` |
| **Harvest Analyzer** | `harvest_analyze.py` | `extract_phrases()`, `extract_bigrams()`, `analyze_rejected_corrections()` |

---

## 8. Debugging Checklist

### No Suggestions Appearing
- [ ] Is `NlpStatus.isSymSpellReady` true? (Check NLP Debug screen)
- [ ] Is `NlpStatus.ngramUnigramCount` > 0?
- [ ] Is `NlpStatus.isBigramTableReady` true?
- [ ] Check logcat for `"SymSpellManager"` init step messages
- [ ] Is the input field type blocking suggestions? (`isRawInputEditor`)

### No Phrase Predictions
- [ ] Type a common word and press space — does phrase row appear?
- [ ] Check logcat for `"BigramTable"` — is it loaded with >0 first-words?
- [ ] Check logcat for `"PhraseTable"` — loaded or "No personal_phrases.tsv found"?
- [ ] Verify the previous word is being detected: check `getPreviousWord()` output

### Bad Autocorrections
- [ ] Check if it's an anti-correction candidate: look for `"ANTI-CORRECTION"` in logcat
- [ ] Check spatial cost: is a nearby-key typo getting 2.0 instead of ~1.0?
- [ ] Check bigram context: is `prevWord` null when it shouldn't be?
- [ ] Check `"GRAMMAR BLOCK"` or `"BIGRAM BLOCK"` messages in logcat

### Spatial Model Verification
```
Expected distances (approximate):
  r→t : 1.00  (adjacent same row)
  r→e : 1.00  (same row, 1 key over)
  r→f : 1.12  (diagonal, close)
  r→m : 2.00  (capped at max)
  a→s : 1.00  (adjacent home row)
  a→z : 1.41  (diagonal to bottom row)
  q→p : 2.00  (capped — opposite ends)
```

---

## 9. Developer Guide: Extending the System

### Adding a New Word to the Dictionary
1. Add to `app/src/main/assets/ime/dict/unified_dictionary.tsv`
2. Format: `word\tfrequency` (higher = more likely to be suggested)
3. Common frequency ranges: slang 50000, normal 500000, ultra-common 5000000
4. Rebuild

### Blocking a Bad Correction (Anti-Correction)
1. Edit `PersonalPreferences.kt`
2. Add to `ANTI_CORRECTIONS` map: `"typed" to listOf("bad_correction")`
3. This permanently prevents `typed` from being corrected to `bad_correction`

### Adding a New Bigram Pair
1. Add to `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv`
2. Format: `word1 word2\tfrequency`
3. This influences both context scoring AND next-word predictions

### Tuning the Spatial Model
- Edit `QWERTY_POSITIONS` in `KeyboardLayout.kt` if using a non-standard layout
- The `isAdjacent()` threshold is 1.5 — adjust if needed
- Distance is clamped to [0, 2] so CandidateScorer constants don't need retuning

### Tuning Phrase Prediction
- **Chain length:** Adjust `maxWords` parameter in `predictPhrases()` (default: 6)
- **Frequency decay:** Adjust the `0.10` threshold in BigramTable (higher = shorter chains, lower = longer but riskier)
- **Personal phrase threshold:** Adjust `MIN_PHRASE_FREQ` in `harvest_analyze.py` (default: 3 occurrences)

### Adding a New Scoring Factor
1. Add constant to `CandidateScorer.kt` top section
2. Add computation in `score()` method
3. Convention: negative values = better (reward), positive = worse (penalty)
4. Keep factors additive — this makes it easy to replace with a neural model later

### Running the Full Harvest → Improve Cycle
```bash
# 1. Use keyboard for a while (days/weeks)
# 2. Pull harvest data
python3 harvest.py

# 3. Analyze
python3 harvest_analyze.py

# 4. Review harvest_summary.md

# 5. Apply changes
# - Anti-corrections → PersonalPreferences.kt
# - Dictionary additions → unified_dictionary.tsv
# - Bigrams → final_mobile_bigrams.tsv
# - Personal phrases → app/src/main/assets/ime/dict/personal_phrases.tsv

# 6. Rebuild and push to build factory
git add -A && git commit -m "harvest: apply data cycle N"
git push

# 7. Install new APK and verify via NLP Debug screen
```

---

## 10. Architecture Principles

These are the design choices that shaped the system. Respect them when extending.

1. **Lower score = better candidate.** Penalty-based scoring (like edit distance). All factors are additive. This makes it trivial to add new signals or replace with `model.predict()`.

2. **Never auto-commit phrases.** Only single-word corrections auto-commit on space. Phrases require explicit tap. This prevents catastrophic multi-word insertions.

3. **Personal > Generic.** PhraseTable (personal typing patterns) always takes priority over BigramTable (generic language statistics). Your keyboard should sound like you.

4. **Fail silently, not fatally.** Every data loading step has try-catch. A missing `personal_phrases.tsv` means no personal phrase predictions — not a crash. A corrupted bigram entry is skipped, not propagated.

5. **The commit pipeline doesn't know about word count.** `commitCompletion()` just calls `candidate.text.toString()`. "up man" works identically to "up". This is intentional — it means new prediction types (trigrams, templates, etc.) need zero changes to the commit layer.

6. **Spatial distance is physical, not logical.** Keys are positioned by their real coordinates on the QWERTY layout with stagger offsets. This means the model automatically handles cross-row slips correctly without hand-tuning adjacency lists.

7. **The harvest system is the memory.** The keyboard learns from its mistakes through the harvest → analyze → inject cycle. Anti-corrections prevent repeated bad suggestions. Personal phrases surface actual user patterns. This is how the keyboard becomes "bespoke."
