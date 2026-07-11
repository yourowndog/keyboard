# Swipe Training Research

> Status: Shelved research  
> Reclassified: 2026-07-11

This directory preserves experiments for extracting FUTO swipe samples,
generating synthetic traces, preparing training data, and producing precomputed
gesture assets.

It is distinct from OmniBoard's active glide-typing runtime. The live runtime
continues to detect glide paths and classify candidates without requiring this
research workflow.

## Known state

- Several scripts require `pyarrow`, NumPy, external FUTO data, or a separate
  trainer checkout.
- Some paths are machine-specific to `/home/sam/projects/keyboard`.
- `setup_training.py` expects external `florisboard`, `futo`, and trainer trees.
- JSON and binary generators target different asset filenames.
- `PrecomputedGestureCache` currently looks for `ime/swipe/futo_swipes.bin` and
  tolerates it being absent.
- The former packaged `precomputed_gestures.json` had no runtime reader. It is
  preserved under `artifacts/` so it no longer adds roughly 31 MB to the APK.

## Revival checklist

1. Identify the intended runtime classifier and exact asset format.
2. Document external dataset licensing and provenance.
3. Replace machine-specific paths with CLI arguments or repository-relative
   paths.
4. Establish a reproducible environment and dependency file.
5. Create a tiny fixture and end-to-end asset-load test.
6. Compare model size, startup memory, latency, and accuracy on device.
7. Only then add a generated swipe asset back to the Android package.
