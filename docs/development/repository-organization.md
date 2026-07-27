# Repository Organization

> Status: Canonical  
> Last verified: 2026-07-15

This doc owns **directory ownership**. For end-to-end **data flow** — every pipeline, script,
its purpose, inputs, and where output goes — see the companion canonical map:
[`data-and-scripts-map.md`](./data-and-scripts-map.md).

The repository root is reserved for project entry points, Gradle configuration,
and first-class source/data areas. IDE, build, and agent-local directories may
exist in a working copy while remaining ignored.

## Ownership

```text
app/          Android application, IME runtime, and packaged assets
benchmark/    Android benchmark module
data/         retained working data, especially canonical harvest corpora
dict_sources/ large dictionary build inputs; never loaded at runtime
docs/         verified current documentation and labeled history
external/     vendored or external project inputs
fastlane/     Android distribution metadata inherited from the project
gradle/       Gradle wrapper and version configuration
lib/          shared Android, Kotlin, Compose, native, and Snygg modules
libnative/    native dummy crate required by lib/native's Cargo graph
research/     shelved or exploratory work, not active production workflows
tools/        maintained harvesting and dictionary maintenance commands
training/     active offline ONNX autocorrect training pipeline
utils/        older upstream dictionary/config generators
```

## Canonical boundaries

- Runtime dictionaries live in `app/src/main/assets/ime/dict/`; their source
  corpora do not.
- Raw harvest corpora, reports, and derivatives live under `data/harvest/`.
- Device pulls are immutable timestamped inbox snapshots and are ignored.
- Harvest tools capture or derive evidence; dictionary maintenance tools may
  mutate packaged assets and require diff review.
- Built-in themes live in application assets. Old `.flex` builds and extracts
  are research artifacts, not sources of truth.
- Active glide typing is runtime code. The FUTO/precomputed training effort is
  shelved under `research/swipe-training/`.
- Historical docs explain evolution but never override canonical docs or code.

## Cleanup rule

Do not solve clutter by creating a generic archive directory. Before removing a
file, preserve any still-true lesson in the relevant canonical document, then
delete the superseded source. The pre-reorganization commit archive recorded in
the project handoff remains the recovery point for this migration.
