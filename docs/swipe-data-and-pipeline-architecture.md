# OmniBoard Swipe-Synthesis Data & Pipeline Architecture

> **Status:** Canonical Architecture Specification (Phase 2 Clean Foundation)  
> **Audited In:** [`docs/swipe-synthesis-project-state-audit.md`](file:///home/sam/projects/keyboard/docs/swipe-synthesis-project-state-audit.md)  
> **Research Foundations:** [`docs/futo-swipe-and-synthetic-trajectory-research.md`](file:///home/sam/projects/keyboard/docs/futo-swipe-and-synthetic-trajectory-research.md)  
> **Date:** August 2026

This document establishes the lossless raw-data foundation, dataset hierarchy, acquisition procedures, and asset classifications for OmniBoard's second-generation swipe synthesis and neural glide-typing pipeline.

---

## 1. Directory & Storage Architecture

```
keyboard/
├── data/
│   └── swipe/
│       ├── README.md                 # Swipe data documentation & manifest guide
│       ├── raw/                      # [IMMUTABLE RAW DATA] (gitignored)
│       │   └── futo/                 # Canonical public FUTO swipe dataset
│       │       ├── manifest.json     # Shard hashes, row counts, and schema verification
│       │       ├── swipe-1/          # Primary Wikipedia / Mozilla Common Voice split
│       │       │   ├── train/        # 4 shards (939,550 swipes)
│       │       │   ├── validation/   # 1 shard (54,269 swipes)
│       │       │   └── test/         # 1 shard (49,970 swipes)
│       │       ├── swipe-2/train/    # Informal reviews & TV dialogue (28,095 swipes)
│       │       ├── swipe-3/train/    # Slang & OpenWebText (38,228 swipes)
│       │       ├── swipe-4/train/    # Hard path-confusable negatives (50,300 swipes)
│       │       └── swipe-5/train/    # Multilingual & dual-finger gestures (59,247 swipes)
│       └── derived/                  # [FUTURE DERIVED DATA] (gitignored)
│           └── (reserved for normalized splits, aspect ratio filters, and cache arrays)
│
├── research/swipe-training/
│   ├── README.md                     # Active research guide
│   ├── futo_dataset_lock.json        # Tracked cryptographic SHA-256 and revision lock manifest
│   ├── acquire_futo_data.py          # Lossless FUTO reacquisition & verification tool
│   ├── profile_corpus_kinematics.py  # Comprehensive vectorized empirical kinematics profiler
│   ├── corpus_kinematics_profile.json # Quantified statistical distribution profile across 100k human swipes
│   ├── target_swipe_vocabulary_supplement.txt  # 6,842 clean target words from Sam's harvest
│   ├── harvested_missing_words.tsv   # 13,258 categorized harvest frequency unigrams
│   ├── futo_words_unique.txt         # 91,104 unique FUTO reference unigrams
│   ├── missing_words_top1000.txt     # Top 1,000 dictionary gap unigrams
│   ├── sams_custom_words.txt         # Curated custom vocabulary seed
│   ├── analyze_futo_deeply.py        # Parquet trajectory kinematic profiler
│   ├── investigate_futo_coords.py    # Coordinate system inspector
│   ├── analyze_vocab_gaps.py         # Vocabulary gap analyzer
│   ├── explore_data.py               # Parquet chunk inspector
│   ├── count_words.py                # Word frequency counter across parquet shards
│   ├── train_neuroswipe_v1.py        # Transformer recognizer model definition (Model 2)
│   └── legacy/                       # [QUARANTINE] Quarantined first-generation artifacts
│       ├── generators/               # Heuristic & deterministic MSE generators
│       ├── evaluators/               # Flawed 7-word evaluation scripts
│       ├── external_glue/            # Third-party repo automation
│       ├── artifacts/                # Historical straight-line / heuristic outputs
│       └── README.md                 # Detailed quarantine explanations
```

---

## 2. Canonical FUTO Dataset Inventory & Verification

The raw FUTO dataset (`futo-org/swipe.futo.org`) has been acquired and verified losslessly at `data/swipe/raw/futo/`.

### 2.1 Volume Breakdown Across Shards

| Run | Split | Shard | Rows | Disk Size | Column Count | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`swipe-1`** | `train` | `0.parquet` | 237,744 | 444.16 MB | 12 | Verified Lossless |
| **`swipe-1`** | `train` | `1.parquet` | 248,234 | 443.67 MB | 12 | Verified Lossless |
| **`swipe-1`** | `train` | `2.parquet` | 244,910 | 443.31 MB | 12 | Verified Lossless |
| **`swipe-1`** | `train` | `3.parquet` | 208,662 | 372.49 MB | 12 | Verified Lossless |
| **`swipe-1`** | `validation` | `0.parquet` | 54,269 | 100.48 MB | 12 | Verified Lossless |
| **`swipe-1`** | `test` | `0.parquet` | 49,970 | 88.98 MB | 12 | Verified Lossless |
| **`swipe-2`** | `train` | `0.parquet` | 28,095 | 34.18 MB | 11 | Verified Lossless |
| **`swipe-3`** | `train` | `0.parquet` | 38,228 | 65.64 MB | 11 | Verified Lossless |
| **`swipe-4`** | `train` | `0.parquet` | 50,300 | 146.71 MB | 11 | Verified Lossless |
| **`swipe-5`** | `train` | `0.parquet` | 59,247 | 85.05 MB | 14 | Verified Lossless |
| **TOTAL** | | **10 shards** | **1,219,659** | **2,224.67 MB** | | **Complete Corpus** |

### 2.2 Verified Field Schema & Types

Every record across all shards preserves the full, un-decimated data structure:

| Column Name | Arrow Type | Physical Meaning | Preserved Status |
| :--- | :--- | :--- | :--- |
| `id` | `int64` | Row index within split | Preserved |
| `session` | `string` | Anonymous session UUID (`anon-session-<UUID>`) | Preserved (enables session-level motor signature grouping) |
| `timestamp` | `int64` | Gesture completion epoch millisecond | Preserved |
| `word` | `string` | Target prompt word string | Preserved |
| `canvas_width` | `double` | Viewport / canvas width in pixels | Preserved (enables physical aspect ratio recovery) |
| `canvas_height` | `double` | Viewport / canvas height in pixels | Preserved (enables physical aspect ratio recovery) |
| `orientation` | `string` | Display orientation (e.g. `'portrait-primary'`) | Preserved |
| `data` | `struct[]` / `json` | Raw continuous trajectory touch points $\{x, y, t\}$ | **Preserved Losslessly** (no spatial resampling, no timestamp dropping) |
| `sentence` | `string` | Full prompt sentence | Preserved |
| `word_idx` | `int64` | 0-indexed position in sentence prompt | Preserved |
| `distance` | `double` | Geometric deviation from ideal path | Preserved |
| `potentially_invalid_sentence` | `bool` | Sentence parsing validity flag (`swipe-1`) | Preserved |
| `language` | `string` | ISO language code (`swipe-5`) | Preserved |
| `layout` | `string` | Keyboard layout name (`swipe-5`) | Preserved |
| `dual_finger` | `int64` | Dual-thumb typing flag (`swipe-5`) | Preserved |

---

## 3. Immutable Raw Data vs. Derived Representations Policy

1. **Raw Data Store (`data/swipe/raw/`)**:
   - Strictly read-only and immutable.
   - Scripts may read from `data/swipe/raw/futo/` but must never modify or write into it.
2. **Derived Representations (`data/swipe/derived/`)**:
   - Any derived dataset (e.g. portrait aspect-ratio filtered subsets, tokenized tensors, normalized kinematic features) must be written exclusively to `data/swipe/derived/`.
   - Every derived artifact must be paired with an acquisition/generation script and a provenance log detailing exact filter criteria and parent shard hashes.

---

## 4. Reacquisition & Verification Tooling

The dataset can be completely reacquired and verified at any time using:

```bash
uv run --with pyarrow --with requests python3 research/swipe-training/acquire_futo_data.py
```

Arguments:
- `--output-dir <path>`: Custom destination (defaults to `data/swipe/raw/futo/`).
- `--force`: Force re-download even if shards exist on disk.
- Automatically generates and updates `data/swipe/raw/futo/manifest.json`.

---

## 5. Reusable Active Assets vs. Quarantined Legacy Inventory

### 5.1 Reusable Active Assets

| Component | Path | Description / Role |
| :--- | :--- | :--- |
| **Harvested Target Vocabulary** | [`research/swipe-training/target_swipe_vocabulary_supplement.txt`](file:///home/sam/projects/keyboard/research/swipe-training/target_swipe_vocabulary_supplement.txt) | 6,842 clean target unigrams missing from FUTO, derived from 9 months of mobile usage. |
| **Harvest Frequency Table** | [`research/swipe-training/harvested_missing_words.tsv`](file:///home/sam/projects/keyboard/research/swipe-training/harvested_missing_words.tsv) | 13,258 unigrams with frequency and category breakdown (contractions, tech/AI, slang). |
| **FUTO Reference Unigrams** | [`research/swipe-training/futo_words_unique.txt`](file:///home/sam/projects/keyboard/research/swipe-training/futo_words_unique.txt) | 91,104 unigrams present in the FUTO dataset. |
| **Dictionary Gap Words** | [`research/swipe-training/missing_words_top1000.txt`](file:///home/sam/projects/keyboard/research/swipe-training/missing_words_top1000.txt) | Top 1,000 dictionary gap words from `unified_dictionary.tsv`. |
| **Custom Word Seed** | [`research/swipe-training/sams_custom_words.txt`](file:///home/sam/projects/keyboard/research/swipe-training/sams_custom_words.txt) | Curated custom vocabulary unigrams. |
| **Kinematic Trajectory Profiler** | [`research/swipe-training/analyze_futo_deeply.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_futo_deeply.py) | High-speed PyArrow trajectory analysis tool. |
| **Coordinate System Inspector** | [`research/swipe-training/investigate_futo_coords.py`](file:///home/sam/projects/keyboard/research/swipe-training/investigate_futo_coords.py) | Verifies coordinate normalization and aspect ratio ranges. |
| **Vocabulary Gap Tool** | [`research/swipe-training/analyze_vocab_gaps.py`](file:///home/sam/projects/keyboard/research/swipe-training/analyze_vocab_gaps.py) | Compares dictionary vocabularies against parquet shards. |
| **Dataset Counter & Inspector** | [`research/swipe-training/explore_data.py`](file:///home/sam/projects/keyboard/research/swipe-training/explore_data.py), [`count_words.py`](file:///home/sam/projects/keyboard/research/swipe-training/count_words.py) | Fast row and chunk inspection. |
| **Transformer Recognizer** | [`research/swipe-training/train_neuroswipe_v1.py`](file:///home/sam/projects/keyboard/research/swipe-training/train_neuroswipe_v1.py) | Downstream 4-layer PyTorch Transformer recognizer model. |
| **Harvest Extractor** | [`tools/harvesting/extract_harvest_swipe_vocab.py`](file:///home/sam/projects/keyboard/tools/harvesting/extract_harvest_swipe_vocab.py) | Daily driver telemetry log extractor. |

### 5.2 Quarantined Legacy Material

All items below reside under `research/swipe-training/legacy/` and are excluded from the active pipeline:

| Quarantined Item | Subdirectory | Classification | Primary Flaw / Reason for Quarantine |
| :--- | :--- | :--- | :--- |
| `generate_synthetic_swipes.py` | `legacy/generators/` | Heuristic Kinematic Simulation | Handcrafted minimum-jerk/Bezier heuristics; rigid $[0, 1]^2$ box; $1.8\times$ distance gap from human swipes. |
| `train_seq2traj.py` | `legacy/generators/` | Deterministic Neural Regression | Bi-GRU trained with MSE loss; mode-averaging collapse ($\mathbb{E}[Y\|X]$) produces floaty, unnatural paths. |
| `precompute_gestures*.py` | `legacy/generators/` | Geometric Straight-Line Paths | Polygonal line connections between key centroids; zero velocity or kinematic information. |
| `extract_futo_swipes.py` | `legacy/generators/` | Flawed Binary Extractor | Discarded timestamps; 12-byte header mismatch with Android runtime. |
| `prepare_training_data.py` | `legacy/generators/` | Monolithic Data Merger | Tied to abandoned external training pipeline. |
| `validate_against_futo.py` | `legacy/evaluators/` | Flawed Evaluation Script | Only tests 7 words; `corner_err` metric uses relative standard deviation that rewards over-smoothed paths. |
| `setup_training.py` | `legacy/external_glue/` | Third-party Setup Automation | Hardcoded paths for `proshian/neural-swipe-typing`. |
| `train_on_colab.ipynb` | `legacy/external_glue/` | Legacy Colab Runner | Configured for abandoned third-party repository. |
| `precomputed_gestures.json` | `legacy/artifacts/` | Historical Straight-Line Data | 32.2 MB geometric JSON without temporal dynamics. |
| `test_synthetic*.jsonl` | `legacy/artifacts/` | Historical Heuristic Output | 15 samples across 5 words from Attempt A. |
