package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import com.darkrockstudios.symspellkt.impl.SymSpell
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object SymSpellManager {
    private var symSpell: SymSpell? = null
    @Volatile private var isReady = false

    // Config
    private const val MAX_EDIT_DISTANCE = 2
    private const val PREFIX_LENGTH = 7
    private const val DICT_ASSET_PATH = "ime/dict/frequency_dictionary_en.txt"

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Initialize Engine Configuration
                val settings = SpellCheckSettings(
                    maxEditDistance = MAX_EDIT_DISTANCE.toDouble(), // FIX: Compiler wants Double
                    prefixLength = PREFIX_LENGTH,
                    countThreshold = 1L                             // FIX: Compiler wants Long
                )

                // 2. Create Instance
                val instance = SymSpell(spellCheckSettings = settings)

                // 3. Load Real Dictionary from Assets
                context.assets.open(DICT_ASSET_PATH).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.forEachLine { line ->
                            val parts = line.split(" ")
                            if (parts.size >= 2) {
                                val term = parts[0]
                                // Parse frequency safely, defaulting to 1.0 if malformed
                                val count = parts[1].toDoubleOrNull() ?: 1.0
                                instance.createDictionaryEntry(term, count)
                            }
                        }
                    }
                }

                symSpell = instance
                isReady = true
                android.util.Log.i("SymSpellManager", "Reflexes Ready: Loaded real dictionary from $DICT_ASSET_PATH")
            } catch (e: Exception) {
                android.util.Log.e("SymSpellManager", "Reflexes Failed to Load Dictionary", e)
            }
        }
    }

    fun fix(input: String): String {
        if (!isReady) return input
        val instance = symSpell ?: return input
        if (input.length < 2) return input

        // Reflexes: Fast correction
        val suggestions = instance.lookup(input, Verbosity.Top, MAX_EDIT_DISTANCE.toDouble())
        return suggestions.firstOrNull()?.term ?: input
    }

    fun suggest(input: String): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()

         val suggestions = instance.lookup(input, Verbosity.Closest, MAX_EDIT_DISTANCE.toDouble())
         return suggestions.map { it.term }
    }
}
