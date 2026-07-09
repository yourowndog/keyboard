# Report: Autocorrect Intent Triage & Rejection Outcome Analysis
**Date of Analysis:** July 3, 2026
**Corpus Analyzed:** [usage_harvest.md](file:///data/data/com.termux/files/home/projects/keyboard-local/usage_harvest.md) (63,135 valid log records spanning December 24, 2025 to July 3, 2026)

This report consolidates the methodology, distribution metrics, app-specific cross-tabulations, and training calibration calibration denominators extracted from the OmniBoard harvest logs.

---

## 1. Intent Classification Methodology

To isolate actual user intent from isolated log lines, the diagnostic pipeline traces event sequences chronologically within individual app sessions. By matching `ACCEPTED` and `REJECTED` events against immediately following log lines, the system classifies events into six distinct intent buckets:

1. **`validated_reject` (Rejection Kept):** A `REJECTED` event followed by a typing session text block containing the reverted original word (proving the user intentionally wanted and kept their original spelling; the reject was correct).
2. **`manual_fix` (User Corrected):** A `REJECTED` event followed immediately by a `MANUAL_FIX` event matching the same typed word (proving the user corrected their input manually).
3. **`capitulation` (Fought but accepted):** A `REJECTED` event followed shortly by an `ACCEPTED` correction for the same word stem (proving a pushy, incorrect autocorrect that the user initially fought but eventually settled for).
4. **`validated_fix` (Genuine Correction):** An `ACCEPTED` event where the originally typed string is different from the corrected string (the confusion-matrix seed).
5. **`leave_alone` (Control):** `INSISTED` and `NEW_WORD` events (proving spelling matches intent exactly; maps to itself as a false-positive control).
6. **`unresolved` (Ambiguous):** Events where context switches, boundary flushes, or ambiguous inputs prevent classification.

---

## 2. Intent Outcome Distribution

Running this analysis over the entire synced database yields the following intent profile:

| Intent Category | Event Count | Description / Training Role |
| :--- | :--- | :--- |
| **`validated_reject`** | 522 | Autocorrect was rejected, and user kept original |
| **`manual_fix`** | 36 | Autocorrect was rejected, and user manually typed a new spelling |
| **`capitulation`** | 66 | Autocorrect was rejected, but user gave up and accepted it later |
| **`validated_fix`** | 1,227 | Autocorrect suggestions accepted with a genuine character change |
| **`leave_alone`** | 241 | User explicitly selected typed text / entered new dictionary words |
| **`unresolved`** | 1,047 | Boundary splits, app shifts, or missing trailing outcomes |

---

## 3. Calibration Denominators for Autocorrect Training

Grouping these counts into the three usable model calibration targets:

```text
🌟 CALIBRATION DENOMINATORS:
  • Validated Fixes (Confusion-Matrix Seed)       : 1,227
  • Identity/Leave-Alone (False-Positive Control) :   241
  • Capitulation/Fights (False-Positive Signal)   :   588
```

* **Validated Fixes (1,227):** Maps typings to corrections, establishing the primary weight updates for the ranking model's confusion matrix.
* **Identity/Leave-Alone (241):** Verifies correct mappings, establishing a control to keep the model from over-correcting standard typing runs.
* **Capitulation/Fights (588):** Represents model friction. The 522 validated fights and 66 capitulations serve as critical false-positive indicators where current scoring heuristics fail.

---

## 4. App Provenance Cross-Tabulation (Part 2 Events)

The following cross-tabulation tracks labeled events by application package name (top packages shown):

| Application Package | ACCEPTED | REJECTED | INSISTED | NEW_WORD | MANUAL_FIX |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `com.google.android.apps.messaging` | 525 | 374 | 93 | 23 | 54 |
| `UNKNOWN_APP` (Legacy logs) | 346 | 302 | 25 | 16 | 0 |
| `com.facebook.orca` (Messenger) | 215 | 253 | 7 | 9 | 31 |
| `com.beeper.android` | 173 | 42 | 34 | 4 | 2 |
| `com.anthropic.claude` | 61 | 51 | 12 | 2 | 12 |
| `com.openai.chatgpt` | 34 | 52 | 0 | 0 | 9 |
| `com.carriez.flutter_hbb` | 20 | 43 | 0 | 0 | 9 |
| `com.google.android.googlequicksearchbox` | 0 | 2 | 0 | 0 | 62 |
| `com.android.chrome` | 18 | 21 | 1 | 0 | 13 |
| `com.samsung.android.biometrics.app` | 20 | 0 | 8 | 0 | 0 |
| `io.brokentooth.soma` | 11 | 6 | 0 | 3 | 0 |
| `com.google.android.apps.labs.language.tailwind` | 10 | 9 | 0 | 0 | 0 |

---

## 5. Summary and Key Findings

1. **Autocorrect Rejection Accuracy:** When you reject suggestions (`REJECTED` events), you are correct **42.82%** of the time (you kept your reverted typing in `validated_reject`), whereas you only capitulated in **5.41%** of rejections. This indicates that rejections are a very clean, high-precision training signal for model adjustment.
2. **Manual Adjustment Proximity:** The low manual fix count immediately following a rejection (2.95%) indicates that you rarely edit a word immediately after reverting it; you generally accept your raw typed text as correct or keep moving.
3. **Unresolved Sessions (48.81%):** Almost half of the rejections cannot be sequenced. This occurs because the logging flush criteria (such as shifting focus or exiting the application) closes the typing session, isolating the `REJECTED` line without a trailing `SESSION` text block.
