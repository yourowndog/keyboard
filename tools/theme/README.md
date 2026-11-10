# Theme tooling

## `validate_stylesheets.py`

Static validation of every packaged Snygg stylesheet. Run it after any theme
edit:

```bash
python3 tools/theme/validate_stylesheets.py
```

Requires `jsonschema`. It checks the three failure modes that have silently
shipped in this repository before:

1. **Dead elements** — a rule naming an element no composable queries. The rule
   parses fine and does nothing. Several shipped themes carried these.
2. **Schema-invalid values** — checked against the generated
   `lib/snygg/schemas/stylesheet.schema.json`, which is authoritative.
3. **Unknown key codes** — a `[code=...]` selector naming a code absent from
   `KeyCode.kt`, so the rule matches no key.

The schema's regexes use ECMA named groups; the script rewrites them to Python
syntax before validating.

This is static checking only. It cannot tell you a theme looks right, and it is
not a substitute for device validation.

## `build_jefferies.py`

Generates the LCARS Jefferies stylesheet variants from a single authored base:

```bash
python3 tools/theme/build_jefferies.py
```

Sources:

- `jefferies/jefferies.base.json` — the authored stylesheet, with
  `__FONT_FAMILY__` and `__FONT_FILE__` placeholders.
- `jefferies/variants.json` — the font table. One entry per generated theme.

The script writes the stylesheets into the `org.florisboard.themes` extension
and rewrites that extension's theme registry, dropping any previously generated
Jefferies entry so deleting a variant from `variants.json` unregisters it.

**The generated stylesheets are committed but are not the source.** Edit the
base or the font table and rerun. They carry no in-file "generated" banner
because the stylesheet schema sets `additionalProperties: false` at the root and
permits only `$schema`, so any marker key would make the file invalid.

### Ending the font bakeoff

Once a typeface is chosen, delete the losing entries from `variants.json`,
remove their `.ttf` and `OFL-*.txt` files, give the winner an empty `suffix`,
and rerun the script.
