# Visual iteration loop

See what a change looks like without touching Sam's phone, compare it with the
design reference and with the previous round, and have a second model critique
it. Rounds are kept, so the trajectory over many changes stays visible. Everything runs on **sisyphus** (KVM, Android SDK). See
[fleet](../../docs/development/fleet.md) for the machines.

```
build x86_64 APK -> round.sh: capture -> compare -> critic -> history -> edit -> repeat
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
tools/visual/round.sh app/build/outputs/apk/debug/app-debug.apk "stadium shift inner edge" "shift key shape"
```

The second argument says what the round changed; the optional third narrows
the critique. Each round lands in `~/omniboard-visual/rounds/<yyyymmdd-hhmmss>-<slug>`
(`OMNI_VISUAL_HOME` moves the root) with `label.txt` and `meta.txt` (commit,
APK hash, which round it was compared against). `round.sh` runs four steps,
each also usable alone:

- `capture.sh <apk> <dir>` boots the emulator headless if it is not running
  (port 5584), installs the APK, restores the settings, forces night mode,
  opens a text field and saves full screenshots plus `*.keyboard.png` and
  `*.smartbar.png` crops. States captured today: idle and typing (with
  candidates). Shift, caps lock and voice still need scripted taps.
- `compare.sh <before-dir|-> <after-dir>` writes `compare-<state>.png`: four
  panels, REFERENCE, BEFORE, AFTER and CHANGED (the before shot faded, changed
  pixels in coral), plus the share of changed pixels in `compare.txt`. The
  before is the newest earlier round; `BEFORE=<dir>` picks another and
  `BEFORE=-` compares against nothing. A change that touched pixels the round
  did not mean to touch shows up in CHANGED.
- `critic.sh <dir> [focus]` runs a headless Claude Code review against the
  reference, [rubric.md](rubric.md), the comparison and the previous round's
  critique. It writes `critique.md` in three parts: defects, trajectory (did
  the round move toward the target, what regressed) and depth and character
  (ideas for layers and personality beyond the reference). It ends with
  `VERDICT: PASS` or `VERDICT: FAIL`, judged on defects only. Treat it as a
  reviewer, not a gate: read the images yourself too. `NO_CRITIC=1` skips it.
- `history.sh [rounds-dir]` rebuilds `rounds/trajectory.png` (the reference
  and the newest 11 rounds' idle keyboards in order) and `rounds/history.md`
  (every round with its change, commit, changed share and verdict).

Rounds from before `round.sh` live in `~/omniboard-visual/shots/r0`-`r4`.

## Limits

The emulator renders with SwiftShader, so colours and anti-aliasing are close
to but not identical to icarion. Touch feel, haptics and real voice input still
need the phone. Leave the emulator running between rounds; `adb -s
emulator-5584 emu kill` stops it.
