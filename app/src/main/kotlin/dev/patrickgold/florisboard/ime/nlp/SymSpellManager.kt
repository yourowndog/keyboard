package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import com.darkrockstudios.symspellkt.api.SymSpell
import com.darkrockstudios.symspellkt.impl.createSymSpell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SymSpellManager {
    private var symSpell: SymSpell? = null
    @Volatile private var isReady = false

    // Config: Max edit distance of 2 is standard for mobile typing
    private const val MAX_EDIT_DISTANCE = 2
    private const val PREFIX_LENGTH = 7

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // Initialize Engine
                val instance = createSymSpell(MAX_EDIT_DISTANCE, PREFIX_LENGTH)
                
                // TODO: In the future, load the real `ime/dict/data.json` or frequency list here.
                // For now, we seed a small dummy dictionary to prove the pipeline works.
                val dummyWords = listOf("the 23000", "and 20000", "hello 15000", "world 10000", "floris 500")
                dummyWords.forEach {
                    val parts = it.split(" ")
                    instance.createDictionaryEntry(parts[0], parts[1].toLong())
                }

                symSpell = instance
                isReady = true
                android.util.Log.i("SymSpellManager", "Reflexes Engine Loaded.")
            } catch (e: Exception) {
                android.util.Log.e("SymSpellManager", "Reflexes Failed", e)
            }
        }
    }

    // Fast Autocorrect (For "Reflexes")
    fun fix(input: String): String {
        if (!isReady) return input
        val instance = symSpell ?: return input
        if (input.length < 2) return input

        // Verbosity.TOP gets the single best match
        val suggestions = instance.lookup(input, SymSpell.Verbosity.TOP, MAX_EDIT_DISTANCE)
        return suggestions.firstOrNull()?.term ?: input
    }
    
    // Suggestion List (For "Smartbar")
    fun suggest(input: String): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()
         
         // Get closest matches
         val suggestions = instance.lookup(input, SymSpell.Verbosity.CLOSEST, MAX_EDIT_DISTANCE)
         return suggestions.map { it.term }
    }
}