package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages the new n-gram based suggestion engine (SymSpell-free).
 * Keeps all inputs/outputs primitive so a JNI/Rust backend can be swapped in later.
 */
object NgramEngineManager {
    @Volatile private var engine: SuggestionEngine? = null
    @Volatile private var isReady: Boolean = false

    private const val TAG = "NgramEngine"

    // Assets for default (cleaned) and AOSP paths
    private const val CLEAN_UNIGRAM = "ime/dict/frequency_dictionary_en.cleaned.txt"
    private const val CLEAN_BIGRAM = "ime/dict/final_mobile_bigrams.tsv"
    private const val AOSP_UNIGRAM = "ime/dict/aosp_unigram.tsv"

    // Toggle to switch between cleaned vs AOSP unigram source.
    @Volatile var useAosp: Boolean = false

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val assets = context.assets
                val unigramPath = if (useAosp) AOSP_UNIGRAM else CLEAN_UNIGRAM
                val bigramPath = CLEAN_BIGRAM // default to SMS+user blend
                val unigramStream = assets.open(unigramPath)
                val bigramStream = assets.open(bigramPath)
                engine = NgramSuggestionEngine.fromStreams(
                    unigramStream = unigramStream,
                    bigramStream = bigramStream,
                )
                isReady = true
                Log.i(TAG, "Ngram engine ready with $unigramPath + $bigramPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init ngram engine", e)
                isReady = false
                engine = null
            }
        }
    }

    fun suggest(request: SuggestionRequest): List<SuggestionCandidate> {
        val eng = engine ?: return emptyList()
        val result = eng.suggest(request)
        // Debug for visual verification
        Log.d(TAG, "Top suggestions for '${request.typed}' -> ${result.take(3)}")
        return result
    }

    fun predictNext(prev: String?, max: Int = 3): List<String> {
        val eng = engine ?: return emptyList()
        return eng.predictNext(prev, max)
    }

    fun ready(): Boolean = isReady
    fun generateAiCompletion(prompt: String): String? {
        val eng = engine ?: return null
        return eng.generateAiCompletion(prompt)
    }
}
