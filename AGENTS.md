# OmniBoard Agent Guide

OmniBoard is Sam's personal Android keyboard and daily driver. It is derived
from FlorisBoard, so package and class names frequently retain the FlorisBoard
name. Optimize for reliability and Sam's actual workflow, not hypothetical
distribution requirements.

## Orientation

Read [docs/README.md](docs/README.md), then
[docs/development/agent-reorientation.md](docs/development/agent-reorientation.md)
and the relevant subsystem guide:

- Layouts, keys, geometry, popups, or customization:
  [docs/keyboard/README.md](docs/keyboard/README.md)
- Autocorrect, suggestions, dictionaries, phrases, or neural scoring:
  [docs/autocorrect/README.md](docs/autocorrect/README.md)
- Snygg or theme work: [docs/theming/README.md](docs/theming/README.md)
- Voice and AI integrations: [docs/architecture/voice-ai.md](docs/architecture/voice-ai.md)
- Builds and tests: [docs/development/building.md](docs/development/building.md)

Do not infer current behavior from historical journals, generated reports, or
documents calling themselves “reality.” Superseded root handoffs and manuals
were removed after their durable knowledge was extracted.

## Working rules

- Preserve unrelated user changes. The worktree may already be dirty.
- Explain the behavioral reason before a non-trivial code change.
- Verify source claims against live code, tests, schemas, and assets.
- For layout/touch/theme/IME behavior, include device validation when source and
  unit tests cannot establish the result.
- Do not push, run a build, install an APK, or rewrite harvested data unless
  the task authorizes it.
- Keep plans in `ROADMAP.md`; keep current behavior in `docs/`.
- Update the closest canonical document when behavior changes. Avoid creating a
  new root-level handoff as a substitute.

## Build workflow

Builds run on **sisyphus**, which has JDK 17, the Android SDK and adb. Build in
its checkout at `/home/sam/projects/keyboard`; there is no build-factory git
remote to push to. GitHub Actions is not the normal build path. See
[fleet and infrastructure](docs/development/fleet.md) for the machines and
[the building guide](docs/development/building.md) before invoking a build.

Do not build on sleeper. Its disk is small and build output fills it.

## Priority knowledge areas

The whole codebase matters, but layout/key programming/geometry and
autocorrect/neural scoring are especially easy to misunderstand. Use their
detailed guides and preserve hard-won behavior when changing them.
