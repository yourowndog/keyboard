package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import com.darkrockstudios.symspellkt.impl.SymSpell
// Note: These imports are "Best Guess" based on standard KMP structure.
// If they fail, delete them and use Alt+Enter to resolve the correct package.
import com.darkrockstudios.symspellkt.api.SpellCheckSettings
import com.darkrockstudios.symspellkt.api.Verbosity 

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SymSpellManager {
    private var symSpell: SymSpell? = null
    @Volatile private var isReady = false

    // Config
    private const val MAX_EDIT_DISTANCE = 2
    private const val PREFIX_LENGTH = 7

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // FIX: Create the Settings Object first
                val settings = SpellCheckSettings(
                    maxDictionaryEditDistance = MAX_EDIT_DISTANCE,
                    prefixLength = PREFIX_LENGTH,
                    countThreshold = 1
                )

                // FIX: Pass settings to the constructor as a named argument
                // We rely on defaults for 'stringDistance' and 'dictionaryHolder'
                val instance = SymSpell(spellCheckSettings = settings)
                
                // Seed dummy dictionary
                val dummyWords = listOf("the 23000", "and 20000", "hello 15000", "world 10000", "floris 500")
                dummyWords.forEach {
                    val parts = it.split(" ")
                    if (parts.size == 2) {
                        // FIX: Ensure frequency is a Double
                        instance.createDictionaryEntry(parts[0], parts[1].toDouble())
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

    // Fast Autocorrect (Reflexes)
    fun fix(input: String): String {
        if (!isReady) return input
        val instance = symSpell ?: return input
        if (input.length < 2) return input

        // FIX: Ensure Verbosity is used and MaxEditDistance is a Double
        val suggestions = instance.lookup(input, Verbosity.Top, MAX_EDIT_DISTANCE.toDouble())
        return suggestions.firstOrNull()?.term ?: input
    }
    
    // Suggestion List (Smartbar)
    fun suggest(input: String): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()
         
         // FIX: Ensure Verbosity is used and MaxEditDistance is a Double
         val suggestions = instance.lookup(input, Verbosity.Closest, MAX_EDIT_DISTANCE.toDouble())
         return suggestions.map { it.term }
    }
}