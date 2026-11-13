# Building OmniBoard

> Status: Canonical  
> Last verified: 2026-11-10  
> Verified against: Gradle configuration, a successful `assembleDebug` on
> sisyphus at commit `844fb55`, and the installed toolchain there

OmniBoard is built locally or on **sisyphus**, the build machine. GitHub
Actions is not the active build workflow.

See [fleet and infrastructure](fleet.md) for sisyphus's toolchain paths, the
adb quirk, and how to reach the fleet.

## Local prerequisites

- JDK 17 for Gradle execution.
- Android SDK and the versions declared in `gradle/tools.versions.toml`.
- The checked-in Gradle wrapper.
- `local.properties` for machine-local Android and optional voice settings.

The application currently targets JVM 11 bytecode even though Gradle runs on
JDK 17.

## Local checks

Run focused checks first, then a debug build when appropriate:

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon assembleDebug
```

The current working copy passed `testDebugUnitTest` on 2026-07-11. A test result
does not validate device-only IME, touch, Snygg, sidecar, or voice behavior.

## Local configuration

`app/build.gradle.kts` reads values from `local.properties` or the environment.
Relevant voice settings include:

```properties
WHISPER_RELAY_URL=https://weakling.chronicle-procyon.ts.net/v1/whisper/transcriptions
WHISPER_RELAY_TOKEN=...
ARCHIVE_TOKEN=...
```

`ARCHIVE_TOKEN` authenticates the voice archive (`:8790`) and the keylog archive
(`:8791`) on Titan; its value is `/etc/voice-archive/token` there. A device build
without it uploads nothing, and Gradle warns
`ARCHIVE_TOKEN is not set`. Every sisyphus checkout has it as of 2026-11-13.

The token authenticates only to the narrow relay and is expected to be
extractable from a distributed APK. Never place an OpenAI provider credential
in this file for an OmniBoard build. Do not commit `local.properties`.

## Building on sisyphus

There is **no build factory and no `factory` git remote.** The Beksinski
droplet that hosted one was retired around May 2026; see
[fleet and infrastructure](fleet.md#changes). Builds are not triggered by
pushing a branch. sisyphus builds from its own checkout, so push to `origin`
and pull there:

```bash
cd /home/sam/projects/keyboard && git pull --ff-only origin main
```

```bash
./gradlew --no-daemon assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`
(about 150 MB). Start a build with the operator tooling's `process_start` on
node `sisyphus` with `execution_class=build`; `run_command` is capped at 120
seconds and will cut a cold build off.

## Branch policy

Work is committed on the selected development branch and merged to `origin`
`main` through a pull request, which the agent that opened it merges once its
checks pass (see `AGENTS.md`). Builds then come from a checkout of that branch
on sisyphus. The old
GitHub Actions workflow is not the day-to-day build mechanism.

`main` is the only line of development. The Nov 11, 2026 consolidation
merged everything worth keeping and retired the rest: each retired branch,
Kanban worktree and uncommitted snapshot is preserved as an `archive/*` tag
on GitHub (`git tag -l 'archive/*'`). Treat those tags as history, not as
work to resume. Branches that still exist on GitHub with no `archive/*` tag
were already fully merged into `main`. Sleeper's checkout tracks `origin/main`
over HTTPS; work from a short-lived branch off `main`, not from a long-lived
side branch.

