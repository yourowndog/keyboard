# Harvest System - User Guide

**Last Updated:** 2025-12-31

---

## What is Harvest?

A system that captures your real keyboard usage to improve autocorrect and predictions. Because you rebuild the app frequently, normal "learning" gets wiped. Harvest writes to a file that **survives reinstalls**, letting you and AI agents analyze patterns and bake improvements into the dictionary.

**Think of it as:** Your keyboard's external memory + collaborative tuning sessions

---

## What Gets Logged

| Event | When It Happens | What It Means |
|-------|-----------------|---------------|
| `SESSION` | You type sentences/paragraphs | Captures actual typing flow and context |
| `ACCEPTED` | Autocorrect stood, you continued | Correction was good - keep it |
| `REJECTED` | You backspaced to revert autocorrect | Correction was wrong - learn from it |
| `INSISTED` | You tapped your exact word in smartbar | Strong signal - you want this word |
| `PICKED` | You manually picked a suggestion | Preference indicator |
| `NEW_WORD` | Typed word not in dictionary | Potential vocabulary addition |
| `INTENT` | System inferred your true intent | Learning from correction rejections |

---

## How It Works

### 1. Use the Keyboard Normally
Just type. Events are logged automatically to `/sdcard/Documents/usage_harvest.md` on your device.

**No action needed from you.** The keyboard handles it.

###2. Pull the Harvest (For Review)

#### Preferred Method (ADB - Laptop)
When you're ready to analyze (weekly or when you remember):

```bash
cd ~/projects/keyboard
adb pull /sdcard/Documents/usage_harvest.md usage_harvest.md
```

That's it! The file is now in your repo for review.

#### Fallback Method (Termux - No ADB)
If you're phone-only or ADB isn't available:

```bash
# In Termux
harvest  # Alias copies file to repo and clears device log
```

This copies the harvest to `~/vault/projects/keyboard/usage_harvest.md` and clears the device file.

**When to use:** Phone-only sessions, traveling without laptop, ADB connection issues

### 3. Review with AI Agent
Tell your agent:
> "Analyze usage_harvest.md and propose dictionary additions, frequency tweaks, or logic improvements based on the patterns."

The agent will:
- Identify repeated rejections → propose anti-corrections
- Find missing vocabulary → propose dictionary additions  
- Spot common phrases → propose bigram additions
- Detect logic bugs → flag for fixes

### 4. Approve & Deploy
- Agent creates proposals (NEVER auto-commits)
- You review and approve
- Agent makes changes
- Rebuild → test → see improvement

---

## Example Workflow

### Monday: Normal Typing
You text your wife about car repairs, type some work messages, browse Reddit. 287 SESSION events, 72 ACCEPTEDs, 90 REJECTEDs logged to device.

### Friday: Review Session
```bash
# Pull harvest
cd ~/projects/keyboard
adb pull /sdcard/Documents/usage_harvest.md usage_harvest.md

# Get analysis
git add usage_harvest.md
git commit -m "harvest: usage data from week of 2025-12-31"

# Tell agent
> "Review latest harvest and propose improvements"
```

### Agent Responds
```markdown
## Proposals from Harvest Analysis

### Dictionary Additions
- "Ony" (Pokemon character) - rejected Onyx 3x
- "Hurray" (exclamation) - rejected Hurrah 2x

### Anti-Corrections  
- "sams" → never suggest "samson" (rejected 5x)

### Bigrams
- "fuel injector" (car context, 3x this week)
- "going to" (common phrase, 12x this week)

Approve?
```

### You Approve
Agent commits changes. Rebuild app. Next time you type "sams", it won't suggest "samson". Magic.

---

## File Locations

| Location | Purpose | Survives Rebuilds? |
|----------|---------|-------------------|
| `/sdcard/Documents/usage_harvest.md` | Live log on device | ✅ Yes |
| `~/projects/keyboard/usage_harvest.md` | Repo copy for review | ✅ Yes (committed to git) |
| App's internal storage | (none - no persistent learning) | ❌ No |

**Key Point:** The file is OUTSIDE the app, so reinstalls don't wipe it.

---

## Current Known Issues

### 1. Logging Format (Being Fixed)
**Problem:** Currently logs individual characters instead of complete words.

**What you see:**
```
[SESSION] "m e c h s"
```

**What it should be:**
```
[SESSION] "mechs"  
```

**Status:** Fix in progress (see task.md in artifacts)

### 2. Single-Letter Corrections
Sometimes "s" gets corrected to "so" when you meant "a". This is a logic bug being tracked.

---

## Tips for Better Results

### Do:
- ✅ Type naturally - don't change your style for logging
- ✅ Use INSISTED when suggestions are wrong - it's a strong signal
- ✅ Review weekly to catch patterns early
- ✅ Approve thoughtful agent proposals

### Don't:
- ❌ Delete harvest file manually (it's your memory!)
- ❌ Edit the raw log (post-processing is automated)
- ❌ Expect instant perfection (it's iterative refinement)

---

## Privacy Note

The harvest file contains **everything you type**. It's stored locally and only you (and your AI agents) see it.

**Not shared with:**
- Cloud services
- Firebase
- Anyone else

**Your data stays on:**
- Your device (`/sdcard/Documents/`)
- Your laptop (`~/projects/keyboard/`)
- Your git repo (local or private remote)

---

## Troubleshooting

### "Can't find harvest file on device"
```bash
# Check if it exists
adb shell ls -l /sdcard/Documents/usage_harvest.md

# Try alternate path
adb pull /storage/emulated/0/Documents/usage_harvest.md
```

### "Harvest file empty after lots of typing"
- Check keyboard has storage permission (Settings → Apps → OmniBoard → Permissions)
- Check `HarvestManager` is initialized (logs should show it in logcat)

### "Logs are still character-by-character"
- Fix not deployed yet - current builds have this bug
- Temporary: agent can still reconstruct from char logs for analysis

---

## Technical Details (For Developers)

**Source Code:**
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/HarvestManager.kt` - Logging logic
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt` - Session hooks
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt` - onDestroy flush

**Init:** Called from `FlorisImeService.onCreate()`  
**Requires:** `WRITE_EXTERNAL_STORAGE` permission

**Flush Behavior:**
- Auto-flushes SESSION after 5 words (to prevent data loss)
- Flushes on app destroy (rebuild/reinstall)
- Punctuation triggers session completion

---

## See Also

- **HARVEST_REVIEW_PROCESS.md** - How AI agents analyze harvest data
- **KEYBOARD_MISSION.md** - Overall project vision and goals
- **task.md** - Current implementation tasks (in artifacts)

---

## Questions?

If the harvest system isn't working as expected, check:
1. Storage permission granted?
2. File exists on device? (`adb shell ls ...`)
3. HarvestManager initialized? (check logcat)
4. Bug already known? (check task.md)

If still stuck, tell your agent:
> "Troubleshoot harvest logging - nothing is appearing in usage_harvest.md"
