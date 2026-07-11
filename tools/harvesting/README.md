# OmniBoard Harvest Tools

> Status: Maintained tooling  
> Last verified: 2026-07-11

These tools capture, measure, analyze, repair, and convert OmniBoard usage
events. Raw event order and duplicate events are meaningful. Never deduplicate
the corpus by line content.

## Data locations

```text
data/harvest/
  inbox/     exact timestamped device snapshots; ignored by Git
  raw/       canonical reviewed Markdown and JSONL corpora
  reports/   aggregate measurements and analysis reports
  derived/   intermediate proposals and reversible mappings
```

Packaged runtime language assets remain in
`app/src/main/assets/ime/dict/`. Derived harvest files do not become runtime
assets until they are reviewed and deliberately applied or rebuilt.

## Capture an exact snapshot

From a workstation with ADB, including ADB-over-Wi-Fi:

```bash
python3 tools/harvesting/snapshot_device.py --adb
```

Select a particular connected device when necessary:

```bash
python3 tools/harvesting/snapshot_device.py --adb --serial HOST:PORT
```

From Termux on the device:

```bash
python3 tools/harvesting/snapshot_device.py --local
```

Snapshots are exact copies under `data/harvest/inbox/<timestamp>/`. The command
does not merge, deduplicate, clear, or overwrite the canonical corpus.

Promotion from an inbox snapshot into `raw/` remains a reviewed operation until
the historical Markdown stream and monotonic JSONL IDs have a tested merge tool.

## Measure the corpus

```bash
python3 tools/harvesting/harvest_manifest.py
```

The default input is the canonical Markdown corpus. Aggregate JSON output goes
to `data/harvest/reports/harvest_manifest.json`. An alternate input and output
directory can be supplied as positional arguments.

## Generate legacy analysis proposals

```bash
python3 tools/harvesting/harvest_analyze.py
```

This writes the human summary to `reports/` and proposal/intermediate files to
`derived/`. Its accepted/rejected metrics are event-level and should not replace
sequence-aware JSONL calibration analysis.

## Recover concatenated prose

```bash
python3 tools/harvesting/segment_recovery.py
```

This produces a personal unigram seed and reversible segmentation mapping in
`derived/`. It does not rewrite the raw corpus.

## Rebuild packaged dictionaries

Inspect without writing assets:

```bash
python3 tools/harvesting/build_dictionary.py --dry-run
```

The non-dry-run mode writes the packaged unigram, bigram, and phrase assets.
Use it only after reviewing corpus provenance, triage, output diffs, and expected
runtime effects.

## Neural training

The active ONNX scorer workflow lives in `training/`. It reads the canonical
raw Markdown and JSONL paths. `make -C training pull` creates an exact inbox
snapshot; it no longer overwrites the canonical corpus directly.

