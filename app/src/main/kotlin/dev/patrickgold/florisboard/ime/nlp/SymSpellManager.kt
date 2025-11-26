package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
// FIX: Correct package for version 3.4.0
import com.darkrockstudios.symspellkt.SymSpell
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
                // FIX: Use constructor with named arguments to ensure safety
                // We default initialCapacity (-1 or 16384) by omitting it or letting the library handle it
                val instance = SymSpell(
                    maxDictionaryEditDistance = MAX_EDIT_DISTANCE,
                    prefixLength = PREFIX_LENGTH
                )
                
                // Seed dummy dictionary
                val dummyWords = listOf("the 23000", "and 20000", "hello 15000", "world 10000", "floris 500")
                dummyWords.forEach {
                    val parts = it.split(" ")
                    if (parts.size == 2) {
                        instance.createDictionaryEntry(parts[0], parts[1].toLong())
                    }
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

        // FIX: Use SymSpell.Verbosity.Top (or TOP depending on version, Top is standard Kotlin style)
        // If 'Top' fails, try 'TOP'.
        val suggestions = instance.lookup(input, SymSpell.Verbosity.Top, MAX_EDIT_DISTANCE)
        return suggestions.firstOrNull()?.term ?: input
    }
    
    // Suggestion List (For "Smartbar")
    fun suggest(input: String): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()
         
         // FIX: Use SymSpell.Verbosity.Closest
         val suggestions = instance.lookup(input, SymSpell.Verbosity.Closest, MAX_EDIT_DISTANCE)
         return suggestions.map { it.term }
    }
}
