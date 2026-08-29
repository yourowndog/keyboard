# Voice corpus derivation tools

`segment_verbatim.py` creates a model-neutral, versioned training view without modifying the
source corpus. `backfill_verbatim.py` creates immutable CrisperWhisper annotation overlays for
sources whose archived sidecars lack a trustworthy verbatim label. Raw audio and raw sidecars
remain byte-for-byte unchanged. The hard requirements are intentional:

- the source appears in `manifests/utterances.jsonl`;
- the source sidecar contains `transcript_verbatim` and timed `verbatim_words`;
- every long take can be divided between words into segments of 3–30 seconds;
- the source hash still matches the archive manifest.

The default is a read-only plan:

```bash
python tools/voice_corpus/segment_verbatim.py \
  --root /home/sam/datasets/sam-voice \
  --recipe segments-v2 \
  --annotation-recipe crisper2-large-v1
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

`adopt_unmanifested.py` safely appends recoverable audio/sidecar pairs to the canonical JSONL
manifest. It is dry-run by default, locks and rechecks the append-only manifest under `--apply`,
and never moves or edits the underlying raw files.

## Verbatim backfill

Plan without model work:

```bash
python tools/voice_corpus/backfill_verbatim.py \
  --root /home/sam/datasets/sam-voice \
  --recipe crisper2-large-v1
```

On Titan the tracked systemd unit supplies the existing endpoint credential without exposing it
in a command line. Applied runs are resumable: each successful source is atomically published as
`annotations/crisper2-large-v1/<source-id>.json`, existing annotations are verified and skipped,
and failures are reported under that recipe's `reports/` directory. Long audio is converted only
in a temporary directory and split at quiet transport boundaries below CrisperWhisper's 20-second
safety limit. Even a placeholder WAV header is repaired only on the temporary copy.
