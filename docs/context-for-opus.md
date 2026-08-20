# Context for Opus: Generative Swipe Model & Training Pipeline

> **Target Agent**: Claude Opus  
> **Topic**: Neural Text-to-Trajectory Generator for Swipe Typing (OmniBoard / NeuroSwipe)  
> **Status**: Handover & Architectural Blueprint  
> **Date**: August 2026

---

## 1. Executive Summary & The Problem

OmniBoard needs an on-device neural glide-typing recognizer (**Model 2: NeuroSwipe Transformer**).
To train the recognizer effectively, we need swipe recordings for Sam's 9-month custom harvested vocabulary (**6,842 words** missing from public datasets like FUTO).

Because we cannot manually swipe 6,842 words thousands of times, we need a generative model (**Model 1: Generative Sequence-to-Trajectory**).
- **Model 1 (Generator)**: Learns human motor kinematics from real FUTO human swipe data (~939k samples) and synthesizes realistic swipe recordings for the 6,842 missing words.
- **Model 2 (Recognizer)**: Trains on the combined corpus (Real FUTO + Synthetic Supplement) to classify continuous touch trajectories into candidate words.

### The Danger: Data Poisoning
If the generator produces trajectories that are robotic, over-smoothed, or out-of-distribution, it **poisons the training data** for Model 2. The recognizer will learn spurious decision boundaries for rare/harvested words, causing glide typing to fail on Sam's physical phone.

---

## 2. What Previous Agents Tried & Why It Failed

### Attempt A: Heuristic Kinematic Simulation (`generate_synthetic_swipes.py`)
- **What they did**: Used minimum-jerk polynomials (Flash & Hogan 1985), cubic Bezier curves, hardcoded QWERTY key centroids, and ad-hoc overshoot formulas.
- **Why it failed**: Handcrafted rules cannot capture human motor variability. The curves were mechanically rigid, lacked natural biomechanical noise, and failed to pass human realism standards.

### Attempt B: Naive Deterministic Seq2Traj (`train_seq2traj.py`)
- **What they did**: Built an autoregressive Bi-GRU word encoder + GRU trajectory decoder trained with Mean Squared Error (MSE) coordinate regression.
- **Why it failed (Mode Averaging)**: Human swiping is multi-modal (fast thumb swipers cut corners; index finger swipers dwell on keys; tall screens compress vertically). When an MSE model trains on multi-modal trajectories, it mathematically converges to the **conditional expectation (the arithmetic mean)** $\mathbb{E}[Y | \text{word}]$. This produces an artificial, "floaty", over-smoothed path that cuts corners in a way no real human ever does.

### Attempt C: Bogus Validation Metrics (`validate_against_futo.py`)
- **What they did**: Previous agents celebrated a metric called `corner_err` falling from `58.9% → 8.0%` and established a bogus `25%` acceptance threshold.
- **The flaw**:
  1. `corner_err` calculated discrete numerical second derivatives relative to each curve's *own* standard deviation (`mean + 0.5 * std`). A smooth line with tiny ripples detected false "corners."
  2. The evaluation ran on only **7 hardcoded test words** (`ham`, `bag`, `you`, `run`, `the`, `and`, `about`).
  3. The drop to 8% merely reflected the neural network's MSE loss smoothing out point fluctuations to match the dataset's average second-derivative scale. It had **zero correlation with human realism**.

---

## 3. The Empirical Ground Truth Numbers

During trajectory distance analysis against real FUTO human swipes, the following empirical distances were measured:

| Metric | Measured Value | Physical Meaning |
| :--- | :--- | :--- |
| **`human → nearest human`** | **`0.072`** | **Empirical ground truth floor**: The natural spatial distance/variance between two distinct human swipes of the same word. |
| **`synthetic → nearest human`** | **`0.128`** | **Previous attempt gap (~1.8×)**: Previous synthetic trajectories were nearly twice as far from real humans as humans are from each other. |
| **Target Acceptance Ratio** | **$\mathbf{1.00 \pm 0.10}$** | $\frac{\text{dist}(\text{Synthetic}, \text{Human})}{\text{dist}(\text{Human}, \text{Human})} \to 1.0$. The synthetic data must lie squarely inside the human distribution. |

---

## 4. Why the 1.8× Gap Exists (Root Causes)

1. **The "Curse of the Centroid"**: In high-dimensional trajectory space, real human swipes lie in stylistic clusters on the manifold shell. The MSE synthetic trajectory sits at the interior centroid. The distance from the centroid to the shell is geometrically $\sqrt{2}\times$ to $2\times$ greater than the intra-cluster distance.
2. **Coordinate & Aspect Ratio Mismatch**: FUTO captures swipes across hundreds of different physical screen aspect ratios (16:9 to 21:9) and keyboard heights. The previous generator used a rigid, static $[0, 1] \times [0, 1]$ unit square. This creates a permanent $0.04-0.06$ affine distortion.
3. **Kinematic Phase Shifts**: Humans follow the **Two-Thirds Power Law** ($v(t) \propto \kappa(t)^{-1/3}$), slowing down dramatically at sharp corners and accelerating on straightaways. Uniform or unconstrained point step predictions create phase errors.

---

## 5. Architectural Blueprint for Opus (What to Build)

We need a **true generative model** (not heuristics, and not deterministic regression).

```
  Word String (e.g. "somnambulist")
                 │
         [ Tokenizer + Embed ]
                 │
         [ Word Sequence Encoder ] ───┐
                                      ├──> [ Trajectory Decoder ] ──> Realistic Human Swipe
  Latent Style z ~ N(0, I) ───────────┘    (Diffusion / CVAE)         (Points, Timestamps, Velocities)
  (Speed, Handedness, Corner Radius,
   Device Aspect Ratio)
```

### A. Core Architecture: Conditional VAE (CVAE) or Diffusion / Flow-Matching
- **Word + Dynamic Layout Encoder**: 
  - Takes character tokens **and** their corresponding active $(x_k, y_k)$ 2D key centroids from whatever keyboard layout is currently active.
  - **Crucial for OmniBoard**: Because OmniBoard is under active development (rows toggle in/out, keyboard height/spacing changes), the model MUST NOT hardcode key positions. It learns the general motor mapping: *Given target key coordinates $(x_1, y_1), \dots, (x_N, y_N)$ on screen, synthesize the human swipe trajectory connecting them.*
- **Latent Conditioning**: Sample $z \sim \mathcal{N}(0, I)$ representing latent human execution styles (right-handed thumb arc, swipe velocity, corner-rounding tolerance).
- **Trajectory Decoder**: Emits continuous $(x_t, y_t, \Delta t_t)$ trajectories conditioned on $[\text{Word Representation}, \text{Dynamic Key Coordinates}, z]$. Sampling different $z$ values produces diverse, valid human execution variations for the same word.

### B. Dataset Filtering & Specialization (Right-Handed Portrait)
- **Portrait Filtering**: Filter FUTO data to portrait aspect ratios (`canvas_height > canvas_width`, ratio $\ge 1.77$), dropping landscape/tablet records.
- **Right-Handed Thumb Kinematics**: Right-handed thumb swiping on an S25 Ultra exhibits distinct radial curvature (swipes from bottom-left to top-right bow outward due to the thumb pivoting at the bottom-right palm). The generator learns this biomechanical prior from portrait FUTO swipes and reproduces it across dynamic layouts.
- **Strictly Model-Based (No Heuristics)**: Heuristic physics simulation (Attempt A) was discarded because handcrafted rules cannot adapt to dynamic layouts or capture true biological motor variability. We require a purely learned PyTorch generative model.

### C. Kinematic & Geometric Loss Formulation
Do not use raw MSE on coordinates alone. Use a multi-task loss:
$$\mathcal{L} = \mathcal{L}_{\text{coord}} + \lambda_v \mathcal{L}_{\text{velocity}} + \lambda_j \mathcal{L}_{\text{jerk}} + \lambda_{\text{DTW}} \mathcal{L}_{\text{Soft-DTW}} + \beta \mathcal{L}_{\text{KL}}$$
- **Coordinate Loss**: Reconstruction of the spatial path.
- **Velocity Profile Loss**: Enforces deceleration at high curvature (Two-Thirds Power Law).
- **Minimum Jerk Loss**: $\int (\dddot{x}^2 + \dddot{y}^2) dt$ penalizes unnatural biomechanical twitches.
- **Soft-DTW Loss**: Aligns trajectories spatially without penalizing local timing phase shifts.
- **KL Divergence**: Regularizes the latent space $z$.

### D. Grounded Validation Protocol
- Discard the 7-word `validate_against_futo.py`.
- Evaluate against a stratified test set of **500 unseen words** measuring:
  1. **Trajectory Distance Ratio**: $\frac{\text{dist}(\text{Synth}, \text{Human})}{\text{dist}(\text{Human}, \text{Human})} \approx 1.00 \pm 0.10$.
  2. **Kinematic Power Law Fit**: Log-velocity vs log-curvature slope matches human $\beta \approx 1/3$.
  3. **Zero-Shot Downstream Classification Gate**: Train a mini recognizer on synthetic data for a held-out vocabulary slice; test top-1/top-3 accuracy against real human FUTO swipes for those exact words.

---

## 6. Repository Map & Key Files

| Path | Description | Current State |
| :--- | :--- | :--- |
| `research/swipe-training/train_seq2traj.py` | Seq2Traj model definition & generator | Needs upgrade to CVAE / generative formulation with dynamic layout conditioning. |
| `research/swipe-training/generate_synthetic_swipes.py` | Heuristic physics generator (Minimum Jerk / Bezier) | **Discarded approach**. Reference only; do not revive heuristics. |
| `research/swipe-training/validate_against_futo.py` | Validation script | **Flawed**. Must be replaced with 500-word DTW / Fréchet distribution suite. |
| `research/swipe-training/target_swipe_vocabulary_supplement.txt` | 6,842 clean target words from Sam's 9-month harvest missing from FUTO | Ready. Target vocabulary for generation. |
| `research/swipe-training/futo_swipes.parquet` | Real human swipe training dataset (~939k swipes) | Source dataset for training the generative model. Filter for portrait. |
| `research/swipe-training/train_neuroswipe_v1.py` | Downstream 4-layer Transformer classifier (Model 2) | Target recognizer to be trained on Real + Synthetic data. |

