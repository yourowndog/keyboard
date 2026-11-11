# Harvest-to-Training Pipeline

> **Status**: Verified architecture  > **Last verified**: 2026-09-29  > **Verified against**: live source in `~/projects/keyboard`

This document traces one keyboard usage event from device capture through
offline training to an ONNX model deployed back on-device.

---

## 1. Device-side collection (Kotlin)

All harvest code lives under
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/`.

| File | Lines | Role |
|------|-------|------|
| `HarvestManager.kt` | 631 | Orchestrator. Receives events from `NlpManager` and `LatinLanguageProvider` (suggestions shown, words committed, auto-applied corrections, reverts, manual edits). Writes dual output: human-readable markdown (`usage_harvest.md`) and machine JSONL via `HarvestJsonl`. Guards password fields via `AppContext.isHarvestBlocked`. |
| `HarvestV4.kt` | 567 | V4 word-slot state machine. Dual-writes alongside the v3 event stream: collects touches, suggestion offers, edits, outcome, neural shadow data, and layout context into a single `WORD_SLOT` record per word position. Defines `HarvestRoute` enum (TYPED_THROUGH, BAR_PICK, AUTO_APPLIED, GLIDE, VOICE, PASTE, ABANDONED) and `HarvestTouch` data class. |
| `HarvestJsonl.kt` | 216 | Append-only JSONL writer. Monotonic event IDs survive restarts (recovered from file tail). Session IDs rotate on app change. REVERTED events carry an `undoes` pointer to the AUTO_APPLIED they cancel. Output: `/sdcard/Documents/usage_harvest.jsonl`. |

Total: ~1,414 lines of Kotlin for collection.

**Output on device:**
- `/sdcard/Documents/usage_harvest.md` (human-readable, v3 legacy)
- `/sdcard/Documents/usage_harvest.jsonl` (machine, mixed v3 + v4 dual-write)

---

## 2. Device-side neural scorer (Kotlin)

The neural scorer runs on-device in shadow mode by default.

| File | Lines | Role |
|------|-------|------|
| `NeuralScorer.kt` | 217 | Loads `.florisboard` ONNX model via `OnnxRuntime`. Builds `VocabularyFeatureSet`, runs forward pass over candidates, blends neural probability with unigram/phrase scores. Emits `ShadowDecision` with `neuralTop`, `neuralMargin`, `wouldFire`, `agrees`, and `policyBlockers`. Disabled by default (`pref: useNeuralScorer=false`). |
| `NeuralScorerTest.kt` | 46 | Unit tests with a dummy model. |

Shadow logging is enabled by default (`pref: neuralScorerShadow=true`). The
neural output feeds back into the harvest stream as `NEURAL_SHADOW` events,
which `analyze_shadow.py` consumes later.

---

## 3. Transfer (Python)

| File | Lines | Role |
|------|-------|------|
| `tools/harvesting/snapshot_device.py` | 94 | ADB pull of `usage_harvest.{md,jsonl}` from device to `data/harvest/inbox/<timestamp>/`. Non-destructive: never merges or overwrites the canonical corpus. Supports `--adb`, `--adb --serial HOST:PORT`, and `--local` (Termux) modes. |
| `tools/harvesting/migrate_harvest_v4.py` | 295 | Converts mixed v3/v4 JSONL into canonical `word_slots.v4.jsonl` + `.sessions.jsonl`. Deduplicates by `slot` key (v4 supersedes v3 mirrors). Raises `RuntimeError` if v3 `WORD_COMMITTED` counts do not balance with v4 output. Never rewrites the input. |
| `tools/harvesting/tests/test_migrate_harvest_v4.py` | 72 | Tests for migration correctness. |

**Commands:**
```bash
# Capture from device
python3 tools/harvesting/snapshot_device.py --adb

# Convert to canonical v4 (idempotent)
python3 tools/harvesting/migrate_harvest_v4.py \
  data/harvest/raw/usage_harvest.jsonl \
  data/harvest/derived/word_slots.v4.jsonl
```

---

## 4. Training pipeline (Python)

All scripts live under `training/` (1,870 lines total). The `Makefile`
coordinates the workflow.

| File | Lines | Role |
|------|-------|------|
| `extract.py` | 327 | Parses mixed v3/v4 JSONL (and legacy markdown). Handles session boundaries. Suppresses v3 rows that have a v4 `slot` counterpart. Outputs to `training/data/`: `clean_corpus.txt`, `eval_pairs.jsonl`, `eval_negatives.jsonl`, `trace_samples.jsonl`. |
| `noise_model.py` | 172 | Learns spatial key adjacency weights from `noise.json`. Models realistic typo transformations for synthesis. |
| `synthesize.py` | 243 | Generates labeled train/validation examples by applying the noise model to clean corpus data. |
| `featurize.py` | 44 | Builds vocabulary and feature matrices. Feature contract defined in `feature_spec.md`. |
| `nn_scorer.py` | 56 | Neural network model definition used during training. |
| `train.py` | 275 | Training, evaluation, and ONNX export entry point. Cross-validation, 1,000+ configurable arguments. |
| `evaluate.py` | 135 | Baseline heuristic evaluation (BLEU/WER). |
| `analyze_shadow.py` | 170 | Analyzes `NEURAL_SHADOW` events logged on-device. Measures what the neural scorer would have done vs what actually happened. |
| `tau_sweep.py` | 62 | Sweeps the temperature/gate threshold parameter. Evaluates precision-recall trade-offs for the live neural gate. |
| `common.py` | 386 | Shared utilities used across the pipeline. |

**Commands:**
```bash
make pull       # snapshot from connected device
make extract    # harvest -> normalized examples
make noise      # learn typo noise model
make synth      # construct train/validation data
make eval       # baseline evaluation
make shadow     # analyze device shadow output
```

`make data` runs extract, noise, synthesis, and evaluation in sequence.

---

## 5. Data directory layout

```
data/harvest/
  inbox/         exact timestamped device snapshots (Git-ignored)
  raw/           canonical reviewed corpora (usage_harvest.jsonl, usage_harvest.md)
  reports/       aggregate measurements (harvest_manifest.json, HARVEST_RECON.md, etc.)
  derived/       intermediate outputs (word_slots.v4.jsonl, bigrams, personal vocab)
```

---

## 6. Current state

- **Harvest data**: `data/harvest/raw/usage_harvest.jsonl` has 273 lines, all v3 format.
- **Event distribution**: 184 SUGGESTIONS_SHOWN, 44 WORD_COMMITTED, 23 AUTO_APPLIED,
  15 SESSION_TEXT, 4 REVERTED, 2 MANUAL_EDIT, 1 INSISTED.
- **Migration**: `migrate_harvest_v4.py` has not been run on the current corpus.
  No `word_slots.v4.jsonl` exists yet in `data/harvest/derived/`.
- **Training data**: `training/data/` does not exist yet. `extract.py` has not been run.
- **Neural gating**: disabled by default on device (`useNeuralScorer=false`).
  Shadow logging is active and collecting counterfactual data.
- **Full corpus history**: `harvest_manifest.json` shows 63,135 valid log records
  spanning 2025-12-24 to 2026-07-03 (from the larger historical corpus).

---

## 7. Privacy boundaries

- `AppContext.isHarvestBlocked` is checked in `HarvestManager`, `HarvestJsonl`,
  and `HarvestV4` — password fields are never harvested.
- Snapshots are exact copies; the pipeline never deduplicates or overwrites
  source data.
- Derived outputs in `derived/` do not become runtime assets until reviewed
  and deliberately applied.

---

## 8. Cross-references

- End-to-end flow diagram: `docs/architecture/autocorrect-harvest-titan-loop.md`
- Neural scorer runtime: `docs/autocorrect/neural-scorer.md`
- Harvest tools reference: `tools/harvesting/README.md`
- Training workflow: `training/README.md`
- Feature spec: `training/feature_spec.md`
