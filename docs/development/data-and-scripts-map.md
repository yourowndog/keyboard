# Data & Scripts Map

> Status: Canonical
> Last verified: 2026-07-15

The single index of **every pipeline, every script, its purpose, its inputs, and where its
output goes.** Companion to [`repository-organization.md`](./repository-organization.md):
that doc owns *directory ownership*; this doc owns *data flow*. If you add or move a script,
update the relevant table here and its directory README.

## How the data flows (one glance)

```text
DEVICE (OmniBoard)                          OFFLINE (this repo)
  usage_harvest.jsonl  ── snapshot_device ─▶ data/harvest/inbox/<ts>/   (immutable, git-ignored)
   (append-only event log)                          │
                                                     ├─ (checkpoint) ─▶ data/harvest/raw/
                                                     │
                                    analyze / manifest / segment_recovery
                                                     │
                                     ┌───────────────┼────────────────┐
                                     ▼               ▼                ▼
                            data/harvest/reports  data/harvest/derived  app assets
                                                     │
                                          training/ pipeline (Makefile)
                                                     ▼
                                          autocorrect_v1.onnx (shadow)
```

**Design principle — ONE log, MANY views.** There is a single append-only event log on the
device (`usage_harvest.jsonl`). Every consumer (dictionary, bigrams, error model, ranker) is a
*filtered view* produced by an extractor — never a separate log. See the north-star plan §4d/§6d.

---

## Pipeline 1 — Harvest (device → corpus)

Tools in `tools/harvesting/` (see its README for detail).

| Script | Purpose | Reads | Writes |
|---|---|---|---|
| `snapshot_device.py` | Pull exact harvest files from device, no merge/dedupe | device via ADB | `data/harvest/inbox/<ts>/` |
| `harvest_analyze.py` | Comprehensive usage-data analysis | inbox / raw JSONL+MD | `data/harvest/reports/` |
| `harvest_manifest.py` | Measurement-only triage manifest | `usage_harvest.md` | `data/harvest/reports/harvest_manifest.json` |
| `segment_recovery.py` | Recover omitted-space splits (`inthe`→`in the`) | `usage_harvest.md` | `data/harvest/derived/segmentation_recovery.tsv` |
| `build_dictionary.py` | Rebuild language assets from first principles | corpora + `dict_sources/` | `app/src/main/assets/ime/dict/` |

- **Checkpoint boundary:** the last timestamp in `data/harvest/raw/` marks "already processed."
  Analyze only events *after* it from the newest `inbox/` snapshot.
- **Inbox snapshots are immutable and git-ignored.** Never edit one in place.

## Pipeline 2 — Dictionary / bigram maintenance

Tools in `tools/dictionary/` (mutate packaged assets — always diff-review).

| Script | Purpose | Reads | Writes |
|---|---|---|---|
| `clean_bigram_spam.py` | Strip SMS/telecom spam + dev jargon | bigram/phrase TSVs | cleaned TSVs |
| `rescale_bigrams.py` | Re-weight bigram frequencies | bigram TSV | rescaled bigram TSV |
| `inject_anchors.py` | Inject anchor entries into dictionary | dict + anchors | updated dict |

## Pipeline 3 — Neural training (offline ranker)

`training/`, driven by the **Makefile**. Outputs land in `training/data/`. Runs the noisy-channel
loop: fit Sam's error model → synthesize training data → train ranker → evaluate → shadow-analyze.

| `make` target / script | Purpose | Reads | Writes |
|---|---|---|---|
| `extract.py` | Parse harvest MD+JSONL into training inputs | `usage_harvest.{md,jsonl}` | eval pairs / corpus inputs |
| `noise_model.py` | Fit Sam's personal typo distribution `P(typed\|intended)` | extracted pairs | `training/data/noise_model.json` |
| `synthesize.py` | Generate synthetic training set from clean corpus + noise model | `clean_corpus.txt`, `noise_model.json` | `training/data/train.jsonl`, `val.jsonl` |
| `featurize.py` | Torch-free featurization contract (shared) | pairs | feature vectors |
| `train.py` | Train listwise ranker (run on `titan`) | `train.jsonl`/`val.jsonl` | `autocorrect_v1.onnx`, `metrics.json` |
| `nn_scorer.py` | ONNX scorer plug-in for `evaluate.py` | `autocorrect_v1.onnx` | (scores, in-process) |
| `evaluate.py` | Score ANY scorer against the gold eval set | eval set + scorer | `eval_results.json` |
| `tau_sweep.py` | Calibrate fire threshold τ on the REAL eval set | eval set + shadow logs | `tau_sweep_real.json` |
| `analyze_shadow.py` | Analyze `NEURAL_SHADOW` events (live model vs typed) | `usage_harvest.jsonl` | shadow report |

- **Shadow status:** model logs but does NOT gate live (τ is an *evaluation* knob in shadow, not a
  ship constant). Retrain to improve; sweep τ to measure. See north-star plan §5.
- **`noise_model.py` and the ranker are a PIPELINE, not rivals:** the error model *generates* the
  ranker's training data. See north-star plan §4b/§4c.

## Pipeline 4 — Standalone / forensic

| Script | Purpose | Location |
|---|---|---|
| `autocorrect_trace.py` | Frozen offline replay for autocorrect forensics | `tools/autocorrect/` |
| `utils/*` | Older upstream dictionary/config generators (not active workflow) | `utils/` |

---

## Where outputs live (canonical destinations)

| Output kind | Destination | Notes |
|---|---|---|
| Raw device pulls | `data/harvest/inbox/<ts>/` | immutable, git-ignored |
| Processed checkpoint | `data/harvest/raw/` | the "already seen" boundary |
| Analysis reports | `data/harvest/reports/` | human-readable; regenerate, don't trust stale |
| Derived consumer views | `data/harvest/derived/` | bigrams, dictionary adds, anti-corrections, etc. |
| Runtime dictionaries | `app/src/main/assets/ime/dict/` | source corpora do NOT live here |
| Trained model + metrics | `training/data/` | ONNX + json metrics |

## Convention — how to keep this doc true

1. **New script → add a top docstring** (what it does / reads / writes) AND a row here + in its dir README.
2. **New output → put it in a canonical destination above**, not a new ad-hoc folder.
3. **One log, many views:** never fork the event log for a new consumer — add an extractor/view.
4. AGENTS.md and CLAUDE.md link here + to `repository-organization.md` as the two canonical maps.
