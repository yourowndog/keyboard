# Snygg Hard-Won Lessons

> Status: Canonical operational guidance  
> Last verified: 2026-07-12
> Sources: live theme implementation plus reconciled theme manuals, testing
> checklists, agent handoffs, and LCARS iterations

## Verify the process, not just the file

An Android IME process can outlive an APK replacement or theme asset change.
When an apparently valid theme edit has no effect:

1. Confirm the installed build contains the edited asset.
2. Switch away from OmniBoard and force-stop it, or otherwise ensure the IME
   process is recreated.
3. Re-select the theme.
4. Only then conclude that selector matching failed.

This cache/process lesson is durable. The earlier conclusion that custom state
attributes were necessarily broken is not.

## Style the full IME

A keyboard theme is incomplete if only `window`, `key`, and `key:pressed` are
styled. Check at least:

- alphabet, number, modifier, navigation, command, and view-switch keys
- hints and popups
- candidates and smartbar actions
- actions overflow and editor
- clipboard normal, empty, disabled, locked, dialogs, and item actions
- emoji tabs, keys, popups, and bottom row
- subtype and one-handed panels
- extracted landscape input
- inline autofill, incognito indicator, and glide trail

Use `FlorisImeUi.entries` as the coverage inventory. A fixed minimum rule count
is a useful smell test, not proof of completeness.

## Preserve hierarchy

The successful LCARS work repeatedly relied on visual grouping:

- Alpha keys form the quiet baseline.
- Command keys carry the strongest accents.
- Modifier and navigation groups have related but distinguishable treatments.
- Number and developer rows read as intentional bands.
- Clipboard, media, and action panels remain in the same visual family while
  having their own zone accents.

These are design principles, not engine constraints.

## Pressed states must survive real typing

Pressed feedback is brief. Test it at typing speed, not only by holding a key.
Active toggles currently reuse `:pressed`, so confirm both momentary and latched
states. A theme can look correct in a static screenshot while providing poor
feedback during use.

Ctrl and the Tmux prefix need special care. Ctrl remains in `:pressed` while it
is latched. After handing Ctrl+B to an active input connection, Tmux keeps a
keyboard-owned visual latch until the next non-Tmux key is released or the
input session ends. The `org.florisboard.themes` LCARS Tactical and Neon styles
give both keys a high-contrast active color; a small brightness or text-only
change on an already bright command key was not perceptible in normal use.

## Transparency and the IME surface

The Android IME host window is transparent-format at runtime. OmniBoard normally
looks opaque because the themed Snygg `window` element paints the visible input
area. This was verified on the Android 16 daily-driver device against the
installed `e39bb6ea` build: the host reported `fmt=TRANSPARENT`, while LCARS
Tactical resolved `window.background` to opaque black.

The visible path is:

```text
transparent Android input-method window
  -> Compose SnyggBox for the measured `window` element
  -> API 30+ RGBA SnyggSurfaceView behind Compose/inline-autofill content
  -> target application's compositor surface underneath
```

Important consequences:

- Snygg color values accept alpha, so an alpha-valued `window.background` can
  plausibly reveal the target app. All bundled themes currently use an opaque
  window color. Treat deliberate translucency as device-experimental until it
  is checked across apps, IME resize modes, navigation bars, and inline
  autofill.
- Visual transparency is not touch-through. `FlorisImeService.onComputeInsets()`
  still marks the measured keyboard region as touchable and obscuring.
- Portrait/landscape bottom offset is padding *inside* the themed window box.
  It enlarges and raises the input area and participates in the measured IME
  insets; it is not designed as a transparent hole.
- Navigation-icon light/dark appearance is derived from the theme's window
  color, not from whatever app is visible beneath an alpha background.

There is a strongly supported redraw race in the API 30+ surface path. For a
static background, `SnyggSurfaceView` uses `PixelFormat.TRANSPARENT`, then posts
one canvas frame from an effect keyed by the view, color, image, and content
scale. Surface size is not a key, there is no `SurfaceHolder.Callback` redraw on
`surfaceChanged`, and an invalid surface aborts drawing without a retry.
Changing bottom offset resizes this separate RGBA surface. A new or expanded
buffer can therefore briefly contain transparent pixels and expose the app
below, which matches the reported glitch. For the installed LCARS
static/no-image case, SurfaceFlinger confirmed the separate non-opaque layer and
a single posted 1440x1115 frame; the resize glitch itself was not safely
captured, so this remains the leading mechanism rather than a reproduced proof.

Any future reliability fix should redraw on `surfaceCreated` and
`surfaceChanged`, include size in redraw state, and retry after an invalid
surface. Avoiding the separate surface when no background image is present may
also help, but inline-autofill layering must be checked before doing that.

## Geometry belongs elsewhere

Snygg margin and padding affect themed content, but keyboard allocation,
touch targets, row sizing, and per-key physical customization live in the
keyboard geometry pipeline. Do not attempt to repair a hitbox or row-width bug
with invented Snygg width properties.

## Packaging

A `.flex` theme is a ZIP-compatible extension archive whose manifest registers
stylesheet IDs and whose `stylesheets/` files match those IDs. Validate the
manifest and stylesheet inside the final archive; validating a loose working
copy does not prove the package contains it.

For built-in themes, update the bundled extension registry and stylesheet
assets, then rebuild the app. For rapid external iteration, package a `.flex`
and test extension reload/process behavior separately from built-in assets.
