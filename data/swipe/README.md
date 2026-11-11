# OmniBoard Swipe Data Repository

This directory contains swipe datasets and representations for OmniBoard gesture typing synthesis and recognizer training.

---

## 1. Directory Layout

```
data/swipe/
├── README.md                 # This documentation
├── raw/                      # IMMUTABLE raw/source datasets (gitignored)
│   └── futo/                 # Canonical FUTO dataset shards (swipe-1 to swipe-5)
│       ├── manifest.json     # Verification manifest (hashes, row counts, schemas)
│       ├── swipe-1/          # Main Wikipedia & Common Voice dataset (~1.04M swipes)
│       │   ├── train/        # 4 shards: 0..3.parquet (939,550 swipes)
│       │   ├── validation/   # 1 shard: 0.parquet (54,269 swipes)
│       │   └── test/         # 1 shard: 0.parquet (49,970 swipes)
│       ├── swipe-2/train/    # Informal reviews & TV dialogue (28,095 swipes)
│       ├── swipe-3/train/    # Slang & OpenWebText (38,228 swipes)
│       ├── swipe-4/train/    # Hard path-confusable negatives (50,300 swipes)
│       └── swipe-5/train/    # Multilingual & dual-finger runs (59,247 swipes)
└── derived/                  # Future normalized, filtered, and tokenized training splits (gitignored)
```

---

## 2. Immutable Raw Data Policy

- All files in `data/swipe/raw/` are **immutable upstream artifacts**.
- **DO NOT** modify, overwrite, resample, smooth, filter, or re-encode raw parquet shards in place.
- All filtering (e.g. portrait aspect ratio selection), normalization, coordinate scaling, or trajectory resampling must output to `data/swipe/derived/` with full provenance logs.

---

## 3. Reacquisition

To reacquire or verify the canonical FUTO dataset from Hugging Face:

```bash
uv run --with pyarrow --with requests python3 research/swipe-training/acquire_futo_data.py
```

This downloads missing shards directly from `https://huggingface.co/datasets/futo-org/swipe.futo.org` and verifies:
- 1,219,659 total swipe trajectories across 10 parquet shards
- Complete schema preservation (`id`, `session`, `timestamp`, `word`, `canvas_width`, `canvas_height`, `orientation`, `data` with raw $\{x, y, t\}$, `sentence`, `word_idx`, `distance`, and run-specific metadata)
- SHA256 integrity and row count correctness
