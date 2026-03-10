# State of the World — OmniBoard Keyboard
**Project:** `/home/sam/projects/keyboard-dev`
**Date:** Tuesday, March 10, 2026

This document is a comprehensive consolidation of two parallel agent sessions. It outlines the objectives, execution, successes, failures, and the exact current state of the project to ensure a smooth handoff for the antigravity team.

---

## 1. Global Objectives & User Requests
- **Space Row Redesign (Phase 2):** Implement a long-press spacebar toggle to show/hide the modifier rows (`modRowsVisible`).
- **Autocorrect & SymSpell Intelligence:** Fix "Missing Suggestions" bug where words like "app" and "fix" were aggressively corrected to "appear" and "fixed". Integrate frequency awareness into `CandidateScorer`.
- **Privacy & Security:** Stop the `HarvestManager` from logging passwords and PINs.

---

## 2. Session A: Space Row Redesign (Phase 2)
**Goal:** Wire up a long-press on the spacebar to toggle the bottom modifier rows on and off.

### What Was Done
- **`AppPrefs.kt`:** Added a `modRowsVisible` boolean preference (default `true`).
- **`LayoutManager.kt`:** Modified `mergeLayouts` to skip merging the `modifierToLoad` rows if `prefs.keyboard.modRowsVisible` is `false`.
- **`KeyboardManager.kt`:** Created `handleSpaceLongPress()` to toggle `modRowsVisible` and trigger a relayout via `updateActiveEvaluators()`. Added explicit debugging logs (`Log.i`).
- **`TextKeyboardLayout.kt`:** Wired the `onLongPress` event for `KeyCode.SPACE` and `KeyCode.CJK_SPACE` to directly call `keyboardManager.handleSpaceLongPress()`.
- **`EditRuleDialog.kt`:** Fixed a lingering compilation error related to `Keyboard.layout()` signature changes from Phase 1.

### Successes
- The code changes compiled successfully.
- An APK was generated and successfully pushed to the device via ADB.

### Failures & Unexpected Behaviors
- **Long-Press Not Firing:** In the physical app, long-pressing the spacebar does not trigger the toggle or logging correctly. The event seems to be swallowed or bypassed.
- **Catastrophic Layout Breakage on Number Toggle:** When hitting the Greek Sigma key (Number Row Toggle), the layout breaks severely. The spacebar shrinks to the size of a postage stamp, a `CTRL` key replaces the `ENTER` key, and `HOME`/`UP`/`END` arrow keys randomly appear on the left side. Crucially, there is no way to exit this broken layout to get the normal enter key back. 
- **Note:** The Dev Row toggle still works perfectly. The breakage seems isolated to how the Number Row toggle interacts with the new `modRowsVisible` layout filtering or the Phase 1 `isSpaceRow` width immunities.

---

## 3. Session B: Privacy Fixes & Autocorrect Intelligence
**Goal:** Enhance keyboard autocorrect intelligence, fix missing common words, and stop logging sensitive fields.

### What Was Done
- **`HarvestManager.kt` & `InputAttributes.kt`:** Added `isPassword` detection to abort logging for sensitive fields. (Committed to file system).
- **`unified_dictionary.tsv`:** Manually appended missing words ("app" and "fix") with a high frequency score (5,000,000).
- **`CandidateScorer.kt`:** Removed the aggressive `CULLED_SCORE` (hard block) for bigrams. Previously, this deleted intended words from the Smartbar UI entirely. Converted these hard blocks into heavy penalties (`+20.0`).
- **`SymSpellManager.kt`:** Attempted to pass SymSpell's raw frequency into the `CandidateScorer` so obscure words stop beating common words (like "this").

### Failures & Build Breakage
- **The Build is Broken:** In `SymSpellManager.kt`, an attempt was made to access `candidate.count` from SymSpell's `SuggestItem`. This threw an `Unresolved reference 'count'` compilation error.
- The property name in version 3.4.0 of `com.darkrockstudios.symspellkt` is likely different (e.g., `frequency`, `freq`, or `weight`), but this was not resolved before the build failed.

---

## 4. Current State of the Codebase
- **Compilation Status:** **FAILING**. `app:compileDebugKotlin` is failing because of two unresolved references to `count` in `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt` (lines 433 and 519).
- **Installed APK:** The last successfully built APK on the device contains the Space Row Phase 2 changes, but it exhibits the severe layout breakage when the Number toggle is used, and the spacebar long-press is non-functional.
- **Git Status:** Changes from Session B (Privacy + Autocorrect) are currently unverified and causing the build failure. Changes from Session A are compiled into the broken APK but may or may not be committed.

---

## 5. Next Steps for the Antigravity Team
1. **Fix the Build (Immediate):**
   - Open `SymSpellManager.kt` and resolve the `candidate.count` property. Check autocomplete/library docs to see if it should be `frequency` or `freq`.
2. **Fix the Number Toggle Layout Bug:**
   - Investigate why the Greek Sigma toggle results in a tiny spacebar, arrows on the left, and a missing Enter key. Look closely at `LayoutManager.kt` and `TextKeyboard.kt` (specifically the `isSpaceRow` detection and `widthFactor` logic introduced in Phase 1 interacting with the `modRowsVisible` filter).
3. **Fix Spacebar Long-Press:**
   - Diagnose why `TextKeyboardLayout.kt` is failing to fire `keyboardManager.handleSpaceLongPress()`. The gesture dispatcher may be interpreting it as a swipe, or another gesture handler is consuming the event first.
4. **Deploy and Verify:**
   - Once compiling and fixed, push to device via ADB to verify the layout remains stable, the mod rows toggle correctly, passwords are not harvested, and "app"/"fix" suggest correctly.