# Voice Corpus & Transcription Plan

**Branch:** `feature/voice-corpus-v2` (from `swipe-synthesis-v2`, which is 36 commits ahead of `dev`)
**Status:** Steps 1–3 landed. Steps 4–6 open.

## What this is

OmniBoard's voice input serves two goals at once:

1. **Dictation** — fast, clean text in the keyboard. Fillers removed, punctuation applied.
2. **Corpus** — a permanent archive of natural speech for training a personal voice model later.

These two goals want *different transcripts of the same audio*, and only one of them is
reversible. A cleaned transcript can always be regenerated from a verbatim one. Audio thrown
away at capture time is gone permanently.

Everything below follows from that asymmetry.

---

## Step 1 — Lossless capture ✅

**Problem.** `Recorder.kt` used `MediaRecorder` with AAC at 128 kbps from `AudioSource.MIC`.
Two irreversible losses:

- **AAC is lossy.** Fine for playback, bad as voice-model training material — the model learns
  to reproduce the codec's artifacts along with the speaker.
- **`MIC` is the processed path.** On Samsung hardware it applies noise suppression and automatic
  gain control. AGC flattens the difference between shouting and near-whispering, which is
  exactly the dynamic range a voice model needs.

**Change.** `AudioRecord` → uncompressed 16-bit PCM → WAV container.

| | Before | After |
|---|---|---|
| Source | `MIC` (processed) | `UNPROCESSED` if supported, else `VOICE_RECOGNITION` |
| Encoding | AAC 128 kbps (lossy) | PCM 16-bit (lossless) |
| Sample rate | 44.1 kHz | 48 kHz (native hardware rate, no resample) |
| Platform DSP | whatever the OS chose | AEC / NS / AGC explicitly disabled |
| Container | MP4 | WAV |

**Why WAV and not FLAC on-device.** FLAC encoding on Android means `MediaCodec`, which adds
failure modes to the one component that must never fail. WAV is a header plus raw samples —
nothing to go wrong. Titan converts to FLAC on ingest. Lossless either way.

**Cost.** ~350 MB/hour on device, ~170 MB/hour after FLAC on Titan. 100 hours ≈ 17 GB archived.

**Capture metadata.** `Recorder.CaptureMetadata` records which mic path the device actually gave
us, whether each effect really turned off, peak amplitude, and clipped sample count. Samsung
advertises `UNPROCESSED` support inconsistently, so this has to be written down per-recording —
it cannot be recovered later, and it's what lets us segregate ambiguous audio during curation.

`MODIFY_AUDIO_SETTINGS` added to the manifest (install-time, no user prompt) — required to
disable the effects. `WhisperClient` now picks its MIME type from the file extension so queued
`.mp4` recordings from before this change still replay correctly.

---

## Step 2 — Stop discarding metadata ✅

Two separate losses today:

**We don't ask for it.** The relay sends a bare transcription request. Whisper-1 supports:

```
response_format=verbose_json
timestamp_granularities[]=word
timestamp_granularities[]=segment
```

which returns per-word start/end times plus per-segment `avg_logprob`, `compression_ratio`, and
`no_speech_prob`. Those last three are the standard hallucination detectors — a high compression
ratio means the model started looping, a low avg_logprob means it was guessing. Free quality
signal.

> **Stay on `whisper-1`.** `gpt-4o-transcribe` only supports `response_format=json` and cannot
> return timestamps at all. The two feature sets are mutually exclusive across models, and
> timestamps are non-negotiable for corpus work.

**We throw away what we do get.** `WhisperClient.transcribe()` does
`JSONObject(it).getString("text")` and drops the rest.

**Also:** `KeyboardManager` applies a hardcoded `Kiri` → `Kiry` regex and saves the *edited*
string as the label. The stored transcript is therefore already not what the model said. Split
into separate fields:

| Field | Meaning |
|---|---|
| `transcript_raw` | exactly what the engine returned, never edited |
| `transcript_display` | what got committed to the editor, after fixups |
| `transcript_verbatim` | filled in later by the local verbatim engine |

Plus the full engine response, and the capture metadata from Step 1.

---

## Step 3 — Get it to Titan ✅

No Syncthing. Reuse the retry queue that already exists in `VoiceManager.kt` — it already tracks
pending recordings and replays failures. Add a second job type: archive upload to a small
receiver on Titan over Tailscale.

No new daemon, no folder watching, nothing to configure. And it converges: once local
transcription lands (Step 5), the archive upload *is* the transcription request. One POST, file
stored and transcribed, Weakling demoted to cloud fallback only.

### Layout on Titan

```
~/datasets/sam-voice/
├── raw/
│   └── 2026/08/21/
│       ├── 20260821T142301-0500_a8f31c.flac
│       └── 20260821T142301-0500_a8f31c.json
├── manifests/
│   └── utterances.jsonl
└── derived/
    ├── 16k-asr/
    ├── 24k-tts/
    └── denoised/
```

Three rules that make this survive years of reprocessing:

1. **`raw/` is immutable and never reorganized.** Date-sharded to keep directory sizes sane.
   Paths recorded in the manifest stay valid forever.
2. **`derived/` is named by recipe and always disposable.** Anything there can be deleted and
   rebuilt from raw. That's what makes experimentation free.
3. **The JSONL manifest is the index.** One line per utterance, appendable, greppable, no
   database. Points at the raw file and every derived version.

The `a8f31c` suffix is a content hash — it catches duplicates when a retry double-uploads.

**Normalisation is a `derived/` decision, not a capture decision.** Whether the corpus ends up
loudness-normalised is deliberately deferred: raw capture preserves the dynamics, so a normalised
set can be generated any time. The reverse is impossible.

---


### What shipped

**Relay widened, narrowly.** `brokentooth-relay` rebuilt every request from scratch with a
hardcoded model and `filename="audio.m4a"`, discarding anything the client asked for — so
requesting metadata from the app was a no-op. It now forwards `response_format` and
`timestamp_granularities[]` through an **allowlist** (known keys, known values) rather than a
blanket passthrough, and derives the upload extension from the real filename. Verified against
a live recording: 35 word timestamps, 4 segments, and the quality fields.

**Sidecar schema 2.** `transcript_raw` (never edited) is now separate from `transcript_display`
(what got committed, after the `Kiri`→`Kiry` fixup), alongside the full engine response and the
capture metadata from Step 1. The sidecar write used to fail into `catch (_: Exception) {}`;
that silence is gone.

**Upload size ceiling.** The API caps uploads at 25 MiB, which at 48 kHz lossless is only
~4.5 minutes — a regression the move to lossless introduced. The upload copy is now decimated
3:1 to 16 kHz, giving ~13.6 minutes. This costs nothing: Whisper resamples to 16 kHz internally
before building its mel spectrogram, so it is the same audio the model would have seen. The
decimation averages each group of three samples rather than dropping two, because naive
decimation would alias everything above 8 kHz down into the speech band. **The archive copy
stays 48 kHz lossless.** At the limit the recorder stops itself and transcribes rather than
failing, with a warning 90 seconds out.

**Cancelled takes.** `cancelVoiceInput()` stopped the recorder without transcribing, leaving
audio on disk with no sidecar and no queue entry — invisible to the uploader and permanent.
Six such files were found and recovered. Cancelled takes are now archived with an empty
transcript and `transcribed: false`.

### The receiver

`voice-archive.service` on Titan (`100.104.232.94:8790`), stdlib Python, system service so it
survives reboot — `Linger=no` would have killed a user service. Bound to the Tailscale
interface, shared-token authenticated.

Deletion on the phone requires **both** conditions:

1. Titan returned a SHA-256 matching the local file — not merely HTTP 200.
2. The capture is older than a 24-hour grace period.

An unreachable Titan therefore costs disk space, never audio. Re-uploads are idempotent: same
content at the same path returns `duplicate: true` instead of filing a second copy.

**Sharding uses the device's timezone, not Titan's.** The phone is `America/Chicago` and Titan
runs Eastern; sharding by the server clock filed anything spoken after 23:00 Central under the
following day. The device sends its UTC offset and the receiver honours it.

**`raw/` stores the exact bytes received, not FLAC.** Converting on ingest would break the
checksum the phone verifies before deleting its copy. 561 GB free is ~1,600 hours of 48 kHz
audio; FLAC compaction can happen later as a `derived/` job that verifies losslessness first.


## Step 4 — Provider selector in the smartbar

Icon + dropdown for transcription provider. Wired with OpenAI as the only entry initially, so
adding local engines later is a config change rather than a code change.

---

## Step 5 — Local transcription (kills the OpenAI bill)

**CrisperWhisper 2.0** on Titan. ~3 GB VRAM.

Why this one rather than a bigger model: it solves the two-transcript problem natively.
`transcribe_dual()` returns clean *and* verbatim from a single pass, and `verbatimize(audio,
transcript)` can add disfluencies back into a clean transcript from any other engine.

The size instinct is wrong here. Measured disfluency capture:

| Model | Catches |
|---|---|
| Whisper large-v3 | ~10% |
| NVIDIA Canary-1B | ~20% |
| **CrisperWhisper 2.0** | **~88%** |

Large ASR models were trained on transcripts where a human had already deleted the fillers, so
they learned deletion is correct. That's training data, not capacity — a bigger model doesn't fix
it. Word timing is ~30 ms, better than WhisperX (65 ms) or NeMo forced alignment (60 ms).

License is non-commercial research. Private use, so not a constraint here.

**Optional second engine:** Qwen3-ASR-1.7B (Apache 2.0, ~5 GB) is more accurate on content,
especially proper nouns — SOMA, OmniBoard, Icarion, Qwen, Weakling. Add it only if names come
back mangled in practice, and combine via `verbatimize()`.

---

## Step 6 — Voice model tiers

All fit on the 3090 (24 GB) with room to spare.

| Tier | Model | Your data | VRAM |
|---|---|---|---|
| Instant clone | Chatterbox | 10 sec | ~6 GB |
| Real fine-tune | F5-TTS | 30 min – 2 hrs | ~6 GB |
| Best available | Higgs Audio V2 (5.8B) | 3–10 hrs, LoRA | ~14 GB |
| Full fine-tune experiment | Higgs V2.5 1B / CSM-1B | 3–10 hrs | fits fully |

### Notes that matter more than the table

**Pretraining hours are not your hours.** Orpheus's "100,000 hours" is its pretraining, already
done. Fine-tuning contributes a few hours on top. That's the point — you inherit the model's
general speech ability and supply only the voice.

**LoRA is not a compromise.** With a few hours of data, updating all of a 5.8B model's parameters
overfits and degrades it — the model memorises the recordings and forgets general speech
(catastrophic forgetting). LoRA freezes the base and trains a small adapter alongside, which
structurally protects the general ability. It is the correct method here, not the budget one.
Full fine-tuning is right when you have hundreds of hours and want to change what the model
fundamentally does.

**Full fine-tuning at home is real but bounded.** A 5.8B full fine-tune needs ~70 GB (weights +
gradients + Adam optimiser state + activations). That's a wall, not a slow lane — it OOMs rather
than taking longer. DeepSpeed ZeRO-3 CPU offload genuinely works if Titan has the system RAM,
at heavy PCIe cost. **Titan has 46 GB of system RAM, which settles this: a 5.8B full fine-tune
is out even with offload, since the optimiser state alone wants ~70 GB.** A 1B model full
fine-tunes on the 3090 with no tricks, which is the honest way to run that experiment.

**Don't chase model size to fix a data problem.** A published voice-clone comparison found
Orpheus underperforming a much smaller model, then traced it to audio chunking and normalisation
rather than the model. Corpus quality dominates. Model choice is cheap to change later; the
corpus is not.

**Fillers: keep them, and keep them matched.** The failure mode is not fillers in training data —
it's fillers in the *audio* that are missing from the *transcript*. That teaches the model to
emit random unexplained hesitations. Audio and transcript must agree, which is what makes the
verbatim transcript load-bearing for the voice model and not just the corpus.

Two adapters from one corpus, later:

- **A** — trained on clean transcripts → narration voice, for reading text back.
- **B** — trained on verbatim transcripts → conversational voice, with real hesitations.

---

## Order of work

Steps 1–3 have a deadline; the rest don't. Every hour recorded before Step 1 lands is an hour
that can never be upgraded, and every transcription before Step 2 loses metadata that costs money
to regenerate.

1. ✅ Lossless capture
2. ✅ Full metadata request + storage, split raw/display transcripts
3. ✅ Archive upload to Titan + ingest to `~/datasets/sam-voice/`
4. Provider selector in smartbar
5. CrisperWhisper on Titan → OpenAI bill goes away
6. Chatterbox clone as soon as any audio exists — cheapest way to see the ceiling
