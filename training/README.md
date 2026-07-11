# Neural autocorrect training

This directory contains the active offline pipeline for OmniBoard's ONNX
autocorrect scorer. The model is a gate for automatic replacement; the
heuristic scorer still orders the suggestions shown to the user.

## Inputs and outputs

- Canonical harvest data: `../data/harvest/raw/`
- Immutable device pulls: `../data/harvest/inbox/<timestamp>/`
- Generated training artifacts: `data/` (ignored where appropriate)
- Android model and metadata: see `../docs/autocorrect/neural-scorer.md`

Never concatenate, deduplicate, or overwrite a device snapshot while pulling
it. Capture first with `make pull`; review and promote data deliberately.

## Typical workflow

Create `training/.venv`, install the dependencies used by these scripts, then
run from this directory:

```bash
make pull       # exact snapshot from the connected default adb device
make extract    # harvest events -> normalized examples
make noise      # learn/generate the typo noise model
make synth      # construct train/validation data
make eval       # baseline evaluation
make shadow     # analyze device shadow-scoring output
```

`make data` runs extract, noise, synthesis, and evaluation. Training and ONNX
export are performed by `train.py`; inspect its arguments before a run rather
than treating old handoff notes as configuration. `tau_sweep.py` evaluates gate
thresholds. Keep live gating disabled until a shadow evaluation justifies the
chosen threshold.

The `sync-titan` target is retained for the historical Titan training host. It
is not the Beksinski Android build-factory workflow.

## Pipeline map

- `extract.py`: parses current harvest formats and creates normalized examples.
- `noise_model.py`: estimates realistic error transformations.
- `synthesize.py`: produces labeled train/validation examples.
- `featurize.py` and `feature_spec.md`: define the model feature contract.
- `nn_scorer.py`: model definition used during training.
- `train.py`: training, evaluation, and export entry point.
- `evaluate.py`: heuristic/baseline evaluation.
- `analyze_shadow.py`: evaluates scores logged by the Android shadow path.
- `tau_sweep.py`: threshold trade-off analysis.

For runtime wiring and defaults, read `../docs/autocorrect/neural-scorer.md`.
