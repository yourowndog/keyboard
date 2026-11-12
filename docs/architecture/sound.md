# Key and feature sounds

> Status: Canonical (sound packs are a stub: chosen but not yet played)
> Last verified: 2026-11-12 against `ime/sound/` and `InputFeedbackController.kt`

## Entry points

Code that reacts to input says *what happened*. It never chooses or plays a
clip.

- Key presses go through
  [`InputFeedbackController`](../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/input/InputFeedbackController.kt)
  (`keyPress`, `keyLongPress`, `keyRepeatedAction`, the swipe gestures), which
  classifies the key with `SoundEvent.forKey`.
- Features raise their own events with
  `InputFeedbackController.soundEvent(SoundEvent.X)`: picking a candidate,
  applying an autocorrect, opening a popup, stopping voice, refusing an action.
  None of these call sites exist yet. Add one where the feature completes the
  action, not where it is attempted.

`InputFeedbackController` keeps the existing gates: the user's sound toggle,
the system sound settings, and the volume. Then it hands the event to a
`SoundEngine`.

## The pieces

| Type | File | Role |
| --- | --- | --- |
| `KeyRole` | `ime/text/key/KeyRole.kt` | what a key is for; shared with key skins |
| `SoundEvent` | `ime/sound/SoundEvent.kt` | every sound OmniBoard can make, named by meaning |
| `SoundEngine` | `ime/sound/SoundEngine.kt` | plays an event; returns false when it has nothing for it |
| `SystemSoundEngine` | same | Android's four key clicks; silent for feature events |
| `SoundPackEngine` | same | **stub**: reads a pack and picks clips, then falls back |
| `SoundPackManifest` | `ime/sound/SoundPack.kt` | `soundpack.json` |

Event names are the keys of `soundpack.json`, so they are never renamed.
Delete has a separate auto-repeat event so a held delete can sound lighter than
a tap.

## Sound packs

A pack is a folder on the device holding `soundpack.json` and the clips it
names. Packs never go in the APK or this repository; Sam's LCARS sounds are
licensed for personal use and live on sleeper under
`~/documents/lcars-trek-assets/`.

```json
{
  "id": "lcars-1",
  "events": { "KEY_ALPHA": ["alpha-1.ogg", "alpha-2.ogg"], "KEY_ENTER": ["enter.ogg"] },
  "gainDb": { "KEY_DELETE_REPEAT": -4 }
}
```

Several clips for one event play in turn. Unknown event names are ignored and
reported by `unknownEvents()`. An event the pack leaves out falls back to the
system click for key events and to silence for feature events.

## Finishing the stub

1. `SoundPackEngine`: one `SoundPool` with `USAGE_ASSISTANCE_SONIFICATION`, about
   6 streams, every clip loaded when the pack is selected (never on the key
   path), `gainDb` applied as a volume multiplier, released on `release()` and
   on pack change.
2. A preference naming the pack folder under the app's files directory, an
   import action, and a settings row.
3. Construct `SoundPackEngine(folder, SystemSoundEngine(audioManager))` in
   `InputFeedbackController` when a pack is selected.
4. Add the feature call sites listed above.
