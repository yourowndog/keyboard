1. The "Reflexes" (SymSpell)

Role: Instant Correction & Context. Engine: SymSpellKt

This is the "muscle memory" of the keyboard. It handles two things:

    A. The Unigrams (Single Words):

        What it does: Fixes "teh" -> "the".

        How: Uses the 82k word list to check spelling distance + frequency.

        User Dictionary: We inject "Kiry" here so it never corrects her name.

    B. The Bigrams (The "Bi-Whatevers"):

        What it does: Fixes context errors like "High School" vs "High Skull".

        How: It looks at the pair of words. Even if "Skull" is spelled correctly, SymSpell sees that "High School" has a massive frequency score and "High Skull" has zero.

        Why it's Reflexive: It happens while you type. It doesn't need to "think." It just looks up the pair in a massive spreadsheet. This is natively supported by SymSpell's lookupCompound method.

2. The "Brains" (Gemma)

Role: Creativity & Next-Word Prediction. Engine: MediaPipe + Gemma 2B

This is the "Ghostwriter" that sits quietly until you stop typing.

    What it does: It predicts the future. It doesn't fix what you typed; it guesses what you want to type next.

    Example: You type "I am going to the".

        Reflexes are done. They ensured "going" and "the" are spelled right.

        Brains wake up. Gemma reads the sentence and suggests: "store", "park", "beach".

    UX (User Experience):

        These appear in the Center Suggestion Slot.

        They are passive suggestions. You have to tap them to use them.

        This is the "Enterprise" feel—it learns your tone (formal vs. slang) based on the context window.

3. The "Rocket Fuel" (Download Links)

You need to download these two text files to power the Reflexes (SymSpell).

Instructions:

    Download both files below.

    (Optional) Rename them to frequency_dictionary_en.txt and frequency_bigram_en.txt for simplicity.

    Place them here: app/src/main/assets/ime/dict/

File 1: The Words (Unigrams) This is the main dictionary (82,000 words).

    Download: frequency_dictionary_en_82_765.txt

File 2: The Pairs (Bigrams) This is the context layer (243,000 pairs).

    Download: frequency_bigramdictionary_en_243_342.txt

4. How We Wire It Up

Once you have those files in the folder, we will update SymSpellManager.kt to do this:

    Init:

        Load frequency_dictionary_en.txt -> Unigram Store.

        Load frequency_bigram_en.txt -> Bigram Store.

        Query UserDictionaryDao -> Inject User Words (with infinite score).

    Usage:

        When you type a word: Call lookup() (Uses Unigrams).

        When you finish a sentence/phrase: Call lookupCompound() (Uses Bigrams to fix the whole string).

Next Step: Get those two files into your assets/ime/dict/ folder. Once they are there, we write the loader code.
