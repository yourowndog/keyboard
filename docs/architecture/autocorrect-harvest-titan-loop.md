# Autocorrect + Usage Harvest + Titan Learning Loop — Current State Map

> **Status**: Canonical snapshot  
> **Last verified**: 2026-09-29  
> **Verified against**: live source paths, docs, tests, and tooling listed below

---

## 1. End-to-End Data Flow (one diagram)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        TYPED / SWIPED INPUT                                  │
│  EditorContext → NlpManager.suggest() → LatinLanguageProvider               │
│       │                    │                        │                         │
│       ▼                    ▼                        ▼                         │
│  SymSpellManager.findCandidates/  NgramSuggestionEngine.rank()               │
│  findPrefixCandidates()        (CandidateScorer penalties)                   │
│       │                    │                        │                         │
│       └────────────────────┴────────────────────┘                          │
│                           │                                                │
│                           ▼                                                │
│              ┌────────────────────────┐                                   │
│              │  NeuralScorer (ONNX)   │ ◄── optional, shadow by default   │
│              │  scoreCandidates()     │     (pref: useNeuralScorer=false) │
│              └────────────────────────┘                                   │
│                           │                                                │
│                           ▼                                                │
│              ┌────────────────────────┐                                   │
│              │  CorrectionDecision    │ ─── CASING_FAST_PATH,             │
│              │  + CommitPolicy Gate   │     LICENSED_CONTRACTION_...,     │
│              └────────────────────────┘     ENGINE_UNAVAILABLE bypass     │
│                           │                                                │
│                           ▼                                                │
│              ┌────────────────────────┐                                   │
│              │  SUGGESTIONS_SHOWN     │  (candidates + confidences)       │
│              │  event → HarvestMgr    │                                   │
│              └────────────────────────┘                                   │
│                           │                                                │
│              ┌──────────┴──────────┐                                       │
│              ▼                     ▼                                       │
│    USER ACCEPTS                USER REJECTS / REVERTS                      │
│    (continues typing)          (backspace)                                 │
│              │                     │                                       │
│              ▼                     ▼                                       │
│    logAccepted()             logRejected() / logIntent()                  │
│    AUTO_APPLIED              REVERTED (undoes pointer)                    │
│              │                     │                                       │
│              └──────────┬──────────┘                                       │
│                         ▼                                                  │
│              ┌────────────────────────┐                                   │
│              │   HarvestV4 WORD_SLOT  │ ◄── single record per word        │
│              │   (keys, offers, edits,│     position: touch, edits,       │
│              │    outcome, shadow,    │     suggestions, reachability)    │
│              │    signals)            │                                   │
│              └────────────────────────┘                                   │
│                         │                                                  │
│            ┌────────────┴────────────┐                                    │
│            ▼                         ▼                                    │
│   usage_harvest.md            usage_harvest.jsonl                         │
│   (human-readable)            (machine v3 + v4 dual-write)                │
│            │                         │                                    │
│            └────────────┬────────────┘                                    │
│                         ▼                                                  │
│          ┌──────────────────────────┐                                     │
│          │  tools/harvesting/       │                                     │
│          │  snapshot_device.py --adb│  → data/harvest/inbox/<ts>/        │
│          └──────────────────────────┘                                     │
│                         │                                                  │
│                         ▼                                                  │
│          ┌──────────────────────────┐                                     │
│          │  tools/harvesting/       │                                     │
│          │  migrate_harvest_v4.py   │  → data/harvest/derived/           │
│          │  (v3→v4 projection,      │     word_slots.v4.jsonl +          │
│          │   dedup by slot key)     │     .sessions.jsonl                │
│          └──────────────────────────┘                                     │
│                         │                                                  │
│                         ▼                                                  │
│          ┌──────────────────────────┐                                     │
│          │  training/extract.py     │                                     │
│          │  (mixed v3/v4 →          │  → training/data/                  │
│          │   clean_corpus.txt,      │     clean_corpus.txt               │
│          │   eval_pairs.jsonl,      │     eval_pairs.jsonl (EVAL ONLY)   │
│          │   eval_negatives.jsonl,  │     eval_negatives.jsonl           │
│          │   trace_samples.jsonl)   │     trace_samples.jsonl            │
│          └──────────────────────────┘                                     │
│                         │                                                  │
│                         ▼                                                  │
│          ┌──────────────────────────┐                                     │
│          │  Titan (corpus archive)  │ ◄── ArchiveClient uploads          │
│          │  - voice WAV + sidecar   │     finished takes                 │
│          │  - harvest-derived       │     ArchiveQueue waits for         │
│          │    training inputs       │     SHA-256 match before delete    │
│          └──────────────────────────┘                                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Exact Source Paths (verified)

### Core NLP / Autocorrect
| Component | Path |
|-----------|------|
| IME entry & lifecycle hooks | `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt` |
| NLP orchestration | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt` |
| Latin provider (main path) | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt` |
| SymSpell retrieval | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt` |
| Ngram engine + CandidateScorer | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NgramSuggestionEngine.kt` |
| Neural ONNX scorer | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NeuralScorer.kt` |
| Shared decision logic | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/CorrectionDecision.kt` |
| Commit policy (gate) | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/CommitPolicy.kt` |
| Shortcut / fallback logic | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/shared/ShortcutCorrection.kt`, `FallbackCorrection.kt` |
| Personal preferences | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/PersonalPreferences.kt` |

### Harvest / Telemetry
| Component | Path |
|-----------|------|
| Markdown + v3 JSONL | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/HarvestManager.kt` |
| Structured JSONL writer | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/HarvestJsonl.kt` |
| v4 Word-slot state machine | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/HarvestV4.kt` |
| Touch / edit / offer events | `HarvestTouch`, `HarvestRoute` enums in `HarvestV4.kt` |

### Training / Extraction
| Script | Purpose |
|--------|---------|
| `tools/harvesting/snapshot_device.py --adb` | Pull raw harvest from device → `data/harvest/inbox/<ts>/` |
| `tools/harvesting/migrate_harvest_v4.py` | Convert mixed v3/v4 JSONL → canonical v4 `word_slots.v4.jsonl` + sessions |
| `training/extract.py` | Harvest (md + jsonl) → `training/data/{clean_corpus.txt, eval_pairs.jsonl, eval_negatives.jsonl, trace_samples.jsonl}` |
| `tools/voice_corpus/prepare_voxcpm2.py` | Build immutable VoxCPM2 recipe from verified voice segments (Titan upload path) |

### Voice / Titan Archive
| Component | Path |
|-----------|------|
| Archive upload (Titan direct) | `app/src/main/kotlin/dev/patrickgold/florisboard/net/ArchiveClient.kt` |
| Archive queue & disk reclamation | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/ArchiveQueue.kt` |
| Voice provider selection | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceManager.kt` (enum `TITAN_LOCAL`) |
| Voice Inbox (Room) | `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceDatabase.kt` |

---

## 3. Event Taxonomy (what is actually logged)

### v3 / Markdown (HarvestManager.append)
| Category | Trigger | Key Fields |
|----------|---------|------------|
| `ACCEPTED` | Typed word auto-corrected, user continues | `typed → correctedTo`, `prevWord`, `trace`, `candidates`, `slot` |
| `REJECTED` | User backspaced to revert autocorrect | `typed ← rejectedCorrection`, `prevWord`, `undoes` (v3 event ID) |
| `NEW_WORD` | Word not in dictionary committed | `word`, `prevWord`, `slot` |
| `MANUAL_FIX` | User backspaced & retyped differently | `before → after`, `trace`, `slot` |
| `INSISTED` | User tapped typed word from smartbar | `word`, `prevWord`, `shadow`, `slot` |
| `PICKED` | User tapped a suggestion | `typed → picked`, `shadow` |
| `INTENT` | Typed → auto-corrected → reverted → retyped | `typed`, `rejected`, `intent` |
| `NO_SUGGESTION` | No candidates offered | `typed`, `prevWord` |
| `SUGGESTIONS_SHOWN` | Candidate list captured at decision time | `typed`, `candidates[]`, `slot` |
| `MULTI_ATTEMPT` | Multiple tries for same position | `attempts[]`, `final`, `prevWord` |
| `IGNORED_SUGGESTIONS` | All offered suggestions ignored | `typed`, `offered[]`, `finalTyped` |
| `BACKSPACE_STORM` | Many backspaces on one word | `word`, `backspaceCount`, `final` |
| `NEURAL_SHADOW` | Counterfactual: what neural would do | `typed`, `ngramTop`, `neuralTop`, `margin`, `wouldFire`, `agrees`, `ranked[]`, `policyBlockers`, `slot` |
| `SESSION_TEXT` | Flush sentence buffer (typing or voice) | `text`, `src` (TYPING/VOICE) |

### v4 WORD_SLOT (HarvestV4.emitSlot)
Single record per word-position containing:
- `keys[]` — every touch: character, key, normalized coords (xn,yn), deltas, duration, source
- `offers[]` — each suggestion panel shown: prefix, engine, candidates+confidences, shown count
- `edits[]` — backspaces, RETURN_EDIT, etc. with left-context
- `outcome` — final word, route (TYPED_THROUGH, BAR_PICK, AUTO_APPLIED, GLIDE, VOICE, PASTE, ABANDONED), barIndex, autoFrom, reverted flags, reachability
- `shadow` — heuristicTop, neuralTop, neuralMargin, wouldFire, agrees, policyBlockers, reachable
- `signals` — attempts, totalBksp, maxBkspBurst, struggle, capitulation, unreachable, dwellMs, partial
- `ctx` — app, fieldId, inputType, inputVariation, flags, imeOptions, inputSession, register
- `layout` — fingerprint (fp, alphaW/H, aspect, rows, dpi, orientation, oneHanded, numberRow)
- `voice` — audioRef, transcriptKind, asrConf, segIndex (if voice segment)

---

## 4. Working / Broken Boundaries

| Boundary | Status | Evidence / Notes |
|----------|--------|------------------|
| **Typed input → suggestions** | ✅ Working | `LatinLanguageProvider.suggest()` → `NlpManager.addLogEvent()` → `HarvestManager.logSuggestionsShown()` |
| **Neural shadow logging** | ✅ Working | `pref: neuralScorerShadow=true` (default); `logNeuralShadow()` called from provider & NlpManager |
| **Live neural gating** | ⚠️ Disabled by default | `pref: useNeuralScorer=false`; enabling requires threshold calibration (see neural-scorer.md) |
| **Auto-commit (AUTO_APPLIED)** | ✅ Working | `CorrectionDecision` + `CommitPolicy`; guarded by valid-word immunity, protected vocab, anti-correction |
| **Revert detection (REVERTED)** | ✅ Working | `HarvestJsonl.rememberApplied()` + `findUndoId()`; `HarvestV4.revert()` marks outcome |
| **Manual correction capture** | ✅ Working | `logManualCorrection()` → `HarvestV4.returnEdit()` + commit; `MANUAL_EDIT` in v3 |
| **Smartbar pick (BAR_PICK)** | ✅ Working | `logInsisted()` / `logPicked()` → `HarvestV4.commit(route=BAR_PICK)` |
| **Phrase prediction (2nd row)** | ✅ Working | `NlpManager` tiered `PhraseTable` + `BigramTable`; never auto-commits |
| **v3→v4 dual-write & dedup** | ✅ Working | `migrate_harvest_v4.py` suppresses v3 rows when `slot` key present in v4 |
| **v3 word-count preservation** | ✅ Enforced | `migrate_harvest_v4.py` raises `RuntimeError` if counts don't balance |
| **Training extraction (v3/v4 mixed)** | ✅ Working | `training/extract.py` reads both; suppresses joined v3 mirrors via `slot` key |
| **Password-field guard** | ✅ Implemented | `AppContext.isHarvestBlocked` checked in HarvestManager, HarvestJsonl, HarvestV4 |
| **Voice → Titan Local transcription** | ✅ Working | `VoiceManager.TITAN_LOCAL` → `WhisperClient` → Tailscale → CrisperWhisper on Titan |
| **Voice archive → Titan** | ✅ Working | `ArchiveClient` uploads directly to Titan; `ArchiveQueue` waits for SHA-256 match |
| **Voice Inbox retry/recovery** | ✅ Working | `VoiceCaptureLifecyclePolicy` preserves state across IME restarts |
| **SmolLM reranking** | ❌ Disabled | Commented out in `LatinLanguageProvider` (blocking HTTP calls) |
| **Gemma sidecar actions** | ⚠️ Unverified | `GemmaClient` exists; end-to-end availability not live-verified |

---

## 5. Commands & Verification Evidence

### Capture device harvest
```bash
python3 tools/harvesting/snapshot_device.py --adb
# → data/harvest/inbox/YYYYMMDD-HHMMSS/usage_harvest.{md,jsonl}
```

### Convert to canonical v4 (idempotent)
```bash
python3 tools/harvesting/migrate_harvest_v4.py \
  data/harvest/raw/usage_harvest.jsonl \
  data/harvest/derived/word_slots.v4.jsonl
# → also writes word_slots.v4.sessions.jsonl
# Verification: prints slot counts, v3 preservation balance
```

### Extract training inputs (mixed v3/v4 safe)
```bash
python3 training/extract.py
# → training/data/{clean_corpus.txt, eval_pairs.jsonl, eval_negatives.jsonl, trace_samples.jsonl}
# Verification: prints corpus lines, pair/negative/trace counts, filter stats
```

### Voice corpus recipe (Titan training)
```bash
python3 tools/voice_corpus/prepare_voxcpm2.py --apply
# → /home/sam/datasets/sam-voice/derived/voxcpm2-v1-curated/
# Verification: prints summary.json with segment counts, hours, rejection reasons
```

### Unit tests
```bash
# Harvest v4 migration
./gradlew :app:test --tests "*HarvestV4StateTest*"
# Training extraction v4 path
python3 -m pytest training/tests/test_extract_v4.py -v
```

---

## 6. Next Concrete Implementation Fixes (priority order)

| # | Fix | Owner hint | Source references |
|---|-----|------------|-------------------|
| 1 | **Enable live neural gating with calibrated threshold** | NLP lead | `docs/autocorrect/neural-scorer.md` validation requirements 1-7; `prefs.suggestion.neuralThreshold` default 0.30 |
| 2 | **Fix SmolLM async reranking** | NLP / infra | `LatinLanguageProvider.kt:363-371` — currently commented out; needs async pipeline to avoid multi-second UI lag |
| 3 | **Voice Inbox → Titan archive backfill verification** | Voice lead | `VoiceManager.kt` legacy cancelled takes; `ArchiveQueue` only uploads on SHA-256 match |
| 4 | **Add register-aware classification to extract.py** | Data lead | `docs/autocorrect/harvesting.md:46-47` — Termux/URLs/code must not be treated as prose |
| 5 | **Proposal-based review tooling** | Tooling | `docs/autocorrect/harvesting.md:82-91` — current scripts are ad-hoc; need "propose → approve → apply" workflow |
| 6 | **Consolidate dual harvest paths (v3/v4) in docs** | Docs | `docs/autocorrect/harvesting.md` references old `tools/harvesting/README.md` which no longer exists |
| 7 | **Gemma sidecar live verification** | AI integrations | `docs/architecture/voice-ai.md:104-105` — mark verified or remove if unused |
| 8 | **Spatial coordinate hardening regression test** | Layout/Keyboard | `docs/development/spatial-coordinate-hardening-prompt.md` — ensure `HarvestV4.touch()` coords survive layout switches |

---

## 7. Key Config Flags (device prefs)

| Preference | Default | Effect |
|------------|---------|--------|
| `suggestion.useNeuralScorer` | `false` | Enable neural gate for auto-commit |
| `suggestion.neuralScorerShadow` | `true` | Log `NEURAL_SHADOW` counterfactuals |
| `suggestion.neuralThreshold` | `0.30` | Margin `P(top) - P(typed)` to fire |
| `suggestion.enabled` | `true` | Master suggestion toggle |
| `smartbar.enabled` | `true` | Show suggestion bar / harvest BAR_PICK |
| `clipboard.suggestionEnabled` | `true` | Clipboard suggestions in smartbar |
| `emoji.suggestionEnabled` | `true` | Emoji suggestions |

---

## 8. Historical Context (do not resurrect)

- **MediaPipe / on-device Gemma** — removed; `docs/architecture/voice-ai.md:113-117`
- **Legacy `frequency_dictionary_en.txt` + `frequency_bigram_en.txt`** — replaced by unified TSV + BigramTable
- **Old harvest board** — this document supersedes; do not duplicate
- **Forensic record** — `docs/archive/autocorrect-refactor-2026/autocorrect-regression-forensic-record.md` frozen at its revision; unresolved device-session questions remain unresolved

---

## 9. Deliverables from this Task

This file: `docs/architecture/autocorrect-harvest-titan-loop.md` — the single current-state artifact requested.

No code changes were made. All paths, commands, and behaviors verified against live source.