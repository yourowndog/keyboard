/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * On-device learning: the words this person actually commits, the words they
 * have defended by undoing a correction, and the corrections they rejected.
 */
package dev.patrickgold.florisboard.ime.nlp.shared

import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure, Android-free learning state. Every rule here comes from the 2026-11 harvest replay:
 *
 * - A word the person keeps typing is vocabulary, even when the packaged dictionary does not
 *   know it (`herdr`, `chezmoi`, `litellm`). Without this, valid-word immunity never protects
 *   it and every commit is a fresh chance to "correct" it into a stranger.
 * - A word that sits one edit away from something the person types far more often is a typo
 *   they failed to fix (`thst` next to `that`), not vocabulary. It must stay correctable even
 *   though it has been committed many times, so repetition alone does not teach it.
 * - Undoing an automatic correction usually means the typed form was wanted: it is learned
 *   and that exact pair is refused from then on. But the harvest also holds hundreds of
 *   "undos" of real typos (`tbe` -> the, `snd` -> and), because a backspace straight after a
 *   correction restores the typo while the person is fixing something else. So the same
 *   rival test applies: an undo never protects a form sitting next to a far more used word.
 */
class PersonalLexiconState(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val counts = HashMap<String, Int>()
    private val learned = HashSet<String>()
    private val rejected = HashMap<String, MutableSet<String>>()

    @Volatile
    var revision: Long = 0L
        private set

    @Synchronized
    fun recordCommit(word: String, weight: Int = 1) {
        val key = normalize(word) ?: return
        counts[key] = (counts[key] ?: 0) + weight.coerceAtLeast(1)
        revision++
        if (counts.size > maxEntries) prune()
    }

    /** The person undid `rejected` back to `typed`: learn the typed form and refuse the pair. */
    @Synchronized
    fun recordRevert(typed: String, rejectedCandidate: String) {
        val t = normalize(typed) ?: return
        val r = rejectedCandidate.trim().lowercase(Locale.ROOT)
        learned += t
        if (r.isNotEmpty() && r != t) rejected.getOrPut(t) { HashSet() } += r
        revision++
    }

    /** The person picked their literal typed form from the bar over every correction. */
    @Synchronized
    fun recordInsist(word: String) {
        val key = normalize(word) ?: return
        learned += key
        revision++
    }

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

    /** Merges serialized lines (the own store, or an offline seed built from the harvest). */
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
        revision++
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
 * Process-wide store with file persistence. Writes happen off the input thread and are
 * coalesced; a crash loses at most the commits since the last flush.
 */
object PersonalLexicon {
    const val STORE_FILE = "personal_lexicon.tsv"
    const val SEED_FILE = "personal_lexicon_seed.tsv"

    @Volatile
    var state: PersonalLexiconState = PersonalLexiconState()
        private set

    private var storeFile: File? = null
    private var savedRevision = 0L
    private val loaded = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "PersonalLexicon").apply { isDaemon = true } }

    /**
     * Loads the store from [filesDir]. An offline seed (built from the harvest on the training
     * host and pushed to [seedDir]) is merged once and then renamed so it is never double-counted.
     */
    fun init(filesDir: File, seedDir: File?) {
        if (!loaded.compareAndSet(false, true)) return
        io.execute {
            runCatching {
                val fresh = PersonalLexiconState()
                val store = File(filesDir, STORE_FILE)
                storeFile = store
                if (store.exists()) store.useLines { fresh.merge(it) }
                val seed = seedDir?.let { File(it, SEED_FILE) }
                var seeded = false
                if (seed != null && seed.exists()) {
                    seed.useLines { fresh.merge(it) }
                    seeded = true
                }
                state = fresh
                if (seeded) {
                    writeNow()
                    seed?.renameTo(File(seed.parentFile, "$SEED_FILE.imported"))
                } else {
                    savedRevision = fresh.revision
                }
                android.util.Log.i("PersonalLexicon", "loaded ${fresh.size()} words (seeded=$seeded)")
            }.onFailure { android.util.Log.e("PersonalLexicon", "load failed", it) }
        }
    }

    fun recordCommit(word: String) {
        state.recordCommit(word)
        maybeFlush()
    }

    fun recordRevert(typed: String, rejected: String) {
        state.recordRevert(typed, rejected)
        flush()
    }

    fun recordInsist(word: String) {
        state.recordInsist(word)
        flush()
    }

    fun isKnown(word: String): Boolean = state.isKnown(word)

    fun isRejected(typed: String, candidate: String): Boolean = state.isRejected(typed, candidate)

    private fun maybeFlush() {
        if (state.revision - savedRevision >= FLUSH_EVERY) flush()
    }

    fun flush() {
        if (storeFile == null) return
        io.execute { runCatching { writeNow() } }
    }

    private fun writeNow() {
        val store = storeFile ?: return
        val rev = state.revision
        if (rev == savedRevision && store.exists()) return
        val tmp = File(store.parentFile, "$STORE_FILE.part")
        tmp.writeText(state.serialize())
        if (!tmp.renameTo(store)) {
            store.delete()
            tmp.renameTo(store)
        }
        savedRevision = rev
    }

    private const val FLUSH_EVERY = 25L
}
