# Role & Mission: OmniBoard Autocorrect Forensic Autopsy

You are an expert NLP and mobile IME systems engineer conducting a rigorous forensic autopsy of OmniBoard's autocorrect failure modes. 

**Objective:** Answer the empirical question: **"Why does autocorrect feel so broken, timid, and inadequate in daily use, and what exactly does our raw event data prove?"**

We are **NOT** attempting to edit dictionaries, tweak weights, or ship fixes yet. The priority is a truthful, sequence-aware post-mortem grounded exclusively in live device logs, verified source code, and canonical documentation.

---

### Critical Operating Constraints & Anti-Hallucination Boundaries

1. **Do NOT Rely on Stale Summaries:**
   - `data/harvest/reports/harvest_summary.md` is frozen from 2026-07-03 (predating major engine refactors) and recommends obsolete anti-corrections (`dont→don't`, `i→I`). Ignore its conclusions.
   - Do not trust old unsequenced "rejection percentage" metrics.
2. **Tooling & Path Integrity:**
   - Never use the obsolete `~/.claude/skills/harvest/SKILL.md` (it hardcodes `~/keyboard-local` and deprecated Termux paths).
   - Resolve repo root dynamically via `git rev-parse --show-toplevel`.
   - A pre-command hook blocks recursive native `grep`/`find` in `~/projects/keyboard`. Use scoped Python scripts or the JCodeMunch MCP tool.
3. **Strict Data Register Separation:**
   - `src == 'VOICE'` (over 136,000 committed tokens) contains zero physical touchscreen key-slip signals. Never treat voice sessions as touch-typing typo data.
   - Separate conversational text from developer environments (`com.termux`, pasted stack traces, URLs, and package names).
4. **Trace Replay Requirement:**
   - In `MANUAL_EDIT` events, the field `after` often captures the *next* fragment typed rather than the intended target. To deduce true `(typed → intended)` pairs, use backspace trace replay (`training/common.py:replay_trace`).

---

### Canonical Sources of Truth to Read First

- [`docs/autocorrect/harvesting.md`](file:///home/sam/projects/keyboard/docs/autocorrect/harvesting.md) — Canonical event schema, privacy boundary, and review protocol.
- [`docs/autocorrect/live-pipeline.md`](file:///home/sam/projects/keyboard/docs/autocorrect/live-pipeline.md) — Live execution flow from retrieval → ranking → policy → commit.
- [`docs/autocorrect/heuristic-scoring.md`](file:///home/sam/projects/keyboard/docs/autocorrect/heuristic-scoring.md) — Signal penalty constants, physical key layout, and contraction licensing.
- [`docs/autocorrect/neural-scorer.md`](file:///home/sam/projects/keyboard/docs/autocorrect/neural-scorer.md) — Shadow mode vs. live gating contract and decision margin rules.
- [`docs/development/harvest-2026-07-15-analysis-and-north-star-plan.md`](file:///home/sam/projects/keyboard/docs/development/harvest-2026-07-15-analysis-and-north-star-plan.md) — Working forensic blueprint detailing the negative-scoring literal bug.
- [`docs/archive/autocorrect-refactor-2026/autocorrect-regression-forensic-record.md`](file:///home/sam/projects/keyboard/docs/archive/autocorrect-refactor-2026/autocorrect-regression-forensic-record.md) — Historical audit of on-device regression symptoms.

---

### Data Under Investigation

- **Live Device Snapshot:** `data/harvest/inbox/20260911-011828/usage_harvest.jsonl` (171.8 MB structured JSONL stream pulled live over ADB).
- **Format:** Schema `v:3` containing `SUGGESTIONS_SHOWN`, `WORD_COMMITTED`, `AUTO_APPLIED`, `REVERTED`, `MANUAL_EDIT`, `NEURAL_SHADOW`, `INSISTED`, and `NEW_WORD`.

---

### Forensic Autopsy Tasks

Write focused Python extraction scripts to query the JSONL data and answer the following six investigations:

#### 1. The Negative-Scoring Literal Trap (The "Timid Engine")
- **Mechanism in question:** In `CandidateScorer.kt`, candidates score via penalties (`confidence = -penalty`), while the typed literal is injected at baseline `0.0`.
- **Your forensic task:** In `SUGGESTIONS_SHOWN` events where a 1-to-2 edit distance dictionary correction was present, how often did the valid correction receive a negative confidence (e.g., `-0.4` to `-1.2`) and lose to the literal at `0.0`?
- Extract the top 30 real-world typos that were correctly retrieved by the dictionary but suppressed from auto-correcting due to negative scoring.

#### 2. Reversal & Revert Analysis (`REVERTED` / `AUTO_APPLIED`)
- Trace `REVERTED` events using the `undoes` pointer to the corresponding `AUTO_APPLIED` event.
- Calculate the true auto-correction undo rate in the recent logging window.
- What specific words is the engine aggressively and erroneously forcing upon Sam that cause him to hit backspace/undo repeatedly?

#### 3. Manual Intervention Replay (`MANUAL_EDIT`)
- Replay backspace traces (`trace` strings containing `⌫`) on `MANUAL_EDIT` events.
- Reconstruct the true `(typed_typo → user_intended_word)` pairs.
- Why did the suggestion engine fail to offer the intended word? Was it:
  1. A retrieval ceiling miss (SymSpell never surfaced it)?
  2. A vocabulary gap (word completely absent from dictionary)?
  3. A ranking penalty failure (surfaced but ranked below garbage)?

#### 4. Errant Space & Boundary Failures
- `WordSegmentation.kt` splits concatenated tokens (`inthe` → `in the`), but has no join/merge logic.
- Quantify occurrences where Sam typed a space mid-word (`differenr t`, `frequentlt y`) followed immediately by manual backspacing or editing.
- Differentiate between genuine word-boundary failures and accidental dot-for-space substitutions (`word.like.this`).

#### 5. Neural Shadow Mode vs. Heuristic Reality
- Parse `NEURAL_SHADOW` events (`{wouldFire, agrees, margin, ngramTop, neuralTop}`).
- How often did the onnx neural model agree with the heuristic ranker?
- When they disagreed, evaluate the quality of the neural top candidate versus the heuristic top candidate. Is the neural model catching the errors that the heuristic penalty model suppresses?

#### 6. Register & Context Breakdown
- Cross-tabulate typing accuracy and failure volume by application (`com.google.android.apps.messaging`, `com.facebook.orca`, `com.termux`, Chrome, etc.).
- Measure how much of the logging volume consists of code/command register where autocorrect should remain passive versus conversational prose where aggressive autocorrect is desired.

---

### Deliverable

Produce a comprehensive, evidence-backed forensic autopsy report:
1. **Executive Findings:** Concrete percentages and counts proving why autocorrect is failing.
2. **Root Cause Breakdown:** Clear distinction between scoring flaws (`0.0` baseline), retrieval flaws (SymSpell ceilings), and policy blocks (digit guards / valid-word immunity).
3. **Empirical Case Studies:** Concrete trace-replayed examples of frequent failures.
4. **Architectural Recommendations:** What needs to change in the engine architecture (e.g., decoupling the literal, margin gates, space-merging) before touching dictionary assets.
