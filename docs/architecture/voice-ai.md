# Voice and AI Integrations

> Status: Canonical  
> Last verified: 2026-08-25
> Verified against: `FlorisImeService.kt`, `KeyboardManager.kt`, `VoiceManager.kt`,
> `VoiceTranscriptionInputLayout.kt`, `Recorder.kt`, `VoiceDatabase.kt`,
> `VoiceWavRecovery.kt`, `WhisperClient.kt`, `GemmaClient.kt`, `SmolLMClient.kt`,
> application initialization, assets, and dependencies

OmniBoard currently has three distinct AI-related mechanisms. They must not be
described as one architecture.

## Voice transcription

The voice key starts/stops recording through `KeyboardManager`. In the voice
smartbar, the cloud icon opens a persisted provider selector: **OpenAI Cloud**
or **Titan Local**. `WhisperClient` sends that constrained selection, the WAV,
and the `verbose_json` word/segment request to the authenticated Brokentooth
relay on weakling; the APK contains only its endpoint-scoped relay credential.

OpenAI Cloud remains the fallback and receives the allowlisted request fields.
Titan Local is forwarded over Tailscale to the private CrisperWhisper adapter
on Titan. The adapter runs `transcribe_dual()` and returns OpenAI-compatible
`verbose_json`: clean intended text and its `words`/`segments`, plus
`verbatim_text` and `verbatim_words` extensions. The keyboard stores both
transcripts and writes the full response to the Schema 2 sidecar, so filler
words and their timestamps survive in the corpus. The persisted Cleaned versus
Verbatim preference controls foreground auto-insertion. Either version can be
copied or explicitly inserted from Voice Inbox.

Each WAV receives a `VoiceTake` row in the local Room `voice_takes.db` as soon
as capture starts. The row progresses through Recording, Saved, Transcribing,
Ready, or Failed independently of the Compose view. Pending retries retain the
provider selected for the original failed take and run sequentially so recovery
cannot fan out into many simultaneous Titan requests.

Voice capture is owned by the application-scoped `KeyboardManager`, not by a
Compose view. A non-finishing input-view restart can therefore reattach to the
same recording/transcription state after rotation. If the input session,
window, or IME service actually goes away, the lifecycle callback synchronously
finalizes the WAV before teardown and registers it in the durable pending
transcription queue before any network request. That background result is saved
to Voice Inbox and never inserted into a later, unrelated editor. The recorder
also refuses a second `start()` while live, so a UI-state mismatch cannot
replace the active file or writer-thread handles.

On startup, existing Schema 2 sidecars are imported into Voice Inbox and
untranscribed WAVs in `Recordings/Whisper_Vault` are queued for Titan. A WAV
whose recorder placeholder header is still all zeroes can be repaired from its
intact PCM length; recovery rewrites only the first 44 bytes and refuses unknown
nonzero headers. A legacy take which remains larger than the relay limit after
16 kHz downsampling is divided at quiet PCM windows; chunk timestamps are
shifted back to the original timeline and the full responses remain in the
sidecar. Successful takes enter the existing Titan archive queue. Phone
audio is retained until Titan returns the matching checksum and the existing
24-hour grace period expires; Voice Inbox does not manually delete recordings.

## ONNX autocorrect scorer

The packaged `autocorrect_v1.int8.onnx` model runs in-process through ONNX
Runtime. It evaluates correction candidates and can gate automatic commits. It
does not generate prose and does not use the Gemma sidecar.

See `docs/autocorrect/neural-scorer.md`.

## Gemma sidecar actions

`GemmaClient` posts explicit reply, rewrite, or continuation prompts to a local
loopback HTTP service at `127.0.0.1:8081`. The persona is loaded from
`assets/ime/nlp/gemma_persona.txt`. `KeyboardManager` invokes this path for the
AI-generation action.

The client and dispatch path exist. End-to-end sidecar availability and current
device usage still require live verification.

## SmolLM experiment

`SmolLMClient` targets a loopback service at port 8080 and contains sentence
scoring/reranking logic. The call from `LatinLanguageProvider` is commented out,
so this is not part of live candidate ranking.

## Historical MediaPipe design

Older documents describe an on-device Gemma `.task` file and MediaPipe GenAI.
The current application dependency set does not contain that integration and
the referenced bridge class is absent. Preserve it only as architecture history.
