# Two-Stage Swipe Model Runbook

Stage 1 learns human swipe kinematics from FUTO and synthesizes trajectories for
vocabulary FUTO does not cover. Stage 2 trains the on-device classifier on the
combined corpus and exports it for OmniBoard.

## Files

| File | Role |
|---|---|
| `swipe_common.py` | Keyboard geometry, tokenizer, FUTO loader, features, corner metric. Single source of truth for both stages. |
| `train_seq2traj.py` | Stage 1 generator: `calibrate` / `train` / `synthesize`. |
| `validate_seq2traj.py` | Acceptance gate comparing real vs Seq2Traj vs legacy physics kinematics. |
| `train_neuroswipe_v1.py` | Stage 2 classifier: `train` / `export`. |
| `smoke_test.sh` | End-to-end run on a tiny slice. Run before any full training. |

Inputs live alongside these: `futo_swipes.parquet` (748 MB),
`target_swipe_vocabulary_supplement.txt`, `futo_words_unique.txt`.

## Environment

Titan had no Python environment for this — it was created as:

```bash
cd /home/sam/projects/omniboard-dirs/keyboard/swipe-training
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python --index-url https://download.pytorch.org/whl/cu130 torch
uv pip install --python .venv/bin/python numpy pyarrow onnx onnxruntime
```

System Python on Titan is 3.14, which torch does not ship wheels for; the venv
pins 3.12 for that reason.

## Sequence

### 0. Smoke test

```bash
./smoke_test.sh
```

Exercises all six steps in a few minutes. Everything below assumes this is green.

### 1. Calibrate the coordinate frame

```bash
.venv/bin/python train_seq2traj.py calibrate --parquet futo_swipes.parquet
```

FUTO stores raw pixels plus `canvas_width`/`canvas_height`; everything downstream
works in keyboard-normalized 0–1 space. `calibrate` fits an axis-aligned affine
map from `QWERTY_LAYOUT` key centers to observed touch-down/lift-off positions.

**Read `identity_like`.** If it is `false`, the layout table does not describe the
frame FUTO recorded in, and training on it produces a model that draws swipes for
the wrong keyboard. `train` refuses to start in that case unless you pass
`--force`. It also prints the real corner-kinematics baseline — record those
numbers, they are what the gate in step 3 compares against.

### 2. Train the generator

```bash
.venv/bin/python train_seq2traj.py train \
    --parquet futo_swipes.parquet \
    --epochs 30 --batch-size 256 \
    --out-dir models
```

Roughly 2M parameters; a few hours on the 3090 over the full ~230k samples.
Checkpoints to `models/seq2traj_best.pt`, resumable with `--resume`.

Watch the `vel` loss component, not just total loss. Velocity error is what
corresponds to corner braking; total loss can fall while velocity plateaus, and
that combination means the model is learning shape without kinematics.

### 3. Gate on corner kinematics

```bash
.venv/bin/python validate_seq2traj.py \
    --parquet futo_swipes.parquet \
    --checkpoint models/seq2traj_best.pt \
    --report validation_report.json
```

Compares corner speed ratio — speed at a direction-change vertex over trajectory
mean speed — across real FUTO, Seq2Traj, and the legacy Bezier/spring generator,
on the same held-out words.

Humans brake into corners, so the real ratio sits well below 1.0. The legacy
generator carries speed through the vertex; that is the +1531% corner-velocity
error already on record, and it should show up here as the failing baseline.

Exit code 0 = pass, 1 = fail, 2 = incomplete. **A fail here means stop.** Feeding
Stage 2 a generator that cuts corners just relocates the same defect into the
final model. Options on a fail: train Stage 1 longer, raise `w_vel` in
`seq2traj_loss`, or lower `--temperature` at synthesis.

### 4. Synthesize the supplement

```bash
.venv/bin/python train_seq2traj.py synthesize \
    --checkpoint models/seq2traj_best.pt \
    --vocab target_swipe_vocabulary_supplement.txt sams_custom_words.txt \
    --out synthetic_supplement.jsonl \
    --variations 10 \
    --overlap-source futo_words_unique.txt --overlap-words 2000
```

`--overlap-words` also synthesizes vocabulary FUTO *already* covers. Without that
overlap, every synthetic trajectory carries a label no real trajectory has, and
Stage 2 can score well by learning "generator artifact ⇒ rare word" instead of
learning geometry. The overlap makes the two distributions share labels so that
shortcut stops paying.

### 5. Train the classifier

```bash
.venv/bin/python train_neuroswipe_v1.py train \
    --parquet futo_swipes.parquet \
    --synthetic synthetic_supplement.jsonl \
    --epochs 40 --batch-size 256 \
    --out-dir models
```

4-layer Transformer encoder-decoder, d_model 128, ~1.6M parameters.

Validation reports real and synthetic accuracy separately, and checkpoint
selection uses **real** word accuracy only. A gap where synthetic accuracy far
exceeds real is the shortcut described above showing itself — lower
`--synth-weight` if that appears.

### 6. Export

```bash
.venv/bin/python train_neuroswipe_v1.py export \
    --checkpoint models/neuroswipe_v1_best.pt --executorch
```

Emits `neuroswipe_v1_encoder.onnx` and `neuroswipe_v1_decoder.onnx`, and verifies
numerical parity against eager PyTorch if `onnxruntime` is installed.

The export is split deliberately: autoregressive decoding cannot be one static
graph. On device, run the encoder once per gesture, then loop the decoder step.

## Design decisions worth knowing

**Apostrophes are geometry-free.** On a swipe keyboard you draw `d-o-n-t` and the
decoder emits `don't`. So apostrophes are stripped from the traversed key path but
kept in the label. `QWERTY_LAYOUT` therefore has no apostrophe key while the
decoder alphabet does. The original scripts placed the apostrophe at `(0.875, 0.50)`
— the same coordinates as `l` — which made `i'll`, `ill`, and `i'l` geometrically
identical at the input layer, on exactly the vocabulary this project exists to fix.

**Unknown characters raise instead of padding.** The original tokenizer mapped any
out-of-alphabet character onto `<pad>`, silently corrupting targets. `UnsupportedWord`
is raised and the sample is dropped with a count.

**Fixed-length trajectories, no EOS head.** Arc-length resampling to 48 points puts
shape in the x/y channels and the entire velocity profile in `dt`, which removes a
separate length-classification problem.

**Heteroscedastic output.** The generator predicts mean and log-variance per channel
and is trained with Gaussian NLL, so sampling at synthesis draws from variation the
model learned from humans — wide mid-stroke, tight at corners — rather than uniform
jitter added afterwards.

## Vocabulary hygiene

`swipe_common.is_plausible_word` filters the harvested list. Of the 6,842 harvested
words, roughly 1,700 are not words: space-stripped sentences
(`aandwhnididinoticedmynailsgrewfaster...`), lowercased Android class names
(`accessibilitynodeinfo`, `abstractcomposeview`), and hash fragments
(`aacgykaqgsarqsfqhgx`). About 5,100 survive.

Short consonant clusters are kept deliberately — `mcp`, `adb`, `tmux` are exactly
the target vocabulary. Verified survivors include `mcp`, `openclaw`, `tmux`,
`jcodemunch`, `adb`, `don't`, `i'm`, `we're`, `kotlin`, `gradle`, `onnx`.

A tail of typo entries (`abovwnthe`, `addrwss`) still passes. That is a deliberate
tradeoff: tightening the filter starts dropping real vocabulary, and a small
fraction of typo labels costs the classifier little.

Pass `--no-filter` to `synthesize` to use the raw list instead.
