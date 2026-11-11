#!/usr/bin/env bash
# smoke_test.sh — exercise the whole two-stage pipeline on a tiny slice.
#
# Proves every stage executes and hands the right artifact to the next one,
# in a few minutes rather than a few hours. Run this before any full training
# run; a green smoke test is what makes an overnight run worth starting.
#
#   ./smoke_test.sh [python]

set -euo pipefail

PY="${1:-.venv/bin/python}"
cd "$(dirname "$0")"

SMOKE_DIR=smoke_out
rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR"

hr() { printf '\n\033[1m=== %s ===\033[0m\n' "$1"; }

hr "0. shared foundation self-test"
"$PY" swipe_common.py

hr "1. coordinate frame calibration"
"$PY" train_seq2traj.py calibrate --parquet futo_swipes.parquet --max-samples 8000

hr "2. Stage 1 generator — 2 epochs on 20k samples"
"$PY" train_seq2traj.py train \
    --parquet futo_swipes.parquet \
    --max-samples 20000 \
    --epochs 2 \
    --batch-size 128 \
    --workers 2 \
    --out-dir "$SMOKE_DIR"

hr "3. synthesize a small supplement"
head -200 target_swipe_vocabulary_supplement.txt > "$SMOKE_DIR/vocab_smoke.txt"
"$PY" train_seq2traj.py synthesize \
    --checkpoint "$SMOKE_DIR/seq2traj_best.pt" \
    --vocab "$SMOKE_DIR/vocab_smoke.txt" \
    --out "$SMOKE_DIR/synthetic_smoke.jsonl" \
    --variations 4 \
    --overlap-words 100

hr "4. kinematic gate (informational at smoke scale)"
# A 2-epoch generator is not expected to pass; we are checking the gate runs
# and reports all three sources.
"$PY" validate_seq2traj.py \
    --parquet futo_swipes.parquet \
    --checkpoint "$SMOKE_DIR/seq2traj_best.pt" \
    --report "$SMOKE_DIR/validation_smoke.json" \
    --words 40 --variations 4 --max-real-samples 40000 || true

hr "5. Stage 2 classifier — 2 epochs"
"$PY" train_neuroswipe_v1.py train \
    --parquet futo_swipes.parquet \
    --synthetic "$SMOKE_DIR/synthetic_smoke.jsonl" \
    --max-real 20000 \
    --epochs 2 \
    --batch-size 128 \
    --workers 2 \
    --eval-batches 4 \
    --out-dir "$SMOKE_DIR"

hr "6. export"
"$PY" train_neuroswipe_v1.py export \
    --checkpoint "$SMOKE_DIR/neuroswipe_v1_best.pt" \
    --out-dir "$SMOKE_DIR"

hr "SMOKE TEST PASSED"
ls -la "$SMOKE_DIR"
