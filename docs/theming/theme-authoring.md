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

## Built-in promotion

When promoting a theme:

1. Add or update its entry in the correct bundled `extension.json`.
2. Copy the validated stylesheet into that extension's `stylesheets/`.
3. Check IDs and filenames for exact agreement.
4. Build and install the app.
5. Force a fresh IME process and select the theme.
6. Run the full coverage checklist.

