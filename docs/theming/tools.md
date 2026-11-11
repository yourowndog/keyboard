# Snygg Theme Authoring Tools

> Status: Canonical reference  
> Tools: `tools/theming/snygg_tool.py`, `tools/previewer/index.html`

## Overview

OmniBoard provides tooling to make theme development fast, verifiable, and repeatable:

1. **Validation & Linting (`tools/theming/snygg_tool.py validate`)**: Verifies Snygg v2 syntax, rule selectors, pseudo-classes, attribute filters, variable references (`@defines`), and element property specs against `FlorisImeUiSpec.kt`. Also validates extension manifests (`extension.json`).
2. **Element Coverage Audit (`tools/theming/snygg_tool.py audit`)**: Audits element rule coverage against `FlorisImeUi.entries` (39 UI surfaces including keyboard, smartbar, clipboard, media, and auxiliary panels).
3. **Packaging & Extraction (`tools/theming/snygg_tool.py package / unpack`)**: Packages theme source directories into validated `.flex` archives and unpacks existing packages for editing.
4. **Web Previewer (`tools/previewer/index.html`)**: Fast visual preview for layouts and themes (supports live hot-reload, switching between LCARS variants, and testing styling without waiting for an Android build).

---

## CLI Usage (`snygg_tool.py`)

### 1. Validate a Stylesheet or Extension

To validate a single Snygg JSON stylesheet:
```bash
python3 tools/theming/snygg_tool.py validate app/src/main/assets/ime/theme/com.brokentooth.lcars/stylesheets/lcars_tactical.json
```

To validate an entire theme extension directory:
```bash
python3 tools/theming/snygg_tool.py validate app/src/main/assets/ime/theme/com.brokentooth.lcars/
```

### 2. Audit Element Coverage

Check which `FlorisImeUi` elements are explicitly styled and which are inherited:
```bash
python3 tools/theming/snygg_tool.py audit app/src/main/assets/ime/theme/com.brokentooth.lcars/stylesheets/lcars_neon.json
```
Output shows total rules, element count, coverage percentage, and lists any unstyled surfaces.

### 3. Package an Extension into `.flex`

Package an extension directory into a `.flex` archive for rapid sideloading or sharing:
```bash
python3 tools/theming/snygg_tool.py package app/src/main/assets/ime/theme/com.brokentooth.lcars/ -o ~/.hermes/cache/scratch/lcars.flex
```
*(Validation runs automatically before packaging; packaging aborts if errors are found).*

### 4. Unpack a `.flex` Package

Extract a `.flex` file to inspect or edit its contents:
```bash
python3 tools/theming/snygg_tool.py unpack my_theme.flex -o my_theme_source/
```

---

## Testing

Run unit tests for the theme authoring tools:
```bash
python3 -m unittest tools/theming/tests/test_snygg_tool.py
```
