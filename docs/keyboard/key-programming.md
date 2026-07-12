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
