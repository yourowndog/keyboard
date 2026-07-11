# Testing and Runtime Validation

The final interactive pass is specified in
[Batched Device Validation](device-validation.md).

> Status: Canonical  
> Last verified: 2026-07-11

## Automated checks

The repository contains JVM unit tests for NLP, dictionaries, extensions,
quick-action arrangement, and shared Kotlin/Snygg behavior. Android instrumented
tests exist but require a device or emulator.

Use risk-proportionate checks:

- Pure Kotlin/NLP change: targeted unit test, then `testDebugUnitTest`.
- Resource or Gradle change: debug assembly.
- Native change: relevant ABI build plus runtime load.
- Layout, touch, IME, theme, voice, clipboard, media, or sidecar change: device
  validation in addition to automated checks.

## Device truth

ADB over Wi-Fi is available when Sam connects the device. Batch manual checks
near the end of a coherent change set. Ask for immediate device help only when
runtime evidence is necessary to choose the implementation safely.

A useful device check states:

1. Exact starting preference, subtype, layout, theme, and target app.
2. Short numbered actions.
3. Expected result for each action.
4. Logs, screenshots, or exported state needed for failures.
5. A compact response format.

## Runtime areas that require a device

- IME process recreation and theme/extension cache behavior.
- Actual touch targets, edge behavior, and long-press/swipe arbitration.
- Raw key events and chords in Termux or other target applications.
- Snygg visual states across keyboard, clipboard, media, and panels.
- Autocorrect commit/revert timing and editor-specific behavior.
- Whisper recording/transcription and loopback LLM sidecars.
- Public Documents harvesting and password-field exclusion.

Historical root testing checklists describe particular builds and theme
iterations. Extract still-relevant scenarios into focused regression lists;
do not treat unchecked boxes from an old build as current failures.
