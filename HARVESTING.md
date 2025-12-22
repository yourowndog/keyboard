# Developer Harvest System

A system for capturing your real-world keyboard usage to improve autocorrect and suggestions.

## Why?

As a developer constantly rebuilding, normal "learned personalization" gets wiped on each install. This system writes your usage to a file that survives reinstalls — letting you and your agents review patterns and bake improvements into the dictionary/ignore lists.

## What Gets Logged

| Event | Meaning | Action |
|-------|---------|--------|
| `ACCEPTED` | Autocorrect stood (you continued typing) | Correction is probably good |
| `REJECTED` | You backspaced to revert an autocorrect | Consider adding original to dict, or adding to ignore list |
| `NEW_WORD` | You typed a word not in dictionary | Consider adding to dict if repeated |
| `INSISTED` | You picked your literal typed word over suggestions | Strong signal — add to dict |
| `PICKED` | You manually picked a different suggestion | Useful for understanding preferences |

## Workflow

### 1. Use the Keyboard
Just use it normally. Events are logged automatically to `/sdcard/Documents/usage_harvest.md`.

### 2. Harvest (Termux)
```bash
harvest
```
This appends new entries to the repo copy and clears the device file.

### 3. Review with Agents
Tell your agent:
> "Check `usage_harvest.md` and advise on dictionary additions or ignore list updates based on the patterns."

### 4. Act on Recommendations
- **REJECTED patterns** → Add original word to dict, or add `original → correction` to ignore list
- **INSISTED/NEW_WORD patterns** → Add to `PROPER_OVERRIDES` or dictionary
- **Repeated ACCEPTED** → Confirms autocorrect is working well for those words

## File Locations

| Location | Purpose |
|----------|---------|
| `/sdcard/Documents/usage_harvest.md` | Live file written by keyboard |
| `~/vault/projects/keyboard/usage_harvest.md` | Repo copy (appended by `harvest` alias) |

## Technical Details

- **Source:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/HarvestManager.kt`
- **Init:** Called from `FlorisImeService.onCreate()`
- **Requires:** Storage permission for the keyboard app

## Example Entry

```
[REJECTED] 2025-12-15 17:45:23 | kiry ← Kirk (reverted) | ctx: "hey"
```

This means you typed "kiry", it was autocorrected to "Kirk", and you backspaced to revert it. The previous word was "hey". **Action:** Add "kiry" to `PROPER_OVERRIDES` in SymSpellManager.kt.
