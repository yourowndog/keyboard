# Context-Enriched Harvest Logging

## What We Added

Enhanced OmniBoard's harvest logging system to capture **app and field context** for every typing event. This enables per-app dictionary learning and behavior customization.

---

## Changes Made

### 1. New Data Structure: `AppContext` (HarvestManager.kt)

```kotlin
data class AppContext(
    val packageName: String,       // e.g., "com.termux", "org.telegram.messenger"
    val fieldId: Int,               // Unique ID within app, identifies conversations/fields
    val inputVariation: String,     // e.g., "NORMAL", "URI", "PASSWORD", "SHORT_MESSAGE"
    val flags: String,              // Comma-separated: "noSuggestions,autoCorrect" or "none"
)
```

### 2. Enhanced Logging Methods (HarvestManager.kt)

All logging methods now accept optional `appContext: AppContext?` parameter:

- `logAccepted(typed, correctedTo, prevWord, prevPrevWord, appContext)`
- `logRejected(typed, rejectedCorrection, prevWord, prevPrevWord, appContext)`
- `logNewWord(word, prevWord, appContext)`
- `logInsisted(word, prevWord, appContext)`
- `addToSession(word, appContext)`
- `flushSession(terminator, appContext)`

**Backward compatible**: All parameters are optional, existing calls still work.

### 3. Context Builder (EditorInstance.kt)

Added `buildAppContext()` helper method that extracts from `activeInfo: FlorisEditorInfo`:

```kotlin
private fun buildAppContext(): AppContext? {
    val pkg = activeInfo.packageName ?: return null
    val flags = buildList {
        if (activeInfo.inputAttributes.flagTextNoSuggestions) add("noSuggestions")
        if (activeInfo.inputAttributes.flagTextAutoCorrect) add("autoCorrect")
        if (activeInfo.inputAttributes.flagTextAutoComplete) add("autoComplete")
        if (activeInfo.imeOptions.flagNoPersonalizedLearning) add("noPersonalizedLearning")
        if (activeInfo.imeOptions.flagForceAscii) add("forceAscii")
    }.joinToString(",").ifEmpty { "none" }

    return AppContext(
        packageName = pkg,
        fieldId = activeInfo.fieldId,
        inputVariation = activeInfo.inputAttributes.variation.toString(),
        flags = flags,
    )
}
```

### 4. Injection Points (EditorInstance.kt)

Modified all HarvestManager call sites to pass `buildAppContext()`:

- `commitChar()` → lines 221, 226
- `commitText()` → lines 302, 305, 310
- `commitCompletion()` → lines 345, 349
- `tryRevertLastAutoCorrect()` → line 700

**Voice logging** automatically covered because `KeyboardManager` calls `editorInstance.commitText()`, which now captures context.

---

## Log Format Examples

### Before (no context)
```
[ACCEPTED] 2026-02-18 14:23:15 | dont → don't | ctx: "I" | trigram: "I dont"
[SESSION:TYPING] 2026-02-18 14:23:20 | "I don't think so"
```

### After (with context)
```
[ACCEPTED] 2026-02-18 14:23:15 | dont → don't | ctx: "I" | trigram: "I dont" | app: "org.telegram.messenger" | field: 123456 | inputType: SHORT_MESSAGE | flags: autoCorrect
[SESSION:TYPING] 2026-02-18 14:23:20 | "I don't think so" | app: "org.telegram.messenger" | field: 123456 | inputType: SHORT_MESSAGE | flags: autoCorrect
```

Termux example:
```
[SESSION:TYPING] 2026-02-18 14:30:05 | "cd into projects" | app: "com.termux" | field: 789012 | inputType: VISIBLE_PASSWORD | flags: noSuggestions
```

Chrome URL bar:
```
[SESSION:TYPING] 2026-02-18 14:35:10 | "https://github.com" | app: "com.android.chrome" | field: 456789 | inputType: URI | flags: noSuggestions
```

---

## Next Steps: Analysis Pipeline

### harvest_analyze.py Enhancements

Add these features to extract per-app insights:

```python
# Parse app context from log lines
APP_CONTEXT_PATTERN = re.compile(r'app: "([^"]+)" \| field: (\d+) \| inputType: (\w+) \| flags: ([\w,]+)')

# Group sessions by app
app_sessions = defaultdict(list)
for line in harvest_log:
    match = APP_CONTEXT_PATTERN.search(line)
    if match:
        app, field, input_type, flags = match.groups()
        app_sessions[app].append(extract_session_text(line))

# Generate per-app outputs
for app, sessions in app_sessions.items():
    app_slug = app.split('.')[-1]  # e.g., "termux", "telegram"

    phrases = extract_phrases(sessions)
    write_tsv(f"dict_{app_slug}_phrases.tsv", phrases)

    words = extract_vocabulary(sessions)
    write_tsv(f"dict_{app_slug}_words.tsv", words)

    bigrams = extract_bigrams(sessions)
    write_tsv(f"dict_{app_slug}_bigrams.tsv", bigrams)
```

### Per-Conversation Detection

Use `fieldId` to identify conversations:

```python
# Group by app + field
conversations = defaultdict(list)
for line in harvest_log:
    match = APP_CONTEXT_PATTERN.search(line)
    if match:
        app, field = match.groups()[:2]
        conv_key = f"{app}:{field}"
        conversations[conv_key].append(line)

# Detect participants via name extraction
for conv_key, lines in conversations.items():
    text = " ".join(extract_all_text(lines))
    names = extract_capitalized_names(text)  # "Kiry", "Mom", etc.

    # If "Kiry" appears frequently, this is the Kiry conversation
    if "Kiry" in names or "kiry" in text.lower():
        phrases = extract_phrases(lines)
        write_tsv("dict_messaging_kiry_phrases.tsv", phrases)
```

### Input Type-Specific Dictionaries

```python
# Group by input type instead of app
input_type_sessions = defaultdict(list)
for line in harvest_log:
    match = APP_CONTEXT_PATTERN.search(line)
    if match:
        input_type = match.group(3)
        input_type_sessions[input_type].append(line)

# Generate specialized dictionaries
# URI → domains, protocols, common paths
# EMAIL_ADDRESS → email formats, domain names
# SHORT_MESSAGE → casual abbreviations, emoji, slang
# VISIBLE_PASSWORD → terminal commands (from Termux)
```

---

## Usage

1. **Build and install** the updated keyboard
2. **Type normally** for a few days across different apps (Termux, messaging, browser, etc.)
3. **Pull harvest data**: `python3 harvest.py`
4. **Run enhanced analysis**: `python3 harvest_analyze.py` (after adding app-aware parsing)
5. **Review outputs**:
   - `dict_termux_words.tsv` → shell commands, paths, technical terms
   - `dict_telegram_phrases.tsv` → casual conversation phrases
   - `dict_messaging_kiry_phrases.tsv` → phrases specific to conversations with Kiry
   - `dict_chrome_urls.tsv` → frequently typed domains and URLs
6. **Deploy**: Copy per-app dictionaries to assets, modify DictionaryLoader to switch based on `activeInfo.packageName`

---

## Benefits

### Immediate
- **Data collection** for per-app and per-conversation learning starts now
- **Zero disruption** to current typing experience (backward compatible)

### After Analysis Pipeline
- **Termux**: Suggestions for `kubectl`, `docker`, `cd`, `sudo`, git commands instead of English words
- **Messaging**: "on my way", "love you", "see you soon" phrases instead of formal language
- **With Kiry**: Pet names, inside jokes, relationship-specific vocabulary
- **URL bars**: Domain autocompletion, `https://` shortcuts
- **Email fields**: Domain names, formal greetings

### Future Enhancements
- Auto-toggle number row when entering Termux (using `packageName` detection)
- Auto-toggle number row in URI/password fields (using `inputVariation`)
- Disable autocorrect in password fields automatically (using `flags`)
- Different phrase prediction aggressiveness per app
- Conversation-aware learning (detect Kiry's chat via fieldId + name analysis)

---

## Files Modified

| File | Changes |
|------|---------|
| `HarvestManager.kt` | Added `AppContext` data class, enhanced all logging methods with optional `appContext` parameter, modified `append()` to write context to log |
| `EditorInstance.kt` | Added `buildAppContext()` helper, injected context into all HarvestManager calls (commitChar, commitText, commitCompletion, tryRevertLastAutoCorrect) |

**Voice logging** automatically inherits context (no KeyboardManager changes needed).

---

## Testing

Run these scenarios and check `/sdcard/Documents/usage_harvest.md`:

1. **Termux**: Type `cd into projects` → verify `app: "com.termux" | inputType: VISIBLE_PASSWORD | flags: noSuggestions`
2. **Telegram**: Type "hey what's up" → verify `app: "org.telegram.messenger" | inputType: SHORT_MESSAGE`
3. **Chrome URL bar**: Type a URL → verify `app: "com.android.chrome" | inputType: URI | flags: noSuggestions`
4. **Password field**: Type in any password field → verify `inputType: PASSWORD | flags: noPersonalizedLearning`
5. **Voice input**: Dictate in any app → verify SESSION:VOICE has app context

All events should now include the 4-part context string: `app | field | inputType | flags`.
