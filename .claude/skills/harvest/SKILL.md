---
name: harvest
description: Run an OmniBoard harvest analysis session. Syncs typing data from the device, reasons about autocorrect failures and fat-finger patterns using LLM inference, proposes dictionary/bigram/anti-correction/logic improvements, and applies approved changes.
allowed-tools: Bash, Read, Edit, Grep, Write
---

# OmniBoard Harvest Session

This is a **collaborative reasoning session**, not a script runner. Your job is to think about what Sam's typing data actually reveals — using genuine inference about soft keyboard geometry, edit distance behavior, and autocorrect logic — then propose targeted improvements, and apply only what gets approved.

**Single user. Personal keyboard. Every pattern is Sam's.**

---

## Step 1 — Sync

Run from the project root (Termux, no ADB available):

```bash
cd ~/keyboard-local
python3 harvest.py
python3 harvest_analyze.py
```

Read `harvest_summary.md` for the current stats snapshot. Then open `usage_harvest.md` and focus on entries **after the most recent `<!-- HARVEST BATCH` marker** — do not re-analyze previously reviewed data.

---

## Step 2 — Analytical Framework

The keyboard is a **soft QWERTY touchscreen**. The overwhelming majority of errors are fat-finger misses, not spelling mistakes. Before drawing any conclusion, ask: *what physical event caused this input?*

### Soft QWERTY Adjacency — Think This Way First

- Home row substitutions: `s`↔`a`, `d`↔`f`/`s`, `j`↔`h`/`k`, `l`↔`k`/`;`
- Vertical misses: `e`↔`d`/`r`, `i`↔`o`/`u`/`k`, `n`↔`h`/`b`/`m`
- Right-thumb stretch: tends to hit `p`, `l`, `m` instead of `o`, `k`, `n`
- A lone committed `s` almost certainly means `a` — adjacent key, not a word attempt
- Repeated pattern of same wrong char → geometric problem, not vocabulary gap

### What Each Event Actually Signals

| Event | Real meaning |
|-------|-------------|
| `REJECTED` | Autocorrect was confidently wrong — anti-correction candidate |
| `MANUAL_FIX` | Autocorrect offered *nothing* for a real typo — highest priority miss |
| `NO_SUGGESTION` | Dictionary gap or edit distance ceiling too tight |
| `INSISTED` | Sam's word, not in dict — add it |
| `BACKSPACE_STORM` | High-effort word — needs better suggestion or dict entry |
| `MULTI_ATTEMPT` | Repeated struggle — logic or adjacency issue worth investigating |
| `SESSION:TYPING` | Use for bigrams AND autocorrect signal analysis |
| `SESSION:VOICE` | Use for bigrams **only** — voice text is clean, not a typo signal |

### Special Watches — Always Check These

- **`MANUAL_FIX` entries** *(new event type, may be sparse initially)*: These are the most valuable signal — the system had nothing to offer for a real mistake. Analyze the adjacency between original and corrected to understand why SymSpell didn't surface it. Is the edit distance threshold too tight? Is the word missing from the dictionary? Is this an adjacency pattern we could add to the spatial cost model?

- **`id` corrections**: Track the preceding context word in every trigram. After determiners/possessives (`my`, `your`, `the`, `that`) → `is`. After conjunctions/discourse markers (`and`, `but`, `so`, `yeah`) → `I'd`. Add newly confirmed context words to `preferIsContext` / `preferIdContext` in `CandidateScorer.kt`.

- **Single-character commits** (`s`, `d`, `t`, `n`, etc.): These are almost always fat-fingers, not word attempts. Note the character and its QWERTY neighbors — this is geometry data for the spatial cost model.

- **Punctuation artifacts**: `words.like.this` or missing spaces suggest the keyboard is substituting `.` for space in some context. Flag these — they won't appear in standard event types, only in SESSION text.

---

## Step 3 — Proposals

Organize findings into these buckets. Always show the evidence (event count, example entries).

### Anti-Corrections → `PersonalPreferences.kt`
Corrections rejected ≥2x. Add to `ANTI_CORRECTIONS` map:
```kotlin
"typed" to listOf("wrongSuggestion"),
```

### Dictionary Additions → `unified_dictionary.tsv`
Words Sam uses that aren't in the dict (INSISTED, NEW_WORD ≥2x, MANUAL_FIX targets).
Frequency guidelines: proper nouns/names 200k+, personal slang 150k, technical terms 100k, uncommon but valid 50k.
Format: `word\tfrequency`

### Bigrams → `final_mobile_bigrams.tsv`
From SESSION:TYPING ≥2x, and all SESSION:VOICE (clean natural speech patterns).
Format: `word1 word2\tfrequency`

### Logic / Scoring Issues
Patterns pointing to a systematic problem — adjacency miss, edit distance threshold, casing rule, scoring weight. Describe the root cause and the specific file/function implicated, not just the symptom.

### `id` Context Updates → `CandidateScorer.kt`
New context words to add to `preferIsContext` or `preferIdContext` based on trigram evidence from this session.

---

## Step 4 — Approval Loop

Present proposals clearly by category. **Do not apply anything without explicit approval.** Ask per category: *"Apply these N anti-corrections?"*, *"Add these words to the dictionary?"*, etc. Push back if a proposed change seems fragile or has weak evidence.

---

## Step 5 — Apply & Commit

Apply approved changes to the relevant files. Commit with:

```
feat(dict): harvest review YYYY-MM-DD

[What changed and the evidence behind it]

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

Do not push unless explicitly asked.
