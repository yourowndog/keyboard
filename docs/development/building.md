# Building OmniBoard

> Status: Canonical for local builds; Beksinski retrieval details pending live verification  
> Last verified: 2026-07-11  
> Verified against: Gradle configuration, current repository remotes, and local
> debug unit-test execution

OmniBoard is built locally or by pushing a branch to the Beksinski build
factory. GitHub Actions is not the active build workflow.

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
OPENAI_API_KEY=...
WHISPER_MODEL=whisper-1
```

Do not commit `local.properties`.

## Beksinski factory

The configured Git remote is named `factory` and currently resolves to:

```text
beksinski:/home/silo/git/omniboard.git
```

The established trigger is a normal branch push, for example:

```bash
git push factory dev
```

Do not assume only `dev` can build; older instructions say the factory detects
the pushed branch. That behavior and the current artifact URL should be checked
in a batched live factory validation before this section is marked completely
verified.

## Branch policy

The current checkout is `dev-latest` tracking `origin/dev`. Work should be
committed intentionally on the selected development branch and pushed to the
factory only when a remote build is desired. GitHub `main` and the old Actions
workflow are not the day-to-day build mechanism.

