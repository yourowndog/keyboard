# Swipe-Synthesis Project State & Historical Artifacts Audit

> **Audit Date:** August 2026  
> **Status:** Canonical audit & state of the world  
> **Scope:** Full audit of datasets, preprocessing pipelines, generators, training scripts, evaluators, and runtime hooks across OmniBoard swipe synthesis.  
> **Mode:** Audit and artifact classification only (no modifications or architectural redesign).

---

## 1. Raw / Source Datasets Present and Their Schemas

| Dataset / File | Location | Format & Size | Schema / Fields | Provenance & Completeness |
| :--- | :--- | :--- | :--- | :--- |
| **FUTO Unique Vocabulary List** | [`research/swipe-training/futo_words_unique.txt`](file:///home/sam/projects/keyboard/research/swipe-training/futo_words_unique.txt) | Plain text (829 KB, 91,104 lines) | Single string per line (`word`) | Extracted previously from `futo_swipes.parquet` (`swipe-1` run). Contains raw unigrams (including punctuation artifacts like `-adic`, `!!destroy-oh-boy!!`). |
| **Sam's Custom Vocabulary Seed** | [`research/swipe-training/sams_custom_words.txt`](file:///home/sam/projects/keyboard/research/swipe-training/sams_custom_words.txt) | Plain text (4.9 KB, 660 lines) | Markdown sections + unigrams (`word`) | Hand-curated list of family names, tech abbreviations, and domain jargon. |
| **Harvest Raw Event Streams** | [`data/harvest/raw/usage_harvest.jsonl`](file:///home/sam/projects/keyboard/data/harvest/raw/usage_harvest.jsonl)<br>[`data/harvest/inbox/*/usage_harvest.jsonl`](file:///home/sam/projects/keyboard/data/harvest/inbox/20260819-070118/usage_harvest.jsonl) | JSONL & Markdown | `{"type": "...", "text": "...", "word": "...", "applied": "...", "typed": "...", "sess": "...", "ts": ...}` | 9 months of continuous mobile device telemetry from Sam's daily driver. |
| **Unified Lexicon TSV** | [`app/src/main/assets/ime/dict/unified_dictionary.tsv`](file:///home/sam/projects/keyboard/app/src/main/assets/ime/dict/unified_dictionary.tsv) | TSV (3.4 MB, 147k entries) | `word\tfrequency` | Production Android unigram dictionary used for candidate scoring and baseline vocab lookup. |
| **FUTO Human Swipes Dataset (`futo_swipes.parquet`)** | *Referenced in 7 scripts* | Parquet (~1.05 GB compressed) | `id`, `session`, `timestamp`, `word`, `canvas_width`, `canvas_height`, `orientation`, `data` (`[{x, y, t}, ...]`), `sentence`, `word_idx`, `distance`, `potentially_invalid_sentence` | **NOT PRESENT LOCALLY.** The `.parquet` file was never committed to git and is not currently on disk. It must be downloaded/streamed from Hugging Face ([`futo-org/swipe.futo.org`](https://huggingface.co/datasets/futo-org/swipe.futo.org)). |

---

## 2. Survival of Original FUTO Fields

A forensic inspection of all data files and scripts reveals how previous pipelines handled the canonical FUTO fields:

| Field Name | Description | Survived in Local Files? | Status in Previous Processing Scripts | Information Loss / Notes |
| :--- | :--- | :--- | :--- | :--- |
| `word` | Target word prompt string | **YES** ([`futo_words_unique.txt`](file:///home/sam/projects/keyboard/research/swipe-training/futo_words_unique.txt)) | Preserved as primary dictionary key | Preserved verbatim (91,104 unique tokens). |
| `data` (`x, y, t`) | Raw continuous touch trajectory points | **NO** (0 raw points stored locally) | Severely collapsed / modified | In [`extract_futo_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/extract_futo_swipes.py), timestamps ($t$) were completely dropped, and coordinates were linearly resampled to 50 spatial points. In [`train_seq2traj.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_seq2traj.py), $t$ was converted to $\Delta t$. |
| `canvas_width`, `canvas_height` | Screen/viewport dimensions in px | **NO** | Used only as normalization divisor; discarded | Discarded after division ($x / W$, $y / H$). This destroyed the physical aspect ratio ($W/H$), distorting portrait vs. landscape swipes into a forced $[0, 1]^2$ unit box. |
| `timestamp` | Epoch ms of gesture completion | **NO** | Discarded | Discarded entirely in all scripts. |
| `orientation` | Screen orientation (e.g. `portrait-primary`) | **NO** | Ignored / Discarded | Not read by any previous script, mixing landscape, tablet, and portrait touch dynamics. |
| `session` | Anonymous session UUID (`anon-session-<UUID>`) | **NO** | Ignored / Discarded | Never read or stored. Inter-user clustering and session-level motor signature isolation were completely lost. |
| `sentence`, `word_idx` | Sentence prompt context and word offset | **NO** | Discarded | Discarded during conversion to isolated word tokens. |
| `distance` | Geometric deviation from ideal path | **NO** | Ignored / Discarded | Never used for outlier rejection or filtering. |
| `potentially_invalid_sentence` | Data cleaning flag | **NO** | Ignored / Discarded | Discarded. |
| `language`, `layout`, `dual_finger` | Multi-layout / two-thumb flags (`swipe-5`) | **NO** | Ignored / Discarded | Not supported by old pipeline. |

---

## 3. Derived and Normalized Datasets

| Derived File | Generator Script | Transformation Applied | Information Discarded / Collapsed |
| :--- | :--- | :--- | :--- |
| [`research/swipe-training/harvested_missing_words.tsv`](file:///home/sam/projects/keyboard/research/swipe-training/harvested_missing_words.tsv) (13,258 rows) | [`tools/harvesting/extract_harvest_swipe_vocab.py`](file:///home/sam/projects/keyboard/tools/harvesting/extract_harvest_swipe_vocab.py) | Extracted from 9-month raw harvest JSONL/MD streams, filtered against [`futo_words_unique.txt`](file:///home/sam/projects/keyboard/research/swipe-training/futo_words_unique.txt), and categorized into contractions, tech/AI terms, slang, and frequent domain words. | **Clean & lossless extraction** of vocabulary intent and frequencies. |
| [`research/swipe-training/target_swipe_vocabulary_supplement.txt`](file:///home/sam/projects/keyboard/research/swipe-training/target_swipe_vocabulary_supplement.txt) (6,842 rows) | [`tools/harvesting/extract_harvest_swipe_vocab.py`](file:///home/sam/projects/keyboard/tools/harvesting/extract_harvest_swipe_vocab.py) | Filtered subset of `harvested_missing_words.tsv` with frequency $\ge 3$ or domain priority. | Single-occurrence proper nouns and low-confidence typos pruned. High quality. |
| [`research/swipe-training/missing_words_top1000.txt`](file:///home/sam/projects/keyboard/research/swipe-training/missing_words_top1000.txt) (1,000 rows) | [`research/swipe-training/analyze_vocab_gaps.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_vocab_gaps.py) | Top 1,000 frequent words in `unified_dictionary.tsv` missing from FUTO. | Retains only top 1,000 entries. |
| [`research/swipe-training/artifacts/precomputed_gestures.json`](file:///home/sam/projects/keyboard/research/swipe-training/artifacts/precomputed_gestures.json) (32.2 MB, 10,000 words) | [`research/swipe-training/precompute_gestures.py`](file:///home/sam/projects/keyboard/research/swipe-training/precompute_gestures.py) | Straight lines connecting static QWERTY key centroids, with ad-hoc loops for duplicate letters, resampled to 50 points (100 flat floats per word). | **Total collapse:** No velocity profiles, no human motor kinematics, no timestamps, static unit-square QWERTY positions. |
| [`research/swipe-training/test_synthetic.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_synthetic.jsonl)<br>[`research/swipe-training/test_improved.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_improved.jsonl) (15 lines each) | [`research/swipe-training/generate_synthetic_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/generate_synthetic_swipes.py) | Minimum-jerk polynomial interpolation + cubic Bezier + ad-hoc corner overshoot on static unit square for 5 words (`Kiry`, `Sam`, `Mike`, `Elijah`, `Levi`, 3 variations each). | **Severely collapsed:** Handcrafted polynomial curves with synthetic timestamps. Does not reflect true human motor distribution. |

---

## 4. Script Inventory & Analysis

Below is the inventory of every script involved in the previous swipe attempts:

```
research/swipe-training/
├── extract_futo_swipes.py          # [OLD GENERATOR / EXTRACTOR]
├── generate_synthetic_swipes.py     # [OLD GENERATOR - HEURISTIC]
├── train_seq2traj.py               # [OLD GENERATOR - DETERMINISTIC NN]
├── train_neuroswipe_v1.py          # [REUSABLE INFRASTRUCTURE - RECOGNIZER]
├── validate_against_futo.py        # [EVALUATION - FLAWED]
├── prepare_training_data.py        # [DERIVED DATA PIPELINE]
├── setup_training.py               # [LIKELY OBSOLETE - EXTERNAL GLUE]
├── precompute_gestures.py          # [OLD GENERATOR - GEOMETRIC]
├── precompute_gestures_binary.py   # [OLD GENERATOR - BINARY]
├── analyze_futo_deeply.py          # [REUSABLE INFRASTRUCTURE - PROFILER]
├── analyze_vocab_gaps.py           # [REUSABLE INFRASTRUCTURE - PROFILER]
├── count_words.py                  # [REUSABLE INFRASTRUCTURE - UTILITY]
├── explore_data.py                 # [REUSABLE INFRASTRUCTURE - UTILITY]
├── investigate_futo_coords.py      # [REUSABLE INFRASTRUCTURE - PROFILER]
└── train_on_colab.ipynb            # [LIKELY OBSOLETE - COLAB RUNNER]
```

### Detailed Script Analysis

1. [`extract_futo_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/extract_futo_swipes.py):
   - **What it actually does**: Reads `futo_swipes.parquet`, selects top $N$ words (10 samples per word), normalizes coordinates by dividing by canvas size, linearly interpolates to 50 equidistant points, and writes a binary format with a 12-byte header `[num_words: u32, swipes_per_word: u32, points_per_swipe: u32]`.
   - **Flaw**: Dropped timestamps and used a 12-byte header that does not match the 8-byte header expected by the Android runtime [`PrecomputedGestureCache.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/PrecomputedGestureCache.kt).
2. [`generate_synthetic_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/generate_synthetic_swipes.py):
   - **What it actually does**: Hardcodes a static QWERTY layout on a $[0, 1] \times [0, 1]$ grid. Computes minimum-jerk polynomials (Flash & Hogan 1985) between key centers with spring-mass-damper physics, artificial overshoot, and two-thirds power law timing.
   - **Flaw**: Handcrafted heuristics produce rigid, un-biomechanical curves that suffer from an affine aspect-ratio distortion ($1.8\times$ distance gap from human swipes).
3. [`train_seq2traj.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_seq2traj.py):
   - **What it actually does**: Defines a PyTorch Seq2Traj model consisting of a character embedding + Bi-GRU encoder and an autoregressive GRU decoder with linear heads predicting $(x_t, y_t, \Delta t_t, \text{eos})$.
   - **Flaw**: Trained with deterministic regression (MSE). Because human swiping is multi-modal, MSE mathematically collapses to the conditional expectation $\mathbb{E}[\text{traj} \mid \text{word}]$ (the arithmetic centroid), resulting in "floaty," unnatural corner-cutting.
4. [`train_neuroswipe_v1.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_neuroswipe_v1.py):
   - **What it actually does**: Defines a clean, standalone PyTorch 4-layer Transformer Encoder-Decoder classifier ($d_{\text{model}}=128$, 4 heads, causal decoding) designed to decode 7-channel trajectory inputs $(x, y, dx, dy, dt, v, \theta)$ into candidate word tokens.
   - **Status**: Structurally sound recognizer template for downstream training.
5. [`validate_against_futo.py`](file:///home/sam/projects/keyboard/research/swipe-training/validate_against_futo.py):
   - **What it actually does**: Extracts 5 samples for **7 hardcoded test words** (`ham`, `bag`, `you`, `run`, `the`, `and`, `about`) from `futo_swipes.parquet` and compares duration, velocity, corner radii, and overshoot against synthetic outputs.
   - **Flaw**: Calculates corners using normalized 2nd derivative standard deviation thresholds ($> \mu + 0.5\sigma$), detecting ripples as corners. Only tests 7 words.
6. [`prepare_training_data.py`](file:///home/sam/projects/keyboard/research/swipe-training/prepare_training_data.py):
   - **What it actually does**: Converts `futo_swipes.parquet` to JSONL (capped at 50 samples per word) and merges with `synthetic_swipes_final.jsonl` into `combined_training_data.jsonl`.
7. [`setup_training.py`](file:///home/sam/projects/keyboard/research/swipe-training/setup_training.py):
   - **What it actually does**: Glue script creating keyboard grid JSONs, tokenizers, trajectory statistics, bounding boxes, and training configs for the third-party `proshian/neural-swipe-typing` repo.
8. [`precompute_gestures.py`](file:///home/sam/projects/keyboard/research/swipe-training/precompute_gestures.py):
   - **What it actually does**: Connects key centroids in straight lines (with small square loops for repeated letters) and resamples to 50 points; writes `precomputed_gestures.json` (32.2 MB).
9. [`precompute_gestures_binary.py`](file:///home/sam/projects/keyboard/research/swipe-training/precompute_gestures_binary.py):
   - **What it actually does**: Implements the same geometric logic as `precompute_gestures.py`, but writes an 8-byte header binary file (`precomputed_gestures.bin`) matching [`PrecomputedGestureCache.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/PrecomputedGestureCache.kt).
10. [`analyze_futo_deeply.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_futo_deeply.py), [`investigate_futo_coords.py`](file:///home/sam/projects/keyboard/research/swipe-training/investigate_futo_coords.py), [`explore_data.py`](file:///home/sam/projects/keyboard/research/swipe-training/explore_data.py), [`count_words.py`](file:///home/sam/projects/keyboard/research/swipe-training/count_words.py):
    - **What they actually do**: Fast, memory-efficient PyArrow inspection scripts to verify parquet row groups, schema column ranges, and sample distributions.
11. [`analyze_vocab_gaps.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_vocab_gaps.py):
    - **What it actually does**: Cross-references unigrams in `unified_dictionary.tsv` against `futo_swipes.parquet` to produce `missing_words_top1000.txt`.
12. [`train_on_colab.ipynb`](file:///home/sam/projects/keyboard/research/swipe-training/train_on_colab.ipynb):
    - **What it actually does**: Colab notebook configured to clone `proshian/neural-swipe-typing`, upload training shards, patch `accelerator='gpu'`, and train for 2–3 hours on a T4 GPU.
13. [`tools/harvesting/extract_harvest_swipe_vocab.py`](file:///home/sam/projects/keyboard/tools/harvesting/extract_harvest_swipe_vocab.py):
    - **What it actually does**: Extracts unigrams from 9-month mobile logs, strips metadata artifacts, counts frequencies, cross-checks against FUTO unigrams, and emits categorized TSV and TXT files.
14. [`app/src/main/kotlin/.../PrecomputedGestureCache.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/PrecomputedGestureCache.kt):
    - **What it actually does**: Android runtime asset loader for `ime/swipe/futo_swipes.bin`. Reads an 8-byte header `[numWords: Int32, numSamplePoints: Int32]` followed by word lengths, UTF-8 strings, and 50 $(x,y)$ float pairs.

---

## 5. Trained Models, Checkpoints, and Historical Artifacts

| Category | Item Name / Path | File Size | Status / Verdict |
| :--- | :--- | :--- | :--- |
| **Model Weights & Checkpoints** | `models/neuroswipe_v1_best.pt`<br>`models/neuroswipe_v1.onnx`<br>`models/neuroswipe_v1.pte`<br>`checkpoints/*.ckpt` | **None** | **0 model checkpoints exist.** No trained weights survived from any previous swipe experiment. (Note: [`app/src/main/assets/ime/nn/autocorrect_v1.int8.onnx`](file:///home/sam/projects/keyboard/app/src/main/assets/ime/nn/autocorrect_v1.int8.onnx) is for tap-typing autocorrect scoring, not swipe decoding). |
| **Generated Output Files** | [`research/swipe-training/artifacts/precomputed_gestures.json`](file:///home/sam/projects/keyboard/research/swipe-training/artifacts/precomputed_gestures.json) | 32.2 MB | 10,000 words in straight-line geometric via-points. Archived to avoid inflating APK size. |
| **Generated Sample Output** | [`research/swipe-training/test_synthetic.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_synthetic.jsonl)<br>[`research/swipe-training/test_improved.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_improved.jsonl) | 67.6 KB<br>70.4 KB | 15 synthetic traces across 5 test words (`Kiry`, `Sam`, `Mike`, `Elijah`, `Levi`). |
| **Historical Metric Records** | Recorded in [`docs/context-for-opus.md`](file:///home/sam/projects/keyboard/docs/context-for-opus.md) | Markdown | Empirical ground-truth measurements: Human-to-Human distance floor $= 0.072$; Previous Synthetic-to-Human distance $= 0.128$ ($1.8\times$ gap). |

---

## 6. Generator Output Quality Assessment & Baseline Validity

### Classification: **TOO CONTAMINATED / INVALID TO COMPARE**

Based on concrete evidence from the repository, **none of the surviving generator outputs can serve as a valid historical comparison baseline**:

```
                               ┌─────────────────────────────────────────────────────────────┐
                               │       SURVIVING PREVIOUS GENERATOR OUTPUTS                  │
                               └──────────────────────────────┬──────────────────────────────┘
                                                              │
                    ┌─────────────────────────────────────────┴─────────────────────────────────────────┐
                    ▼                                                                                   ▼
   ┌──────────────────────────────────┐                                                ┌──────────────────────────────────┐
   │    precomputed_gestures.json     │                                                │   test_synthetic / improved      │
   │            (32.2 MB)             │                                                │            (15 lines)            │
   ├──────────────────────────────────┤                                                ├──────────────────────────────────┤
   │ • Straight-line key centroids    │                                                │ • Only 5 words (15 samples)      │
   │ • Zero temporal / velocity info  │                                                │ • Handcrafted polynomial physics │
   │ • Single letters: fixed points   │                                                │ • Rigid [0,1]x[0,1] unit box     │
   │ • Statistically invalid path     │                                                │ • 1.8x distance gap from human   │
   └────────────────┬─────────────────┘                                                └────────────────┬─────────────────┘
                    │                                                                                   │
                    └─────────────────────────────────────────┬─────────────────────────────────────────┘
                                                              │
                                                              ▼
                                               ┌──────────────────────────────┐
                                               │           VERDICT            │
                                               │   Too Contaminated/Invalid   │
                                               │   to Serve as a Baseline     │
                                               └──────────────────────────────┘
```

### Detailed Rationale & Evidence:
1. **`precomputed_gestures.json` (Attempt 0 - Polygonal Straight Lines)**:
   - **Method**: Generated by connecting key centers with straight line segments and resampling to 50 points.
   - **Evidence**: Inspection reveals single-letter words like `'a'` are just `[0.075, 0.5]` repeated 50 times. For multi-letter words, paths are polygonal segments. It has zero temporal dynamics, zero velocity variation, and zero biomechanical curves. It is completely unusable as a generator baseline.
2. **`test_synthetic.jsonl` / `test_improved.jsonl` (Attempt A - Heuristic Physics)**:
   - **Method**: Minimum-jerk polynomial curves with spring-mass-damper overshoot in [`generate_synthetic_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/generate_synthetic_swipes.py).
   - **Evidence**:
     - *Inadequate Sample Size*: Only covers 5 words (`Kiry`, `Sam`, `Mike`, `Elijah`, `Levi`) with 3 samples each (15 rows total). This is statistically insufficient for evaluating distribution coverage.
     - *Coordinate & Aspect Ratio Distortion*: The trajectories assume a fixed $[0, 1] \times [0, 1]$ square keyboard. Real phone keyboards have aspect ratios of $1.77:1$ to $2.2:1$, introducing a permanent $0.04-0.06$ affine coordinate distortion.
     - *The 1.8× Distance Gap*: As recorded in [`docs/context-for-opus.md`](file:///home/sam/projects/keyboard/docs/context-for-opus.md#L45-L53), the spatial Fréchet/DTW distance of these curves to human ground truth is $0.128$, compared to the natural human-to-human distance of $0.072$.
3. **Seq2Traj Neural Output (Attempt B - Deterministic Bi-GRU)**:
   - **Method**: Autoregressive GRU trained with MSE in [`train_seq2traj.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_seq2traj.py).
   - **Status**: No output files or checkpoints survived on disk.

---

## 7. Reusability of Old Evaluation Metrics and Test Sets

| Asset / Metric | Location | Evaluation of Reusability | Recommendation |
| :--- | :--- | :--- | :--- |
| **7-Word Validation Script (`validate_against_futo.py`)** | [`research/swipe-training/validate_against_futo.py`](file:///home/sam/projects/keyboard/research/swipe-training/validate_against_futo.py) | **NOT REUSABLE (Flawed)**. Tests only 7 hardcoded words (`ham`, `bag`, `you`, `run`, `the`, `and`, `about`). The `corner_err` metric uses relative standard deviation thresholds ($\mu + 0.5\sigma$) that reward over-smoothed neural predictions and flag noise ripples as corners. | **Discard completely.** |
| **Target Supplement Vocabulary** | [`research/swipe-training/target_swipe_vocabulary_supplement.txt`](file:///home/sam/projects/keyboard/research/swipe-training/target_swipe_vocabulary_supplement.txt) | **HIGHLY REUSABLE**. 6,842 clean, deduplicated target unigrams derived from 9 months of Sam's real mobile usage. | **Retain as primary synthesis target list.** |
| **Missing Top 1,000 Dictionary Words** | [`research/swipe-training/missing_words_top1000.txt`](file:///home/sam/projects/keyboard/research/swipe-training/missing_words_top1000.txt) | **REUSABLE**. 1,000 frequent English unigrams missing from FUTO. | Retain as secondary evaluation/supplement slice. |
| **Literature Evaluation Protocols** | [`docs/futo-swipe-and-synthetic-trajectory-research.md`](file:///home/sam/projects/keyboard/docs/futo-swipe-and-synthetic-trajectory-research.md#L166-L181) | **HIGHLY REUSABLE**. Documents standard evaluation metrics: Trajectory Distance Ratio ($\frac{\text{dist}(\text{Synth}, \text{Human})}{\text{dist}(\text{Human}, \text{Human})} \to 1.00 \pm 0.10$), Two-Thirds Power Law kinematic slope fit ($\beta \approx 1/3$), and zero-shot downstream recognizer Top-1/Top-3 accuracy. | **Use as mathematical specification for new validation suite.** |

---

## 8. Repository Classification & Detritus Map

The table below classifies every file and directory in the swipe-training sub-tree:

| Path | File Type | Classification | Rationale & Action |
| :--- | :--- | :--- | :--- |
| [`research/swipe-training/futo_words_unique.txt`](file:///home/sam/projects/keyboard/research/swipe-training/futo_words_unique.txt) | TXT (829 KB) | **RAW SOURCE** | 91,104 unique FUTO words. Keep as reference lookup. |
| [`research/swipe-training/sams_custom_words.txt`](file:///home/sam/projects/keyboard/research/swipe-training/sams_custom_words.txt) | TXT (4.9 KB) | **RAW SOURCE** | Hand-curated custom vocabulary. Keep. |
| [`research/swipe-training/harvested_missing_words.tsv`](file:///home/sam/projects/keyboard/research/swipe-training/harvested_missing_words.tsv) | TSV (468 KB) | **DERIVED DATA** | Categorized harvest frequency table. Keep. |
| [`research/swipe-training/target_swipe_vocabulary_supplement.txt`](file:///home/sam/projects/keyboard/research/swipe-training/target_swipe_vocabulary_supplement.txt) | TXT (133 KB) | **DERIVED DATA** | 6,842 clean target words for synthesis. Keep. |
| [`research/swipe-training/missing_words_top1000.txt`](file:///home/sam/projects/keyboard/research/swipe-training/missing_words_top1000.txt) | TXT (7.8 KB) | **DERIVED DATA** | Top 1,000 dictionary gap words. Keep. |
| [`research/swipe-training/artifacts/precomputed_gestures.json`](file:///home/sam/projects/keyboard/research/swipe-training/artifacts/precomputed_gestures.json) | JSON (32.2 MB) | **HISTORICAL RESULT** | Archived 10k straight-line gestures. Do not package in APK; keep in research artifacts. |
| [`research/swipe-training/test_synthetic.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_synthetic.jsonl) | JSONL (67 KB) | **HISTORICAL RESULT** | 15 samples of Attempt A heuristic output. Keep as archive. |
| [`research/swipe-training/test_improved.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_improved.jsonl) | JSONL (70 KB) | **HISTORICAL RESULT** | 15 samples of tuned Attempt A output. Keep as archive. |
| [`research/swipe-training/generate_synthetic_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/generate_synthetic_swipes.py) | Python (15 KB) | **OLD GENERATOR** | Discarded heuristic generator. Keep for reference. |
| [`research/swipe-training/train_seq2traj.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_seq2traj.py) | Python (8.6 KB) | **OLD GENERATOR** | Deterministic MSE Seq2Traj model. Keep as reference. |
| [`research/swipe-training/precompute_gestures.py`](file:///home/sam/projects/keyboard/research/swipe-training/precompute_gestures.py) | Python (7.8 KB) | **OLD GENERATOR** | Geometric path generator. Keep for reference. |
| [`research/swipe-training/precompute_gestures_binary.py`](file:///home/sam/projects/keyboard/research/swipe-training/precompute_gestures_binary.py) | Python (6.9 KB) | **OLD GENERATOR** | Binary path generator. Keep for reference. |
| [`research/swipe-training/extract_futo_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/extract_futo_swipes.py) | Python (7.3 KB) | **OLD GENERATOR** | Flawed FUTO binary extractor (header mismatch). |
| [`research/swipe-training/prepare_training_data.py`](file:///home/sam/projects/keyboard/research/swipe-training/prepare_training_data.py) | Python (3.8 KB) | **OLD GENERATOR** | Old merger script for `neural-swipe-typing`. |
| [`research/swipe-training/validate_against_futo.py`](file:///home/sam/projects/keyboard/research/swipe-training/validate_against_futo.py) | Python (13.5 KB) | **EVALUATION** | Flawed 7-word validation script. Quarantine. |
| [`research/swipe-training/train_neuroswipe_v1.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_neuroswipe_v1.py) | Python (4.1 KB) | **REUSABLE INFRASTRUCTURE** | Transformer recognizer architecture definition. |
| [`research/swipe-training/analyze_futo_deeply.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_futo_deeply.py) | Python (8.1 KB) | **REUSABLE INFRASTRUCTURE** | Parquet trajectory profiler. |
| [`research/swipe-training/investigate_futo_coords.py`](file:///home/sam/projects/keyboard/research/swipe-training/investigate_futo_coords.py) | Python (3.1 KB) | **REUSABLE INFRASTRUCTURE** | Coordinate system inspection tool. |
| [`research/swipe-training/analyze_vocab_gaps.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_vocab_gaps.py) | Python (2.3 KB) | **REUSABLE INFRASTRUCTURE** | Vocabulary gap analysis tool. |
| [`research/swipe-training/explore_data.py`](file:///home/sam/projects/keyboard/research/swipe-training/explore_data.py) | Python (1.4 KB) | **REUSABLE INFRASTRUCTURE** | Parquet chunk inspector. |
| [`research/swipe-training/count_words.py`](file:///home/sam/projects/keyboard/research/swipe-training/count_words.py) | Python (1.7 KB) | **REUSABLE INFRASTRUCTURE** | Word frequency counter for parquet shards. |
| [`tools/harvesting/extract_harvest_swipe_vocab.py`](file:///home/sam/projects/keyboard/tools/harvesting/extract_harvest_swipe_vocab.py) | Python (8.0 KB) | **REUSABLE INFRASTRUCTURE** | Clean harvest unigram extraction pipeline. |
| [`research/swipe-training/setup_training.py`](file:///home/sam/projects/keyboard/research/swipe-training/setup_training.py) | Python (11.5 KB) | **LIKELY OBSOLETE** | Hardcoded paths to external `neural-swipe-typing` checkout. |
| [`research/swipe-training/train_on_colab.ipynb`](file:///home/sam/projects/keyboard/research/swipe-training/train_on_colab.ipynb) | Jupyter (11.2 KB) | **LIKELY OBSOLETE** | Legacy Colab notebook for `neural-swipe-typing`. |

---

## 9. Current State Summary

### What data do we actually have?
- **6,842 clean target supplement words** in [`target_swipe_vocabulary_supplement.txt`](file:///home/sam/projects/keyboard/research/swipe-training/target_swipe_vocabulary_supplement.txt), extracted and categorized from 9 months of Sam's physical device usage.
- **91,104 unique FUTO word strings** in [`futo_words_unique.txt`](file:///home/sam/projects/keyboard/research/swipe-training/futo_words_unique.txt).
- **1,000 top dictionary gap words** in [`missing_words_top1000.txt`](file:///home/sam/projects/keyboard/research/swipe-training/missing_words_top1000.txt).
- **660 custom words** in [`sams_custom_words.txt`](file:///home/sam/projects/keyboard/research/swipe-training/sams_custom_words.txt).
- **Full continuous telemetry streams** in [`data/harvest/`](file:///home/sam/projects/keyboard/data/harvest/).

### What information has been preserved or lost?
- **Preserved**: High-quality target vocabulary lists, frequency distributions, categorization (contractions, AI/tech, slang), and profiling insights.
- **Lost / Missing Locally**: The raw FUTO swipe trajectories (`futo_swipes.parquet`) are not stored locally. In previous conversion scripts, session grouping, screen aspect ratios, timestamps, and orientation metadata were discarded.

### What old generator/result artifacts survived?
- [`artifacts/precomputed_gestures.json`](file:///home/sam/projects/keyboard/research/swipe-training/artifacts/precomputed_gestures.json) (32.2 MB, 10,000 words): Straight-line geometric connections.
- [`test_synthetic.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_synthetic.jsonl) & [`test_improved.jsonl`](file:///home/sam/projects/keyboard/research/swipe-training/test_improved.jsonl) (15 samples, 5 words): Heuristic minimum-jerk curves.
- **No model weights or checkpoints exist** for any swipe model in the repository.

### Can the old output serve as a valid comparison point?
- **No.** The surviving outputs are either polygonal line segments (`precomputed_gestures.json`) or tiny 5-word heuristic samples (`test_synthetic.jsonl`) generated on a rigid unit-square grid that deviates $1.8\times$ from human swipe distances. They are too contaminated and limited to serve as an experimental baseline.

### What is reusable?
- The **6,842 target vocabulary supplement** ([`target_swipe_vocabulary_supplement.txt`](file:///home/sam/projects/keyboard/research/swipe-training/target_swipe_vocabulary_supplement.txt)).
- The **NeuroSwipe Transformer recognizer definition** ([`train_neuroswipe_v1.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_neuroswipe_v1.py)).
- The **PyArrow profiling utilities** ([`analyze_futo_deeply.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_futo_deeply.py), [`investigate_futo_coords.py`](file:///home/sam/projects/keyboard/research/swipe-training/investigate_futo_coords.py)).
- The **mathematical evaluation metrics** documented in [`docs/futo-swipe-and-synthetic-trajectory-research.md`](file:///home/sam/projects/keyboard/docs/futo-swipe-and-synthetic-trajectory-research.md) (DTW/Fréchet distance ratios, Two-Thirds Power Law slope fit, downstream recognizer accuracy).

### What should be quarantined as legacy?
- [`validate_against_futo.py`](file:///home/sam/projects/keyboard/research/swipe-training/validate_against_futo.py) (flawed 7-word validation).
- [`generate_synthetic_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/generate_synthetic_swipes.py) (discarded handcrafted heuristics).
- [`extract_futo_swipes.py`](file:///home/sam/projects/keyboard/research/swipe-training/extract_futo_swipes.py) & [`prepare_training_data.py`](file:///home/sam/projects/keyboard/research/swipe-training/prepare_training_data.py) (header and coordinate normalization flaws).
- [`setup_training.py`](file:///home/sam/projects/keyboard/research/swipe-training/setup_training.py) & [`train_on_colab.ipynb`](file:///home/sam/projects/keyboard/research/swipe-training/train_on_colab.ipynb) (tied to abandoned external repo).

### What facts are still unknown and require inspection or reacquisition?
1. **Reacquisition of FUTO Parquet Data**: `futo_swipes.parquet` (`swipe-1` train split: 939,550 swipes) is not stored locally and must be downloaded/streamed from Hugging Face (`futo-org/swipe.futo.org`) to inspect the real portrait swipe distribution and train any learned generator.
2. **Dynamic Screen Aspect Ratio Mapping**: Exact physical key bounding boxes and screen aspect ratios from Sam's target device (Galaxy S25 Ultra) must be extracted from the active layout engine rather than assuming static $[0, 1]^2$ coordinates.
