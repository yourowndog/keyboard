# OmniBoard roadmap

This file contains unfinished product work only. Implemented behavior belongs in
the canonical documentation under `docs/`; experiments belong under `research/`.

The autocorrect rebuild has its own phased plan in
[`docs/autocorrect/roadmap.md`](docs/autocorrect/roadmap.md).

## Near-term validation and repair

### Stabilize RTK integration

The global RTK rewrite hook has repeatedly failed its integrity check and
required `rtk init -g --auto-patch`. Determine which tool is rewriting
`~/.claude/hooks/rtk-rewrite.sh`. Prefer explicit `rtk` command invocation from
`AGENTS.md` and remove the global auto-rewrite hook if it remains contested.

### Smartbar action customization

Verify and repair action reordering, visibility toggles, and persistence. The
microphone has historically behaved differently from the other actions, so test
both ordinary quick actions and pinned/special actions across an IME restart.

### Phrase-row quick action

The second phrase row and its settings preference are implemented. What remains
is an optional quick action that toggles `prefs.smartbar.phraseRowEnabled`
without opening settings and visibly indicates its state.

### Navigation semantics

Home/End dispatch paths exist for key codes `-27` and `-28`. Validate their
behavior in multiline editors, single-line fields, Termux, and selection mode.
Change code only if those live tests show that an app interprets the current
Ctrl+Move events incorrectly.

### Harvest and neural-gate calibration

Continue the snapshot -> review -> derive -> train -> shadow-evaluate cycle
documented in `docs/autocorrect/harvesting.md` and `training/README.md`. Promote
a model or enable live gating only after shadow data supports the threshold.

## Layout and ergonomics

### Independent space-row model

The renderer recognizes a space row, but its source is still merged with the
modifier layout. Consider an explicit third layout source only if independent
height, padding, and scaling cannot be expressed cleanly in the current model.
This is a layout-pipeline change and should be developed on its own branch.

### Visual-width redistribution

Per-key padding currently changes visible bounds without reallocating the freed
space to neighbors. Design a general spacer or row-allocation mechanism before
adding more one-off bounds mutations. Touch bounds must remain intentional and
separately testable from visible bounds.

### Remaining ergonomics backlog

- Dynamic alpha-key width controls.
- Independently configurable spacing groups.
- Intentional hitbox expansion near edges and narrow keys.

## Appearance

### LCARS Jefferies: remaining phases

The stylesheet-only phase is done and awaits device validation. See
`docs/theming/lcars-jefferies.md` for what the theme engine can and cannot
express here. Remaining work, in order, each gated on device validation:

**Pick the typeface.** Four variants ship (Chakra Petch, Oxanium, Saira
Condensed, Rajdhani). On 2026-11-11 Sam judged them on device and approved
both Chakra Petch and Oxanium as really good. Saira Condensed and Rajdhani were
not approved. The final pick between the two approved faces is still open;
once it is made, collapse to the winner per `tools/theme/README.md`.

**Stacked hint plates.** Render-only plates landed: see
`docs/theming/lcars-jefferies.md`. They split the existing key height rather
than adding a row-height budget; revisit the budget only if device use shows the
caps are too short. Original notes follow. The signature of the reference: each letter key is a
two-plate stack, with the symbol layer permanently visible above the cap rather
than hidden behind a long press. The data already exists and is already aligned
1:1 — `LayoutManager.addRowHints()` populates `computedSymbolHint` and
`computedNumberHint` from the symbols layout, indexed against the letter row.
Today `TextKeyButton` draws it as a corner glyph. Needs a new `key-plate`
element in `FlorisImeUi`, a column in `TextKeyButton`, and a row-height budget
in `FlorisImeSizing.keyboardUiHeight()` — which counts rows in two places, so
both must be checked.

Start render-only, with no touch behavior change: long-press and swipe-up keep
working exactly as now. Volatile hitboxes are the main risk to typing accuracy,
so plate tappability is deliberately deferred.

**Ornament layer.** The artwork Snygg cannot express: the delete grille and its
coral rule, the spacebar line art and cyan dot run, the shift ring, the
smartbar divider rules and pill. Proposed shape is a registry of
`keycode -> composable` drawn inside the key's `SnyggBox`, reading its colors
from the resolved Snygg style so ornaments re-theme instead of hard-coding hex
in Kotlin. `VoiceVisualizer` and `WhisperBar` in `Smartbar.kt` are the
precedent. Ornaments should query their own registered elements rather than
motivating new Snygg engine features.

**Smartbar relayout.** The reference is a fixed `MENU | EMOJI | LANG | ... |
VOICE` strip of wide text buttons, with candidates swapping in while typing.
`QuickActionButton` hard-codes `Modifier.aspectRatio(1f)`, forcing every action
key square; that constraint has to come out first.

**Motion.** Spacebar dot animation and press transitions, once the ornament
layer exists.

**Later, optional: plate tappability as a preference.** Numbers tappable (the
top row is the safe one), symbol rows hold-only or behind a modifier chord.
Only after the render-only plates have been in daily use.

### Bezel and the missing keyboard surface

The reference has a `#0C1014` bezel around a black key field. There is no
themable surface between `window` and `key`: `FlorisImeUi.Root` is declared but
never queried, and only `window` paints. Either register an element for the key
field or drop the bezel. Low value on its own (about 5% of the reference's
pixels); worth doing alongside `key-plate` since both are element registration.


### Transparent or frosted keyboard background

The transparent IME host plus alpha-capable Snygg window color appears
structurally capable of intentional translucency. The API 30+ RGBA SurfaceView
also has a likely resize/redraw race that could explain the bottom-offset
see-through glitch. Fix and device-test surface redraws across apps and inline
autofill before exposing an alpha control. True background blur remains API-
and window-compositor-dependent. Do not describe `width`, `height`, or
`opacity` as Snygg properties: they are not in the generated schema.

### Theme change provenance

Prefer reviewable commits and a short rationale in theme documentation over an
append-only root log. If theme iteration needs machine-readable provenance,
add a tool under `tools/` and document the exact promotion workflow from
`research/theme-archive/` into active assets.

## Implemented and awaiting live confirmation

Password exclusion was confirmed during the 2026-07-11 device pass: ordinary
marker text was harvested while the password-field attempt emitted no password
variation events.

These are not roadmap implementation tasks:

- Numeric-bearing tokens are now blocked from automatic commit by
  `CommitPolicy`, including digits-only values, mixed identifiers, and version
  strings. The captured `742` -> `PS2` revert is a regression fixture; confirm
  on the installed build that numeric suggestions remain non-destructive.
- The `org.florisboard.themes` LCARS Tactical and Neon variants now give Ctrl
  and Tmux high-contrast active styling. Tmux keeps a visual latch after a
  successful Ctrl+B handoff until the next non-Tmux key release. The next
  installed build still needs a Termux visual confirmation.
- QWERTY Wide period long-press now declares single quote, double quote,
  exclamation mark, and question mark; confirm the rendered popup in the next
  installed build.
- Hide-keyboard behavior exists through `SwipeAction.HIDE_KEYBOARD`.
- Tmux prefix key exists as `KeyCode.TMUX_PREFIX` (`-400`) and sends Ctrl+B.
- Start/end navigation dispatch paths exist.
- Phrase prediction and the optional second smartbar row exist.
- Modifier-row visibility controls and spacebar long-press configuration exist.

See `docs/development/testing.md` for static checks and the forthcoming device
validation checklist for behavioral confirmation.

## Intentionally shelved

- Glide typing code exists, but it has never produced acceptable behavior on
  the target device and is not current product work.
- Explicit Gemma actions remain in the app but are not part of the current
  validation or development path.
- The developer row works, but most of its practical role has moved to the
  smartbar; retain it without prioritizing expansion.
