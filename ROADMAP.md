# OmniBoard roadmap

This file contains unfinished product work only. Implemented behavior belongs in
the canonical documentation under `docs/`; experiments belong under `research/`.

## Near-term validation and repair

### Autocorrect and suggestions in `noSuggestions` fields

Autocorrect currently never fires in fields that set `TYPE_TEXT_FLAG_NO_SUGGESTIONS`
(search boxes, AI prompt bars, Termux, URL bars). Root cause is
`AbstractEditorInstance.shouldDetermineComposingRegion`, which returns false when
that flag is set, so no composing region is established and the NLP layer never
sees an active word. Confirmed from device harvest (2026-07-14); this is inherited
FlorisBoard behavior, not a correction-engine regression.

The desired behavior is **not** a blanket removal of the flag check. Split the two
concerns: allow a composing region so **suggestions** appear in text
`noSuggestions` fields (including URL bars and Termux, where suggestions are
genuinely helpful), but keep **automatic commit** off wherever it would corrupt
input — URI and email variations, and Termux — while allowing it in ordinary
search / prompt boxes. Suggestions everywhere; auto-commit only where safe. This
is an editor-layer change with real blast radius; develop it with on-device
validation across a search box, a URL bar, a Termux session, and an email field.

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

### Autocorrect provider and fallback parity

The pure commit-evidence adapter and asset-backed component tests now prevent
provider and tests from independently inventing `CommitPolicy` booleans. Casing
and explicit static-contraction shortcuts also share a production evidence
assembler with asset-backed coverage of effective packaged personal vocabulary,
a real anti-correction, casing, exact static-license binding, and preservation
of ambiguous valid words such as `were` and `its`. The SymSpell-only path no
longer crosses a string-only boundary: `SymSpellManager.suggest` now returns
structured `FallbackCandidate`s carrying real provenance, edit distance,
contraction license, and `FallbackEngineMode`, and the `FallbackCorrection`
adapter routes that evidence through the same `CorrectionDecision`/`CommitPolicy`
Gate as the primary path. `LEGACY_FALLBACK` now marks only the honest
"no correction evidence" case and can no longer auto-commit on rank alone. What
remains is provider-level coverage of final ordering, casing, and returned
eligibility, then an on-device commit/revert and engine-recovery pass before
treating this cleanup as behaviorally closed.

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

- The ranking cleanup now keeps anti-correction pair exclusion, protected-word
  commit vetoes, contextual evidence, contraction rules, segmentation, and
  numerical scoring in distinct components. Its regression suite is static;
  confirm on the next installed build that ordinary suggestions feel unchanged.
- Numeric data remains blocked from automatic commit by `CommitPolicy`,
  including digits-only values, mixed identifiers, and version strings. A
  narrowly shaped single number-row slip inside an otherwise alphabetic word is
  now allowed when its candidate differs only by the adjacent replacement
  letter. Static fixtures cover both sides, including the captured `742` ->
  `PS2` revert; confirm on the installed build that word-shaped slips correct
  while numeric suggestions remain non-destructive. Known minor quirk (deferred,
  not a regression): the number-row slip candidate emits its canonical lowercase
  form and does not inherit the typed casing, so `5his` at a sentence start
  yields `this` rather than `This`, and `5HIS` yields `this` rather than `THIS`.
  Casing propagation for this path is out of scope for the current
  stabilization.
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
