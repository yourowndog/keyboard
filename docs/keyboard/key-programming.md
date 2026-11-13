# Key Programming

> Status: Canonical  
> Last verified: 2026-07-12
> Verified against: `KeyCode.kt`, `TextKeyData.kt`, `ComputingEvaluator.kt`,
> `LayoutManager.kt`, `KeyboardState.kt`, `TextKeyboardLayout.kt`, and
> `KeyboardManager.kt`, plus `AbstractEditorInstance.kt` and
> `FlorisImeService.kt`

A programmable key has up to five separate concerns:

1. Identity: an integer code or character code point.
2. Serialization/resolution: how JSON or a Layout Pack names it.
3. Presentation: label, icon, group, state, and Snygg attributes.
4. Dispatch: what happens on press, release, long press, or chord handling.
5. Geometry: any special intrinsic sizing or padding.

Adding only a constant does not create a working key.

## Authoritative codes

`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/key/KeyCode.kt` is the
source of truth. Older root references contain conflicting values.

Current OmniBoard-specific examples include:

| Key | Code |
|---|---:|
| Ctrl | -1 |
| Escape | -15 |
| Tab | -14 |
| Voice input | -233 |
| Toggle number row | -305 |
| Toggle developer row | -306 |
| AI generate | -307 |
| Tmux prefix | -400 |

Printable characters normally use their Unicode code point. Internal actions
use negative values in the internal range.

## Bundled JSON

Common serialized key-data forms include fixed text keys, automatic text keys,
and selectors. Selectors choose another key-data object according to shift,
input variation, layout direction, character width, or kana state.

A modifier-row placeholder uses code `0`, but code `0` is not a normal
interactive key in that context; `LayoutManager` interprets it while merging.

## Layout Builder resolution

Layout Builder accepts:

- One Unicode code point, such as `a` or `λ`.
- A known internal-key label from `TextKeyData.InternalKeys`.
- A supported alias such as `KEYCODE_TAB`, `CTRL_MOD`, or `MODE_SYMBOLS`.
- A numeric key code.

Resolution and validation are not perfectly identical. When adding aliases,
update both the resolver and validation rules or packs may accept something the
runtime cannot resolve, or reject something it could resolve.

## Implementation checklist

For a new internal action:

1. Add a unique constant to `KeyCode.kt`.
2. Add or expose corresponding `TextKeyData` so layouts can name it.
3. Add Layout Builder alias/validation support if packs should use it.
4. Add label or icon computation in `ComputingEvaluator` when needed.
5. Add dispatch behavior in `KeyboardManager` at the correct event phase.
6. Decide whether it is momentary, sticky, locked, or a one-shot chord.
7. Add intrinsic sizing in `TextKey.compute()` only if ordinary sizing is
   insufficient.
8. Add theme coverage using the numeric code or supplied state attributes.
9. Test tap, long press, repeat, popup interaction, and mode switching.

## Chords and terminal keys

Ctrl, Tmux prefix, Escape, Tab, and navigation keys are not ordinary text
insertion. Their behavior depends on Android key events, editor behavior, and
state clearing. Test them in the target application—especially Termux—rather
than assuming a successful visual press means the event was delivered.

Ctrl is a keyboard-owned latch and remains visually pressed while active or
locked. Tmux prefix remains a one-shot Ctrl+B dispatch, but records a visual
latch after the event is handed to an active input connection, until the next
non-Tmux key is released or the input session ends. That latch does not pretend
that the keyboard can observe tmux's external state or prove that the target
application handled the event.

## Shift and Delete gestures

Delete's swipe left selects text before the cursor as the finger travels and
deletes it on release (`gestures.deleteKeySwipeLeft` picks characters or words,
delete or select only). Tapping Shift and dragging right is its mirror: once the
drag passes the swipe detector's first step heading right, while the finger is
still on Shift and before the long press has fired, the touch becomes a forward
delete for the rest of the gesture (`ShiftForwardDelete`,
`TextKeyboardLayout.handleShiftForwardDeleteSwipe`). It selects after the
cursor, deletes on release, follows the same precision setting, and restores
the shift state the press started from (so caps lock survives it).

What this replaces: a horizontal slide from Shift onto Z used to type a capital.
Slides that leave Shift upward still do, and so do slides in raw input editors
(terminals), where a forward delete has no selection to grow. Holding Shift
first and then moving keeps the long-press behaviour.

Swiping up from Delete (at least four swipe units up, at most one sideways)
puts back what the last Delete or Shift swipe deleted, once
(`DeleteSwipeUndo`). While the undo is armed the spacebar gauge shows how many
characters will come back. Only swipe deletions are remembered, and the memory
is cleared when a new input field starts.

## Enter long press

Holding Enter opens its popup from the `~enter` entry of the default popup
mapping. The main choice is `NEWLINE` (`-310`, ⏎), a line break that never
fires the field's action, so a chat message can have a second line without
sending. The others are Tab, Esc, the emoji panel, the clipboard and one-handed
mode.
