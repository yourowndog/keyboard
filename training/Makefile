PY := .venv/bin/python

.PHONY: data pull extract noise synth eval shadow clean

# Full pipeline: harvest inputs -> synthetic training set + baseline eval
data: extract noise synth eval

pull:
	$(PY) ../tools/harvesting/snapshot_device.py --adb

extract:
	$(PY) extract.py

noise:
	$(PY) noise_model.py

synth:
	$(PY) synthesize.py

eval:
	$(PY) evaluate.py

sync-titan:
	rsync -av --progress data/ titan:omniboard-training/data/

shadow:
	$(PY) analyze_shadow.py

clean:
	rm -f data/train.jsonl data/val.jsonl
