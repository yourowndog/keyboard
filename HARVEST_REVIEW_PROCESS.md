# Harvest Review Process

**Last Updated:** 2025-12-31  
**Paradigm:** Paragraph-style logging, AI-driven collaborative analysis

---

## Overview

The harvest system captures Sam's real-world keyboard usage to drive hyperpersonalization. Each review is a **collaborative AI-human analysis session** - not automation.

**Key Files:**
- **Live Log:** `/sdcard/Documents/usage_harvest.md` (on device)
- **Analysis Copy:** `~/projects/keyboard/usage_harvest.md` (pulled for review)
- **Mission:** `.gemini/*/KEYBOARD_MISSION.md` (read this first!)

**Review Cadence:** Weekly or after ~1000 words typed

---

## Quick Start

### 1. Pull Latest Harvest
```bash
cd ~/projects/keyboard
adb pull /sdcard/Documents/usage_harvest.md usage_harvest.md
```

### 2. Analyze
Review the log for:
- 🎯 Patterns (repeated typos, phrases, rejections)
- 📝 Missing vocabulary (NEW_WORD, INSISTED events)
- 🚫 Anti-patterns (corrections rejected 5+ times)
- 💬 Phrase pairs (common 2-3 word sequences)

### 3. Propose Changes
Create proposals in artifact (never auto-commit):
- Dictionary additions
- Frequency adjustments
- Bigram additions
- Anti-corrections
- Logic fixes

### 4. Get Approval & Deploy
Sam reviews → approves → agent commits → rebuild

---

## Log Entry Types

| Event | Meaning | Actionable |
|-------|---------|------------|
| `SESSION` | Paragraph of typed text | Extract vocabulary, phrases, context |
| `ACCEPTED` | Autocorrect kept | Correction working well, consider boosting |
| `REJECTED` | Autocorrect reverted | Original word may need adding, or anti-correction |
| `INSISTED` | User picked their exact word | Strong signal - add to dict if missing |
| `PICKED` | User picked different suggestion | Preference signal |
| `NEW_WORD` | Word not in dictionary | Candidate for addition if repeated |
| `INTENT` | User's true intent after rejection | Learn replacement patterns |

---

## Current Format (Broken - Being Fixed)

**Problem:** Logs individual characters instead of words.

**Example:**
```
[SESSION] "m e c h s"  ← Should be "mechs"
```

**Fix in progress:** See task.md

---

## Target Format (After Fix)

```markdown
## Session: 2025-12-31 11:18
That sounds like the right thing to do. I don't know if they're swapping fuel [injextor→injector✓] around.

**Corrections:**
- injextor → injector (ACCEPTED, trigram: "fuel injextor")
- sams → samson (REJECTED 3x)

**Vocabulary:** sounds, believe, swapping, fuel, injector
```

**Benefits:**
- Readable paragraphs show actual thought flow
- Inline markers show what happened without breaking flow
- Summary sections for quick scanning

---

## Analysis Workflow

### Step 1: Skim for Patterns

**Look for:**
- Same correction REJECTED multiple times → anti-pattern
- Same word appearing in INSISTED/NEW_WORD → dictionary add
- Common 2-word sequences → bigram candidate
- Spatial typo patterns (e.g., "thr" typed for "the" repeatedly)

**Tools:**
```bash
# Count rejection frequency
grep "REJECTED" usage_harvest.md | cut -d'|' -f2 | sort | uniq -c | sort -nr

# Find repeated NEW_WORD entries  
grep "NEW_WORD" usage_harvest.md | cut -d'|' -f2 | sort | uniq -c | sort -nr

# Extract bigrams from sessions (after fix)
# Will need custom script
```

### Step 2: Cross-Reference Dictionary

For each candidate word:
```bash
grep -i "^wordname" app/src/main/assets/ime/dict/unified_dictionary.tsv
```

If missing and appears 2+ times → propose addition.  
If present but low frequency and gets rejected → propose boost.

### Step 3: Identify Anti-Patterns

**Rule:** 5+ REJECTED for same correction = anti-pattern

**Example:**
```
[REJECTED] sams ← samson (3x in one session)
```

**Action:** Add to `PersonalPreferences.kt`:
```kotlin
"sams" to listOf("samson", "samoa"),
```

### Step 3b: Special Case - "id" Context Monitoring

**Background:** "id" can mean "is" (spatial typo, d→s) or "I'd" (contraction). Currently using context-aware scoring in `CandidateScorer.kt`:
- After "this/that/it/he/she/what/which/who/there/here" → prefer "is"
- After "and/but/so/or/because/if/when/well/yeah/yes/no" → prefer "I'd"

**Agent Responsibility:** Always watch harvest for "id" corrections:
- Where was it ACCEPTED as "is"? Note the previous word.
- Where was it ACCEPTED as "I'd"? Note the previous word.
- Where was it REJECTED? What did Sam really mean?

**Goal:** Build Sam-specific bigram context lists over time. Add new context words to `preferIsContext` or `preferIdContext` sets in CandidateScorer.kt as patterns emerge.

**Example harvest entries to watch:**
```
[ACCEPTED] id → is | trigram: "here id"   → Add "here" to preferIsContext
[REJECTED] id ← I'd | trigram: "maybe id" → Sam wanted "is", add "maybe" to preferIsContext
[ACCEPTED] id → I'd | trigram: "well id"  → Confirms "well" in preferIdContext
```

### Step 4: Extract Bigrams

From SESSION paragraphs, look for:
- Repeated 2-word pairs ("I'm gonna", "going to", "call you")
- Frequency threshold: 2+ occurrences

**Process:**
1. Parse session paragraphs
2. Extract all 2-word pairs
3. Rank by frequency
4. Propose additions to `final_mobile_bigrams.tsv` with modest scores (5,000)

### Step 5: Spot Logic Bugs

**Common issues:**
- Single-letter corrections (e.g., "s" → "so" when user wanted "a")
- Casing errors (e.g., "ac" → "Act" vs "AC")
- Contraction failures (e.g., "itd" not becoming "it'd")
- Spatial typos not catching (e.g., adjacent keys)

**Action:** Note in Bugs section, create separate issue/PR

---

## Proposal Format

After analysis, create artifact with:

### Dictionary Additions
```markdown
| Word | Frequency | Evidence |
|------|-----------|----------|
| Ony | 50,000 | Proper noun, 1x REJECTED(Onyx) |
| Hurray | 100,000 | Exclamation, 1x REJECTED(Hurrah) |
```

**Frequency Guidelines:**
- Family/friends names: 200,000+
- Common Sam slang: 150,000
- Technical terms: 100,000
- Less common but valid: 50,000

### Frequency Adjustments
```markdown
| Word | Current | New | Reason |
|------|---------|-----|--------|
| ambien | 50,000 | 100,000 | 4x REJECTED(ambient) |
```

### Bigram Additions
```markdown
| Prev Word | Next Word | Frequency | Evidence |
|-----------|-----------|-----------|----------|
| fuel | injector | 10,000 | 2x in Session 2025-12-31 |
| going | to | 50,000 | Common phrase, high usage |
```

**Note:** Always add to bigram table FIRST, then smartbar pulls from table (not directly from harvest).

### Anti-Corrections
```markdown
| Typed | Never Suggest | Evidence |
|-------|---------------|----------|
| sams | samson, samoa | 3x REJECTED in single session |
| Hurray | Hurrah | 1x REJECTED |
```

### Logic Bugs
```markdown
- [ ] "s → so" false positive - need better single-letter logic
- [ ] "Ony → Onyx" - proper noun not in dict
- [ ] SESSION logging chars not words (critical fix in progress)
```

---

## File Locations

### Dictionary & Scoring
- `app/src/main/assets/ime/dict/unified_dictionary.tsv` - Main dictionary
- `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv` - Bigram table  
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt` - USER_OVERRIDES (high-priority words)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/CandidateScorer.kt` - Scoring logic
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/CasingUtils.kt` - PROPER_NOUNS

### Personalization (New)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/PersonalPreferences.kt` - Anti-corrections, typo patterns

### Logging
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/HarvestManager.kt` - Logging implementation
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt` - Session hooks
- `/sdcard/Documents/usage_harvest.md` - Live device log  
- `~/projects/keyboard/usage_harvest.md` - Analysis copy

---

## Commit Message Format

```
feat(dict): harvest review 2025-12-31

Dictionary additions:
- Ony, Hurray (proper nouns/exclamations)

Frequency adjustments:
- ambien: 50k → 100k (rejected ambient 4x)

Bigrams added:
- fuel injector, going to

Anti-corrections:
- sams → samson (rejected 3x)

Bugs identified:
- #456: SESSION logging chars not words
- #457: "s → so" single-letter logic
```

---

## Agent Responsibilities

### DO:
- ✅ Propose all changes (never auto-commit)
- ✅ Explain rationale and evidence
- ✅ Generate new ideas for improvement
- ✅ Push back if Sam's idea is fragile/complex
- ✅ Think holistically about thought patterns
- ✅ Create detailed implementation plans

### DO NOT:
- ❌ Auto-add to dictionary without approval
- ❌ Modify core SymSpell or adjacency map
- ❌ Delete vocabulary (only reduce scores)
- ❌ Skip collaborative review process
- ❌ Make assumptions (ask clarifying questions)

---

## Success Metrics

### Primary Goal
- **Rejection Rate:** <5% of all autocorrect events
- **Measured as:** `REJECTED events / (ACCEPTED + REJECTED events)`

### Leading Indicators
- INSISTED events decreasing (words already in dict)
- Same correction not appearing in REJECTED repeatedly
- Longer session paragraphs (smooth typing flow)
- Multi-word predictions being used

### Current Baseline
- **Session 2025-12-31:** ~20% rejection rate (6/30)
- **Target:** <5% within 6 months of weekly reviews

---

## Troubleshooting

### "Can't pull harvest file"
```bash
# Check device connection
adb devices

# Check file exists
adb shell ls -l /sdcard/Documents/usage_harvest.md

# Try full path
adb pull /storage/emulated/0/Documents/usage_harvest.md
```

### "Logs still showing characters not words"
- Bug fix not deployed yet - see task.md
- Temporary workaround: manually reconstruct from char logs

### "Bigrams not showing in smartbar"
- Check bigrams added to `final_mobile_bigrams.tsv`
- Check NlpManager loads bigram table
- Check CandidatesRow UI displays multi-word chips
- Feature may not be implemented yet - see task.md

---

## Next Steps After Review

1. **Create proposals artifact** (never auto-commit)
2. **Get Sam's approval** on each change
3. **Make code changes** (dict, bigrams, logic)
4. **Commit with descriptive message**
5. **Rebuild and deploy** to device
6. **Test changes** in real usage
7. **Monitor next harvest** for improvement

**Remember:** This is iterative refinement, not one-time fix. Tiny tweaks, constant improvement.

---

## See Also

- **KEYBOARD_MISSION.md** - Overall vision and context (read first!)
- **HARVESTING.md** - User-facing guide for harvest system
- **task.md** - Current implementation tasks
- **harvest_session_*.md** - Reformatted session analyses
