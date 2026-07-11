# OmniBoard

OmniBoard is a personal Android keyboard built from FlorisBoard and tailored
for a Termux-heavy, highly customized mobile workflow. It combines programmable
terminal-oriented keys, custom layouts and geometry, Snygg themes, voice
transcription, and a personal autocorrect and prediction stack.

This repository is a working personal system, not a general-purpose product
release. FlorisBoard package and class names remain throughout the codebase.

## Current capabilities

- Custom character, modifier, number, developer, symbol, and terminal-oriented
  keyboard layouts.
- Layout Builder packs with per-key units, spacers, and internal key aliases.
- Programmable Ctrl, Escape, Tab, navigation, Tmux-prefix, voice, and AI-action
  keys.
- Independent alpha/modifier sizing, row height and gap controls, per-key
  customization, and separate visual/touch bounds.
- Snygg-based themes, including the built-in LCARS family.
- SymSpell candidate retrieval with personal dictionaries, bigram context,
  phrase prediction, and heuristic scoring.
- An ONNX neural scorer that can run in shadow mode or gate automatic
  corrections.
- Configurable directional gestures; glide-typing code is present but shelved.
- Whisper voice transcription; explicit loopback local-LLM actions are shelved.
- Markdown and JSONL usage harvesting for deliberate, review-driven tuning.

## Documentation

Start with the [documentation index](docs/README.md) and
[system map](docs/architecture/system-map.md).

High-detail areas:

- [Keyboard construction and customization](docs/keyboard/README.md)
- [Autocorrect and neural scoring](docs/autocorrect/README.md)
- [Snygg theming](docs/theming/README.md)
- [Building locally or on Beksinski](docs/development/building.md)

Superseded handoffs and contradictory manuals have been removed after their
durable lessons were incorporated into the canonical documentation.

## Build and test

The normal workflow is a local Gradle build or a branch push to the Beksinski
build factory. GitHub Actions is not the active build system.

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon assembleDebug
```

Machine-local Android and voice configuration belongs in `local.properties`,
which is ignored by Git.

## Repository layout

```text
app/        Android application and IME runtime
lib/        shared Android, Kotlin, Compose, color, native, and Snygg libraries
docs/       verified current documentation and historical context
gradle/     dependency and tool version catalogs
training/   active neural scorer training pipeline
tools/      maintained supporting tools
data/       harvest corpora, generated reports, and derivatives
research/   shelved swipe work and historical theme artifacts
```

## Project lineage and license

OmniBoard is derived from the open-source
[FlorisBoard](https://github.com/florisboard/florisboard) project. Package names
and significant portions of the application and libraries retain that lineage.

The repository is licensed under the Apache License 2.0; see [LICENSE](LICENSE).
Copyright notices in source files and the project history remain applicable.
