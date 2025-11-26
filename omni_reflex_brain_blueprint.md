📂 PROJECT OMNIBOARD: "REFLEXES + BRAINS" BLUEPRINT

Status: Phase 1 Complete (Green Build). Phase 2 (Data Injection) Pending. Architecture: Hybrid Prediction Engine (SymSpell + Gemma 2B).

1. THE ARCHITECTURE

We are replacing the stock FlorisBoard prediction engine with a two-tier system:

    Tier 1: "Reflexes" (SymSpell)

        Role: Sub-millisecond typo correction (e.g., "teh" -> "the").

        Engine: SymSpellKt v3.4.0 (Kotlin Multiplatform).

        Fuel: A static "Rocket Fuel" frequency dictionary (~82,000 words) + Dynamic User Dictionary injection.

        Status: WIRED & COMPILING. Currently running on a 5-word dummy list.

    Tier 2: "Brains" (Gemma 2B)

        Role: Next-word prediction (e.g., "I am going to the..." -> "store").

        Engine: MediaPipe GenAI Tasks (LlmInference API).

        Fuel: On-device LLM (.task file).

        Status: CODE EXISTS (GemmaBridge.kt) BUT UNTESTED. We integrated the code in "Turn 14" but haven't turned it on yet because we were fighting the SymSpell build errors.

2. THE ASSET REGISTRY (CRITICAL PATHS)

Do not lose these locations.

A. The AI Model (Gemma)

    Device Location: /data/local/tmp/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task

    Integration File: app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/GemmaBridge.kt

    Current State: The code in GemmaBridge checks for this file. If missing, it fails gracefully (returns null). We need to verify this file is still on your phone.

B. The Dictionary ("Rocket Fuel")

    Source File: frequency_dictionary_en_82_765.txt (Standard SymSpell English corpus).

    Target Location: app/src/main/assets/ime/dict/frequency_dictionary_en.txt

    Status: MISSING. You need to download this file and push it to this location on your laptop/repo.

C. The User Dictionary

    Access Class: UserDictionaryDao (Interface).

    Location: Found by Agent in app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/UserDictionary.kt.

    Plan: We will query this DAO and inject words into SymSpell with Long.MAX_VALUE frequency so they override the "Rocket Fuel."

3. THE CODEBASE STATE (Snapshot)

app/build.gradle.kts

    Status: Cleaned via "Nuclear Rewrite."

    Dependencies:

        implementation("com.darkrockstudios:SymSpellKt-android:3.4.0") (Reflexes)

        implementation("com.google.mediapipe:tasks-genai:0.10.14") (Brains)

app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt

    Status: GREEN. Compiles successfully.

    Logic: Uses SpellCheckSettings object (Configuration Pattern).

    Configuration:

        maxEditDistance: 2 (Int)

        prefixLength: 7 (Int)

        countThreshold: 1 (Long) - This was the hard-won fix.

        distance: Double (in lookup calls).

app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/GemmaBridge.kt

    Status: DORMANT. Created in Turn 14.

    Logic: Wraps LlmInference.

    Action Needed: Once dictionary is loaded, we need to verify GemmaBridge.predictNextWord() is actually being called in LatinLanguageProvider.

app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt

    Status: WIRED.

    Logic: We "gutted" the old logic.

        suggest() calls SymSpellManager.suggest() (Reflexes).

        suggest() also calls GemmaBridge.predictNextWord() (Brains) if no current word is being typed.

4. THE ROADMAP (Next Steps)

    Verify Laptop Build: You just ran git clean -fd and git reset --hard. Run ./gradlew assembleDebug on the laptop to confirm it matches the phone's green state.

    Download Rocket Fuel: Get frequency_dictionary_en_82_765.txt and place it in app/src/main/assets/ime/dict/.

    Update SymSpellManager (The Loader):

        Modify init() to read the asset file line-by-line.

        Modify init() to query UserDictionaryDao and inject custom words.

    Test Gemma:

        Push the build.

        Type a full sentence ("I am going to the").

        Wait. See if Gemma suggests "store" in the Smartbar.

        If not, check logcat for GemmaBridge errors (file permission issues in /data/local/tmp are common).

5. THE "CARRY OVER" PROMPT

If you ever need to start a fresh session with a new AI, paste this block immediately. It brings them up to speed instantly.

    SYSTEM CONTEXT: PROJECT OMNIBOARD I am developing a fork of FlorisBoard (Android) with a Hybrid "Reflexes + Brains" architecture.

    1. CURRENT STATE:

        Reflexes: SymSpellKt v3.4.0 is integrated and compiling. It is currently using a dummy 5-word list in SymSpellManager.kt.

        Brains: MediaPipe GenAI (tasks-genai:0.10.14) is integrated via GemmaBridge.kt. The model file is at /data/local/tmp/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task.

        Build: The project builds successfully after fixing SpellCheckSettings type mismatches (Double for distance, Long for countThreshold).

    2. IMMEDIATE GOAL: We need to replace the dummy dictionary in SymSpellManager with "Rocket Fuel."

        Asset: Load frequency_dictionary_en_82_765.txt from assets.

        User Data: Inject words from UserDictionaryDao with max frequency.

    3. KEY FILE LOCATIONS:

        app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/SymSpellManager.kt (The Engine)

        app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/GemmaBridge.kt (The LLM Interface)

        app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt (The Decision Logic)

    DO NOT suggest changing build.gradle.kts dependencies (we already solved the KMP variant issues). DO NOT suggest changing SpellCheckSettings types (we verified them: Int, Int, Long).
