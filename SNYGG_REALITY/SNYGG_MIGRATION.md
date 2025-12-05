# Migrating from "The Lies" to Reality

If you were following `SNYGG_ENGINE_SPEC.md` (SNYGG1), here is what you need to un-learn.

## 1. CSS Grouping is Fake
**The Lie:** The manual implied standard CSS grouping worked:
`"key, smartbar": { ... }`

**The Reality:** The regex parser in `SnyggRule.kt` expects strictly **one** element pattern.
Comma-separated rules will fail silently or cause parse errors.

**Solution:** Duplicate the rule block for each element, or use variable defines to share values.

## 2. Missing Properties
**The Lie:** You can set `width`, `height`, `opacity`.

**The Reality:**
*   `width`/`height`: These properties are **not in the schema**. Snygg cannot resize keys. That is the job of the Layout Engine (JSON layouts).
*   `opacity`: Use RGBA colors. `rgba(255, 255, 255, 0.5)`.

## 3. Gradient Support
**The Lie:** `linear-gradient(...)` works.

**The Reality:** The Kotlin classes exist but are commented out/disabled in the parser. Only solid colors (`rgba`, `hex`) work for now.

## 4. Attributes List
**The Lie:** Attributes like `[group="..."]` are special hardcoded filters.

**The Reality:** `SnyggAttributes` parses *any* key-value pair. The Layout Engine attaches data like `code`, `group`, `shiftstate`. You can theoretically match anything the engine attaches, but `code` and `group` are the primary ones.

## 5. Key Codes
**The Lie:** Some manuals list Backspace as `-7` or `-9`.

**The Reality:** While legacy codes might exist, the modern standard in `KeyCode.kt` often maps standard Backspace to `-4`. Always check the `SNYGG_CHEATSHEET.md` or the source code (`KeyCode.kt`) if a key isn't styling correctly.
