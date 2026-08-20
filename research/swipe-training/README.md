# Swipe Synthesis & Training Research

> **Status:** Active (Phase 2 Clean Foundation)  
> **Updated:** August 2026  
> **Canonical Audit:** [`docs/swipe-synthesis-project-state-audit.md`](file:///home/sam/projects/keyboard/docs/swipe-synthesis-project-state-audit.md)  
> **Architecture Spec:** [`docs/swipe-data-and-pipeline-architecture.md`](file:///home/sam/projects/keyboard/docs/swipe-data-and-pipeline-architecture.md)

This directory houses the second-generation swipe-synthesis research, reusable profiling utilities, target vocabulary corpora, and downstream recognizer modeling for OmniBoard.

---

## 1. Directory Structure & Active Components

```
research/swipe-training/
├── acquire_futo_data.py                         # [ACTIVE] Lossless acquisition & verification of canonical FUTO dataset
├── target_swipe_vocabulary_supplement.txt       # [ACTIVE] 6,842 clean target words from Sam's 9-month harvest missing from FUTO
├── harvested_missing_words.tsv                  # [ACTIVE] 13,258 harvest unigram frequency and category breakdown
├── futo_words_unique.txt                        # [ACTIVE] 91,104 unique FUTO reference unigrams
├── missing_words_top1000.txt                    # [ACTIVE] Top 1,000 dictionary gap unigrams
├── sams_custom_words.txt                        # [ACTIVE] Curated custom vocabulary seed
├── analyze_futo_deeply.py                       # [ACTIVE] Parquet trajectory kinematic profiler
├── investigate_futo_coords.py                   # [ACTIVE] Coordinate system inspector
├── analyze_vocab_gaps.py                        # [ACTIVE] Vocabulary gap analyzer
├── explore_data.py                              # [ACTIVE] Parquet chunk inspector
├── count_words.py                               # [ACTIVE] Parquet word frequency counter
├── train_neuroswipe_v1.py                       # [ACTIVE] PyTorch Transformer recognizer model definition
└── legacy/                                      # [QUARANTINE] Isolated first-generation experiments and artifacts
    ├── generators/                              # Heuristic and deterministic MSE generators
    ├── evaluators/                              # Flawed 7-word evaluation scripts
    ├── external_glue/                           # Abandoned third-party repo glue
    ├── artifacts/                               # Surviving polygonal/heuristic outputs
    └── README.md                                # Detailed quarantine audit and explanations
```

---

## 2. Dataset Management & Boundaries

- **Raw Datasets (`data/swipe/raw/futo/`)**:
  - Immutable parquet shards from Hugging Face `futo-org/swipe.futo.org` (~1.22M swipes).
  - Preserved losslessly with full $\{x, y, t\}$ trajectories, screen aspect ratios, sessions, and metadata.
  - Reacquired and verified via `uv run --with pyarrow --with requests python3 research/swipe-training/acquire_futo_data.py`.
- **Derived Datasets (`data/swipe/derived/`)**:
  - Future normalized, aspect-ratio filtered, or tokenized representations belong here.
- **Git Tracking**:
  - Large parquet datasets and derived caches are ignored in `.gitignore`.

---

## 3. Legacy Quarantine Summary

All first-generation heuristic simulators (`generate_synthetic_swipes.py`), deterministic MSE neural models (`train_seq2traj.py`), straight-line geometric generators (`precompute_gestures*.py`), flawed 7-word evaluators (`validate_against_futo.py`), and external repo glue have been quarantined into `legacy/`. See [`legacy/README.md`](file:///home/sam/projects/keyboard/research/swipe-training/legacy/README.md) for forensic rationale.
