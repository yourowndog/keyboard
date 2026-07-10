# handoff.md — OmniBoard Personal Autocorrect NN

Sam's Android keyboard (FlorisBoard fork, `~/projects/keyboard`). Goal: replace the
heuristic autocorrect scorer with a tiny personal neural net trained on Sam's own
typing/voice data. Single user, all data local, zero recording limits. Machines:
**sleeper** = this workhorse (repo, adb-connected phone), **titan** = training box.

## Latest implementation update — 2026-07-09

Neural v1 is now integrated on sleeper and installed on the phone in **shadow mode**.
Current keyboard behavior remains controlled by the existing dictionary/ngram path;
the neural model logs counterfactual decisions but does not own the suggestion row
or autocorrect by default.

Completed:
- Repaired RTK global hook. `rtk verify` passes (`78/78 tests passed`).
- Added ONNX Runtime Android dependency:
  `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")`.
- Added model asset:
  `app/src/main/assets/ime/nn/autocorrect_v1.int8.onnx`.
- Added `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NeuralScorer.kt`.
  It loads the int8 ONNX model, creates the feature tensors, runs inference with
  one intra/inter-op thread, applies softmax, and returns a thresholded decision.
- Added hidden prefs in `AppPrefs.kt`:
  - `suggestion__neural_scorer_shadow = true`
  - `suggestion__use_neural_scorer = false`
  - `suggestion__neural_threshold = 0.30f`
- Added featurization tests in `NeuralScorerTest.kt`, including the FNV bucket
  vectors from `training/feature_spec.md`.
- Fixed stale NLP unit-test imports/constructor usage enough for focused tests.
- Built and installed debug APK to the connected phone:
  `dev.patrickgold.florisboard.debug`, version `0.5.0-debug+16a51826`.

Verification already done:
```bash
rtk ./gradlew --no-daemon :app:testDebugUnitTest \
  --tests dev.patrickgold.florisboard.ime.nlp.NeuralScorerTest \
  --tests dev.patrickgold.florisboard.ime.nlp.SuggestionEngineTest \
  --tests dev.patrickgold.florisboard.ime.nlp.SymSpellTest \
  :app:assembleDebug

rtk adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Both build/tests and install succeeded. APK inspection confirmed the model asset
and ONNX native libraries are packaged.

Important product finding from first on-device shadow logs:
- The initial wiring accidentally fed neural both typo-correction candidates and
  prefix/autocomplete candidates. Shadow logs showed bad product behavior such as
  partial words being scored toward completions (`what` → `whatever's`,
  `is` → `isolated`, single-letter prefixes → long words).
- This was patched before the final install. Neural now receives **edit-distance
  correction candidates only**, not prefix candidates, and ignores typed strings
  shorter than 3 chars. The next-word prediction row and prefix/autocomplete row
  should remain ngram/dictionary-driven.
- Because of this finding, do not enable live neural autocorrect yet. Continue
  collecting shadow logs first.

Current neural behavior:
- Shadow mode logs to Logcat tag `NeuralShadow` (real-time observation).
- Shadow mode also persists `NEURAL_SHADOW` events to `usage_harvest.jsonl` via
  the editor-aware `NlpManager` → `HarvestManager.logNeuralShadow()` path.
  This route honors password-field filtering automatically.
- Each `NEURAL_SHADOW` JSONL event contains: `typed`, `prev`, `ngramTop`,
  `neuralTop`, `typedP`, `topP`, `margin`, `wouldFire`, `agrees`, `ranked`.
- Existing ngram ranking remains the visible suggestion source.
- `suggestion__use_neural_scorer` defaults false. If enabled later, the current
  code still keeps ngram ranking visible and only lets neural gate auto-commit
  safety. Treat this as experimental.

Dev friction to remember:
- GitHub push protection blocked this branch on historical secrets in `usage_harvest.md`, so the branch had to be rewritten several times. Any future push from this branch will need `--force-with-lease`.
- `local.properties` must stay local and ignored. A Gradle build can still succeed without it because the OpenAI/Whisper key is optional at build time, but voice/OpenAI calls will fail at runtime until the right key is restored.
- If the Whisper/OpenAI path is broken on a machine, copy the already-existing local `local.properties` from the other machine in this workspace boundary and verify by runtime behavior, not by committing or printing the value.

How to move forward:
1. Use the keyboard normally for a day with shadow mode on. Watch logs when doing
   targeted tests:
   ```bash
   rtk adb logcat -c
   rtk adb logcat -s NeuralScorer:I NeuralShadow:I '*:S'
   ```
   Good test words: real mistypes, apostrophe-less forms (`dont`, `im`, `wifes`),
   and normal words that should be left alone.
2. Judge neural by shadow behavior, not by current typing feel. Current typing
   improvements mostly came from dictionary rebuild + Dec fixes, not v1.
3. Look for:
   - `agrees=true` on corrections current system already gets right.
   - `wouldFire=false` on valid words that should be left alone.
   - Any high-margin `wouldFire=true` where the neural top is obviously wrong.
4. Next implementation task should be structured shadow harvesting with privacy:
   attach the neural counterfactual to the existing candidate snapshot or
   `SUGGESTIONS_SHOWN`/commit-time JSONL events from an editor-aware layer.
5. After enough shadow data, evaluate v1 against the current rebuilt-dictionary
   baseline. Only then consider exposing a settings toggle/slider or enabling
   live neural gating.

## Current state (done, don't revisit)

- **Pipeline:** SymSpell retrieves candidates → `NgramSuggestionEngine.rank()` scores
  via `shared/CandidateScorer` (heuristic penalties) → `PersonalPreferences`
  anti-correction veto → commit. The NN replaces CandidateScorer's job only.
- **Logging v3 is live on device.** `HarvestJsonl.kt` appends structured events to
  `/sdcard/Documents/usage_harvest.jsonl`: monotonic `id`, `sess` (rotates per app),
  `type` ∈ {`WORD_COMMITTED` (has `trace` = literal keys incl. `⌫`), `SESSION_TEXT`,
  `AUTO_APPLIED` (`typed`, `applied`, `prev`, `prev2`, `auto`, full `candidates`
  [[word, conf]…], `trace`), `REVERTED` (`undoes` → AUTO_APPLIED id), `MANUAL_EDIT`,
  `SUGGESTIONS_SHOWN` (per keystroke), `INSISTED`, `NEW_WORD`, …}. Old markdown log
  (`usage_harvest.md`, 64.9k lines, Dec 2025–Jul 2026) still dual-written.
- **Dictionary/bigrams/phrases rebuilt** (`build_dictionary.py`, installed): AOSP
  base + personal overlay → `unified_dictionary.tsv` (161,510 words; runtime reads
  `ln(freq+1)`); `final_mobile_bigrams.tsv` (24,483) and `personal_phrases.tsv`
  (7,457) are 100% corpus-derived.
- **First task: verify Sam's recent on-device test typing.** `adb pull
  /sdcard/Documents/usage_harvest.jsonl`; events `id > 87` postdate the last fixes.
  Check `prev`/`prev2` are real context (not the corrected word itself), `undoes`
  links resolve, traces sane, and ask Sam how the new dictionary feels.
- Caveats: `MANUAL_EDIT` emits false pairs when Sam edits across words — filter by
  edit distance in the extractor, don't fix on device. Focused NLP unit tests now
  pass, but full-suite health was not rechecked after the shadow install. Build:
  `rtk ./gradlew --no-daemon :app:assembleDebug` + `rtk adb install -r`.
  RTK hook rewrites bash; `rtk proxy` bypasses. Apostrophe key missing from layout
  (Sam will fix separately) — expect apostrophe-less typed data.

## Data inventory (for steps 3–4)

- ~270k clean tokens (~152k voice/Whisper = error-free; ~118k typed). Triage
  classifier (clean/code/url/concatenated) already exists in `build_dictionary.py::classify_session` — reuse it.
- Gold labels: ~1,254 real ACCEPTED pairs (typed→corrected), ~588 false-positive
  signals (REJECTED/capitulations), ~241 leave-alone (INSISTED/NEW_WORD).
  **These are EVAL ONLY. Never train on them.**
- Growing: jsonl adds traces + candidate lists + linked reverts daily.

## STEP 3 — Extractor, noise model, synthetic training set (build on sleeper)

Create `training/` in the repo. Python 3, no heavy deps except `symspellpy` (mirror
of the Kotlin SymSpellKt retrieval) and stdlib. Four scripts, strict file contracts:

### 3.1 `training/extract.py`
Inputs: `usage_harvest.md` + pulled `usage_harvest.jsonl`.
Outputs (all under `training/data/`):
- `clean_corpus.txt` — one session line per row, triage=clean only, voice + typed
  flagged (`V\t…` / `T\t…`). Source for contexts and synthetic corruption.
- `eval_pairs.jsonl` — real correction pairs: `{typed, intended, prev, prev2, src}`.
  From md ACCEPTED lines + jsonl AUTO_APPLIED **not** followed by a linked REVERTED.
  An AUTO_APPLIED whose id appears in any `undoes` = the correction was WRONG →
  goes to `eval_negatives.jsonl` instead (intended = typed).
- `eval_negatives.jsonl` — leave-alone truth: INSISTED, NEW_WORD, REJECTED-validated,
  and reverted AUTO_APPLIEDs. `{typed, prev, prev2}` where firing = failure.
- `trace_samples.jsonl` — from WORD_COMMITTED/AUTO_APPLIED with `trace ≠ word`:
  `{trace, final}`. Filter MANUAL_EDIT-derived pairs with
  `levenshtein(before, after) > max(3, len/2)` — those are edits, not typos.

### 3.2 `training/noise_model.py`
Fit Sam's personal error distribution. Two sources, merged:
1. **Char-align eval_pairs** (Levenshtein alignment, unit costs) → counts of ops:
   `sub(a→b)`, `del(a)`, `ins(a after b)`, `transpose(ab→ba)`.
2. **Replay traces**: simulate `trace` (`wpr⌫⌫ord`) against `final` (`word`) to
   recover pre-correction string (`wprd`) → align → same op counts. This source is
   the richer one (captures self-fixed typos autocorrect never saw).
Smooth with a QWERTY-adjacency prior (standard layout per Sam): P(sub a→b) ∝
`count(a→b) + α·adj(a,b)`, α≈0.5, adj=1 for physically adjacent keys else 0.05.
Also fit: per-word error rate p_err = (#eval_pairs + #self-fixed traces) / total
committed words (expect ~1–2%); apostrophe-drop probability (dont/im/wifes — Sam's
signature, and the layout forces it); doubled-letter and dropped-space rates.
Output `noise_model.json` (op probability tables + rates). Sanity-print top-20 subs;
they should look like fat-finger neighbors, not random.

### 3.3 `training/synthesize.py`
Walk `clean_corpus.txt` (voice lines preferred; clean typed lines OK). For each word
with prob `p_err`, sample a corruption from `noise_model.json` (1 op usually, 2 ops
with p≈0.15). Emit `train.jsonl` rows shaped EXACTLY like inference:
```
{"typed": "wprd", "prev": "the", "prev2": "typed",
 "cands": [["word",1,13.2,412],["ward",1,11.1,0],["wprd",0,0.0,0], …],
 "label": "word"}
```
- `cands` = SymSpell lookup (symspellpy, max_edit_distance=2, loaded from
  `unified_dictionary.tsv`) each with [term, edit_dist, ln_unigram_freq,
  bigram_count(prev, term)]. **Always include the typed string itself as a candidate**
  — "keep what they typed" must be a class the model can choose.
- ~99% of words are uncorrupted → label = typed itself. Do NOT downsample these
  below ~10:1; restraint is the product. Corrupted rows: label = the original word.
- Inject identity rows for Sam's lingo (`bc`, `rn`, `ya`, `im`, dictionary overlay
  words): typed = word, label = word. Cheap alignment with the runtime veto.
- Split by session/time, not by row (no leakage of near-duplicate lines).
  Target ≥2M rows; it's cheap, regenerate at will.

### 3.4 `training/evaluate.py`
Runs ANY scorer (baseline heuristic or NN) over `eval_pairs` + `eval_negatives`:
- **fix-recall@1**: model's top choice == intended, on real pairs.
- **false-fire rate**: model changes the word, on negatives.
- Report both against the current `CandidateScorer` logic re-implemented in ~40
  lines of Python (edit distance + BIGRAM_WEIGHT=5.0 etc — port from
  `shared/CandidateScorer.kt`) as the baseline to beat. No training run ships
  unless it beats baseline on BOTH axes.

## STEP 4 — The net, the retrain loop, the integration

### 4.1 Model (train on titan, PyTorch)
Task = listwise ranking over the candidate set (typed word included = the abstain
class). Per candidate, encode:
- chars of `typed` and `candidate` via a shared char-embedding (dim 32, vocab =
  a–z + ' + boundary) → 1-layer bi-GRU (hidden 64) each → concat last states;
- scalar features: edit_dist, ln_freq, ln(bigram_count+1), len(typed),
  is_typed_itself flag;
- context: hash-embed `prev`/`prev2` (2×32-dim, 30k hash buckets).
Concat → MLP 256→64→1 score per candidate → softmax over the candidate list →
cross-entropy on the label. ~1–2M params. Char encoders give fat-finger
generalization; the is_typed_itself flag + abstain examples give restraint.
Train: AdamW, lr 1e-3, batch 512 lists, ~3 epochs, minutes on titan. Calibrate a
decision threshold τ on eval (fire only if P(top) − P(typed) > τ); τ ships as a
runtime pref, not baked in.

### 4.2 Export + Android integration
- DONE for v1: exported int8 ONNX model is packaged at
  `app/src/main/assets/ime/nn/autocorrect_v1.int8.onnx`.
- DONE for v1: `nlp/NeuralScorer.kt` loads the model from assets and mirrors the
  Python feature contract for char ids, scalar features, and FNV context buckets.
- DONE for v1 shadow mode: `LatinLanguageProvider` calls the scorer for active
  typed-word correction candidates and logs `NeuralShadow` counterfactuals.
- STILL TODO before live rollout: move neural shadow results into privacy-safe
  JSONL harvesting from an editor-aware layer, evaluate against the current
  rebuilt-dictionary baseline, and only then decide whether live neural gating
  should be user-exposed.
- Do not wire neural into prefix/autocomplete or next-word prediction without a
  separate model/metric. The first device logs proved v1 behaves badly when asked
  to score prefix completions.

### 4.3 Retrain loop (steady state)
1. sleeper: `adb pull` jsonl → `extract.py` → `noise_model.py` → `synthesize.py`
   (one `make data` target).
2. rsync `training/data/` → titan; `train.py` → `autocorrect_vN.onnx` + `metrics.json`.
3. `evaluate.py` gates: beats baseline AND previous vN-1 on fix-recall without
   false-fire regression.
4. Copy model into assets, bump N, `assembleDebug`, `adb install -r`.
Full retrain every time — never fine-tune increments. Weights are disposable;
the extractor is the asset. Cadence: whenever corpus grows ~10% or Sam reports drift.

### Sequencing for the next session
1. ~~Collect and review `NeuralShadow` logs from real typing.~~ **DONE** — persistent
   `NEURAL_SHADOW` JSONL events are now written through the privacy-safe harvest
   path. Use keyboard normally, then:
   ```bash
   cd training && make pull && make shadow
   ```
   or watch live: `rtk adb logcat -s NeuralShadow:I '*:S'`.
2. ~~Add privacy-safe persistent neural shadow logging to JSONL.~~ **DONE** —
   `LatinLanguageProvider` surfaces `NeuralSnapshot` → `NlpManager` consumes it
   and calls `HarvestManager.logNeuralShadow()` → `HarvestJsonl.event()` with
   password guard.
3. Pull harvest data and run `make shadow` to evaluate v1 against the current
   dictionary-fixed baseline on real typing data.
4. If v1 wins on real shadow/eval data, expose a user-facing toggle/threshold UI
   or enable live neural gating for a narrow test.
5. If v1 does not win, retrain on the new corpus/current candidate distribution
   before doing more Android integration work.

To get there: **build the APK with the shadow logging changes and install it.**
```bash
rtk ./gradlew --no-daemon :app:assembleDebug
rtk adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Then use the keyboard for a day. Pull and analyze:
```bash
cd training && make pull && make shadow
```
