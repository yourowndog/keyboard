# OMNIBOARD CARRYOVER CONTEXT (SESSION HANDOFF)

**Status:** 🟢 Build Green. 🟡 Feature Incomplete (Bigrams Logic).

## 1. The Mission
We are replacing the stock FlorisBoard prediction engine with a hybrid **SymSpell (Reflexes) + Gemma LLM (Brains)** architecture.
Currently, we are polishing the **Reflexes (SymSpell)**.

## 2. Achievements (What works)
*   **Dictionary Surgery:** We replaced the massive 35MB web-crawl dictionary with a **"Platinum List" (2.7MB)**.
    *   **Content:** Top 15,000 words + ALL Contractions.
    *   **Result:** `brin` -> `bring` works (garbage "brin" deleted). `they're` is safe. Lag is gone.
*   **Logic Fixes:**
    *   **Single-Letter Bug:** Removed `input.length < 2` check. Now corrects `i` -> `I` (technically).
    *   **Loading:** Switched to `readBytes()` for instant dictionary loading.

## 3. The Problems (Loose Threads)
*   **"I" Malfunction:** The keyboard aggressively capitalizes "i" to "I" due to a hardcoded block in `SymSpellManager.applyCasingPattern`. This interferes with typing words like "I'm" (becoming "Im").
*   **"Your" vs "You're":** `your` wins purely on frequency (3.9M vs 0.2M). The engine ignores context.
*   **Bigrams are DEAD:**
    *   We load `frequency_bigram_en.txt` (4.9MB) into RAM.
    *   **We NEVER use it.** `SymSpellManager.suggest()` calls `lookup()` (Unigram only).
    *   The app is context-blind.

## 4. The Path Forward (Technical Implementation)
The next agent must implement **Bigram Re-ranking**:

1.  **Modify `SymSpellManager.suggest()`:**
    *   Add parameter: `prevWord: String?`.
    *   Get standard candidates (Unigrams).
    *   **Re-rank:** Iterate candidates. Query `bigramDictionary` for `"$prevWord $candidate"`. Boost score if pair exists.
    *   *Note:* `SymSpellKt`'s `InMemoryDictionaryHolder` exposes bigrams. You might need to add a helper method to access them if `SymSpell` class doesn't expose a "score with context" method.

2.  **Modify `LatinLanguageProvider.kt`:**
    *   Update the call to `SymSpellManager.suggest`.
    *   Pass the previous word from the `precedingWords` list.

3.  **Fix "I" Logic:**
    *   Delete the manual `original == "i" -> return "I"` block in `SymSpellManager`. Let the frequency dictionary handle it.

## 5. Resources
*   **Dictionary Script:** `utils/format_dicts.py` (Python script used to generate the Platinum List).
*   **Dictionaries:** `app/src/main/assets/ime/dict/` (Currently loaded with the optimized files).

---
*AGENT INSTRUCTION: Read this context, perform the necessary code changes to enable Bigrams and fix the casing logic, then DELETE this file (`CARRYOVER_PROMPT.md`).*
