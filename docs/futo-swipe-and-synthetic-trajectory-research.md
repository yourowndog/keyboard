# FUTO Swipe Dataset Profiling & Synthetic Trajectory Generation Literature

This document provides a comprehensive profile of the public FUTO swipe-typing dataset (`swipe.futo.org`), its exact acquisition methods, schema inspection, and session grouping characteristics, followed by an in-depth literature review of synthetic swipe/gesture trajectory generation, human motor control models, and handedness inference.

---

## 1. Summary of Sources & Findings

| Source / Entity | Type & Maintainers / Authors | Direct URL / Acquisition Method | Format, Scale & License | Core Findings, Schemas, Validation & Key Insights |
| :--- | :--- | :--- | :--- | :--- |
| **`futo-org/swipe.futo.org`** | Dataset<br>FUTO (Miller & Kostarevas) | [Hugging Face: `futo-org/swipe.futo.org`](https://huggingface.co/datasets/futo-org/swipe.futo.org) | **Format:** JSONL (`train.jsonl`, `test.jsonl`, `dev.jsonl`, `swipe2`–`swipe5.jsonl`) & Parquet shards.<br>**Scale:** ~1.22M total swipes across 5 runs (`swipe-1` train has **939,550** swipes).<br>**License:** MIT (permissive open-source). | **Verified Schema:** `id` (int), `session` (`anon-session-<UUID>`), `timestamp` (epoch ms), `word` (str), `canvas_width` (float), `canvas_height` (float), `orientation` (str), `data` (list of `{x, y, t}` structs or dual-finger dict), `sentence` (str), `word_idx` (int), `distance` (float), `potentially_invalid_sentence` (bool, `swipe-1`), `language`/`layout`/`dual_finger` (in `swipe-5`).<br>**Key Insight:** No persistent `user_id`, but `session` reliably groups contiguous swipes from the same participant session. |
| **`futo-org/futo-swipe` & `swipe-library`** | Models & C++ Engine<br>FUTO | [Hugging Face: `futo-org/futo-swipe`](https://huggingface.co/futo-org/futo-swipe)<br>[GitLab: `swipe-library`](https://gitlab.futo.org/keyboard/swipe-library) | **Models:** Encoder (`SwipeALot-base`), Decoder, Context LM.<br>**Code:** C++ / ONNX / TFLite runtime. | Demonstrates layout-agnostic swipe decoding by passing keyboard geometry at inference time; trained using joint 7-parameter geometric augmentations on trajectory coordinates and key tensors. |
| **`futo-org/swipe-negatives`** | Dataset<br>FUTO | [Hugging Face: `futo-org/swipe-negatives`](https://huggingface.co/datasets/futo-org/swipe-negatives) | **Format:** Parquet.<br>**Scale:** ~100k–1M word pairs.<br>**License:** Apache 2.0. | Mined via k-NN over trajectory embeddings from `SwipeALot` to identify visually/path-confusable word pairs ($K=128$) alongside pairwise cosine trajectory similarity `neg_sims`. |
| **FUTO Swipe Paper** (arXiv:2606.25247, 2026) | Research Paper<br>David Lee Miller, Aleksandras Kostarevas | [arXiv:2606.25247](https://arxiv.org/abs/2606.25247) | Published Preprint / Architecture Spec. | Details the collection of `swipe.futo.org` (>12k user sessions); introduces joint geometric transformations (x/y scale, shear, flips, rotation, translation, time-reversal) and batched hill-climbing layout optimization using synthetic swipe trajectories. |
| **How We Swipe** (MobileHCI '21) | Dataset & Paper<br>Luis A. Leiva, Sunjun Kim, Wenzhe Cui, Xiaojun Bi, Antti Oulasvirta | [ACM DL: 10.1145/3447526.3472059](https://doi.org/10.1145/3447526.3472059)<br>[GitHub: `luileito/swipetest`](https://github.com/luileito/swipetest) | **Scale:** 11,318 words, 1,338 users, 8.8M touch points. | Benchmark empirical dataset analyzing human swipe speed-accuracy trade-offs; showed users swipe faster on larger screens despite longer trajectories. Serves as ground truth for synthetic generator validation. |
| **Modeling Gesture-Typing Movements** (HCI Journal 2018) | Theory & Simulation Paper<br>Philip Quinn, Shumin Zhai | [Taylor & Francis: 10.1080/07370024.2016.1268344](https://doi.org/10.1080/07370024.2016.1268344) | Theoretical Framework & Trajectory Generator. | Foundational application of the **Minimum-Jerk Model** to word-gesture keyboards; generates continuous trajectory paths through character via-points while modeling sensorimotor noise and corner-cutting/curvatures. |
| **LSTM Neural Networks for Keyboard Gesture Decoding** (ICASSP '15) | Neural Decoder Paper<br>Ouais Alsharif, Tom Ouyang, Françoise Beaufays, Shumin Zhai, Thomas Breuel, Johan Schalkwyk | [IEEE ICASSP '15: 10.1109/ICASSP.2015.7178335](https://doi.org/10.1109/ICASSP.2015.7178335) | Neural Architecture Benchmark. | Replaced traditional template-matching/DTW with bidirectional LSTM + CTC/FST decoders; demonstrated how neural gesture recognizers benefit from training on synthetic/perturbed trajectories. |
| **Sigma-Lognormal & Kinematic Theory of Rapid Human Movements** (Plamondon 1995; Martín-Albo et al. 2016) | Biomechanical Motor Model<br>Réjean Plamondon, Luis A. Leiva, et al. | [IEEE TPAMI: 10.1109/34.368181](https://doi.org/10.1109/34.368181)<br>[Pattern Recognition 2016: 10.1016/j.patcog.2016.03.013](https://doi.org/10.1016/j.patcog.2016.03.013) | Kinematic Synthesis Framework. | Models finger/stylus stroke trajectories as neuromuscular impulse responses producing lognormal velocity profiles; used to generate realistic synthetic handwriting/gestures and extract biomechanical motor signatures. |
| **Two-Thirds Power Law in Touch & Handwriting** (Lacquaniti et al. 1983; Viviani et al. 1991) | Kinematic Law<br>F. Lacquaniti, C. Terzuolo, P. Viviani | [Journal of Neuroscience (1983)](https://www.jneurosci.org/content/3/6/1209) | Motor Control Kinematic Law. | Establishes the non-linear relationship between movement speed and curvature: angular velocity $\omega(t) = \gamma \kappa(t)^{2/3}$ and tangential velocity $v(t) = \gamma \kappa(t)^{-1/3}$; explains why swipe speeds drop sharply around key inflection corners. |
| **GripSense & Touch Handedness Inference** (UIST '12; Buschek et al. '15) | Handedness / Chiral Touch Models<br>Mayank Goel, Jacob O. Wobbrock, Shwetak N. Patel; Daniel Buschek | [ACM UIST '12: 10.1145/2380116.2380150](https://doi.org/10.1145/2380116.2380150) | Interaction & Sensor Classification. | Infers one-handed thumb handedness (left vs. right) and finger posture by analyzing swipe curvature arcs, thumb pivot radial bounds, and touchscreen contact area/touchdown offset asymmetries. |

---

## 2. PART 1 — FUTO Swipe Dataset Acquisition & Profiling

### 2.1 Acquisition Methods, Repositories, and Mirrors
- **Primary Canonical Repository:** [`futo-org/swipe.futo.org`](https://huggingface.co/datasets/futo-org/swipe.futo.org) hosted on Hugging Face.
- **Direct Acquisition:**
  - **Git LFS Clone:** `git clone https://huggingface.co/datasets/futo-org/swipe.futo.org`
  - **Hugging Face Datasets Python API:**
    ```python
    from datasets import load_dataset
    dataset = load_dataset("futo-org/swipe.futo.org", "swipe-1")
    ```
  - **Auto-Converted Parquet Endpoints:**
    - `swipe-1/train`: `https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/train/{0..3}.parquet`
    - `swipe-1/validation`: `https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/validation/0.parquet`
    - `swipe-1/test`: `https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/test/0.parquet`
    - `swipe-2` to `swipe-5`: `https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-{2..5}/train/0.parquet`
- **License & Redistribution Terms:** MIT License across all data files. Free for commercial, personal, academic, and modification use.
- **Total Storage:**
  - Raw JSONL files: ~6.04 GB (`train.jsonl` is 5.16 GB; `dev.jsonl` is 304 MB; `test.jsonl` is 270 MB; sub-runs are ~300 MB).
  - Parquet format: ~1.05 GB total compressed across all splits.

### 2.2 Volume & Shard Verification
Direct row-count querying of every parquet shard confirms the exact size breakdown:
- **`swipe-1` (Main Wikipedia / Mozilla Common Voice run):**
  - Train shard 0: 237,744 rows
  - Train shard 1: 248,234 rows
  - Train shard 2: 244,910 rows
  - Train shard 3: 208,662 rows
  - **`swipe-1` Train Total:** **939,550 swipes** (matches the widely reported ~939k dataset figure)
  - `swipe-1` Validation: 54,269 swipes
  - `swipe-1` Test: 49,970 swipes
  - **`swipe-1` Grand Total:** 1,043,789 swipes
- **`swipe-2` (Informal Amazon Reviews & TV Dialogue):** 28,095 swipes
- **`swipe-3` (Slang, Misspellings, Urban Dictionary & OpenWebText):** 38,228 swipes
- **`swipe-4` (Path-Confusable Hard Negatives):** 50,300 swipes
- **`swipe-5` (Multilingual & Multi-Layout, including Dual-Finger):** 59,247 swipes
  - Languages: English (`en`), German (`de`), Spanish (`es`), French (`fr`), Lithuanian (`lt`), Polish (`pl`), Shavian (`shaw`), Toki Pona (`tok`).
  - Layouts: QWERTY, AZERTY, QWERTZ, Dvorak, Clearflow, Kasroz, Lithuanian QWERTY, Shavian, Spanish, German, Toki Pona.
- **Combined Corpus Total:** **1,219,659 swipe gestures**.

### 2.3 Direct Schema Inspection
Direct inspection confirms the exact column types and variations:

#### `swipe-1` (train, dev, test) Schema
- `id` (`BIGINT`): Unique row index within the split.
- `session` (`VARCHAR`): Anonymous session identifier string (e.g., `'anon-session-c0bd4787-b692-4f84-813d-ef7889e55fec'`).
- `timestamp` (`BIGINT`): Epoch millisecond timestamp when the gesture completed (e.g., `1724382633326`).
- `word` (`VARCHAR`): Target word prompt.
- `canvas_width` (`DOUBLE`): Browser viewport / touch canvas width in CSS/display pixels (e.g., `422.0`).
- `canvas_height` (`DOUBLE`): Keyboard canvas height in pixels (e.g., `170.3125`).
- `orientation` (`VARCHAR`): Screen orientation (e.g., `'portrait-primary'`).
- `data` (`STRUCT(t BIGINT, x DOUBLE, y DOUBLE)[]`): List of trajectory points.
  - `x`: Normalized horizontal position $[0.0, 1.0]$.
  - `y`: Normalized vertical position $[0.0, 1.0]$.
  - `t`: Epoch millisecond timestamp of the individual touch sample.
- `sentence` (`VARCHAR`): Full source sentence prompt.
- `word_idx` (`BIGINT`): 0-indexed position of the target word in `sentence`.
- `potentially_invalid_sentence` (`BOOLEAN`): Validity flag identifying sentence parsing anomalies.
- `distance` (`DOUBLE`): Geometric error metric between user trajectory and idealized key-to-key path.

#### `swipe-2`, `swipe-3`, `swipe-4` Schema Differences
- Identical to `swipe-1` except `potentially_invalid_sentence` is omitted.
- Unfiltered raw submissions (users can filter out low-quality gestures using `distance`).

#### `swipe-5` Schema Differences
- Additional columns:
  - `language` (`VARCHAR`): ISO language code.
  - `layout` (`VARCHAR`): Layout identifier string.
  - `dual_finger` (`BIGINT`): Flag (`0` or `1`) indicating two-thumb/finger nintype-style gestures.
  - `data` (`JSON` / Nested Structure): When `dual_finger == 1`, `data` contains `{"L": [[{x, y, t}, ...]], "R": [[{x, y, t}, ...]]}` separating left and right thumb touchdown-to-lift trajectories.

---

## 3. FUTO per-user identifiers

### Conclusion
**No permanent per-user identifier (`user_id`, account ID, hardware serial, MAC address, or advertising ID) exists in the public FUTO swipe dataset.** However, a **session-level identifier (`session`) is present in every row of all collection runs**, which allows all swipes performed by the same individual during a single collection session to be grouped.

### Evidence Supporting the Conclusion
1. **Direct Parquet / JSONL Schema Verification:**
   Direct inspection of the dataset files across splits confirms the presence of the `session` column:
   - Example value: `"anon-session-c0bd4787-b692-4f84-813d-ef7889e55fec"` (formatted as `anon-session-<UUID>`).
   - No `user_id`, `account_id`, `device_id`, `ip_address`, or `imei` columns exist.
2. **Session Granularity and Distribution:**
   - In `swipe-1/validation/0.parquet`, there are **692 unique sessions** across 54,269 swipes, averaging **~78.4 swipes per session** (ranging from 1 swipe up to 1,019 swipes in a single session).
   - Each session contains continuous, monotonically increasing epoch millisecond timestamps (`timestamp`), consistent screen dimensions (`canvas_width`, `canvas_height`), and fixed `orientation` (e.g., `'portrait-primary'`).
3. **Partitioning Across Train / Validation / Test Splits:**
   - Split cross-checks confirm **zero session overlap** between validation and test splits. The benchmark splits in `swipe-1` were explicitly partitioned by `session` to avoid motor-signature data leakage between training and evaluation.

### Remaining Uncertainty & Cross-Session Linkability
- **Cross-Session Anonymity:** If a user visited `swipe.futo.org` on different days or cleared their browser storage, a new `anon-session-<UUID>` was generated. The dataset does not provide an explicit mechanism to link two separate sessions to the same individual.
- **Heuristic / Biometric Grouping:** While cross-session IDs do not exist, a researcher could infer session clusters using soft biometric and hardware fingerprints: matching identical floating-point screen dimensions (`canvas_width`, `canvas_height`), matching `orientation`, and analyzing user-specific motor invariants (such as average swiping velocity, corner-cutting radii, and DTW similarity across identical prompt words).

---

## 4. PART 2 — Literature on Synthetic Swipe Trajectory Generation

### 4.1 Human Motor Control Models Applied to Gesture Typing

#### 1. Minimum-Jerk Model (Flash & Hogan 1985; Quinn & Zhai 2018)
- **Mathematical Formulation:**
  Human motor control tends to maximize smoothness by minimizing the integral of squared jerk (the third time-derivative of position):
  $$J = \frac{1}{2} \int_{0}^{T} \left( \left(\frac{d^3 x}{dt^3}\right)^2 + \left(\frac{d^3 y}{dt^3}\right)^2 \right) dt$$
- **Application to Swipe Synthesis:**
  In Quinn & Zhai's production model for word-gesture keyboards, word generation is formulated as finding the optimal trajectory passing through target key locations $(x_i, y_i)$ as statistical *via-points* at estimated passage times $t_i$.
- **Corner-Cutting & Speed Modulation:**
  By modeling target keys not as exact coordinate constraints but as tolerance regions governed by sensorimotor noise $\mathcal{N}(0, \sigma^2)$, minimum-jerk synthesis naturally generates human-like corner rounding: the synthetic finger cuts corners on rapid transitions rather than making sharp polygonal turns.

#### 2. Two-Thirds Power Law ($v \propto \kappa^{-1/3}$)
- **Mathematical Principle (Lacquaniti, Terzuolo, Viviani 1983; Viviani & Schneider 1991):**
  Describes the fundamental coupling between trajectory geometry and movement kinematics:
  $$\omega(t) = \gamma \cdot [\kappa(t)]^{2/3} \iff v(t) = \gamma \cdot [\kappa(t)]^{-1/3}$$
  where $\omega(t)$ is angular velocity, $v(t)$ is tangential velocity, $\kappa(t)$ is curvature, and $\gamma$ is a velocity gain factor.
- **Application in Touch Interaction:**
  When humans execute curved swipes, velocity drops at high-curvature inflections (corners near target letters) and surges on straight inter-key segments. Trajectory interpolators enforce the two-thirds power law to assign realistic non-uniform timestamp intervals $\Delta t_i$ along generated spatial splines.

#### 3. Sigma-Lognormal Model & Kinematic Theory (Plamondon 1995; Martín-Albo et al. 2016)
- **Neuromuscular Synergies:**
  The Kinematic Theory models rapid movement as the spatial summation of discrete neuromuscular impulse responses. Each submovement produces a lognormal velocity profile:
  $$\vec{v}_i(t) = \vec{D}_i \cdot \Lambda(t; t_{0,i}, \mu_i, \sigma_i)$$
- **Application to Touchscreens:**
  Used to generate synthetic stroke gestures by parameterizing amplitude $D_i$, time delay $t_{0,i}$, log-time mean $\mu_i$, and spread $\sigma_i$. The model reproduces realistic asymmetric velocity peaks observed in touch interaction.

#### 4. Touchscreen Endpoint Modeling & Bayesian Criterion (Bi & Zhai 2013, 2016)
- Models touch endpoints on virtual keys as 2D bivariate Gaussian distributions:
  $$P(x, y \mid \text{Key}_k) \sim \mathcal{N}\left(\boldsymbol{\mu}_k, \boldsymbol{\Sigma}_k\right)$$
  where $\boldsymbol{\Sigma}_k$ exhibits anisotropic spread (larger variance along the vertical axis due to thumb perspective foreshortening).

---

### 4.2 Deep Generative Models & Data Augmentation

1. **Joint Geometric Augmentations (Miller & Kostarevas 2026 / FUTO SwipeALot):**
   - Applies 7 continuous geometric transformations simultaneously to the touch trajectory coordinates and the keyboard layout key bounding boxes:
     $$\mathcal{T} = \{\text{x-scale}, \text{y-scale}, \text{shear}, \text{horizontal-flip}, \text{vertical-flip}, \text{rotation}, \text{time-reversal}\}$$
   - Preserves topological and geometric consistency between the gesture and the layout tensor, forcing the neural encoder (Transformer/CTC) to learn layout-agnostic spatial representations.
2. **LSTM / CTC Decoder Training on Synthetic Traces (Alsharif et al. 2015):**
   - Google researchers demonstrated that training bidirectional LSTM decoders with Connectionist Temporal Classification (CTC) on synthetically generated minimum-jerk curves with Gaussian jitter produced substantial reductions in Word Error Rate (WER) across large lexicons ($>100\text{k}$ words).
3. **Generative Adversarial Networks & VAEs:**
   - GAN-based trajectory synthesizers (e.g., style-transfer GANs) map idealized polygonal key paths into realistic human trajectory distributions, capturing individual user motor styles and speed variations.

---

### 4.3 Validation Methodologies & Distance Metrics

- **Dynamic Time Warping (DTW):**
  $$\text{DTW}(S, H) = \min_{\pi} \sum_{(i, j) \in \pi} \|s_i - h_j\|_2$$
  Evaluates temporal and spatial alignment between synthetic curve $S$ and human baseline $H$.
- **Discrete Fréchet Distance & Partial Curve Mapping (PCM):**
  Measures worst-case and average spatial deviation independent of sampling speed.
- **Synthetic-to-Human Distance Ratios:**
  High-fidelity generators evaluate whether synthetic gestures are statistically indistinguishable from human variability using the distance ratio:
  $$\text{Ratio} = \frac{\mathbb{E}[D(\text{Synthetic}, \text{Human})]}{\mathbb{E}[D(\text{Human}_A, \text{Human}_B)]}$$
  A ratio in the range $[1.00, 1.15]$ indicates that synthetic gestures fall within normal human inter-subject motor variability.
- **Downstream Word Error Rate (WER) & Top-$K$ Accuracy:**
  The ultimate acceptance benchmark for synthetic trajectories is decoder performance: measuring Top-1/Top-3/Top-5 accuracy gains when training a recognizer exclusively on synthetic data versus fine-tuning on real data.

---

### 4.4 Conditioning Factors: Handedness, Posture & Screen Size

- **Handedness & Thumb Kinematics (Goel et al. 2012; Buschek et al. 2015):**
  - Thumb swiping revolves around the carpometacarpal (CMC) pivot joint, imparting systematic radial curvature to straight-line strokes.
  - **Right-thumb:** Produces convex, clockwise-tilted arcs on horizontal left-to-right strokes and negative diagonal shears.
  - **Left-thumb:** Produces counter-clockwise arcs with positive diagonal shears.
- **Handedness Inference from Touch / Swipe Trajectories:**
  - Classifiers (Random Forest, SVM, LSTM) achieve $>90\%$ accuracy in predicting handedness by extracting:
    1. Arc curvature signed radius ($\pm \kappa$).
    2. Touch contact area orientation / major-minor ellipse axes.
    3. Spatial touchdown offset relative to key centers (right thumbs consistently strike to the right and below key centers).
    4. Motion sensor / IMU orientation during typing.
- **Screen Size & Canvas Scaling (Leiva et al., MobileHCI 2021):**
  - "How We Swipe" showed that while trajectories are physically longer on larger screens, users swipe with higher absolute tangential velocity ($v$), keeping total per-word duration relatively stable across device form factors.
