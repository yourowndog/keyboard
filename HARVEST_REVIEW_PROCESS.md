# Harvest Review Process

This document defines the systematic process for reviewing `usage_harvest.md` and translating user behavior into dictionary and scoring improvements.

---

## Overview

The harvest file captures real keyboard usage:
- **ACCEPTED**: User kept an autocorrection
- **REJECTED**: User reverted an autocorrection (backspaced)
- **INSISTED**: User explicitly tapped their typed word in smartbar
- **PICKED**: User manually selected a different suggestion
- **NEW_WORD**: Word not in dictionary was typed

Each harvest review should result in:
1. **Dictionary additions** (new words)
2. **Frequency adjustments** (boost/reduce existing words)
3. **Bigram/Trigram additions** (context patterns)
4. **Bug identification** (logic issues to fix in code)


## Review Marker
Marker: `<!-- Data below this line is NEW since last review -->` 

## Automation Script
The `process_harvest.py` script handles parsing, locating the marker, processing new entries, and appending a new marker.

## Log Entry Format
`[CATEGORY] timestamp | content | ctx: "word" | trigram: "prev word"`

---

## Pre-Review Checklist

Before analyzing harvest data, the agent MUST:

1. **Pull latest changes**: `git pull` to ensure working with current state
2. **Check dict for each word**: Cross-reference against `unified_dictionary.tsv`
3. **Filter already-fixed items**: Skip words that were added in previous commits
4. **Note timestamps**: Older entries may reflect pre-fix behavior

---

## Word Addition Rules

### DO Add (with user approval):
- Words with 2+ REJECTED events for the same correction
- INSISTED words (strong user signal)
- Pop culture / proper nouns (Minecraft, Pokemon, etc.)
- Common slang/internet speak (bc, ur, Wdym, Ily, etc.)

### DO NOT Add:
- **Apostrophe-less contractions** (`itd`, `dont`, `wont`) - these should be handled by contraction logic
- **Single letters** (`s`, `a`, `n`) - handle with special-case logic
- **Typos** - if the word is clearly a fat-finger error, don't add it
- **Proper nouns without verification** - confirm spelling first

### Frequency Guidelines:
| Word Type | Suggested Frequency |
|-----------|-------------------|
| Common slang (bc, ur, idk) | 150000-200000 |
| Pop culture (Minecraft, Pokemon) | 100000 |
| Medical/Technical (ritalin, termux) | 50000 |
| Less common words | 30000-50000 |

---

## Frequency Adjustment Rules

### ACCEPTED Corrections:
- The correction word is working well
- Consider **boosting** correction frequency by 10-20%

### REJECTED Corrections:
- The correction was wrong in this context
- Consider **reducing** correction frequency by 10-20%
- OR **boosting** the typed word if it's in dict

### INSISTED:
- User explicitly wants this word
- **Add to dict** if missing, or **boost** frequency significantly (+50%)

### Example:
```
[REJECTED] ambien ← ambient (reverted)
```
If `ambien` is in dict but keeps getting corrected to `ambient`:
- Check if `ambien` frequency < `ambient` frequency
- Boost `ambien` frequency above `ambient`

---

## Bigram/Trigram Integration

Starting with trigram-enabled harvest entries, extract context patterns:

```
[ACCEPTED] 2025-12-22 08:19:08 | stredsful → stressful | trigram: "big stredsful"
```

This tells us:
- Trigram: `big stressful` - valid pattern
- Can add to bigram table: `big` → `stressful`

### Proposal Format:
```markdown
## Proposed Bigram Additions:
| Previous Word | Current Word | Evidence |
|---------------|--------------|----------|
| big | stressful | "big stredsful" (typo corrected) |
| Hard | time | "hard rime" (typo corrected) |
```

**Agent MUST get user approval before committing bigram/trigram additions.**

---

## Bug Identification

### Casing Issues:
If `ac` corrects to `Act` instead of `AC`:
- Valid Word Immunity should allow casing fixes
- Check if `AC` is in dict with proper casing
- May be logic bug in `LatinLanguageProvider` or `CasingUtils`

### Contraction Issues:
If `itd` doesn't correct to `it'd`:
- Check `CONTRACTION_SHORTCUTS` in `SymSpellManager`
- Check `handleContractionShortcuts` in `CasingUtils`

### Spatial Typos (e.g., `thud` → `this`):
- 'u' is adjacent to 'i', 'd' is adjacent to 's'
- Indicates `spatialCost` in `CandidateScorer` may need higher weight
- Or spatial neighbors in scoring are misconfigured

---

## Review Output Format

After analyzing harvest, agent should produce:

### 1. Dictionary Additions (Pending Approval)
```markdown
| Word | Frequency | Evidence |
|------|-----------|----------|
| Minecraft | 100000 | 1x REJECTED (corrected to Mineshaft) |
| Pokemon | 100000 | 1x REJECTED (corrected to pikemen) |
```

### 2. Frequency Adjustments (Pending Approval)
```markdown
| Word | Current | New | Reason |
|------|---------|-----|--------|
| ambien | 50000 | 80000 | 4x REJECTED ambient correction |
```

### 3. Bigram Additions (Pending Approval)
```markdown
| Prev | Word | Source |
|------|------|--------|
| Hey | baby | trigram context |
```

### 4. Bugs Identified
```markdown
- [ ] `s` → `so` should be `s` → `a` (special case needed)
- [ ] `thud` → `this` not catching spatial typo (scoring weight issue)
- [ ] `ac` → `Act` instead of `ac` → `AC` (casing logic bug)
```

---

## Commit Message Format

When committing harvest-driven changes:

```
feat(dict): harvest review YYYY-MM-DD

Additions:
- Minecraft, Pokemon, Spiderman (pop culture)
- fave, pic, bc (slang)

Frequency bumps:
- ambien: 50000 → 80000

Bigrams added:
- Hey → baby, big → stressful

Bugs identified (separate PRs):
- #123: s→so should be s→a
- #124: spatial scoring for thud→this
```

---

## Cadence

Recommended review frequency:
- **Weekly**: Quick scan for high-impact items
- **Monthly**: Full review with frequency tuning
- **After major changes**: Validate fixes are working
