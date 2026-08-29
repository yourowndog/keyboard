# Voice Corpus & Transcription Plan

**Status:** Steps 1–5 landed. Step 6 remains exploratory.

Current runtime behavior and lifecycle guarantees are canonical in
[Voice and AI Integrations](voice-ai.md). This document preserves the design
rationale and the remaining personal-model work.

## What this is

OmniBoard's voice input serves two goals at once:

1. **Dictation** — fast, clean text in the keyboard. Fillers removed, punctuation applied.
2. **Corpus** — a permanent archive of Sam's natural speech for training a one-speaker personal
   voice model.

These two goals want *different transcripts of the same audio*, and only one of them is
reversible. A cleaned transcript can always be regenerated from a verbatim one. Audio thrown
away at capture time is gone permanently.

Everything below follows from that asymmetry.

### North Star: theatrical one-speaker realism

The target is not a generic professional narrator. It is the most lifelike private/creative
replica of one particular speaker, Sam, that the available hardware and corpus can produce. The
quality reference is the naturalness of OpenAI's live voice and read-aloud experiences as they
exist in August 2026: convincing timbre, cadence, breath, timing, emotion, and long-form
stability. Matching that closed system exactly is not a promise; it is the direction against
which local models are judged.

Sam uses many fillers, pauses, false starts, repetitions, breaths, and changes of pace. Those are
speaker identity and expressive performance, not defects to clean away. Therefore:

- **Training labels are verbatim.** Every audible word and filler must be represented, and the
  timing must remain matched to the audio.
- **Pauses, breaths, and prosody stay in the audio.** Word timing identifies safe boundaries; it
  does not justify deleting silence or vocal events.
- **Clean transcripts exist only for dictation/text insertion.** They are never paired with audio
  that contains omitted speech during training.
- A narration subset may select or trim genuinely fluent passages, but its label must still match
  exactly what remains audible.
- Training time is not an optimisation target. A run may occupy the RTX 3090 for days or a week
  when that is the best evidence-backed path to higher perceptual quality. VRAM capacity remains
  a hard constraint even when elapsed time is not.

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

> **Current provider trade-off.** OpenAI recommends `gpt-transcribe` for text accuracy and it
> accepts `keywords`, but provider-native word timestamps remain a `whisper-1` feature. Titan's
> local CrisperWhisper path remains the corpus authority because it supplies verbatim words and
> can force-align finalized text. OpenAI is a dictation fallback, not the canonical trainer label.

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
    └── segments-v1/
        ├── audio/
        ├── sidecars/
        ├── manifests/
        └── reports/
```

Three rules that make this survive years of reprocessing:

1. **`raw/` is immutable and never reorganized.** Date-sharded to keep directory sizes sane.
   Paths recorded in the manifest stay valid forever.
2. **`derived/` is named by recipe and always disposable.** Anything there can be deleted and
   rebuilt from raw. That's what makes experimentation free.
3. **The JSONL manifest is the index.** One line per utterance, appendable, greppable, no
   database. Points at the raw file and every derived version.

The first model-neutral derived recipe uses verbatim word timing to create 3–30 second WAVs.
It prefers natural silence near a target duration, cuts only between timed words, retains the
surrounding pause, and records the parent take plus exact start/end offsets. Audio without a
trusted verbatim transcript stays in an `awaiting-verbatim` report instead of being mislabeled.
Every recipe is versioned so corrected transcripts or improved segmentation create a new derived
tree rather than overwriting an old one.

The builder preserves a source end to end whenever that fits model-safe clips. If a take contains
an otherwise unsegmentable span with no timed words—for example, a recorder left open for many
minutes, a long interruption, or corrupted audio the verbatim engine rejected—the derived recipe
keeps every timed word plus two seconds of surrounding pause and reports the omitted parent spans
in `reports/omitted-unlabeled.jsonl`. It never compresses that time, invents a label, or alters the
raw take. Ordinary pauses, fillers, breaths, false starts, repetitions, and vocal events remain.

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


## Step 4 — Provider selector in the smartbar ✅

The voice smartbar exposes a persisted provider selector for OpenAI Cloud and Titan Local.
Each take stores the provider it was created with, so retries do not silently switch engines.

---

## Step 5 — Local transcription (kills the OpenAI bill) ✅

**CrisperWhisper 2.0** runs on Titan behind the authenticated relay. ~3 GB VRAM.
It returns clean and verbatim transcripts from one pass; OmniBoard stores both
and does not double-transcribe the take on the phone.

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

## Step 6 — Voice model programme

The model must learn Sam's complete expressive distribution, not merely match a short timbre
reference. Zero-shot systems remain useful listening benchmarks, but they are not the destination
unless they publish a trainer that can consume the corpus.

### Current order of attack on the RTX 3090

| Role | Model | Why |
|---|---|---|
| **Primary** | **VoxCPM2 2B LoRA** | Official one-speaker trainer, 48 kHz output, about 20 GB VRAM, exact-text training, and optional same-speaker reference conditioning. It can consume every curated segment while keeping the stronger 2B base model intact. |
| Full-update challenger | Qwen3-TTS 0.6B Base SFT | Official single-speaker full fine-tuning path. Test memory before committing to a long run because the project publishes no 24 GB training guarantee. |
| Mature baseline | F5-TTS fine-tune | Simple, maintained trainer and useful quality/control comparison on the same split. |
| Experimental challenger | OmniVoice LoRA | Maintained LoRA trainer and small trainable fraction, but a more complex tokenized WebDataset pipeline and 24 kHz output. |
| Inference benchmark only | Higgs Audio v3 / Chatterbox | Useful for hearing the current zero-shot ceiling. Neither official release provides the corpus trainer required by this project. |

VoxCPM2 LoRA is the present first run. LoRA still trains on **all** of Sam's data; it changes
adapter weights rather than every base weight. That is a capacity choice, not a decision to ignore
the corpus. Official VoxCPM2 guidance puts LoRA near full fine-tuning for one-speaker similarity
and is the only current high-end recipe with an explicit ~20 GB target that fits the 3090.

The first trainer-specific recipe is deliberately stricter than the model-neutral corpus. It uses
lossless WAV parents, rejects clips above 0.1% hard-clipped samples, trims only leading/trailing
inactivity to 0.4 seconds, keeps every internal pause and vocal event, and does no loudness
normalisation. Training and validation are split by original parent take to prevent adjacent
segments leaking across the evaluation boundary. Forty percent of training rows receive a clean
reference clip from another take, matching VoxCPM2's official 30–50% recommendation while retaining
unprompted personalised generation in the remaining rows. The complete `segments-v2` recipe stays
available for an all-data challenger; excluded material is never deleted.

### Models that become reasonable with more resources or data

- **VoxCPM2 full fine-tuning:** official guidance targets large customization/roughly 1,000+
  exactly labeled clips, but estimates about 40 GB VRAM. More time does not remove that memory
  wall; use a larger GPU or a separately validated offload recipe.
- **Dedicated single-speaker models such as StyleTTS2/VITS-family training:** reconsider after
  roughly 10–20+ hours of consistently recorded, exactly labeled expressive speech. They offer
  control and ownership, but starting them on the current few-hour corpus is a lower-confidence
  route than adapting a modern pretrained model.
- **Higgs Audio v2/v3:** remove the old claim that a supported 3–10 hour Higgs LoRA exists. The
  official v3 release is zero-shot inference/serving only as of this verification.

### Notes that matter more than the table

**Pretraining hours are not your hours.** Orpheus's "100,000 hours" is its pretraining, already
done. Fine-tuning contributes a few hours on top. That's the point — you inherit the model's
general speech ability and supply only the voice.

**LoRA does not mean using less corpus.** Every eligible sample participates in optimization.
LoRA limits which parameters change, protecting the base model's general speech ability while the
adapter learns Sam's voice and performance distribution.

**Training time and memory are different constraints.** A week-long run is acceptable. A model
whose training state cannot fit 24 GB VRAM still needs a proven checkpointing/offload strategy or
different hardware; simply waiting longer does not prevent an OOM.

**Don't chase model size to fix a data problem.** A published voice-clone comparison found
Orpheus underperforming a much smaller model, then traced it to audio chunking and normalisation
rather than the model. Corpus quality dominates. Model choice is cheap to change later; the
corpus is not.

**Fillers are a target capability.** The failure mode is fillers in the audio missing from the
label. All training variants use exact verbatim labels. A cleaner narration variant is made from
fluent source passages or explicitly edited audio, never by relabeling disfluent audio with clean
dictation text.

---

## Order of work

Steps 1–3 have a deadline; the rest don't. Every hour recorded before Step 1 lands is an hour
that can never be upgraded, and every transcription before Step 2 loses metadata that costs money
to regenerate.

1. ✅ Lossless capture
2. ✅ Full metadata request + storage, split raw/display transcripts
3. ✅ Archive upload to Titan + ingest to `~/datasets/sam-voice/`
4. ✅ Provider selector in smartbar
5. ✅ CrisperWhisper on Titan → OpenAI bill goes away
6. Build the versioned verbatim segment corpus, then run matched VoxCPM2/Qwen3-TTS/F5-TTS
   evaluations against the theatrical-realism North Star
