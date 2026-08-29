# Voice corpus derivation tools

`segment_verbatim.py` creates a model-neutral, versioned training view without modifying the
source corpus. Its hard requirements are intentional:

- the source appears in `manifests/utterances.jsonl`;
- the source sidecar contains `transcript_verbatim` and timed `verbatim_words`;
- every long take can be divided between words into segments of 3–30 seconds;
- the source hash still matches the archive manifest.

The default is a read-only plan:

```bash
python tools/voice_corpus/segment_verbatim.py \
  --root /home/sam/datasets/sam-voice \
  --recipe segments-v1
```

Add `--apply` to encode 48 kHz mono PCM WAVs and atomically publish
`derived/segments-v1/`. Existing recipe directories are never overwritten. When transcripts or
the boundary recipe change, use `segments-v2` or another new recipe name.

The output contains:

- `audio/` — derived segment WAVs;
- `sidecars/` — parent provenance, offsets, checksums, exact verbatim text, and shifted words;
- `manifests/segments.jsonl` — trainer-neutral `audio` + `text` rows;
- `reports/` — summary, awaiting-verbatim, rejected, and unmanifested-source ledgers.

Training-specific manifests for VoxCPM2, Qwen3-TTS, F5-TTS, or later models must be generated
from this versioned neutral layer. Clean dictation text is never a trainer label.
