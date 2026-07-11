# Reconnaissance Report: OmniBoard Logging and Harvesting Analysis
**Date of Investigation:** July 3, 2026
**Data Scope:** 63,575 synced repository events spanning December 24, 2025 to July 3, 2026.

---

## 1. Current Totals & Event Mix Shifts

### Full Dataset Totals
The combined dataset now contains **63,575 events** (plus 139 duplicates remaining only on the device due to script deduplication). The breakdown by category is as follows:

| Event Category | Full Dataset Count | Percentage | Description |
| :--- | :--- | :--- | :--- |
| **`SESSION:VOICE`** | 31,566 | 49.7% | Voice-to-text sessions (Whisper) |
| **`SESSION:TYPING`** | 16,989 | 26.7% | Regular keyboard typing sessions |
| **`SESSION` (Legacy)** | 11,441 | 18.0% | Legacy typing sessions (no longer logged) |
| **`ACCEPTED`** | 1,481 | 2.3% | Autocorrect suggestions accepted by the user |
| **`REJECTED`** | 1,219 | 1.9% | Autocorrect suggestions undone/reverted by user |
| **`UNKNOWN`** | 440 | 0.7% | Raw text, empty lines, formatting splits |
| **`MANUAL_FIX`** | 198 | 0.3% | Manual edit corrections (missed suggestions) |
| **`INSISTED`** | 183 | 0.3% | Typed words user forced by choosing in Smartbar |
| **`NEW_WORD`** | 58 | 0.1% | Words not in dictionary |
| **Total Events** | **63,575** | **100.0%** | |

* **New Category Types:** No new categories were introduced. All categories present in the new dataset were present in the original dataset. However, the legacy `SESSION` category has been retired; it exists only in the original 25,494 entries.

---

### Comparison: Original 25k vs. New 38k Entries
The dataset is split between the original **25,494 entries** (pre-March 10, 2026) and the newly synced **38,081 entries** (post-March 10, 2026):

| Event Category | Original (25,494 entries) | New (38,081 entries) | Mix Shift |
| :--- | :--- | :--- | :--- |
| **`SESSION:VOICE`** | 10,241 (40.2%) | 21,325 (56.0%) | **+15.8%** |
| **`SESSION:TYPING`** | 2,434 (9.5%) | 14,555 (38.2%) | **+28.7%** |
| **`SESSION` (Legacy)** | 11,441 (44.9%) | 0 (0.0%) | **-44.9%** |
| **`ACCEPTED`** | 574 (2.3%) | 907 (2.4%) | **+0.1%** |
| **`REJECTED`** | 531 (2.1%) | 688 (1.8%) | **-0.3%** |
| **`UNKNOWN`** | 179 (0.7%) | 261 (0.7%) | **0.0%** |
| **`MANUAL_FIX`** | 22 (0.1%) | 176 (0.5%) | **+0.4%** |
| **`INSISTED`** | 47 (0.2%) | 136 (0.4%) | **+0.2%** |
| **`NEW_WORD`** | 25 (0.1%) | 33 (0.1%) | **0.0%** |

* **Shift to Voice Input:** Comparing the total voice sessions to total typing sessions (Legacy + Typing combined):
  * **Original Subset:** 10,241 Voice sessions vs. 13,875 Typing sessions (**42.4% Voice / 57.6% Typing**).
  * **New Subset:** 21,325 Voice sessions vs. 14,555 Typing sessions (**59.4% Voice / 40.6% Typing**).
  * **Finding:** There is a distinct, measurable shift toward **Voice Input (Whisper)** in the new dataset. Voice sessions now make up the absolute majority of all session events in the new data.

---

### Date Range
* **Earliest Timestamp:** `2025-12-24 07:08:41`
* **Latest Timestamp:** `2026-07-03 02:28:06`

---

## 2. The Anti-Pattern Issue

### Description of Behavior
Two major anti-patterns are occurring in the script pipeline:
1. **Global Set De-duplication in Sync:** The script [harvest.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest.py) performs a flat de-duplication check against the entire repository file:
   ```python
   if clean_line not in existing_lines:
       new_entries.append(line)
   ```
   Because it reads the *entire* file into a global `existing_lines` set, any log lines that do not have a unique timestamp (such as copy-pastes, single-word inputs, or lines split by formatting issues that happen to match a line written months ago) are silently ignored and dropped during sync.
2. **Strict Regex Filtering in Analyzer:** The script [harvest_analyze.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest_analyze.py) uses regexes that require lines to end with a double-quote `"$`:
   ```python
   'session_typing': re.compile(r'^\[SESSION:TYPING\] .* \| "(.*)"$')
   'session_voice': re.compile(r'^\[SESSION:VOICE\] .* \| "(.*)"$')
   ```
   However, the keyboard logging engine was upgraded to append metadata fields (like `app`, `field`, `inputType`, `flags`) *after* the quoted session text (e.g. `| "text" | app: "com.termux" ...`). Because the regexes strictly check for `"$` at the end of the line, they fail to match these rich log lines.

### Impact Volume
* **Deduplication Loss:** **139 log entries** were skipped by [harvest.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest.py) and left only on the device due to matching duplicate lines in the history.
* **Regex Filtering Loss:**
  * **`SESSION:TYPING`:** Only **211** out of **16,989** sessions matched the regex (**98.8% of typing data ignored**).
  * **`SESSION:VOICE`:** Only **1,080** out of **31,566** sessions matched the regex (**96.6% of voice data ignored**).
  * **Combined Sessions:** Out of **59,996** total session lines, only **12,590** matched the analyzer regex (**79.0% of total session data ignored**).

### Bug Classification
* **Sync Script (`harvest.py`):** **Downstream processing bug**. The data written to `/sdcard/Documents/usage_harvest.md` by the device is correct, but the deduplication logic in the sync script discards lines.
* **Analyzer Script (`harvest_analyze.py`):** **Downstream processing bug**. The keyboard correctly logs the new metadata format, but the analyzer's regexes are too restrictive to parse it.

---

## 3. The Formatting Problem

### Concrete Formatting Issue
The `SESSION:TYPING` logging is missing space delimiters between consecutive words in certain input states. This occurs when suggestions/autocorrect are disabled (such as in Termux).
In these states, space keypresses are committed via `commitText(" ", 1)` rather than `commitChar(" ")`. The editor logic ([EditorInstance.kt](file:///data/data/com.termux/files/home/projects/keyboard-local/app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt)) only flushes the current word buffer when `commitChar` is triggered. Since space presses committed via `commitText` bypass this trigger, words continue to accumulate and run together in the buffer. When a punctuation key is finally typed (which calls `commitChar`), the entire concatenated string is flushed as one giant word.

* **Broken example:**
  `[SESSION:TYPING] 2026-06-23 16:23:59 | "soexplaintkmethefeasibilityandrealityofallthesethinfs" | app: "com.termux" | ...`
* **Correctly-formatted example:**
  `[SESSION:TYPING] 2026-06-23 16:23:59 | "so explain to me the feasibility and reality of all these things" | app: "com.termux" | ...`

* **Unresolved Issue:** This is the exact same concatenation bug flagged in the prior session, and it remains unresolved in the Kotlin codebase.

---

## 4. Pipeline Scripts Inventory

The following scripts form the harvesting/processing pipeline in the repository:

1. **[harvest.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest.py)**
   * **What it does:** Merges new entries from the device log (`/sdcard/Documents/usage_harvest.md`) to the repository copy (`usage_harvest.md`).
   * **When it runs:** Manually executed by the developer (via terminal alias `harvest`).
   * **Status:** Contributing to the anti-pattern. Its global line-by-line deduplication check drops valid duplicate lines from the sync.
2. **[harvest_analyze.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest_analyze.py)**
   * **What it does:** Parses the merged `usage_harvest.md` to compute keyboard statistics and generate recommendations for word/phrase tables.
   * **When it runs:** Manually executed during analysis.
   * **Status:** Contributing to the anti-pattern. Its strict regexes ignore 79% of all logged session data.
3. **[harvest_analyze_app_context.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest_analyze_app_context.py)**
   * **What it does:** Reference script providing context-aware groupings of session logs by application package.
   * **When it runs:** Unused (not integrated into `harvest_analyze.py`).
   * **Status:** Reference code only. Not directly contributing to bugs, but not active.
4. **[reconstruct_journal.py](file:///data/data/com.termux/files/home/projects/keyboard-local/reconstruct_journal.py)**
   * **What it does:** Reconstructs a chronological text file of user inputs (`sam_journal.txt`) from `usage_harvest.md`.
   * **When it runs:** Separate manual pass.
   * **Status:** Working as intended, but inherits the concatenation/formatting issue present in the raw logs.
5. **[clean_bigram_spam.py](file:///data/data/com.termux/files/home/projects/keyboard-local/clean_bigram_spam.py)**
   * **What it does:** Filters out SMS spam tokens and developer jargon (e.g. references to `.kt`, `snygg`) from generated phrase/bigram TSV files.
   * **When it runs:** Separate manual pass.
   * **Status:** Working correctly.

---

## 5. Storage State

* **Location of the new 38k entries:** The entries have been synced into the main repository file [projects/keyboard-local/usage_harvest.md](file:///data/data/com.termux/files/home/projects/keyboard-local/usage_harvest.md), where they are appended chronologically alongside the original 25k entries.
* **Entries at risk of being lost:**
  * **139 duplicate lines** on the device were skipped by the sync script and remain *only* on the device in `/sdcard/Documents/usage_harvest.md`. If the device log is cleared or deleted, these entries will be permanently lost.
  * *Inference:* If the on-device log file grows excessively and gets truncated or cleared automatically by the system/user, those un-synced duplicates will be lost.

---

## 6. Data Quality Spot-Check on the New Data

A random sample of 20 entries pulled from the newly harvested 38k entries reveals the following:

* **Sample 1:** `[SESSION:TYPING] 2026-03-13 18:09:27 | "notification) just hangs indefinitely. -"` (Clean, contains spaces)
* **Sample 2:** `[SESSION:TYPING] 2026-03-12 02:22:41 | "WHY this happens and what"` (Clean, contains spaces)
* **Sample 3:** `[SESSION:VOICE] 2026-03-18 18:44:28 | "failed? What's my unresolved reference?"` (Clean, contains spaces)
* **Sample 4:** `[SESSION:VOICE] 2026-03-18 08:31:26 | "Concrete Company in July and"` (Clean, contains spaces)
* **Sample 5:** `[SESSION:TYPING] 2026-03-18 08:18:35 | "polished agentic coding tool. Is"` (Clean, contains spaces)
* **Sample 6:** `[SESSION:VOICE] 2026-03-16 09:12:23 | "and we're going to be"` (Clean, contains spaces)
* **Sample 7:** `[SESSION:VOICE] 2026-03-13 09:35:11 | "you have an idea of"` (Clean, contains spaces)
* **Sample 8:** `[SESSION:TYPING] 2026-06-23 17:44:06 | "to unleash it on all"` (Clean, contains spaces)
* **Sample 9:** `[SESSION:TYPING] 2026-03-13 02:35:44 | "[ { "name": "SAPISID", "value":"` (Clean, contains spaces)
* **Sample 10:** `[SESSION:VOICE] 2026-04-08 19:34:06 | "well do the same with"` (Clean, contains spaces)
* **Sample 11:** `[SESSION:TYPING] 2026-03-12 02:37:07 | "in a virtualized root filesystem."` (Clean, contains spaces)
* **Sample 12:** `[SESSION:TYPING] 2026-03-12 02:37:07 | "automated installers is messaging, which"` (Clean, contains spaces)
* **Sample 13:** `[SESSION:TYPING] 2026-03-13 04:03:42 | "config) but config is present"` (Clean, contains spaces)
* **Sample 14:** `[SESSION:TYPING] 2026-03-18 08:18:35 | "a hardcoded app feature, a"` (Clean, contains spaces)
* **Sample 15:** `[SESSION:TYPING] 2026-03-18 08:20:54 | "Transfer: When I walk in"` (Clean, contains spaces)
* **Sample 16:** `[SESSION:VOICE] 2026-06-11 12:43:02 | "insurance salesman guy who my"` (Clean, contains spaces)
* **Sample 17:** `[SESSION:TYPING] 2026-03-12 02:22:41 | "Explain how to debug situations"` (Clean, contains spaces)
* **Sample 18:** `[SESSION:TYPING] 2026-06-25 20:27:46 | "create a minimal "requirements.txt" or"` (Clean, contains spaces)
* **Sample 19:** `[SESSION:VOICE] 2026-03-17 12:58:08 | "more than I get anywhere"` (Clean, contains spaces)
* **Sample 20:** `[SESSION:TYPING] 2026-06-23 16:23:59 | "soexplaintkmethefeasibilityandrealityofallthesethinfsanddontbeshyaboutqueryingnlmmcpnotebooksaboutandroidthinfsgslikeactivityinrenrtenrspermissionsetcserenaisbunkpleaseremoveitfrlmyourmcpconfig,"` (Broken / Concatenated)

### Observations
* **Clean Data (Samples 1-19):** These sessions (which include all voice sessions and typing sessions inside standard Android text fields like search boxes, Flutter remote shells, etc.) are correctly formatted with word spaces.
* **Broken Data (Sample 20):** Exhibits the concatenation problem. This sample occurred in Termux (`app: "com.termux"`), where word suggestions are disabled and characters (excluding spaces) are accumulated in the buffer until punctuation commits them. This confirms that the concatenation bug remains active and specifically targets environments where autocorrect/suggestions are disabled.
* **Regex Compatibility:** **Samples 1 through 20** all contain trailing metadata (`| app: ... | field: ...`). None of these 20 random samples can be parsed by `harvest_analyze.py` in its current state.
