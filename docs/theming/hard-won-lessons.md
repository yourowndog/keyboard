# Snygg Hard-Won Lessons

> Status: Canonical operational guidance  
> Last verified: 2026-07-11  
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

