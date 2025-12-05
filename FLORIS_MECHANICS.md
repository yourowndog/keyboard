# OmniBoard Mechanic's Bible (The "Sam" Customization Guide)

*Authored by Gemini (Codex), December 2025.*

This document captures the hard-won knowledge regarding FlorisBoard's internal layout engine, specifically tailored for advanced customization (5-row layouts, Coder/Termux workflows, and physics tuning).

**If you are an AI Agent assisting Sam, READ THIS FIRST.** It overrides standard assumptions about how this codebase works.

---

## 1. The Layout Pipeline (The Truth)

The keyboard you see is not one file. It is a Frankenstein monster sewn together at runtime.

**Path:** `extension.json` (Registry) → `LayoutManager.kt` (The Surgeon) → `TextKeyboard.kt` (The Renderer).

### The "Split Strategy" (Crucial for 5-Row Layouts)
To achieve a stable 5-row layout without the engine overwriting your keys:
1.  **Main Layout (`characters/qwerty_wide.json`):** Defines the top 4 rows (Alpha + Shift/Space row).
2.  **Mod Layout (`charactersMod/qwerty_wide_mod.json`):** Defines the 5th row (Ctrl/Arrows).
3.  **The Placeholder Hack:** The **first row** of the Mod layout MUST be `[{ "code": 0, "type": "placeholder" }]`.
    *   *Why:* `LayoutManager` merges the *last* row of Main with the *first* row of Mod. The placeholder tells it "Keep the Main row, just append the rest."
    *   *Without this:* Your Shift/Space row gets eaten.

---

## 2. Physics & Key Sizing (`TextKey.kt`)

Keys are not sized by pixels. They are sized by "weights" relative to the row.

**File:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKey.kt`

### `flayWidthFactor` (The Base Width)
*   **1.0f:** Standard Key (Alpha).
*   **1.25f:** Wide Keys (Shift, Enter). Used to anchor the sides.
*   **0.8f:** Narrow Keys (Nav Cluster: Arrows, Home, End). Used to save space.
*   **Hack:** Add `KeyCode` cases to the `when` block in `compute()` to tune specific keys.

### `flayGrow` (The Spreader)
*   **1.0f:** The key expands to consume *all* remaining space in the row.
*   **Usage:** `SPACE` has this by default.
*   **Custom Usage:** We added `VIEW_SYMBOLS` (Mod Key) to this list so the bottom row expands properly when arrow keys are shrunk.

### `flayShrink` (The Shield)
*   **1.5f:** The key resists shrinking when the row is crowded.
*   **Usage:** `SHIFT`, `DELETE`. Keeps them usable on small screens.

---

## 3. The 5-Row Vertical Hack

Standard FlorisBoard assumes 4 rows max. A 5-row layout crushes the keys into unusable slivers. We fixed this with a specific override.

**File 1: `FlorisImeSizing.kt`**
*   **Hack:** In `keyboardUiHeight()`, if `rowCount == 5`, we return `4.5 * baseHeight` instead of `5 * baseHeight`.
*   *Result:* The total keyboard is shorter, saving screen real estate.

**File 2: `TextKeyboard.kt`**
*   **Hack:** In `layout()`, we detect 5-row mode and distribute height unevenly:
    *   **Rows 0-2 (Alpha):** 100% Height.
    *   **Rows 3-4 (Nav/Mod):** 75% Height.
*   *Result:* Letters are big/easy to hit; Modifiers are compact.

---

## 4. The Popup/Hint System (The "Coder" Map)

We replaced the standard vowel accents (á, é) with Coder symbols (!, #, {, }) on the alpha keys.

**File:** `localization/popupMappings/en_wide.json` (Custom file)

### The `symbolHint` Architecture Change
Standard FlorisBoard does NOT support defining the "Hint" label in JSON. It auto-calculates it. We broke this rule.
1.  **Kotlin Change:** Modified `PopupSet.kt` to accept `symbolHint` in the constructor.
2.  **JSON Usage:** Now we can define `"a": { "symbolHint": { "code": 33, "label": "!" } }`.
3.  **Auto-Hint Disable:** We commented out the `addRowHints` block in `LayoutManager.kt`.
    *   *Why:* The auto-hinter saw 5 rows, got confused, and tried to put numbers on the wrong row or skip rows entirely.
    *   *Result:* Hints are now 100% driven by `en_wide.json`. Pure config.

---

## 5. Key Codes & Templates

**Do NOT use raw objects for letters.** Use templates.
*   `"$": "auto_text_key"` -> Standard letters (handles auto-capitalization).
*   `"$": "text_key"` -> Symbols/Punctuation.
*   `"$": "navigation"` -> Arrows, Esc, Tab (Handles icons automatically).

**Essential Coder Codes:**
*   **Undo:** `-131`
*   **Redo:** `-132`
*   **Copy:** `-31`
*   **Paste:** `-33`
*   **Select All:** `-35`
*   **Home:** `-27` (Start of Line)
*   **End:** `-28` (End of Line)
*   **PgUp/PgDn:** `-25`/`-26` (Page Start/End)

---

## 6. Troubleshooting "The Blackout"

If the layout loads but rows are empty or missing:
1.  **Check JSON Structure:** The layout file must be an **Array of Arrays** `[ [row1], [row2] ]`. A missing comma or bracket wipes the board.
2.  **Clear Cache:** FlorisBoard caches aggressively. `Settings -> Apps -> Floris -> Storage -> Clear Storage` is often the only fix for layout changes.
3.  **Extension Registry:** Did you bump the version in `extension.json`? If not, the app might not reload the assets.

---

*Use this guide to maintain the "Wide Coder" variant. It is a specialized fork logic living inside the main repo.*
