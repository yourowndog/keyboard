# Harvest System - Quick Reference

## Overview
Comprehensive data collection and analysis system for refining OmniBoard's autocorrect, dictionary, and bigrams.

## Data Collection (Automatic)

### Event Types
```
[SESSION:TYPING] - Manual keyboard input (5-word chunks)
[SESSION:VOICE] - Whisper voice transcription
[ACCEPTED] - Autocorrect kept
[REJECTED] - Autocorrect reverted
[NO_SUGGESTION] - No autocorrect offered (dictionary gap!)
[MULTI_ATTEMPT] - Multiple correction attempts (user struggling)
[IGNORED_SUGGESTIONS] - Suggestions shown but all ignored
[BACKSPACE_STORM] - High backspace count (high-effort word)
[NEW_WORD] - Word not in dictionary
[INSISTED] - User picked typed word over suggestions
[PICKED] - User picked different suggestion
```

### File Location
`/sdcard/Documents/usage_harvest.md` (survives reinstalls)

## Analysis Workflow

### 1. Pull Data
```bash
cd ~/keyboard-local
python3 harvest.py
```
*Syncs new entries from phone to local repo*

### 2. Analyze
```bash
python3 harvest_analyze.py
```
*Generates all reports and recommendations*

### 3. Review Outputs
- `harvest_summary.md` - Stats + recommendations
- `anti_corrections.txt` - For PersonalPreferences.kt
- `dictionary_additions.txt` - For unified_dictionary.tsv
- `bigrams_combined.tsv` - For final_mobile_bigrams.tsv
- `problem_patterns.txt` - Autocorrect failures

### 4. Apply Changes

**Anti-corrections:**
```kotlin
// PersonalPreferences.kt - Add to ANTI_CORRECTIONS map
val ANTI_CORRECTIONS = mapOf(
    // ... paste lines from anti_corrections.txt
)
```

**Bigrams:**
```bash
cd ~/keyboard-local
cat bigrams_combined.tsv >> app/src/main/assets/ime/dict/final_mobile_bigrams.tsv
# Then sort and dedupe
sort -t$'\t' -k2 -nr app/src/main/assets/ime/dict/final_mobile_bigrams.tsv | \
  awk '!seen[$1]++' > temp.tsv && \
  mv temp.tsv app/src/main/assets/ime/dict/final_mobile_bigrams.tsv
```

**Dictionary words:**
```bash
# Paste lines from dictionary_additions.txt into:
app/src/main/assets/ime/dict/unified_dictionary.tsv
```

## Key Features

### Typing vs Voice Separation
- **SESSION:TYPING** → Autocorrect metrics (real typos)
- **SESSION:VOICE** → Bigrams only (natural speech)
- No pollution: Perfect voice text doesn't skew autocorrect stats

### Aggressive Thresholds (Young Autocorrect)
```python
MIN_WORD_FREQ = 2          # Catch dictionary gaps early
MIN_REJECTION_COUNT = 2    # Any repeat rejection = pattern
MIN_BIGRAM_FREQ = 2        # Inclusive for personal patterns
```

*Rationale: With frequent reinstalls and rapid iteration, waiting for 5+ occurrences means missing critical issues.*

### Failure Detection
- Words with NO suggestions → Dictionary gaps
- Multi-attempt struggles → Need better suggestions
- Ignored suggestions → Visibility or quality issues
- Backspace storms → High-effort words need help

## Configuration

Edit thresholds in `harvest_analyze.py`:
```python
MIN_WORD_FREQ = 2          # Dictionary addition threshold
MIN_REJECTION_COUNT = 2    # Anti-correction threshold
MIN_BIGRAM_FREQ = 2        # Bigram inclusion threshold
```

## Files Modified (Feb 2026)
- `HarvestManager.kt` - Session source tracking, new event types
- `KeyboardManager.kt` - Voice session tagging
- `GEMINI.md` - Full documentation
- `harvest_analyze.py` - Multi-source analyzer
- Removed: `generate_sam_bigrams.py`, `extract_personal_bigrams.py` (antiquated)

## Quick Stats (Last Run)
- Typing: 11,237 sessions, 53,752 words
- Autocorrect: 51.2% accuracy (310 accepted / 295 rejected)
- Found: 20 anti-correction candidates, 3,000+ bigrams

## Next Harvest Cycle
1. Use keyboard → auto-logs to /sdcard/Documents
2. Run `harvest.py` → syncs to repo
3. Run `harvest_analyze.py` → generates reports
4. Apply changes → rebuild → test
5. Repeat

*Designed for rapid iteration workflow with frequent reinstalls.*
