package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import com.darkrockstudios.symspellkt.impl.SymSpell
import com.darkrockstudios.symspellkt.common.SpellCheckSettings 
import com.darkrockstudios.symspellkt.common.Verbosity

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
                // LOGIC FIX: Exact types per compiler error log
                val settings = SpellCheckSettings(
                    maxEditDistance = MAX_EDIT_DISTANCE.toDouble(), // FIX: Compiler requested Double
                    prefixLength = PREFIX_LENGTH,                   // Compiler is happy with Int here
                    countThreshold = 1L                             // FIX: Compiler requested Long
                )

                // Pass the config object to the constructor
                val instance = SymSpell(spellCheckSettings = settings)
                
                // Seed dummy dictionary
                val dummyWords = listOf("the 23000", "and 20000", "hello 15000", "world 10000", "floris 500")
                dummyWords.forEach {
                    val parts = it.split(" ")
                    if (parts.size == 2) {
                        // Note: frequency usually matches the countThreshold type, but let's stick to Double 
                        // for now as that's standard for the dictionary entry method.
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

    fun fix(input: String): String {
        if (!isReady) return input
        val instance = symSpell ?: return input
        if (input.length < 2) return input

        // Use Verbosity.Top
        // We know from previous logs that lookup() definitely wants Double for distance
        val suggestions = instance.lookup(input, Verbosity.Top, MAX_EDIT_DISTANCE.toDouble())
        return suggestions.firstOrNull()?.term ?: input
    }
    
    fun suggest(input: String): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()
         
         // Use Verbosity.Closest
         val suggestions = instance.lookup(input, Verbosity.Closest, MAX_EDIT_DISTANCE.toDouble())
         return suggestions.map { it.term }
    }
}