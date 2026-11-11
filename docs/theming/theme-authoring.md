# Authoring and Packaging a Theme

> Status: Canonical workflow  
> Last verified: 2026-07-11  
> Verified against: bundled theme extensions, Snygg schema, extension loading,
> and historical LCARS package iterations

## Choose the source of truth

- Built-in theme: edit the registered stylesheet under
  `app/src/main/assets/ime/theme/` and rebuild the app.
- External iteration: build a `.flex` extension package and install/reload it.

Do not edit an extracted or numbered `.flex` snapshot and assume the built-in
asset changed.

## Package structure

```text
extension.json
stylesheets/
  theme_id.json
```

The manifest is a theme extension whose theme IDs correspond exactly to files
under `stylesheets/`. Metadata identifies the extension; each theme entry
identifies a selectable stylesheet.

Before copying an old manifest, compare it to a current bundled theme extension.
Extension metadata evolved and old agent manuals may contain optional or stale
fields.

## Authoring sequence

1. Inspect `FlorisImeUi.entries` and choose required surface coverage.
2. Confirm every numeric key code in `KeyCode.kt`.
3. Define a semantic palette in `@defines`.
4. Establish base window, keyboard, key, pressed, hint, and popup rules.
5. Add intentional key groups and active toggle states.
6. Style smartbar, clipboard, media, action editor, subtype, and auxiliary
   surfaces.
7. Validate properties and values against the generated Snygg schema.
8. Validate the final archive contents, not only loose source files.
9. Install and recreate the IME process if cached assets obscure the result.
10. Test rapid pressed feedback, latched toggles, popups, and secondary panels.

## Rapid external iteration

Generate packages into an ignored artifact directory. Retain editable source,
not every numbered `.flex` build. Promote an accepted design into built-in
assets deliberately.

### Native LCARS palette editor

The configuration surface is Settings → Theme → Manage theme extensions → Edit
the writable extension → Edit its theme component. Built-in extensions are
read-only (their Edit button is disabled); use an imported/custom extension.
Keep `lcars` in the theme component ID for the analytic-renderer opt-in.

In the component editor, add/edit these rules and their **Background** property:

- **Keyboard chassis** (`keyboard-chassis`): deck color and alpha.
- **Keyboard chassis primary rail** (`keyboard-chassis-rail-primary`): outer rail color and alpha.
- **Keyboard chassis secondary rail** (`keyboard-chassis-rail-secondary`): inner rail color and alpha.

Alpha is part of the color value, not a separate `opacity` property. Apply the
property dialog, close the component editor, then **Save** in the parent
extension editor. Save serializes the stylesheet into the `.flex` archive;
the extension file observer refreshes the index and invalidates the active
theme cache. Select the saved component as the day/night theme as appropriate.
Editor preview alone is not proof that the active IME has loaded the save.

The active generic `SnyggSpec` registers Background for every element rule.
The commented-out legacy `FlorisImeUiSpec.kt` is not the registration source.
`LcarsPaletteRoundTripTest` exercises property availability and edited alpha
through serialization, reloading, compilation, and theme queries.

In the t_c93d5123 candidate (automated verification only; operator acceptance
pending), code reserves structural space around Smartbar and paints the deck
once; keys retain a full-width backing. Rail thicknesses, bend/shoulder geometry,
and clipping are code-owned, not palette-editor sliders. Image backgrounds,
Smartbar-off, missing semantic rail colors, expanded app-overlay actions, and insufficient content width use
the classic renderer. The `window` background still controls what is visible
outside the shaped chassis: a transparent deck/rail color does not override an
opaque window underneath it. Test save/reload and alpha on the actual candidate
APK before treating this path as accepted daily-driver behavior.

## Built-in promotion

When promoting a theme:

1. Add or update its entry in the correct bundled `extension.json`.
2. Copy the validated stylesheet into that extension's `stylesheets/`.
3. Check IDs and filenames for exact agreement.
4. Build and install the app.
5. Force a fresh IME process and select the theme.
6. Run the full coverage checklist.

