# Task Brief: OmniBoard Dictionary Expansion & Critical Typo Resolution

> **Author / Context**: Antigravity Autopsy & Corpus Audit  
> **Date**: 2026-09-11  
> **Target Audience**: Next Autonomous Model / Senior Systems Engineer  
> **Repository Root**: `/home/sam/projects/keyboard`  
> **Total File Path**: `/home/sam/projects/keyboard/docs/development/dictionary-expansion-and-typo-resolution-brief.md`  
> **Status**: Ready for Implementation  

---

## 1. Executive Summary & Problem Definition

Following the culling of ~47,000 deadweight entries (`wordfreq == 0.0` and 0 user commits) from [`unified_dictionary.tsv`](file:///home/sam/projects/keyboard/app/src/main/assets/ime/dict/unified_dictionary.tsv), OmniBoard is no longer surfacing archaic Victorian garbage or toxic abbreviation collisions.

However, candidate generation currently suffers from two acute, daily-driver failure modes:
1. **The 25.5% Vocabulary Deficit**: In 25.5% of manual edits and lost correction intentions, the word Sam intended to type does not exist in [`unified_dictionary.tsv`](file:///home/sam/projects/keyboard/app/src/main/assets/ime/dict/unified_dictionary.tsv). When the target word is absent, SymSpell cannot retrieve it, and the ranker forces either a wrong high-frequency word (e.g., `idk` → `I'd` ×34) or leaves the text uncorrected.
2. **The Severe Typo Failure Mode (`thst`, `whst`, `hsve`, etc.)**: Common fat-finger slips of high-frequency words (e.g., `thst` for `that`, `whst` for `what`, `hsve` for `have`, `somethinf` for `something`) frequently survive verbatim onto the screen. This brief contains the empirical trace audit proving *why* this happens and what must be fixed.

---

## 2. Workstream 1: Tiers 1 & 2 Vocabulary Ingestion & Rank Calibration

We have mined 63 days of live usage logs from [`usage_harvest.jsonl`](file:///home/sam/projects/keyboard/data/harvest/inbox/20260911-011828/usage_harvest.jsonl) (635,404 events, 2,029 sessions). The user has authorized adding **Tier 1** and **Tier 2** vocabulary into the dictionary.

### 2.1 The Calibration & Frequency Model

In OmniBoard, unigram frequencies are consumed as `ln(freq + 1)` by [`NgramSuggestionEngine.rank()`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SuggestionEngine.kt) and [`DictionaryRepository`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/DictionaryRepository.kt).
- Standard AOSP base words map log-linearly onto $[100 .. 10^7]$ ($\ln \in [4.6 .. 16.1]$).
- In [`tools/harvesting/build_dictionary.py`](file:///home/sam/projects/keyboard/tools/harvesting/build_dictionary.py), words in [`personal_vocabulary.json`](file:///home/sam/projects/keyboard/dict_sources/personal_vocabulary.json) with `null` frequency currently default to $\text{FREQ\_MIN} \times 10 = 1,000$ ($\ln \approx 6.9$). **This is dangerously low**—it causes personal vocabulary to lose out to rare dictionary words.

#### Calibration Tiers for Ingestion:
1. **Tier 1A: Ultra-High Frequency Conversational & Personal Spine** ($\text{Target } f \approx 1,000,000 - 4,000,000 \implies \ln \approx 13.8 - 15.2$):
   * `idk`, `dont`, `thats`, `doesnt`, `didnt`, `youre`, `ive`, `weve`, `havent`, `isnt`, `wasnt`, `wouldnt`, `couldnt`, `yall`, `Tailscale`, `FUTO`, `CCGram`, `AGY`, `chezmoi`, `cmd`.
2. **Tier 1B: Core Domain, Wearables & Project Architecture** ($\text{Target } f \approx 100,000 - 350,000 \implies \ln \approx 11.5 - 12.8$):
   * `Zepp`, `Amazfit`, `Bip`, `watchface`, `watchfaces`, `smartwatch`, `Gadgetbridge`, `Auracast`, `waveshare`, `FlorisBoard`, `jdocmunch`, `CrisperWhisper`, `KeyGeo`, `tailnet`, `VoxCPM`, `MagicDNS`, `systemctl`, `worktree`, `rowCount`, `LayoutManager`, `CandidateScorer`, `CommitPolicy`, `KeyboardMode`, `dumpsys`, `statusbar`, `maxdepth`, `hardcodes`, `uncached`, `deduplication`, `OOM`, `MTP`, `cwd`, `addr`, `oneline`, `TUIs`, `MLX`, `HITL`, `ABIs`, `OOV`.
3. **Tier 2: Modern Technical & Colloquial Vocabulary** ($\text{Target } f \approx 15,000 - 60,000 \implies \ln \approx 9.6 - 11.0$):
   * `dotfiles`, `webhooks`, `lockscreen`, `diffs`, `backends`, `structs`, `autofill`, `serverless`, `composable`, `resample`, `resampled`, `lmk`, `venmo`, `klarna`, `unredacted`, `exfiltration`, `chokepoint`, `ppl`, `wtf`, `Omg`, `Async`, `PRAGMA`, `Automator`, `OpenSSH`, `keycaps`, `KEYCODE`, `inputType`, `WatchWitch`, `commitText`, `setSelection`, `Keypair`, `distro`, `grep`, `proot`, `chroot`, `killall`, `netstat`, `pkill`, `pgrep`, `symlink`, `chmod`, `stacktrace`, `companiondevice`, `queryable`, `autocorrections`, `dedup`, `AliExpress`, `Shopify`, `proactively`, `Mosh`, `yolo`, `wishlist`, `cybersecurity`, `monospace`, `gzip`, `tarball`.

### 2.2 Updating the Asset Build Pipeline

1. **Update [`dict_sources/personal_vocabulary.json`](file:///home/sam/projects/keyboard/dict_sources/personal_vocabulary.json)**:
   Add the curated words under `approved_vocabulary` with explicit target frequencies mapped to the calibration tiers above.
2. **Update [`tools/harvesting/build_dictionary.py`](file:///home/sam/projects/keyboard/tools/harvesting/build_dictionary.py)**:
   * **Bug fix**: `build_dictionary.py` line 40 currently reads from the frozen July `data/harvest/raw/usage_harvest.md`. It must read from the live snapshot: `data/harvest/inbox/20260911-011828/usage_harvest.jsonl` (and latest `.md`).
   * When `approved_vocabulary[word]` has a calibrated frequency, honor `max(calibrated_freq, personal_scale_freq)`.
3. **Rebuild & Format Assets**:
   Run `build_dictionary.py` to emit:
   - `app/src/main/assets/ime/dict/unified_dictionary.tsv`
   - `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv`
   - `app/src/main/assets/ime/dict/personal_phrases.tsv`
   - `app/src/main/assets/ime/dict/protected_forms.txt`

---

## 3. Workstream 2: Forensic Deep-Dive into the "THST" Autocorrect Failures

The user highlighted the severe, daily frustration of slips like `thst` not auto-correcting to `that`.

### 3.1 Empirical Evidence from 635,404 Events

We queried the live stream for all occurrences of the target slips. The data revealed a startling pattern:

| Typed Slip | Total Commits | Auto-Applied (`slip → target`) | Reverted | Surviving Uncorrected Commits | Top Apps Where Slip Survived Uncorrected |
|---|---:|---:|---:|---:|---|
| `thst` | 33 | **50** (`thst → that`) | 4 | **33** | `com.termux` (20), `googlequicksearchbox` (13) |
| `whst` | 30 | **49** (`whst → what`) | 2 | **30** | `com.termux` (24), `googlequicksearchbox` (5), Chrome (1) |
| `hsve` | 14 | **6** (`hsve → have`) | 0 | **14** | `com.termux` (11), `googlequicksearchbox` (3) |
| `tbe` | 18 | **15** (`tbe → the`) | 2 | **18** | `com.termux` (12), `googlequicksearchbox` (4), Chrome (2) |
| `somethinf` | 10 | **9** (`somethinf → something`) | 0 | **10** | `com.termux` (8), Chrome (2) |
| `flr` | 12 | **4** (`flr → for`) | 1 | **12** | `com.termux` (8), `googlequicksearchbox` (4) |
| `wsnt` | 11 | **5** (`wsnt → want`) | 0 | **11** | `com.termux` (7), `googlequicksearchbox` (3), Chrome (1) |
| `eith` | 10 | **24** (`eith → with`) | 0 | **10** | `com.termux` (4), `googlequicksearchbox` (5), Chrome (1) |
| `tbat` | 8 | **2** (`tbat → that`) | 0 | **8** | `com.termux` (7), `googlequicksearchbox` (1) |
| `dure` | 7 | **17** (`dure → sure`/`Durex`) | 5 | **7** | `com.termux` (4), `md.obsidian` (3) |

### 3.2 Root Causes Identified

#### Root Cause A: The Reachability Barrier (>85% of Failures)
The engine *does* know how to repair `thst → that` (it did so 50 times!) and `whst → what` (49 times!). But in >85% of cases where it failed to fire, **the autocorrect engine was never invoked**:
1. **Termux (`com.termux` - 43% of Sam's typing volume)**:
   - Termux runs in a raw terminal mode where `InputConnection` never establishes a composing region (`isRawInputEditor = true`).
   - [`LatinLanguageProvider.kt:80`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt) begins with:
     ```kotlin
     val currentWordRaw = content.composingText.toString().trim()
     ```
   - Because `composingText` is permanently empty, `LatinLanguageProvider.suggest()` drops directly into next-word prediction. It never searches for candidates, never scores `that`, and never auto-commits.
2. **Google QuickSearchBox (`com.google.android.googlequicksearchbox`)**:
   - QuickSearchBox sets `flags: noSuggestions,autoCorrect`.
   - In [`AbstractEditorInstance.kt:296`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt):
     ```kotlin
     protected open fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean {
         return editorInfo.isRichInputEditor && !editorInfo.inputAttributes.flagTextNoSuggestions
     }
     ```
   - Because `flagTextNoSuggestions` is present, `shouldDetermineComposingRegion()` returns `false`, shutting down the composing region completely, even though `autoCorrect` was explicitly requested!

#### Root Cause B: Context Competition & Unigram/Bigram Starvation
In the ~15% of cases where suggestions *were* shown in apps with a composing region (ChatGPT, Messages) but failed to auto-commit:
1. **Negative Scoring on Isolated Typos**:
   Without preceding bigram context (e.g. at the start of a sentence or field), `CandidateScorer` penalizes the spatial edit distance (distance 1 $\implies -0.6455$).
   In logged events:
   `Candidates: [['thst', 0.0], ['that', -0.6455], ['test', -2.0394], ['the', -2.2062]]`
2. **Competitor Overriding (`the` vs `that`)**:
   When preceded by words like "in", bigram frequency for `(in, the)` (+2.9938) outranked `(in, that)` (+2.0866) by a hair, pushing `that` out of slot 1 or splitting ranker confidence.

#### Root Cause C: Absence of Fast-Path Typo Mappings
OmniBoard has a dedicated, fast-path bypass for deterministic corrections in [`ContractionRules.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/ContractionRules.kt) and `typo_mappings` in [`personal_vocabulary.json`](file:///home/sam/projects/keyboard/dict_sources/personal_vocabulary.json) (e.g. `dont → don't`, `chatgbt → ChatGPT`, `apppointment → appointment`).
`thst → that`, `whst → what`, `hsve → have`, `flr → for`, and `somethinf → something` were **never added to `typo_mappings`**. They were left entirely to the mercy of heuristic n-gram spatial scoring and bigram availability.

---

## 4. Required Implementation Tasks for the Next Model

### Task 1: Add High-Confidence Fast-Path Typo Mappings
In [`dict_sources/personal_vocabulary.json`](file:///home/sam/projects/keyboard/dict_sources/personal_vocabulary.json), under `typo_mappings`, explicitly map the empirical top slips to their verified targets:
```json
"thst": "that",
"whst": "what",
"hsve": "have",
"flr": "for",
"wsnt": "want",
"somethinf": "something",
"eith": "with",
"tbat": "that",
"thinf": "thing",
"dure": "sure",
"tje": "the",
"kne": "the",
"fkr": "for",
"sdk": "idk",
"cccram": "ccgram"
```
*Impact*: This gives these acute slips a 100% deterministic auto-commit guarantee across all apps with a composing region, bypassing n-gram competition.

### Task 2: Fix Editor Flag Gating in `AbstractEditorInstance.kt`
In [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt), line 296:
When an editor specifies `flagTextAutoCorrect` alongside `flagTextNoSuggestions` (the classic Google Search box pattern), do not suppress the composing region if autocorrect is requested:
```kotlin
protected open fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean {
    val attrs = editorInfo.inputAttributes
    val allowsAutocorrect = attrs.flagTextAutoCorrect
    return editorInfo.isRichInputEditor && (!attrs.flagTextNoSuggestions || allowsAutocorrect)
}
```
*Impact*: Instantly enables autocorrect in Google QuickSearchBox and browser URL/search bars where `thst` and `whst` were dying.

### Task 3: Termux Composing Region Strategy (Architectural Path)
For `com.termux`, examine the feasibility of:
- Using `InputConnection.setComposingRegion()` on whitespace boundaries, or
- Utilizing the keystroke `trace` buffer that `HarvestManager` already maintains to drive fallback word correction when `composingText.isEmpty() && activeInfo.packageName == "com.termux"`.
*(Ensure the 1.6% command-line register is protected so standard shell syntax like `rm -rf` is not mangled).*

### Task 4: Ingest & Calibrate Tiers 1 & 2 Vocabulary
1. Stage all approved Tier 1 and Tier 2 words into [`dict_sources/personal_vocabulary.json`](file:///home/sam/projects/keyboard/dict_sources/personal_vocabulary.json) with calibrated frequencies (see §2.1).
2. Wire [`tools/harvesting/build_dictionary.py`](file:///home/sam/projects/keyboard/tools/harvesting/build_dictionary.py) to read from [`data/harvest/inbox/20260911-011828/usage_harvest.jsonl`](file:///home/sam/projects/keyboard/data/harvest/inbox/20260911-011828/usage_harvest.jsonl).
3. Execute `build_dictionary.py` to update:
   - `app/src/main/assets/ime/dict/unified_dictionary.tsv`
   - `app/src/main/assets/ime/dict/final_mobile_bigrams.tsv`
   - `app/src/main/assets/ime/dict/personal_phrases.tsv`
4. Run regression test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.*"
   ```

---

## 5. Verification Checklist

- [ ] `unified_dictionary.tsv` contains `Tailscale`, `FUTO`, `idk`, `chezmoi`, `CCGram`, `Zepp`, `systemctl`, `worktree` with frequencies $> 100,000$.
- [ ] Typing `idk` no longer auto-corrects to `I'd`.
- [ ] Typing `thst ` auto-corrects to `that ` in both messaging apps and QuickSearchBox.
- [ ] Typing `whst ` auto-corrects to `what `.
- [ ] All unit tests in `app/src/test/kotlin/.../nlp/` pass cleanly without regressions.
