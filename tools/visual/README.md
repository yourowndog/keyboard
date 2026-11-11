# Visual iteration loop

See what a change looks like without touching Sam's phone, then have a second
model critique it. Everything runs on **sisyphus** (KVM, Android SDK). See
[fleet](../../docs/development/fleet.md) for the machines.

```
build x86_64 APK -> capture.sh (emulator screenshots) -> critic.sh (review) -> edit -> repeat
```

## One-time setup on sisyphus

- SDK packages: `sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"`.
  `capture.sh` creates the `omni-visual` AVD on first run: 1440x3120, density
  485 to match icarion (Galaxy S25 Ultra).
- Sam's keyboard settings, so the emulator shows his real smartbar layout and
  theme: snapshot them from icarion with
  `adb exec-out "run-as dev.patrickgold.florisboard.debug tar -cf - jetpref_datastore/florisboard-app-prefs.jetpref files/ime files/layouts" > ~/omniboard-visual/device-config.tar`.
  Do not include `voice_history`, `voice_archive` or other private stores.

## A round

```
./gradlew --no-daemon assembleDebug -PdeviceAbi=x86_64
tools/visual/capture.sh app/build/outputs/apk/debug/app-debug.apk ~/omniboard-visual/shots/r1
tools/visual/critic.sh ~/omniboard-visual/shots/r1 "command strip labels"
```

`capture.sh` boots the emulator headless if it is not running (port 5584),
installs the APK, restores the settings, forces night mode, opens a text field
and saves full screenshots plus `*.keyboard.png` and `*.smartbar.png` crops.
States captured today: idle and typing (with candidates). Shift, caps lock and
voice still need scripted taps.

`critic.sh` runs a headless Claude Code review against the design reference
and [rubric.md](rubric.md), writes `critique.md`, and ends with
`VERDICT: PASS` or `VERDICT: FAIL`. Treat it as a reviewer, not a gate: read
the images yourself too.

## Limits

The emulator renders with SwiftShader, so colours and anti-aliasing are close
to but not identical to icarion. Touch feel, haptics and real voice input still
need the phone. Leave the emulator running between rounds; `adb -s
emulator-5584 emu kill` stops it.
