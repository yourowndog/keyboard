# PROJECT OMNIBOARD: "REFLEXES + BRAINS" BLUEPRINT

**Status:** Phase 1 Complete (Green Build). Phase 2 (Data Injection) Pending.
**Architecture:** Hybrid Prediction Engine (SymSpell + Gemma 2B).

---

## 1. THE ARCHITECTURE

We are replacing the stock FlorisBoard prediction engine with a two-tier system:

### Tier 1: "Reflexes" (SymSpell)
*   **Role:** Sub-millisecond typo correction (e.g., "teh" -> "the") and context correction via Bigrams (e.g., "High School" vs "High Skull").
*   **Engine:** `SymSpellKt` v3.4.0 (Kotlin Multiplatform).
*   **Fuel:**
    *   **Unigrams:** A static "Rocket Fuel" frequency dictionary (~82,000 words).
    *   **Bigrams:** A frequency pair list (~243,000 pairs) for context.
    *   **User Dict:** Dynamic injection of user words (e.g., "Kiry") with infinite score.
*   **Status:** WIRED & COMPILING. Currently running on a 5-word dummy list.

### Tier 2: "Brains" (Gemma 2B)
*   **Role:** Next-word prediction / "Ghostwriter" (e.g., "I am going to the..." -> "store", "beach").
*   **Engine:** MediaPipe GenAI Tasks (`LlmInference` API).
*   **Fuel:** On-device LLM (`.task` file).
*   **Status:** CODE EXISTS (`GemmaBridge.kt`) BUT UNTESTED. Integrated but dormant.

---

## 2. THE ASSET REGISTRY (CRITICAL PATHS)

**A. The AI Model (Gemma)**
*   **Device Location:** `/data/local/tmp/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task`
*   **Integration File:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/GemmaBridge.kt`
*   **Current State:** Code checks for this file. If missing, it fails gracefully.

**B. The Dictionary ("Rocket Fuel")**
*   **Target Location:** `app/src/main/assets/ime/dict/`
*   **Required Files:**
    1.  `frequency_dictionary_en.txt` (Unigrams, ~82k words)
    2.  `frequency_bigram_en.txt` (Bigrams, ~243k pairs)
*   **Status:** MISSING. Needs to be downloaded and placed in the assets folder.

**C. The User Dictionary**
*   **Access Class:** `UserDictionaryDao` (Interface).
*   **Location:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/UserDictionary.kt`
*   **Plan:** Query DAO and inject words into SymSpell with `Long.MAX_VALUE` frequency.

---

## 3. THE CODEBASE STATE (Snapshot)

**`app/build.gradle.kts`**
*   **Status:** Cleaned via "Nuclear Rewrite."
*   **Key Deps:** `SymSpellKt-android:3.4.0` (Reflexes), `tasks-genai:0.10.14` (Brains).

**`app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt`**
*   **Status:** GREEN. Compiles.
*   **Logic:** Uses `SpellCheckSettings` (maxEditDistance: 2, prefixLength: 7, countThreshold: 1).

**`app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt`**
*   **Status:** WIRED.
*   **Logic:**
    *   `suggest()` calls `SymSpellManager.suggest()` (Reflexes).
    *   `suggest()` calls `GemmaBridge.predictNextWord()` (Brains) if no current word is being typed.

---

## 4. IMPLEMENTATION PLAN

1.  **Download Rocket Fuel:** Get the unigram and bigram files and place them in `app/src/main/assets/ime/dict/`.
2.  **Update SymSpellManager:**
    *   Modify `init()` to read the asset files line-by-line.
    *   Modify `init()` to query `UserDictionaryDao` and inject custom words.
3.  **Test Gemma:**
    *   Push build to phone.
    *   Type a full sentence and wait for suggestions.
    *   Debug via logcat if silent.
