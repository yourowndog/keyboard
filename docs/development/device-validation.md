# Batched Device Validation

> Status: Manual validation plan  
> Run after static checks and a fresh debug install

This keeps the interactive part short while covering behavior that source
inspection cannot prove. Record the app/editor used and any failure; do not
tune several systems at once during this pass.

## Connection and baseline

1. Enable wireless debugging and connect with `adb connect HOST:PORT`.
2. Confirm the intended device with `adb devices -l`.
3. Build/install the debug variant and force-stop/restart the IME.
4. Select OmniBoard and a known bundled theme.

## Layouts and programmed keys

- Open ordinary text, multiline text, and Termux fields.
- Switch character, symbol, number-row, and developer-row states.
- Confirm row geometry, narrow/wide keys, edge hitboxes, popup hints, and the
  period popup without visual/touch-bound mismatch.
- Exercise Ctrl active/locked, Escape, Tab, arrows, Home/End, hide keyboard,
  Tmux prefix, and any AI/voice keys.
- Confirm spacebar long-press and modifier-row visibility preferences survive
  an IME restart.

## Snygg

- Check base, finger-down `:pressed`, and latched-toggle styling.
- Inspect keys, hints, popup box/elements, smartbar, phrase row, clipboard,
  emoji/media, and auxiliary panels using the coverage checklist.
- Switch away and back, then restart the IME to expose package/cache mistakes.

## Autocorrect and phrases

- Type clean words, realistic misspellings, contractions, single-letter words,
  punctuation, and mid-word cursor edits.
- Confirm displayed ranking remains plausible and corrections do not commit in
  password fields.
- Confirm phrase suggestions appear only at valid boundaries and the optional
  second row follows its preference.
- In NLP debug output, confirm neural shadow scores appear while live gating
  remains at the configured state and threshold.

## Glide, voice, and explicit AI actions

- Glide several common and custom words and confirm the active runtime asset is
  used without a crash or pathological result.
- Start a voice take, dictate long enough to recognize it, and rotate the device.
  If Android only recreates the input view, confirm the recording UI reattaches
  and the take continues without creating a second file.
- Repeat with a real interruption such as locking the phone, hiding the IME, or
  moving focus away. Confirm capture stops, the take appears in Voice Inbox,
  and its result is not inserted into a different editor when the keyboard
  returns.
- Long-press the voice key to open Voice Inbox. Confirm a ready take can switch
  between Cleaned and Verbatim and that Copy and Insert use the displayed text;
  confirm a failed take exposes Retry.
- Run one explicit Gemma action; confirm failures are surfaced cleanly if its
  external service is unavailable.

## Harvest integrity

- Produce a small recognizable sequence of typed, corrected, rejected, voice,
  and app-context events.
- Enter text in a password field and confirm its content is absent from harvest.
- Capture once with `python3 tools/harvesting/snapshot_device.py --adb` (or add
  `--serial HOST:PORT`). Confirm a new timestamped inbox directory appears and
  that canonical raw corpus hashes are unchanged.

Save logs/screenshots for failures only. Do not merge the validation snapshot
into canonical harvest data during this test.
