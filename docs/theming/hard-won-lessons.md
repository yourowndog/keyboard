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

- Snygg color values accept alpha (`#RRGGBBAA` and `rgba()`), so an alpha-valued
  `window.background` reveals the target app. All bundled themes still author an
  opaque window color; the **Keyboard background opacity** preference
  (`theme__window_background_opacity`, default `100`) scales that authored alpha
  instead of replacing it, so a theme that already asked for translucency keeps
  its intent. Treat deliberate translucency as device-experimental until it is
  checked across apps, IME resize modes, navigation bars, and inline autofill.
- Visual transparency is not touch-through. `FlorisImeService.onComputeInsets()`
  still marks the measured keyboard region as touchable and obscuring.
- Portrait/landscape bottom offset is padding *inside* the themed window box.
  It enlarges and raises the input area and participates in the measured IME
  insets; it is not designed as a transparent hole.
- Navigation-icon light/dark appearance is derived from the theme's window
  color, not from whatever app is visible beneath an alpha background.

### One colour, three readers

Three places paint or reason about the window background: the Compose `SnyggBox`,
the RGBA `SnyggSurfaceView` when one exists, and the navigation-icon luminance
check in `SystemUiIme`. They must agree, or a translucent plate produces a
double-composited colour or a navigation bar whose icons are picked for a colour
nobody can see.

The opacity preference is therefore applied **once, at the stylesheet seam**:
`FlorisImeTheme` calls `SnyggStylesheet.withWindowBackgroundOpacity()` before the
stylesheet is compiled into a theme, so every reader downstream resolves the same
value. The transform resolves a `var(--…)` window background against the
`@defines` block exactly the way compilation would — one hop, not a chain — and
writes a static colour back into the `window` rules only. It never touches the
`@defines` entry itself, because that variable is usually shared with keys and
the smartbar, and fading it there would fade the whole keyboard rather than the
plate behind it. At `100` it returns the same stylesheet *instance*, so the
default costs nothing and invalidates no `remember`.

### The surface redraw race (fixed)

For a static background, `SnyggSurfaceView` uses `PixelFormat.TRANSPARENT` and
posts canvas frames from an effect. As originally written, that effect was keyed
only by the view, color, image, and content scale: surface size was not a key,
there was no `SurfaceHolder.Callback` redraw on `surfaceChanged`, and an invalid
surface aborted drawing without a retry. Changing bottom offset resizes this
separate RGBA surface, so a new or expanded buffer could briefly contain
transparent pixels and expose the app below — the reported glitch. SurfaceFlinger
confirmed the separate non-opaque layer and a single posted 1440x1115 frame for
the installed LCARS static/no-image case; the glitch itself was never safely
captured, so this was the leading mechanism rather than a reproduced proof.

Three changes close it:

- A `SurfaceHolder.Callback` bumps a generation counter on `surfaceCreated` and
  `surfaceChanged`, and that counter is a redraw key. Size deliberately is *not*
  the key: two resizes landing back on the same dimensions are still two freshly
  allocated buffers, and each needs a frame posted into it.
- `drawToSurface` clears with `PorterDuff.Mode.SRC`, not the `drawColor` default
  of `SRC_OVER`. `lockCanvas(null)` hands back a swap-chain buffer that still
  holds an earlier frame; compositing onto that is invisible for an opaque colour
  and wrong for a translucent one, where each redraw would creep towards opaque.
- An invalid surface now returns `false` rather than being swallowed, and the
  caller retries a few times at frame cadence. This covers the gap between the
  view existing and its surface being ready — exactly when a first frame would
  otherwise be dropped for good.

### The surface only exists for background images

`FlorisImeService.ImeUi` now creates the API 30+ `SnyggSurfaceView` only when the
active theme's `window` element actually declares a background image. That
surface exists to put an *image* underneath inline-autofill chips; with no image
there is nothing to put underneath anything, and the surface is pure cost — it
punches a hole through the Compose background and then has to post a frame into
every buffer it is handed. With no image the Compose background paints the window
colour on its own, alpha included, and there is no second surface to keep in step.

Inline-autofill layering is unchanged for themes that *do* carry a background
image, which is the only case where the surface was load-bearing.

### The text keyboard has one cohesive chassis layer

`TextInputLayout` renders the `keyboard-chassis` Snygg element behind the whole
text input surface: Smartbar, overflow panel, and key rows. It supports both an
ordinary Snygg background and a `background-image`; the image is decorative,
participates in the normal Compose layout, and preserves PNG alpha. This makes
one silhouette possible instead of visually stacking a Smartbar plate on a
separate keyboard plate.

The Smartbar still owns its foreground styling. Its container may be transparent
so the chassis shows through, while action icons, candidate text, tiles, and key
controls remain independently themed and can stay fully opaque. Chassis alpha
must never be multiplied into those descendants.

This is a new element rather than activating the old dormant `keyboard` rules
found in some bundled LCARS stylesheets. Those rules commonly specify a solid,
full-width background and would turn transparent-window themes back into an
opaque rectangle merely by upgrading the app. Existing themes therefore remain
visually unchanged until they opt into `keyboard-chassis`.

A transparent pixel in a chassis image reveals the Snygg `window` layer beneath
it. To reveal the target application through that cutout as well, the window
background must also have alpha. This remains visual only: Android still gives
the application and IME rectangular insets and touch regions, regardless of the
PNG's silhouette.

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
