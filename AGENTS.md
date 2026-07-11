# OmniBoard Agent Guide

OmniBoard is Sam's personal Android keyboard and daily driver. It is derived
from FlorisBoard, so package and class names frequently retain the FlorisBoard
name. Optimize for reliability and Sam's actual workflow, not hypothetical
distribution requirements.

## Orientation

Read [docs/README.md](docs/README.md), then the relevant subsystem guide:

- Layouts, keys, geometry, popups, or customization:
  [docs/keyboard/README.md](docs/keyboard/README.md)
- Autocorrect, suggestions, dictionaries, phrases, or neural scoring:
  [docs/autocorrect/README.md](docs/autocorrect/README.md)
- Snygg or theme work: [docs/theming/README.md](docs/theming/README.md)
- Voice and AI integrations: [docs/architecture/voice-ai.md](docs/architecture/voice-ai.md)
- Builds and tests: [docs/development/building.md](docs/development/building.md)

Do not infer current behavior from root handoffs, journals, conversation
walkthroughs, generated reports, or documents calling themselves “reality.”
They are being retained temporarily while verified knowledge is extracted.

## Working rules

- Preserve unrelated user changes. The worktree may already be dirty.
- Explain the behavioral reason before a non-trivial code change.
- Verify source claims against live code, tests, schemas, and assets.
- For layout/touch/theme/IME behavior, include device validation when source and
  unit tests cannot establish the result.
- Do not push, invoke the build factory, install an APK, or rewrite harvested
  data unless the task authorizes it.
- Keep plans in `ROADMAP.md`; keep current behavior in `docs/`.
- Update the closest canonical document when behavior changes. Avoid creating a
  new root-level handoff as a substitute.

## Build workflow

Development builds are local or use the `factory` remote on Beksinski. GitHub
Actions is not the normal build path. See the building guide before invoking a
remote build.

## Priority knowledge areas

The whole codebase matters, but layout/key programming/geometry and
autocorrect/neural scoring are especially easy to misunderstand. Use their
detailed guides and preserve hard-won behavior when changing them.

