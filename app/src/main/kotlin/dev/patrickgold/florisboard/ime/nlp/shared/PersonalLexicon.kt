/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * The person's own vocabulary, built offline from the keystroke harvest and
 * packed into the APK. Nothing here is learned or written on the device.
 */
package dev.patrickgold.florisboard.ime.nlp.shared

import android.content.Context
import java.util.Locale

/**
 * Pure, Android-free view of the personal lexicon asset. Every rule here comes from the
 * 2026-11 harvest replay:
 *
 * - A word the person keeps typing is vocabulary, even when the packaged dictionary does not
 *   know it (`herdr`, `chezmoi`, `litellm`). Without this, valid-word immunity never protects
 *   it and every commit is a fresh chance to "correct" it into a stranger.
 * - A word that sits one edit away from something the person types far more often is a typo
 *   they failed to fix (`thst` next to `that`), not vocabulary. It must stay correctable even
 *   though it has been committed many times, so repetition alone does not teach it.
 * - Undoing an automatic correction usually means the typed form was wanted: the harvest
 *   builder marks it known and refuses that exact pair. But the harvest also holds hundreds of
 *   "undos" of real typos (`tbe` -> the, `snd` -> and), because a backspace straight after a
 *   correction restores the typo while the person is fixing something else. So the same
 *   rival test applies: an undo never protects a form sitting next to a far more used word.
 *
 * Standing invariant (docs/autocorrect/roadmap.md): no runtime learning. Personalization is
 * harvest -> build-time asset, so every install starts out knowing the person.
 */
class PersonalLexiconState(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val counts = HashMap<String, Int>()
    private val learned = HashSet<String>()
    private val rejected = HashMap<String, MutableSet<String>>()

    @Synchronized
    fun count(word: String): Int = normalize(word)?.let { counts[it] } ?: 0

    @Synchronized
    fun isRejected(typed: String, candidate: String): Boolean {
        val t = normalize(typed) ?: return false
        if (rejected[t]?.contains(candidate.trim().lowercase(Locale.ROOT)) != true) return false
        return !isTypoShaped(t)
    }

    /**
     * True when [word] is this person's vocabulary: explicitly defended, or committed at least
     * [MIN_LEARN_COUNT] times without a far more frequent personal word one edit away.
     */
    @Synchronized
    fun isKnown(word: String): Boolean {
        val key = normalize(word) ?: return false
        if (key !in learned && (counts[key] ?: 0) < MIN_LEARN_COUNT) return false
        return !isTypoShaped(key)
    }

    /** Something one edit away is used at least five times as often as [key]. */
    private fun isTypoShaped(key: String): Boolean {
        val n = counts[key] ?: 0
        val rival = oneEditVariants(key).maxOfOrNull { counts[it] ?: 0 } ?: 0
        return n * RIVAL_RATIO_DENOMINATOR < rival
    }

    @Synchronized
    fun size(): Int = counts.size

    // ── persistence ──────────────────────────────────────────────────────────────────────

    /** Line format: `c<TAB>word<TAB>count`, `l<TAB>word`, `r<TAB>typed<TAB>rejected`. */
    @Synchronized
    fun serialize(): String = buildString {
        for ((w, n) in counts) append("c\t").append(w).append('\t').append(n).append('\n')
        for (w in learned) append("l\t").append(w).append('\n')
        for ((t, rs) in rejected) for (r in rs) append("r\t").append(t).append('\t').append(r).append('\n')
    }

    /** Merges lines of the asset built offline from the harvest. */
    @Synchronized
    fun merge(lines: Sequence<String>) {
        for (line in lines) {
            val p = line.split('\t')
            when (p.getOrNull(0)) {
                "c" -> if (p.size >= 3) {
                    val key = normalize(p[1]) ?: continue
                    val n = p[2].trim().toIntOrNull() ?: continue
                    if (n > 0) counts[key] = (counts[key] ?: 0) + n
                }
                "l" -> if (p.size >= 2) normalize(p[1])?.let { learned += it }
                "r" -> if (p.size >= 3) {
                    val t = normalize(p[1]) ?: continue
                    val r = p[2].trim().lowercase(Locale.ROOT)
                    if (r.isNotEmpty() && r != t) rejected.getOrPut(t) { HashSet() } += r
                }
            }
        }
        if (counts.size > maxEntries) prune()
    }

    private fun prune() {
        // Keep the most used words; learned words and rejection pairs are never pruned.
        val keep = counts.entries.sortedByDescending { it.value }.take(maxEntries * 9 / 10)
            .associate { it.key to it.value }
        counts.clear()
        counts.putAll(keep)
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 40_000
        const val MIN_LEARN_COUNT = 3

        /** A word must be used at least 1/5 as often as its closest rival to count as vocabulary. */
        const val RIVAL_RATIO_DENOMINATOR = 5

        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz'"

        fun normalize(word: String): String? {
            val w = word.trim().trim('.', ',', '!', '?', ';', ':', '"', '(', ')').lowercase(Locale.ROOT)
            if (w.length < 2 || w.length > 32) return null
            if (!w.all { it in 'a'..'z' || it == '\'' }) return null
            if (w.none { it in 'a'..'z' }) return null
            return w
        }

        /** Damerau one-edit neighbourhood (insert, delete, substitute, adjacent swap). */
        fun oneEditVariants(w: String): Set<String> {
            val out = HashSet<String>(w.length * 60)
            for (i in 0..w.length) {
                for (c in ALPHABET) out += w.substring(0, i) + c + w.substring(i)
                if (i < w.length) {
                    out += w.substring(0, i) + w.substring(i + 1)
                    for (c in ALPHABET) if (c != w[i]) out += w.substring(0, i) + c + w.substring(i + 1)
                }
                if (i + 1 < w.length) {
                    out += w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)
                }
            }
            out -= w
            return out
        }
    }
}

/**
 * Process-wide, read-only lexicon loaded from [ASSET_PATH]. The asset is generated by
 * `tools/harvesting/build_personal_lexicon.py` into the gitignored `private-assets/`
 * folder, which the build packs alongside the normal assets. Without it the lexicon is empty
 * and the engine behaves as it would for anyone.
 */
object PersonalLexicon {
    const val ASSET_PATH = "ime/personal/lexicon.tsv"

    @Volatile
    var state: PersonalLexiconState = PersonalLexiconState()
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context
        Thread({
            runCatching {
                val fresh = PersonalLexiconState()
                appContext.assets.open(ASSET_PATH).bufferedReader().useLines { fresh.merge(it) }
                state = fresh
                android.util.Log.i("PersonalLexicon", "loaded ${fresh.size()} words from $ASSET_PATH")
            }.onFailure {
                if (it is java.io.FileNotFoundException) {
                    android.util.Log.i("PersonalLexicon", "no $ASSET_PATH in this build")
                } else {
                    android.util.Log.e("PersonalLexicon", "load failed", it)
                }
            }
        }, "PersonalLexicon").apply { isDaemon = true }.start()
    }

    fun isKnown(word: String): Boolean = state.isKnown(word)

    fun isRejected(typed: String, candidate: String): Boolean = state.isRejected(typed, candidate)

    fun count(word: String): Int = state.count(word)
}
