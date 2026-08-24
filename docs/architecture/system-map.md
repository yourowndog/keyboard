# OmniBoard System Map

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `settings.gradle.kts`, application startup, IME service,
> keyboard, editor, NLP, theme, media, clipboard, and extension packages

OmniBoard is a personal Android input method derived from FlorisBoard. The
package and many class names retain the FlorisBoard name. The active product,
workflow, layouts, themes, language data, and AI integrations are OmniBoard.

## Gradle modules

| Module | Responsibility |
|---|---|
| `:app` | Android application, IME service, settings, keyboard runtime, editor, NLP, voice, media, clipboard, extensions |
| `:lib:android` | Android platform wrappers and helpers |
| `:lib:color` | Color models and generated schemes |
| `:lib:compose` | Shared Compose components and modifiers |
| `:lib:kotlin` | Platform-independent Kotlin utilities |
| `:lib:native` | JNI/native bridge and Rust build |
| `:lib:snygg` | Snygg stylesheet parser, schema, values, and themed UI primitives |

`benchmark/`, `keyboardtools/`, `training/`, and root analysis scripts are not
Gradle application modules. Some are development tools or shelved research and
will be classified separately.

## Runtime spine

```text
FlorisApplication
  -> constructs shared managers

FlorisImeService
  -> owns the Android InputMethodService lifecycle
  -> delegates IME visibility to Android; Back hides the window and a focused editor may request it again
  -> initializes keyboard, editor, NLP, themes, harvesting, and voice support

FlorisImeUi / Compose layouts
  -> render smartbar, keyboard, clipboard, media, popups, and panels

KeyboardManager
  -> selects and computes the active keyboard
  -> evaluates key data
  -> dispatches internal key codes and mode changes

EditorInstance
  -> owns composing text, commits, selection, autocorrect commits and reverts

NlpManager
  -> coordinates suggestion providers, phrase candidates, and feedback
```

## Subsystem map

| Concern | Primary implementation | Supporting data/UI |
|---|---|---|
| Layout selection and merging | `ime/keyboard/LayoutManager.kt` | subtype and keyboard extension JSON |
| Key data and state selection | `ime/keyboard/KeyData.kt`, `ime/text/keyboard/TextKeyData.kt` | layout JSON and layout packs |
| Geometry and hitboxes | `ime/text/keyboard/TextKey.kt`, `TextKeyboard.kt`, `TextKeyboardLayout.kt` | keyboard preferences and per-key customization |
| Key dispatch | `ime/keyboard/KeyboardManager.kt` | `ime/text/key/KeyCode.kt` |
| Styling | `ime/theme/FlorisImeUi.kt`, `lib/snygg` | packaged theme stylesheets |
| Autocorrect retrieval | `ime/nlp/SymSpellManager.kt`, `DictionaryRepository.kt` | dictionary and bigram assets |
| Heuristic ranking | `ime/nlp/SuggestionEngine.kt`, `shared/CandidateScorer.kt` | bigrams and personal vetoes |
| Neural decision gate | `ime/nlp/NeuralScorer.kt`, `latin/LatinLanguageProvider.kt` | packaged ONNX model and suggestion preferences |
| Phrase prediction | `ime/nlp/NlpManager.kt`, `BigramTable.kt`, `PhraseTable.kt` | phrase row in `Smartbar.kt` |
| Feedback collection | `ime/nlp/HarvestManager.kt`, `HarvestJsonl.kt` | editor commit/revert hooks |
| Glide typing | `ime/text/gestures/GlideTypingManager.kt` and classifiers | keyboard geometry and NLP word scoring |
| Voice transcription | `ime/keyboard/KeyboardManager.kt`, `net/WhisperClient.kt` | recorder and smartbar voice UI |
| Explicit local-LLM actions | `ime/nlp/GemmaClient.kt` | `AI_GENERATE` dispatch and persona asset |
| Clipboard | `ime/clipboard` | Room databases and themed clipboard UI |
| Emoji/media | `ime/media` | emoji assets and themed media UI |
| Extensions | extension manager packages | `.flex` archives and bundled extension assets |

## Active, experimental, and shelved distinctions

- The ONNX autocorrect scorer is active code. Shadow mode defaults on; live
  neural gating defaults off.
- Phrase prediction and its second smartbar row are active code, subject to the
  corresponding UI preference.
- Glide typing is active. The newer trained/precomputed swipe-model effort is
  incomplete; runtime tolerates the expected binary asset being absent.
- `GemmaClient` supports explicit actions through a loopback service on port
  8081. It is not the autocorrect neural scorer.
- `SmolLMClient` remains in source, but its candidate-reranking call is commented
  out and is not part of the live suggestion path.
- Earlier MediaPipe/Gemma `.task` architecture documents are historical.

## Repository navigation rule

Begin with this map, then read the guide for the subsystem being changed. Do
not load root journals, conversation transcripts, generated harvest reports,
or shelved training material merely to orient to the current code.
