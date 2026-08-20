# Legacy Swipe-Synthesis Experiments (Quarantine & Reference)

> **Status:** Quarantined Legacy & Reference Material  
> **Audited In:** [`docs/swipe-synthesis-project-state-audit.md`](file:///home/sam/projects/keyboard/docs/swipe-synthesis-project-state-audit.md)  
> **Date:** August 2026

This directory contains first-generation swipe synthesis scripts, heuristic generators, flawed evaluators, third-party glue, and historical output files. These artifacts are preserved solely for historical auditability and reference, and are **isolated from the active pipeline**.

---

## 1. Directory Structure

```
research/swipe-training/legacy/
├── generators/
│   ├── generate_synthetic_swipes.py     # Heuristic kinematic simulator (Minimum-Jerk & Bezier)
│   ├── train_seq2traj.py               # Deterministic Bi-GRU/GRU with MSE regression
│   ├── precompute_gestures.py          # Geometric straight-line path generator (JSON)
│   ├── precompute_gestures_binary.py   # Geometric path generator (Binary)
│   ├── extract_futo_swipes.py          # FUTO binary extractor (12-byte header mismatch)
│   └── prepare_training_data.py        # Dataset merger for neural-swipe-typing
├── evaluators/
│   └── validate_against_futo.py        # Flawed 7-word validation script
├── external_glue/
│   ├── setup_training.py               # Hardcoded setup glue for proshian/neural-swipe-typing
│   └── train_on_colab.ipynb            # Legacy Colab training notebook
└── artifacts/
    ├── precomputed_gestures.json       # 32.2 MB straight-line geometric paths (10k words)
    ├── test_synthetic.jsonl            # 15 samples across 5 words (Heuristic Attempt A)
    └── test_improved.jsonl             # 15 samples across 5 words (Heuristic Attempt A tuned)
```

---

## 2. Inventory and Reason for Quarantine

### A. Generators (`generators/`)
1. **`generate_synthetic_swipes.py` (Attempt A — Heuristic Simulation)**:
   - *Method*: Used minimum-jerk polynomials (Flash & Hogan 1985), cubic Bezier curves, and spring-mass-damper physics on a rigid $[0, 1]^2$ unit grid.
   - *Why it failed*: Handcrafted rules cannot model true human motor variability. The generated curves were mechanically rigid, lacked natural biomechanical noise, and suffered a $1.8\times$ Fréchet/DTW distance gap from real human swipes.
2. **`train_seq2traj.py` (Attempt B — Deterministic Neural Regression)**:
   - *Method*: Autoregressive Bi-GRU word encoder + GRU trajectory decoder trained with Mean Squared Error (MSE) coordinate loss.
   - *Why it failed (Mode Averaging)*: Human swiping is multi-modal. Training an MSE model on multi-modal trajectories converges to the conditional expectation (the arithmetic centroid) $\mathbb{E}[\text{traj} \mid \text{word}]$. This produces an artificial, over-smoothed path that cuts corners in a manner no real human ever executes.
3. **`precompute_gestures.py` & `precompute_gestures_binary.py` (Attempt 0 — Polygonal Paths)**:
   - *Method*: Connected QWERTY key centroids with straight lines (and small square loops for repeated letters) resampled to 50 points.
   - *Why it failed*: Complete absence of kinematics, velocities, timestamps, or human variation.
4. **`extract_futo_swipes.py`**:
   - *Method*: Extracted 50-point swipes from `futo_swipes.parquet` into a binary format.
   - *Flaws*: Discarded timestamps, discarded screen aspect ratios ($W/H$), and wrote a 12-byte header (`[num_words, swipes_per_word, points_per_swipe]`) that mismatched the 8-byte header (`[numWords, numSamplePoints]`) expected by the Android runtime [`PrecomputedGestureCache.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/PrecomputedGestureCache.kt).
5. **`prepare_training_data.py`**:
   - *Method*: Merged parquet data and synthetic JSONL into a monolithic JSONL file for an abandoned third-party trainer.

### B. Evaluators (`evaluators/`)
1. **`validate_against_futo.py`**:
   - *Method*: Compared synthetic curves against FUTO for **7 hardcoded test words** (`ham`, `bag`, `you`, `run`, `the`, `and`, `about`).
   - *Flaws*: Evaluated corner errors using relative standard deviation thresholds ($\mu + 0.5\sigma$) within each curve. Smooth curves with micro-ripples flagged false corners, while over-smoothed MSE predictions were incorrectly scored as high-quality.

### C. External Glue (`external_glue/`)
1. **`setup_training.py` & `train_on_colab.ipynb`**:
   - *Method*: Automated environment setup and Colab runner for the third-party `proshian/neural-swipe-typing` codebase.
   - *Status*: Abandoned in favor of native, standalone training pipelines.

### D. Historical Outputs (`artifacts/`)
1. **`precomputed_gestures.json`** (32.2 MB): 10,000 polygonal paths.
2. **`test_synthetic.jsonl` & `test_improved.jsonl`** (15 samples each): Micro-samples across 5 words.
- *Status*: None of these files represent valid human distributions or baselines. They are preserved purely for archival forensics.
