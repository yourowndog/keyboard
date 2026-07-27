# Harvest Session — Analysis, Design Decisions & North-Star Plan (2026-07-15)

> Status: **Authoritative handoff / working plan.** NOT yet folded into canonical
> `docs/autocorrect/`. To be reconciled during the Phase 0 docs audit (see below).
> Author: harvest session 2026-07-15 (Opus). Preserve verbatim; do not paraphrase
> the numbers or the mechanism — future agents must reconstruct this without drift.

This file exists because a long, high-value harvest + design session risked being
lost to context compaction. It captures (1) what the data showed, (2) the root-cause
mechanism, (3) the design decisions we aligned on, (4) the neural pipeline as it
actually exists, (5) the staged plan from here to the "Gboard-style" north star,
and (6) the immediate next steps. Everything is grounded in files/line refs so it
can be re-verified.

---

## 0. Operating context (things that bit us this session — fix these)

- **The `harvest` skill is node-specific and broke immediately.** `SKILL.md` hardcodes
  `~/keyboard-local` and `python3 harvest.py`. On this node (`sleeper`) the repo is at
  `~/projects/keyboard`, scripts live in `tools/harvesting/`, and data lands in
  `data/harvest/`. **Fix (Phase 0):** make the skill path-agnostic — resolve the repo
  root via git (`git rev-parse --show-toplevel`) and use repo-relative paths, not a
  home-dir path. It must work on any node.
- **Device pull:** phone is `icarion` on Tailscale (`100.101.185.66`), but ADB wireless
  port rotates. This session it connected on the LAN at `192.168.1.143:36951`. Pull with:
  `python3 tools/harvesting/snapshot_device.py --adb --serial <host:port>`.
  Snapshots land in `data/harvest/inbox/<timestamp>/` (md + jsonl), unmerged.
- **A repo hook blocks `grep -r`/`find … keyboard`/`rg`** (redirects to jcodemunch MCP).
  Use Python for filesystem scans, or jcodemunch for code.
- **`data/harvest/reports/harvest_summary.md` is STALE (generated 2026-07-03).** Do NOT
  trust it. It predates the refactor and recommends anti-correcting `dont→don't`,
  `i→I`, `im→I'm` — which the refactor **fixed**. Following it would regress the fix.
  Regenerate before reading.

---

## 1. Data reviewed

- Fresh device snapshot pulled this session: `data/harvest/inbox/20260715-042914/`
  (`usage_harvest.jsonl` 16.8 MB, `.md` 10.4 MB), last event `2026-07-15T03:23`.
- **Review boundary (last event already in canonical `raw/`):** `2026-07-09 02:50:24`.
  All analysis below is the window **after** that boundary (≈6 days of heavy use).
- JSONL is the rich source (schema `v:3`). Event types in-window:
  `WORD_COMMITTED 29504`, `SUGGESTIONS_SHOWN 14989`, `NEURAL_SHADOW 6436`,
  `SESSION_TEXT 6255`, `AUTO_APPLIED 830`, `REVERTED 169`, `MANUAL_EDIT 149`,
  `INSISTED 5`, `NEW_WORD 1`.
- Recent honest accuracy metric: **REVERTED/AUTO_APPLIED = 169/830 = 20.4%** of
  auto-corrections were undone. (The stale summary's "54.7%" is all-time and misleading.)

### Key JSONL schemas (so extractors don't re-guess)
- `SUGGESTIONS_SHOWN`: `{typed, prev, candidates:[[term, score],…]}` — **the gold signal**;
  full scored candidate list per keystroke. Score is confidence (higher=better).
- `NEURAL_SHADOW`: `{typed, prev, ngramTop, neuralTop, typedP, topP, margin, wouldFire, agrees, ranked}`.
- `AUTO_APPLIED` / `REVERTED` (`REVERTED` carries `undoes`=id of the applied event) /
  `MANUAL_EDIT` (`{before, after, prev, trace}` — e.g. `trace:"-⌫s⌫name"`).

---

## 2. Findings (what the data actually showed)

### 2a. THE ROOT CAUSE — valid corrections score *negative* and lose to the literal
**947 missed-correction events across 719 distinct forms in 6 days.** In each, a valid
dictionary fix at edit-distance 1–2 was retrieved and shown but scored *below* the typed
word, so nothing fired:
- `whag → what` (−0.73), `renamw → rename` (−1.05), `autocirrect → autocorrect` (−1.17),
  `clauded → claude` (−0.48).

**Mechanism (verified against `CandidateScorer.kt` + `SuggestionEngine.kt`):**
- Scorer is penalty-based; `toConfidence = -penalty`.
- The **literal typed word is injected into the ranked list at a flat `0.0`** baseline
  (in the provider, *after* `NgramSuggestionEngine.rank()`; `rank()` itself does not add it).
  When the typed word is a real dict word it gets `EXACT_MATCH_BONUS -100` and dominates;
  when it's a non-word (a typo) it sits at `0.0`.
- A distance-1 correction's penalty ≈ `edit(1) + spatial(~1) + noHitBigram(0.2) − freq(~1.3) ≈ +0.9`
  → confidence ≈ **−0.9**, which loses to the literal at `0.0`.
- **Why:** the only reward for "is a real, common word" is `frequency * 0.1`
  (`CandidateScorer.kt:126`), too weak to overcome edit+spatial of even one key-slip; and
  the non-word literal pays **no OOV penalty**. Every one-off typo starts in the hole.
- **This is PRE-EXISTING, not caused by the refactor.** Git shows the refactor only
  touched apostrophe licensing in this file (`APOSTROPHE_TYPO_MIN_LOG_FREQ`, commits
  `91788123`/`334afedb`). Core constants unchanged.
- Contractions/casing (`i→I`, `dont→don't`) still work because they take **separate
  fast-paths** (`CASING_FAST_PATH`, `LICENSED_CONTRACTION_FAST_PATH`) that skip this math.

### 2b. Refactor-caused deltas (narrower, real)
- `334afedb` **removed** the contextual `were→we're` / `its→it's` correction (it misfired
  on "people were → people we're"). Sam *accepted* `were→we're` 13× and `its→it's` 11× in
  this window, so their removal reads as a regression — genuine tradeoff, now literal-only.
- `09792a87` made the **SymSpell-only fallback conservative**: `LEGACY_FALLBACK` candidates
  no longer auto-commit on rank alone (wheres/theres/hell/shell no longer auto-fire degraded).

### 2c. OOM — real, but **resolved** per Sam
`java.lang.OutOfMemoryError: Failed to allocate` stacktraces (real device crashes, Jul 9–10,
saved as `.stacktrace` files Sam was pasting). **Cause (Sam):** old code loaded the whole
dictionary + bigram table multiple times and precomputed 2-edit deletions for the entire
dictionary → OOM. **Now:** generated per-word on the fly → order-of-magnitude less memory,
**no longer in crash/fallback territory.** ⇒ The negative-scoring bug is on the **primary
path**, not the degraded fallback. Drop the "OOM→fallback" thread.

### 2d. Spacing — the errant-space class has NO correction path (feature gap)
- `WordSegmentation.kt` only **splits** an omitted space (`inthe→in the`), suggestion-only,
  one unique dict+bigram-supported split. There is **no join/merge direction at all.**
- So errant in-word spaces (`differenr t`→different, `frequentlt y`→frequently,
  `secone d`→second) are 100% uncorrected. Raw detector counted 486 but is inflated by
  legit `I`/`a`; genuine subset ≈ dozens/week (not yet cleanly counted — TODO).
- **NOT a regression — a missing feature.** The 403 "dot-for-space" were false positives
  (Sam pasting code: package names, `NeuralScorer.kt`, stack traces) — register issue, ignore.

### 2e. Digit corrections too narrow
`CommitPolicy` allows exactly one digit substitution **only when the digit is the sole
difference** → `sugg3tion→suggestion` works, `sugg3stionns→suggestions` fails (digit + extra
edits). Should be a general weighted edit, not a special case.

### 2f. Retrieval ceiling (distinct from scoring)
`wsnt → wasn't` **failed because `wasn't` was never retrieved** (SymSpell edit-dist + apostrophe
didn't surface it). That's a *retrieval* miss, not a scoring miss — motivates hypothesis
generation (§4 mini-decoder).

### 2g. Harvest data-quality issues (affect only the strict consumers)
- **`MANUAL_EDIT.after` often records the NEXT fragment typed, not the intended target**
  (`wifes→name`, trace `-⌫s⌫name`; `goin→g`; `wsnt→ant`). So clean `(typed→intended)` pairs
  must be **reconstructed via trace replay** (`recover_pre_correction`/`replay_trace` in
  `training/common.py`, used by `noise_model.py`), not read off `after` directly.
- Code/stacktrace text pollutes `SESSION_TEXT` → register filtering (by app context) required.

---

## 3. Curated actionable proposals (APPROVED by Sam; NOT yet applied)

Saved raw buckets: `data/harvest/derived/harvest_review_20260715.md`.

- **Anti-corrections → `PersonalPreferences.kt` ANTI_CORRECTIONS** (reverted ≥2×, curated):
  `goin→going's`, `Im`/`im→Important`, `wifes→wife`, `arr→Array`, `icloud→clod`.
  Maybe `kn→and` (weak, 2×). **Excluded** `dure→sure` (accepted 9×), `teh→the` (accepted 11×),
  `were→we're` (already removed by refactor) — blocking them would kill wanted corrections.
- **Personal vocab (leave-alone) / dict adds:** `idk` (missed 16×), `omniboard`,
  and the name **`DiPaola`** (proper casing — Sam corrected `dipaola`). `idk` → PERSONAL_VOCAB
  (never-correct slang, see commit `3d8ae523`); names/omniboard → dictionary.
- **Bigrams:** thin this window — only `do not`, `and the`, `in the` are real prose;
  the rest is a pasted Jetpack Compose stacktrace (`kt at`, `compose ui`, …). Do NOT bulk-add.
- **Neural gate threshold:** do **NOT** ship a change. In shadow mode τ only affects
  `wouldFire` logging. Treat τ as an **evaluation parameter** (sweep it each harvest), not a
  shipped constant. See §5.

---

## 4. Design decisions we aligned on (the architecture direction)

### 4a. Decouple the literal from ranking + explicit margin gate  ← Sam GREEN-LIT
The `0.0` literal conflates three concerns. Separate them:
1. **UI layer:** always surface the literal typed string as a tappable "reclaim" chip,
   by invariant, *outside* the scorer (the Gboard "tap to keep what I typed" behavior).
   It is **not a scored candidate.**
2. **Ranking layer:** score real candidates against each other on quality only; literal not in pool.
3. **Commit gate:** a correction auto-fires iff it clears an absolute quality bar AND beats
   the literal by a **margin** — same shape as the neural rule `P(top) − P(typed) > τ`.
   Populate the existing `CorrectionDecision`/`CommitPolicy` seam.
**Byproduct:** the negative-scoring bug disappears and we DELETE the special case instead of
patching it. This is the fast win; recovers the ~947 missed fixes.

### 4b. Generalize to a "noisy channel" edit model
sub / del / ins / swap / **space-op** / **digit-op** all become **weighted edits**. This
absorbs the digit special-case (§2e) and the space fix (§2d) into ONE place, and matches what
`noise_model.py` already models offline (minus spaces — see §4d). Space = one edit; digit =
one edit with number-row spatial cost (`KeyboardLayout` already has coords). Keep
"don't touch real numbers/versions/IDs" as guards.

### 4c. The three roles (never blur these again)
- **Error model** (`noise_model.json` via `noise_model.py`) = **offline statistics**, not a net.
  Models `P(typed | intended)` — "how Sam's fingers slip." **Generates training data.**
- **Mini-decoder** ("faked Gboard") = **online hand-written code**, not trained. On each
  correction it **proposes extra candidates** (merge `wa nt→want`, split, digit) and hands them
  to the ranker. It generates hypotheses; it decides nothing. Fixes the retrieval ceiling (§2f).
- **Shadow ranker** (`autocorrect_v1.onnx`) = **online trained net**. Scores candidates
  (`P(intended | typed, context)`) and, via the margin gate, decides.
> Error model writes the test · mini-decoder adds options to the test · ranker takes the test.

### 4d. ONE log, MANY filtered views (resolves the data anxiety)
**Log the superset faithfully — including spaces — then let each consumer filter.**
`space_dropped` in `noise_model.py` is a *consumer* choice, NOT a logging choice.
Never drop signal at capture time. Consumers: dictionary (words), bigrams (pairs),
error model (clean pairs, skips space for now), ranker (candidates+context),
future space-decoder (space events — already captured). **Tier by quality:** lenient
consumers (dict/bigram/freq) eat all data incl. old/messy; strict consumers (error
model/ranker) use the clean/recent/trace-reconstructable subset. Nothing falls off a cliff.

---

## 5. Neural pipeline as it ACTUALLY exists (verified in `training/`)

Substantial, coherent, ~half-built toward the vision. Files:
- `noise_model.py` → fits Sam's personal typo distribution (Damerau sub/del/ins/swap +
  backspace-trace replay + QWERTY-adjacency smoothing) → `data/noise_model.json`.
  **Currently SKIPS space and models apostrophe-drop** (`space_dropped` counter, ~line 59).
- `synthesize.py` → runs the noise model FORWARD on clean text → labeled `(typo→intended)`
  training pairs shaped like Sam's errors.
- `nn_scorer.py` (model def), `train.py` (train + ONNX export), `featurize.py` + `feature_spec.md`
  (the Python⇄Kotlin feature contract — char enc 31-id vocab, 5 scalars, FNV-1a context hash).
- `evaluate.py`, `analyze_shadow.py` (NEURAL_SHADOW eval → `shadow_analysis.json`),
  `tau_sweep.py` (threshold tradeoff). `Makefile`: `make pull|extract|noise|synth|eval|shadow`.
- Data: `train.jsonl` 686 MB, `val.jsonl` 38 MB, `autocorrect_v1.onnx` (+int8), `noise_model.json`.

**Model decision rule (`feature_spec.md`):** `fire iff argmax P != typed AND P(top)−P(typed) > τ`;
`is_typed_itself` is an explicit feature; τ is runtime pref `suggestion__neural_threshold`,
calibrated in `metrics.json`, never baked in. Defaults: **shadow logging ON, live gating OFF,
τ=0.30.** So the neural model already solves the "keep typed" question *probabilistically* —
no `0.0` hack. It is the principled version of the gate in §4a.

**Shadow results this window (5,344 decisions):** agrees w/ heuristic 60.7%; would-fire 30.6%
(median firing margin 0.89). In disagreements it is **often better** — picks `going` over
`going's`, `what` over `what's`, `want` over leaving `wan`, `definite` over `defiant`. Weakness:
low-margin fires (`thi→the` @0.53, `goi→got` @0.50). ⇒ It fixes exactly the junk the heuristic
emits. Keep in shadow; **retrain to improve, sweep τ to measure** (retrain moves the whole
precision/recall curve; τ only slides along a fixed curve).

**The "Sam-coded neural net generator" Sam half-remembered = the noise_model → synthesize loop**
(a personal data generator that teaches the net Sam's specific errors). (Verbatim original
proposal recoverable from `~/.claude/projects/-home-sam-projects-keyboard/*.jsonl` if wanted.)

---

## 6. The Gboard north star (what it is, what it replaces)

**What we have:** per-token retrieve-then-rerank; space is a hard wall; candidate set frozen
before scoring (retrieval ceiling); "keep typed" is a hack.
**Gboard-style (public understanding):** a **running-window decoder** over a **spatial model**
(`P(key|touch)` — the error model, promoted to runtime) + a **language model** (`P(sequence)`),
where a decoder (beam/Viterbi or small seq2seq) finds the most probable *word sequence* over the
whole recent window and **emits/deletes spaces as part of a hypothesis**. It can retroactively
split, merge, fix-two-tokens-back, add/drop apostrophes — one probability calc. Also uses
inter-key **timing** (fast space = probably accidental) — a signal we don't fully capture yet.
**If built, it:** replaces the **ranker's architecture** (rerank → decode); **keeps and promotes
the error model** to the runtime spatial model (more central, not deleted); **adds a runtime LM +
decoder.** Not a third rival. **Cost:** multi-month; needs touch/timing capture, on-device LM,
beam search in ~tens-of-ms latency. North star, not this quarter. Phase 0's rich logging collects
its data starting now, so it begins with a corpus, not zero.

---

## 6b. North-star model — CONCRETE choices (added 2026-07-15, follow-up Q)

The north star is **not one downloadable model.** It is **three pieces**, and only one is a neural net.
Do not go looking for a HuggingFace model to plug in — big models (ByT5, T5 grammar correctors:
300MB–1GB) are far too slow/large for per-keystroke on-device inference.

| Piece | What | Source |
|---|---|---|
| **Spatial model** `P(key\|touch)` | per-key Gaussian over landing coords; = `noise_model.py` promoted to runtime | stats from Sam's own touches. Tiny. Neither trained-big nor downloaded. |
| **Language model** `P(sequence)` | scores plausibility of a word sequence | **build from our corpus** with **KenLM** (n-gram toolkit, no training loop). Tiny neural LM optional later. |
| **Decoder** (the engine) | beam/Viterbi over spatial×LM; spaces are add/delete ops | **hand-written algorithm.** Open reference: **AOSP LatinIME** C++ decoder `native/jni/src/suggest/` (Gboard's ancestor). |

Everything is built from Sam's data → that is *why* it fits in ~10ms on-device. Our existing
`nn_scorer.py` ONNX model is the "small, from-scratch, on-device" lineage the north star extends —
not something we swap for a download. **Plug-and-play things that DO exist:** KenLM (the LM piece)
and AOSP LatinIME (a decoder reference to read/adapt) — neither is "the model." A char-level
seq2seq fine-tuned from **ByT5-small** is the only path where a HF download enters, and it needs
distillation to fit on-device — research fork, NOT the recommended path.

## 6c. Logging the north star needs — THE GAP (added 2026-07-15)

Phase 0 item 2 says "add per-keystroke touch+timing." Restating why it is the highest-leverage
new logging: the spatial model + decoder **live on touch coordinates + inter-key timing** (fast
space = probably accidental). **We almost certainly do NOT capture this today** (Open Q #9 —
verify in `HarvestManager`). Text-level logging (what we have) powers Phases 1–2 fully; touch data
powers Phase 3. **No reformatting can conjure touch data that was never recorded**, so the
north-star corpus clock only starts once touch logging ships — add it soon so months of data
accrue. Concrete new per-keystroke schema to add (superset, alongside existing word events):
`{key, touch_x, touch_y, key_center_x, key_center_y, t_ms_since_prev, is_space, was_autocorrected}`.

## 6d. Reformatting the existing 686MB corpus — scripts (added 2026-07-15)

Reformat by **reading, not rewriting** the append-only log. Three scripts:
1. `backfill_schema.py` (one-time) — stamp each record with **schema version** + **quality tier**
   (old/messy vs recent/clean/trace-reconstructable). Writes a tiered index; never mutates originals.
2. **tier-aware `extract.py`** (§4d, Phase 0 #3) — one extractor, many views. Lenient consumers
   (dict/bigram/freq) eat everything incl. old data; strict consumers (error model/ranker) use clean tier only.
3. **trace-replay reconstructor** — rebuild real `(typed→intended)` pairs from `MANUAL_EDIT` backspace
   traces; salvages the low-quality early corpus. Caveat: salvages TEXT only, never touch/timing (§6c).

## 7. THE PLAN — staged

### Phase 0 — Foundation ("canonical, not littered")
1. **Docs audit BEFORE writing.** Inventory `docs/autocorrect/` + `docs/development/`; flag
   stale/overlapping/dupes; define the canonical set; merge/prune. Current docs seen:
   `autocorrect/{README, live-pipeline, heuristic-scoring, neural-scorer, harvesting,
   phrase-prediction, autocorrect-regression-forensic-record}`, plus this file.
2. **Lock canonical log schema as a SUPERSET** — record everything incl. spaces; confirm/add
   per-keystroke touch+timing (needed for north star); stamp schema version + review checkpoint.
3. **Tier `extract.py` by quality** — one extractor, N consumer views.
4. **Cross-link from `AGENTS.md` + `CLAUDE.md`** so "logging run" / "retrain ranker" / "harvest"
   route to the right doc. **Also fix the `harvest` SKILL.md to be node-agnostic (§0).**

### Phase 1 — Incremental wins (fast)
5. **Decouple literal + margin gate** (§4a) — kills negative scoring; recovers ~947 fixes.
6. **Mini-decoder at retrieval seam** (§4c) — merge/split/digit hypotheses feed the existing ranker.

### Phase 2 — Retrain loop (beat CandidateScorer)
7. Each substantial harvest: refit error model → synthesize → retrain ranker →
   `analyze_shadow` + `tau_sweep` → compare. Flip live gating on only when shadow beats heuristic.

### Phase 3 — North star (real decoder) — §6. Data already collected from Phase 0 on.

**Through-line:** Phase 0's unified rich log makes 1–3 possible without re-logging.

---

## 8. Immediate next steps (were queued when we stopped to preserve this)
- (a) **Docs audit** — read existing docs, produce keep/merge/kill inventory + canonical target.
- (b) **Run `extract.py` on `inbox/20260715-042914/`** — verify `(typed→intended)` pairs are
  clean enough to refit; measure how much trace-replay salvages from old low-quality corpus.
- Both read-only. Then apply Phase 1 §5 (already approved) test-first.

## 9. Open questions / TODO not yet resolved
- Clean count of genuine errant-space events/week (raw 486 inflated by `I`/`a`).
- Confirm whether HarvestManager captures per-keystroke touch/timing (needed for Phase 3).
- Verify `extract.py` register-filtering handles code/stacktrace pollution.
