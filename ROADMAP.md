# OmniBoard Roadmap

## LCARS keyboard chassis and Smartbar

> Status: reconnaissance-backed design specification; not current behavior  
> Branch: `design/lcars-keyboard-chassis`  
> Baseline: `1da44479` (`fix/voice-smartbar-regression-stable-base`)  
> First written: 2026-08-21  
> Implementation authorization: specification and reference assets only. Do not
> build, install, deploy, change device settings, or alter voice capture merely
> because this specification exists.

### Product intent

OmniBoard should read as a small retro-futurist instrument in Sam's hand, not as
disembodied keycaps and an unrelated toolbar on a rectangular black slab. The
Smartbar and key field need one intentional physical chassis. Voice recording
and processing visuals then inhabit that chassis; they do not define it.

The target progression is:

```text
theme-colored keys on an opaque rectangle
  -> theme-agnostic keyboard chassis geometry
  -> Smartbar and keys mounted on one coherent panel
  -> deliberate outer transparency around that panel
  -> truthful recording and deterministic processing instruments
```

This is a personal daily-driver design. Reliability, legibility, touch safety,
and lossless PCM capture outrank distributable-theme generality, but the
physical geometry must remain independent of any one Snygg palette.

### Decisions made by this specification

1. **Use one asymmetric left-hand elbow as the base topology.** Its vertical
   shoulder emerges from the keyboard deck, turns through a concentric elbow,
   and becomes a thin rail across the top of the Smartbar. The right rail exits
   or clips at the screen edge.
2. **Do not mirror the elbow in the first implementation.** A mirrored variant
   can be explored later, but it spends content width, over-emphasizes symmetry,
   and risks becoming a generic dashboard capsule.
3. **Use an opaque shaped chassis inside a transparent-format IME window.** The
   target app may show outside the chassis silhouette. It must not show between
   ordinary key gaps or behind labels.
4. **Treat half-pill buttons as terminal punctuation, not the whole grammar.**
   They belong at rail endpoints, in the left control bay, or as one emphasized
   action. They should not become a row of unrelated floating lozenges.
5. **Keep geometry and palette separate.** Code owns topology, measurements,
   clipping, and touch allocation. Snygg supplies semantic colors, borders,
   typography, and state treatment.
6. **Draw structural geometry natively.** The supplied raster images are design
   evidence. Runtime chassis rails should be analytic Compose paths, not scaled
   PNGs, nine-patches, or a traced pile of arbitrary Beziers.
7. **Retain a runtime fallback.** The current flat Smartbar and repaired voice
   pipeline remain available until the chassis passes device validation.
8. **Do not redesign capture while building the chassis.** The existing cyan
   line may be placed unchanged inside the new instrument viewport. Meter history
   and processing animation are later, separable work.

### Reference imagery

These copies are checked into the branch so every agent and node sees the same
evidence. They are visual references, not runtime assets.

Current OmniBoard:

- [Normal Smartbar and key field](docs/assets/lcars-chassis-references/Screenshot_20260821_055154_OmniBoard.jpg)
- [Voice recording Smartbar](docs/assets/lcars-chassis-references/Screenshot_20260821_055146_OmniBoard.jpg)

Primary structural references:

- [Asymmetric double-band elbow selected as the base direction](docs/assets/lcars-chassis-references/Screenshot_20260821_061124_Photos.jpg)
- [Transparent outer elbow fragment](docs/assets/lcars-chassis-references/tp.elbow_outer_t.l_912x246.png)
- [Compact elbow fragment](docs/assets/lcars-chassis-references/e.elbow2_t.l_530x170.png)
- [Alternative bar termination 2](docs/assets/lcars-chassis-references/b.bar_cap2_380x298.png)
- [Alternative bar termination 3](docs/assets/lcars-chassis-references/b.bar_cap3_380x237.png)

Control and instrument references:

- [Half-pill add control](docs/assets/lcars-chassis-references/menu_add.png)
- [Half-pill delete control](docs/assets/lcars-chassis-references/menu_delete.png)
- [Low-complexity list/instrument button](docs/assets/lcars-chassis-references/list_button4_600x100.png)
- [Compact instrument panel grammar](docs/assets/lcars-chassis-references/graphic6_626x206.png)
- [High-complexity composition reference](docs/assets/lcars-chassis-references/graphic3_1117x1067.png)

The compact panel is a reference for segmented indicators, traces, and small
status blocks. The high-complexity composition is a ceiling on visual density,
not a target to squeeze into the Smartbar.

### Verified current architecture

#### Rendering and window stack

The active path is:

```text
Android InputMethodService window
  -> full-height ComposeInputView
  -> ImeUiWrapper / FlorisImeTheme
  -> SnyggBox(element = window), measured to visible IME content
  -> API 30+ SnyggSurfaceView background layer
  -> TextInputLayout
       -> Smartbar
       -> TextKeyboardLayout
```

Relevant implementation:

- `FlorisImeService.onCreateInputView()` creates `ComposeInputView`.
- `FlorisImeService.ImeUi()` owns the root Snygg `window`, safe-area padding,
  bottom offset, one-handed layout, and mode surface.
- `FlorisImeSizing` separately computes solved key-field height and Smartbar
  height, then sums them.
- `Smartbar()` selects main, secondary, phrase, and voice compositions.
- `TextKeyboardLayout()` owns the full key-field pointer stream and draws keys
  from solved structural, visible, and touch bounds.

The canonical background analysis remains
[Transparency and the IME surface](docs/theming/hard-won-lessons.md#transparency-and-the-ime-surface).

#### Live device evidence on the baseline build

Read-only ADB inspection on 2026-08-21 found:

- OmniBoard debug is the active IME and its input view is shown.
- The Android input-method window reports `fmt=TRANSPARENT`.
- The window itself is full-height, bottom-gravity, and alpha `1.0`.
- OmniBoard supplies measured content/visible insets and a rectangular touchable
  region through `onComputeInsets()`.
- The current target application is composited underneath the IME surface.

This disproves the broad claim that Android cannot display the target app under
an IME. It does **not** prove that a Snygg alpha value alone implements a robust
shaped keyboard.

#### Why previous theme-only transparency attempts were inconclusive

There are multiple independently painted layers:

1. `FlorisImeTheme` is the active input-method theme. The separate
   `FlorisAppTheme.Transparent` style declares `windowIsTranslucent`, but it is
   not the IME theme.
2. The root Compose `SnyggBox(window)` participates in background painting.
3. On API 30+, `SnyggSurfaceView` also draws the `window` style behind inline
   autofill. It asks for `style.background(Color.Black)`, so an unspecified
   theme value becomes black rather than transparent.
4. The `SurfaceView` uses `PixelFormat.TRANSPARENT`, but transparent pixel format
   and transparent pixels are not the same as a correctly redrawn shaped layer.
5. The current static surface draw is not keyed to surface size and has no
   `surfaceCreated`/`surfaceChanged` redraw contract. Resizing from bottom-offset
   changes can briefly expose unpainted transparent pixels. That is the leading
   explanation for Sam's observed see-through glitch.

The smallest valid transparency experiment must therefore instrument or control
all active background painters. Changing a stylesheet color without proving the
resolved root color, SurfaceView buffer, and window composition is not a test.

#### Insets and touch are separate from pixels

Visual transparency must not imply touch-through. `onComputeInsets()` currently
marks the measured IME region as a touchable rectangle, with a special extension
for the overlaid actions row. A future chassis can have transparent-looking top
corners while still consuming their touches, or it can intentionally subtract
those corners from `touchableRegion`; those are different product choices.

First implementation policy:

- Keep the entire measured keyboard rectangle touchable.
- Make structural decoration non-interactive.
- Do not carve the touchable region until a concrete interaction requires it.
- Confirm that no app control can be accidentally activated through a visually
  transparent chassis edge.

#### Current sizing constraint

`FlorisImeSizing.smartbarHeight` is reused by normal candidates, quick actions,
voice, the phrase row, and the expanded actions row. Raising it globally would
inflate auxiliary rows and is not the intended design.

The chassis work should introduce distinct concepts:

- `mainChassisHeight`: main Smartbar instrument height;
- `auxiliaryRowHeight`: phrase and expanded-action height;
- `viewportHeight`: uncluttered content/instrument area;
- `railPrimaryThickness`, `railSecondaryThickness`, and `railGap`;
- `deckConnectionDepth`: how far the left shoulder visually enters the key deck.

No names above are mandated API names. The separation is the requirement.

### Physical composition

#### One chassis, three visual layers

```text
background layer: no pointer input
  keyboard deck + elbow + rails + viewport backing

content layer: state-dependent information
  candidates / actions / recording trace / processing scan / status

touch layer: stable rectangular hit targets
  Smartbar controls + existing TextKeyboardLayout pointer surface
```

The deck is an opaque or intentionally translucent theme-colored panel behind
the entire key field. Key gaps reveal the deck, never the target application.
Only space outside the deck's outer silhouette can reveal the application.

The selected elbow is part of that deck. Its vertical shoulder rises out of the
upper-left deck, turns across the top of the Smartbar, and then runs right. This
answers the topology question without drawing a decorative rail that happens to
sit above an unrelated keyboard.

The first deck silhouette should be restrained:

- one shaped top-left shoulder connecting to the Smartbar rail;
- a dark continuous backing behind all key gaps;
- modest outer corner treatment or one additional structural notch;
- no rail around every key and no attempt to imitate an entire television prop;
- semantic key groups may receive subtle backing bands later.

#### Smartbar zones

```text
left shoulder/control bay | flexible instrument viewport | terminal action
```

- The left bay may contain expand/back in normal mode and pause/cancel in voice
  mode.
- The viewport hosts candidates, quick actions, the existing recording line, or
  the processing instrument.
- The right terminal hosts the sticky action or voice submission.
- Optional phrase/extended rows attach below the main rail as a subordinate
  deck; they do not make the main chassis twice as tall.

Half-pill silhouettes are appropriate for a terminal action or a paired control
block. Their visual bounds must remain independent of at least 48 dp touch cells.

### Geometry derivation

#### Measured source ratios

The primary screenshot is JPEG evidence rather than original vector geometry,
so measurements are approximate. The visible double-band elbow occupies a
`1440 x 728 px` bounding box in the 1440 x 3120 screenshot. Normalizing all
measurements by the 728 px module height gives a density-independent starting
construction:

| Feature | Approx. source | Ratio to module height | 64 dp study |
| --- | ---: | ---: | ---: |
| primary horizontal band | 141 px | 0.194 | 12.4 dp |
| inter-band channel | 8 px | 0.011 | 0.7 dp |
| secondary horizontal band | 79-85 px | 0.109-0.117 | 7.0-7.5 dp |
| primary outside bend radius | 337 px | 0.463 | 29.6 dp |
| primary shoulder width at the vertical run | 646 px | 0.887 | 56.8 dp |
| secondary shoulder width at the vertical run | about 260 px | 0.357 | 22.8 dp |
| combined shoulder extent | about 906 px | 1.245 | 79.7 dp |

The important LCARS characteristic is not a single magic radius. It is the
relationship between thin horizontal rails, much wider vertical shoulders,
concentric bends, and narrow negative-space channels. A constant-width rounded
rectangle cannot reproduce it.

#### Construction rules

1. Establish a normalized module height `H`.
2. Derive primary/secondary band thickness, channel, and major radius from `H`.
3. Construct outer arcs analytically. Inner radii follow from the matching outer
   radius and local thickness; do not tune every corner independently.
4. Permit the vertical shoulder to widen beyond the horizontal rail thickness.
   That widening is intentional LCARS panel grammar.
5. Extend the left control bay when accessibility needs more width:

   ```text
   controlBayWidth = max(referenceShoulderWidth, touchCellCount * 48dp + gaps)
   ```

6. Clip the straight right rail at the viewport boundary or screen edge; do not
   round it into a symmetrical capsule by default.
7. Snap very thin channels and keylines to physical pixels at draw time so they
   remain crisp across densities.

Initial studies should compare `H = 60 dp`, `64 dp`, and `68 dp`. The 64 dp
column in the table is a prototype, not a final requirement.

#### Drawing strategy

Use a pure geometry model that produces named paths or primitives, followed by a
Compose renderer:

```text
LcarsChassisGeometry(inputs in logical units)
  -> primary rail path
  -> secondary rail path
  -> deck path
  -> viewport clip path
  -> visual control shapes

Compose Canvas / drawBehind
  -> resolves density and pixel snapping
  -> fills paths from semantic theme roles
  -> clips only visual content

separate Compose controls
  -> retain rectangular semantics and hit targets
```

The pure model should be unit-testable without Android or Compose. Tests should
assert bounds, non-negative radii, channel consistency, arc continuity, clipping
containment, and graceful behavior at narrow widths. Screenshot tests and device
inspection validate appearance; string snapshots of path data do not.

Nine-patch is rejected for the structural chassis because it cannot naturally
express topology changes, multiple coordinated bands, viewport clipping, or
theme-driven fills. Static vectors remain suitable for icons. The reference
PNGs remain documentation only.

### Theme-agnostic styling contract

Geometry must never encode cyan, coral, gray, black, or any other theme color.
The first implementation should query semantic roles and provide fallbacks from
existing elements so older themes remain usable.

Candidate roles for new Snygg elements:

- `keyboard-chassis`
- `smartbar-chassis`
- `smartbar-rail-primary`
- `smartbar-rail-secondary`
- `smartbar-instrument-viewport`
- `smartbar-action-primary`
- `smartbar-action-secondary`
- `smartbar-action-destructive`
- `smartbar-status-trace`

Names must be checked against current `FlorisImeUi` naming conventions before
implementation. Themes may assign any colors to these roles. “Primary,”
“secondary,” and “destructive” describe hierarchy and behavior, not a palette.

Fallback mapping for themes that do not define the new roles should derive from
existing `window`, `smartbar`, action-key, candidate-word, and key styles. The
classic renderer remains the stronger fallback if derived contrast is unsafe.

Snygg currently supports background/foreground colors, borders, type, spacing,
shape, and clipping. It does not support general CSS width, height, opacity, or
arbitrary layout. Chassis topology and rail proportions therefore belong in
code, while alpha-valued colors remain a theme capability.

Every prototype must be checked against multiple materially different themes,
not merely the current LCARS palette:

- dark high-contrast;
- light;
- low-saturation;
- theme with background image;
- theme missing every new selector;
- incognito and pressed/latched states.

### Voice invariants

The chassis branch inherits the repaired voice baseline and must preserve:

- oversized `AudioRecord` safety buffer;
- buffered lossless WAV writing;
- 20 ms PCM reads for responsive non-critical metering;
- normal reader-thread priority;
- stopping `AudioRecord` before joining;
- WAV finalization away from the IME/UI thread;
- immediate recording and processing state transitions;
- stable rollback path.

Chassis work consumes voice state; it does not own recording. The first voice
composition places the existing cyan line and current controls inside the new
zones. Rolling RMS history, transient peak, peak hold, and the deterministic
processing scanner remain later milestones with their own fallback.

### Smallest-step implementation roadmap

Each checkpoint should change one observable relationship and be independently
revertible. Do not combine adjacent checkpoints merely because they are in the
same file.

#### C0 — specification and evidence

- Create the design branch.
- Check in this specification and reference imagery.
- Record source, live-window, theme, sizing, and touch findings.
- No application behavior changes.

Acceptance: documentation links resolve and the worktree contains no Kotlin,
XML, theme, build, or device mutation.

#### C1 — pure normalized geometry model

- Add only the platform-neutral geometry inputs and outputs.
- Encode the measured ratios as named defaults, not anonymous constants.
- Add continuity, bounds, narrow-width, and pixel-snap policy tests.

Acceptance: JVM tests demonstrate the geometry contract; no production UI calls
the model.

#### C2 — debug-only Visualizer Lab shell

- Add a debug settings destination that never appears in release behavior.
- Provide fixed-size preview surfaces and theme switching.
- Do not add the chassis renderer yet.

Acceptance: the lab opens without changing the active IME layout.

#### C3 — primary elbow only in the lab

- Render a monochrome primary shoulder and rail from the pure model.
- Show 60/64/68 dp studies side-by-side.

Acceptance: no generic rounded rectangle; measured tangent and radius overlays
agree with the geometry model.

#### C4 — secondary band, channel, and viewport clip

- Add one secondary rail and the negative-space channel.
- Add the instrument viewport clip, with no content.

Acceptance: band spacing stays consistent through the bend and straight run.

#### C5 — theme role adapter

- Resolve semantic chassis roles from Snygg.
- Exercise current, light, missing-selector, and image-background themes in the
  lab.

Acceptance: geometry is identical across themes and unsafe missing-role contrast
falls back explicitly.

#### C6 — separate main and auxiliary Smartbar heights

- Decouple main chassis height from phrase/expanded rows.
- Keep rendered production dimensions visually unchanged behind the classic
  renderer.

Acceptance: normal, phrase, expanded, overlay, and voice modes occupy their old
heights with the chassis switch off.

#### C7 — actual Smartbar background behind a debug switch

- Mount the chassis as a non-interactive background in the real Smartbar.
- Keep the window fully opaque.
- Keep existing candidates, actions, and waveform rendering.

Acceptance: no touch, candidate, voice, height, or inset regression.

#### C8 — zone normal Smartbar content

- Place normal toggle, candidates/actions, and sticky action into left,
  viewport, and terminal zones.
- Do not change action behavior or overflow topology.

Acceptance: every current Smartbar layout and inline-autofill state remains
reachable and legible.

#### C9 — zone voice content with the existing waveform

- Place pause/cancel, the unchanged line renderer, and submit into the same
  chassis zones.
- Keep recording and processing lifecycle untouched.

Acceptance: a long voice capture remains lossless and responsive; chassis code
does not appear in `Recorder` or audio finalization paths.

#### C10 — controlled alpha probe

- In the Visualizer Lab and then behind a device-only debug switch, make only a
  known corner patch transparent.
- Log resolved Snygg colors, view/surface sizes, and surface lifecycle callbacks.

Acceptance: the target app is predictably visible in the probe area on the daily
driver, not merely during a resize glitch.

#### C11 — repair or bypass the static SurfaceView redraw path

- Redraw on surface creation and size changes, with retry after invalid surface,
  or bypass the separate surface when no background image is active.
- Preserve inline-autofill layering and background-image themes.

Acceptance: repeated offset, orientation, app, and IME hide/show changes never
produce stale or accidentally transparent buffers.

#### C12 — shaped opaque key deck

- Add a theme-colored deck behind the key field.
- Reveal the target app only outside a small, explicit outer silhouette.
- Keep the whole measured keyboard rectangle touchable initially.

Acceptance: key gaps always show deck color, all edge keys remain hitable, and
the keyboard reads as one panel rather than floating keys.

#### C13 — join elbow and deck

- Remove any seam between the Smartbar shoulder and key deck.
- Add at most one restrained outer notch or secondary structural band.

Acceptance: the rail appears to emerge from the keyboard panel at all supported
widths and orientations.

#### C14 — device matrix and performance

- Exercise portrait/landscape, one-handed mode, bottom offsets, expanded rows,
  inline autofill, popups, clipboard/media modes, navigation bar, light/dark
  target apps, and multiple themes.
- Measure frame timing in idle, typing, recording, and processing states.

Acceptance: no new missed-frame pattern, touch dead zone, visibility failure, or
window/inset instability.

#### C15 — Sam-default rollout

- Make the validated renderer selectable as Sam's default while retaining the
  classic runtime fallback.
- Update the closest canonical behavior documents.

Acceptance: rollback does not require reinstalling an older APK.

#### C16 — truthful voice instrument

- Introduce RMS history, transient peak, delayed/smoothed response, and peak
  hold in a shared meter model.
- Retain the simple line fallback.

Acceptance: horizontal history comes from captured PCM rather than a standing
wave formula, without changing lossless capture.

#### C17 — deterministic processing instrument

- Start animation state at submission.
- Use a crisp segmented scanner with approximately 900 ms per pass.
- Do not run processing animation invisibly during recording.

Acceptance: every processing session starts in the same visible phase and stops
when the processing state ends.

### Proposed cross-agent visual iteration workflow

The workflow should be project-owned and agent-neutral. A Codex skill alone must
not become the source of truth.

Proposed canonical artifact after the first successful device loop:

```text
docs/development/device-visual-iteration.md
```

That document should define the stable protocol. Thin Codex, Claude, and Gemini
adapters may point to it and expose their available tools. If global templates,
skills, commands, MCP configuration, or wrappers are added, edit the source at
`~/.local/share/chezmoi`, inspect `chezmoi diff`, and use
`brokentooth-fleet-sync`; never patch only a generated live configuration.

The future skill should remain narrow and progressively disclosed:

```text
omniboard-device-visual-loop/
  SKILL.md                 routing, authority, stop conditions
  references/
    adb-observation.md     screenshots, UI tree, windows, SurfaceFlinger
    visual-matrix.md       states, themes, backgrounds, acceptance evidence
  scripts/                 only deterministic repeated mechanics that prove useful
```

Equivalent adapters for Claude and Gemini should reference the same project
protocol rather than duplicating it. Chezmoi may provide a shared template
fragment plus agent-specific invocation syntax.

#### One authorized iteration

```text
select one checkpoint and acceptance criterion
  -> record branch, commit, installed build, device, and rollback APK
  -> make one behavioral change
  -> run the smallest source/JVM check
  -> build only through the authorized local or factory path
  -> install only when explicitly authorized
  -> navigate to a deterministic test surface
  -> capture screenshot + UI hierarchy + focused logcat
  -> capture window/inset or frame-time evidence when relevant
  -> compare against the checkpoint acceptance criterion
  -> keep, revise once, or revert that isolated change
  -> record evidence before beginning the next checkpoint
```

The loop should use the available ADB/UI-inspector MCPs when possible, with ADB
shell as a documented fallback. Accessibility steering may navigate settings and
test surfaces after authorization, but it must not broaden into unrelated phone
control.

Recommended per-run evidence under an ignored artifact directory:

- branch, commit, APK version, build route, and device ID;
- active theme and relevant Smartbar/keyboard preferences;
- before/after screenshots at native resolution;
- UI hierarchy when it exposes useful controls;
- filtered application and IME logcat;
- `dumpsys input_method` and focused window data for inset/transparency work;
- frame statistics for animation work;
- explicit pass/fail against one acceptance criterion.

The workflow must preserve these authority boundaries:

- reconnaissance does not authorize build or install;
- a local build does not authorize a factory push;
- an install does not authorize settings changes or arbitrary accessibility
  actions;
- visual iteration does not authorize audio-pipeline rewrites, harvested-data
  mutation, repository pushes, or fleet dotfile changes;
- the stable rollback APK and currently installed build are recorded before the
  first device mutation in a run.

### Platform references

- [InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [InputMethodService.Insets](https://developer.android.com/reference/android/inputmethodservice/InputMethodService.Insets)
- [SurfaceView](https://developer.android.com/reference/android/view/SurfaceView)
- [PixelFormat](https://developer.android.com/reference/android/graphics/PixelFormat)
- [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Compose graphics](https://developer.android.com/develop/ui/compose/graphics/draw/overview)

### Open questions resolved by prototypes, not debate

- Whether 60, 64, or 68 dp is the best main chassis height on Sam's device.
- How much the left shoulder should exceed the measured reference ratio to fit
  two reliable voice controls.
- Whether the first outer silhouette exposes only top corners or also a narrow
  side/bottom contour.
- Whether an API 30+ no-image theme should bypass `SnyggSurfaceView` or use a
  lifecycle-correct transparent surface.
- Whether a later mirrored topology contributes enough to justify its width.
- Which semantic Snygg role names best match the existing element taxonomy.

These questions do not block C1-C5. Each has an explicit later checkpoint where
device evidence can decide it.
