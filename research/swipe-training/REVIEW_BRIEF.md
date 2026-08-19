# The Braking Problem

**Swipe trajectory synthesis — Stage 1 review brief**
OmniBoard / FlorisBoard, Android. Prepared for an outside reviewer.

We are generating synthetic swipe-typing trajectories to train a word
recogniser. The generator now passes its acceptance gate, but a validation
metric has diverged in every full-scale run and we would like a second opinion
on whether that matters.

**What we want from you.** Sections 5 and 6 are the live questions — a training
pathology we have a hypothesis about but have not proven, and five design
decisions we would make differently if someone with fresh eyes said so.
Everything before that is context.

---

## 1. Why generate swipes at all

Swipe typing turns a continuous finger path into a word. Training a recogniser
needs paired examples — path plus intended word — and we have a good source of
real ones: the **FUTO** open-source dataset, roughly 230,000 human recordings.

It does not cover our vocabulary. The words users of this keyboard actually
type — technical terms, tool names, project-specific jargon — barely appear. We
have about 5,100 target words with little or no real coverage, so we need to
synthesise trajectories for them.

The previous generator built paths from Bézier splines and a spring model. It
produced curves that looked plausible to the eye and were trivially separable
from human data by the recogniser, which learned "synthetic" as a feature
instead of learning the words. The tell is kinematic, and it is the axis this
whole project is organised around.

---

## 2. The metric: humans brake into corners

A finger changing direction must decelerate — you cannot carry speed through a
hairpin. We measure the **corner speed ratio**: instantaneous speed at a
direction-change vertex, divided by the trajectory's mean speed. Below 1.0
means braking.

All sources resampled to 64 points before measuring. 150 held-out words,
6 variations each.

| Source            | Mean  | Median | p90   | Error  | W₁    |
|-------------------|-------|--------|-------|--------|-------|
| Human (FUTO)      | 0.294 | 0.263  | 0.460 | —      | —     |
| Seq2Traj **PASS** | 0.358 | 0.321  | 0.603 | 21.8%  | 0.159 |
| Spline + spring **FAIL** | 0.685 | 0.479 | 1.403 | 133.2% | 0.478 |

The old generator sits at 0.685 — and its p90 of 1.403 means its worst tenth of
corners are traversed *faster* than the path average, which no hand does. Gate
tolerance is ±25% on the mean. The Seq2Traj row is a four-epoch model trained on
a 20k-sample slice; a full 380k / 30-epoch run is in progress.

---

## 3. Architecture, and what comes after

### Stage 1 — Seq2Traj (generator), 697,478 params

GRU encoder–decoder with additive attention. Embed 64, hidden 128,
bidirectional encoder. In: character ids + key-centre coordinates.
Out: 48 × (x, y, dt).

Heteroscedastic — separate mean and log-variance heads trained under Gaussian
NLL, so the model learns how much variation each point should carry rather than
emitting one rigid path. Loss adds velocity and acceleration terms (weights 6.0
and 2.0 against NLL at 1.0) because a position-only objective over-smooths
exactly the corners we care about. Scheduled sampling decays teacher forcing
from 0.9 to 0.05 over the first 35% of the run.

### Stage 2 — NeuroSwipe (recogniser), 1,334,046 params

Transformer encoder–decoder. d_model 128, 4 heads, 4+4 layers, ff 256,
dropout 0.1. In: 64 × 7 features (x, y, dx, dy, speed, turn, dt).
Out: 24 chars × 30 classes.

Trains on real and synthetic mixed via a weighted sampler. Real and synthetic
accuracy are reported separately and checkpoints are selected on **real** word
accuracy only, so a model that gets good at our own synthetic distribution
cannot win. Exports to ONNX as split encoder and decoder graphs with static
shapes, ~5.3 MB fp32, for on-device inference.

### Downstream — what this unlocks (not yet built)

Once Stage 1 clears the gate we can mint arbitrary labelled trajectories for any
vocabulary. The intended next models are a personalised recogniser fine-tuned on
an individual's own swipe corpus, and a layout-transfer variant — the generator
is conditioned on key coordinates, so retargeting to a different keyboard
geometry should be a matter of feeding new coordinates rather than recollecting
data. We would rather hear now if that conditioning is too weak to carry real
layout transfer.

---

## 4. Four defects, each hiding the next

Worth reading because the same class of error recurred: we repeatedly measured
the model on a task slightly different from the one it was doing. Three of the
four were harness bugs, not model problems.

### 01 — Coordinates divided by a canvas that was already normalised

FUTO stores `x`/`y` pre-normalised to the keyboard; `canvas_width` /
`canvas_height` are dp metadata. Every loader in the repo divided by them,
shrinking coordinates about 400×. Pre-existing, inherited, and it silently
corrupted the human baseline everything else was measured against.

*Fixed by detecting the convention per row. Layout calibration went from a
0.0025 x-scale to 0.989.*

### 02 — The model learned to claim certainty it had not earned

Gaussian NLL pays for confidence. With the log-variance floor at −9.0 the model
could assert σ = 0.011 — a tenth of a key width — and under heavy teacher
forcing it could back that up. Free-running validation loss climbed past 60
while training loss fell to −3.1. An overconfident generator also has no
variation left to spend at synthesis, which is the failure mode we are trying to
escape.

*Floor raised to −7.0 (σ = 0.030); teacher forcing decayed over the first 35% of
the run rather than all of it.*

### 03 — The gate compared trajectories sampled at different densities

Corner detection is sensitive to sampling density. The *same* human swipes
measure a mean corner ratio of **0.161** at their native ~77 points and **0.342**
resampled to 48 — sparser sampling averages away the momentary stops the metric
exists to detect. The gate scored 48-point generated output against
native-resolution human data, so roughly half of every error we reported was an
artifact.

*All sources now resampled to 64 points, matching what Stage 2 feeds the
recogniser. This is why earlier figures of 249% and 771% — and an inherited
"+1531% corner velocity" claim — should be disregarded.*

### 04 — The timing channel clipped away the pauses

Inter-sample interval was clamped to 4–80 ms. Against *raw* FUTO timing that is
generous (0.2% below, 0.6% above). But training runs on arc-length resampled
paths, and arc-length spacing places points evenly in distance — so a pause at a
corner becomes one long interval. On resampled human data p95 is 95 ms and p99
is 202 ms. The window clipped **13.4%** of targets up to the floor and **6.6%**
down to the ceiling, destroying precisely the pauses the gate scores, and
capping the generator at an 80 ms pause. Two-thirds of predicted intervals came
out pinned to a rail.

*Widened to 1–300 ms and moved to log space, since dt spans two orders of
magnitude and a linear sigmoid put the median at 0.04 of its range. This single
change took the gate from 165.8% to 21.8%.*

---

## 5. The open pathology — what the model keeps doing

In every full-scale run, free-running validation loss climbs while both the
teacher-forced loss and the corner metric improve.

380,000 training trajectories, RTX 3090, ~136 s per epoch.
Teacher forcing decays 0.9 → 0.05.

| Epoch | tf   | Train  | Val (forced) | Val (free) | Corner err |
|-------|------|--------|--------------|------------|------------|
| 1     | 0.90 | −1.646 | −1.949       | −1.007     | 58.9%      |
| 2     | 0.81 | −2.089 | −2.550       | −0.650     | 23.6%      |
| 3     | 0.72 | −2.458 | −2.553       | **+8.621** | 20.7%      |
| 4     | 0.63 | −2.604 | −2.833       | +0.331     | 15.4%      |
| 5     | 0.55 | −2.609 | −2.874       | +1.948     | 16.0%      |
| 6     | 0.46 | −2.594 | −2.889       | +0.054     | 13.2%      |

The free-running number oscillates violently rather than climbing monotonically,
which is itself a clue. Corner error improves steadily throughout.

Notably this does *not* reproduce on a 20k-sample slice, where free-running
validation stayed at −2.02 and tracked the teacher-forced number closely for the
whole run. It only appears at full scale.

### Our hypothesis, and the test we built for it

Free-running NLL against one specific held-out recording may simply be the wrong
quantity for a stochastic generator. Given a word there are many valid swipes.
Running free, the model produces *a* plausible trajectory; scoring it pointwise
against *one particular* human recording punishes legitimate diversity, and
punishes it more as the model becomes more confident about its own coherent
alternative. The competing reading is straightforward exposure-bias drift that
the corner metric is too coarse to see.

NLL cannot separate these — both produce a rising number. So we wrote
`diagnose_diversity.py`, which changes the reference instead:

- **human → nearest human**: how far apart are two real recordings of the same
  word? This is the irreducible spread of the task, the floor.
- **model → nearest human**: how far is a generated trajectory from the closest
  real recording of that word?

Distances are mean per-point offset in keyboard widths (0.1 = one key), both
arc-length resampled to 64 points. Measured leave-one-out so the two numbers are
directly comparable. 150 words, ≥6 real recordings each.

| Comparison             | n    | Mean   | Median | p10    | p90    |
|------------------------|------|--------|--------|--------|--------|
| human → nearest human  | 1527 | 0.0715 | 0.0669 | 0.0424 | 0.1034 |
| model → nearest human  | 900  | 0.1279 | 0.1176 | 0.0794 | 0.1947 |
| human → random human   | 1527 | 0.1121 | 0.1033 | 0.0608 | 0.1666 |
| model → random human   | 900  | 0.1700 | 0.1588 | 0.1016 | 0.2523 |

**Ratio 1.79×.** Our trajectories sit about 1.3 key widths from the nearest real
recording; two humans typing the same word sit about 0.7 apart. Outside the
human spread, but not wildly — the ambiguous band.

The discriminator is whether that ratio is *stable* or *rising* across training.
Stable means the model is committing to valid alternatives; rising means drift.
We are sampling it every 7 minutes during the run. First two samples: **1.79×**,
then **1.75×** at epoch 5. Early, but not rising.

**The specific question for you:** is that a sound test? And is there a better
one? We are aware "distance to nearest of N" is optimistic by construction,
which is why the random-pairing control is computed the same way — but we would
rather be told now if the whole framing is wrong.

---

## 6. Decisions we would revisit

### Is arc-length resampling the right representation? *(most load-bearing)*

Resampling to fixed point count moves the entire velocity profile into the `dt`
channel — positions carry shape, timing carries everything kinematic. It made
defect 04 possible and means a single channel bears all the signal the
acceptance metric reads. Uniform time sampling with a length mask is the obvious
alternative. We picked arc-length for fixed-shape export convenience, which is a
weak reason.

### Is one scalar enough to accept on?

The gate passes on mean corner ratio within ±25%. We also compute a 1-D
Wasserstein distance over the ratio distribution but do not gate on it. A single
scalar summary is gameable — we have already seen a case where the mean was
acceptable while the p90 was 2.5× human.

### Is an autoregressive GRU the right family in 2026?

Seq2Traj is a small attention seq2seq trained with scheduled sampling — a
2016-shaped design. Trajectory synthesis at 48 points is small enough that a
conditional flow-matching or diffusion model would train in comparable time,
sidestep exposure bias entirely, and give principled control over sample
diversity. The cost is a heavier sampling path on a mobile-adjacent pipeline. We
would like a view on whether that trade is worth making now rather than after
Stage 2.

### How do we stop Stage 2 learning "synthetic" as a feature?

Currently: separate accuracy reporting for real and synthetic, checkpoint
selection on real only, and a set of overlap words present in both corpora. That
is monitoring, not prevention. A domain-adversarial head or an explicit
discriminator would be prevention, at the cost of training stability.

### Should timing be modelled jointly with position at all?

Positions and `dt` share a decoder and a covariance-free diagonal Gaussian, so
the model cannot express "slow down *because* the path is about to turn" except
implicitly through the hidden state. Given that the accept/reject criterion is
precisely the coupling between speed and curvature, a factorised path-then-timing
model may be the more honest structure.

---

## Environment

PyTorch 2.13.0+cu130, Python 3.12.14, single RTX 3090. Data: FUTO open-source
swipe corpus (748 MB parquet, ~230k recordings), 380k train / 20k val after
filtering. Target vocabulary ~5,100 words after hygiene filtering from 6,842.

Relevant files, all under `research/swipe-training/`:

| File | Role |
|---|---|
| `swipe_common.py` | geometry, tokenisation, FUTO loading, features, corner metric |
| `train_seq2traj.py` | Stage 1 generator — train / synthesize / calibrate |
| `train_neuroswipe_v1.py` | Stage 2 recogniser — train / export |
| `validate_seq2traj.py` | acceptance gate (real vs seq2traj vs physics) |
| `diagnose_diversity.py` | the diversity-vs-drift diagnostic in section 5 |
| `smoke_test.sh` | six-step end-to-end pipeline check |

Figures current as of epoch 6 of a 30-epoch run. The Stage 1 gate result in
section 2 is from a 4-epoch model on a 20k slice.
