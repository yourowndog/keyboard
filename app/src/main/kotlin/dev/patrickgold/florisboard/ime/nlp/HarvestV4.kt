/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Schema-v4 word-slot aggregation. The existing v3 event stream remains intact;
 * this state machine dual-writes richer records to the same append-only JSONL.
 */
package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.keyboard.geometry.LayoutFingerprint
import java.util.Date
import java.util.Locale

object HarvestRoute {
    const val TYPED_THROUGH = "TYPED_THROUGH"
    const val BAR_PICK = "BAR_PICK"
    const val AUTO_APPLIED = "AUTO_APPLIED"
    const val GLIDE = "GLIDE"
    const val VOICE = "VOICE"
    const val PASTE = "PASTE"
    const val ABANDONED = "ABANDONED"
}

data class HarvestTouch(
    val character: String?,
    val key: String,
    val xn: Float?,
    val yn: Float?,
    val dx: Float?,
    val dy: Float?,
    val px: Int,
    val py: Int,
    val durationMs: Long,
    val source: String,
)

private data class V4Slot(
    val openedMs: Long,
    val openedAt: String,
    val session: String,
    val slotId: String,
    val context: AppContext,
    val layout: Map<String, Any?>,
    val voice: Map<String, Any?>? = null,
    val keys: MutableList<Map<String, Any?>> = mutableListOf(),
    val offers: MutableList<Map<String, Any?>> = mutableListOf(),
    val edits: MutableList<Map<String, Any?>> = mutableListOf(),
    var shadow: Map<String, Any?> = emptyMap(),
    val logicalText: StringBuilder = StringBuilder(),
    var previous: List<String> = emptyList(),
    var outcome: MutableMap<String, Any?>? = null,
    var attempts: Int = 1,
    var totalBackspaces: Int = 0,
    var currentBackspaceBurst: Int = 0,
    var maxBackspaceBurst: Int = 0,
    var typedAfterBackspace: Boolean = false,
    var returnEdited: Boolean = false,
    var capitulation: Boolean = false,
    var eventCount: Int = 0,
)

/**
 * Stateful aggregator kept independent of Android so its close/reopen semantics can be unit tested.
 * Every public operation is synchronized because suggestions arrive from a coroutine while touches
 * and editor outcomes normally arrive on the IME thread.
 */
internal class HarvestV4State(
    private val emit: (AppContext, String, String, List<Pair<String, Any?>>) -> Unit,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val timestamp: (Long) -> String = { HarvestJsonl.formatTimestamp(Date(it)) },
) {
    private var context: AppContext? = null
    private var layout: Map<String, Any?> = emptyMap()
    private var session = "unattached"
    private var inputSession = 0L
    private var slotOrdinal = 0L
    private var current: V4Slot? = null
    private var pendingClosed: V4Slot? = null
    private var voiceMetadata: Map<String, Any?>? = null
    private var voiceSegmentIndex = 0
    private val recentRejectedTargets = linkedMapOf<String, Long>()

    @Synchronized
    fun attach(appContext: AppContext, restarting: Boolean, sessionId: String) {
        flushAll(partialActive = true)
        context = appContext
        session = sessionId
        if (!restarting || inputSession == 0L) {
            inputSession++
            recentRejectedTargets.clear()
        }
    }

    @Synchronized
    fun updateLayout(fingerprint: LayoutFingerprint) {
        layout = linkedMapOf(
            "fp" to fingerprint.fp,
            "alphaW" to fingerprint.alphaW,
            "alphaH" to fingerprint.alphaH,
            "aspect" to fingerprint.aspect,
            "alphaRows" to fingerprint.alphaRows,
            "modRowsTop" to fingerprint.modRowsTop,
            "modRowsBottom" to fingerprint.modRowsBottom,
            "baseKeyH" to fingerprint.baseKeyH,
            "dpi" to fingerprint.dpi,
            "orientation" to fingerprint.orientation,
            "oneHanded" to fingerprint.oneHanded,
            "numberRowPresent" to fingerprint.numberRowPresent,
        )
    }

    @Synchronized
    fun beginVoice(audioRef: String, transcriptKind: String, asrConfidence: Float? = null) {
        voiceMetadata = linkedMapOf(
            "audioRef" to audioRef,
            "transcriptKind" to transcriptKind,
            "asrConf" to asrConfidence,
        )
        voiceSegmentIndex = 0
    }

    @Synchronized
    fun endVoice() {
        voiceMetadata = null
        voiceSegmentIndex = 0
    }

    @Synchronized
    fun touch(touch: HarvestTouch) {
        val app = context ?: return
        if (app.isHarvestBlocked) return
        val wordProducing = !touch.character.isNullOrBlank() && touch.key != "SPACE"
        val editing = touch.key == "BKSP" || touch.key == "BKSP_WORD"
        if (current == null && !wordProducing && !editing) return
        if (current == null && editing && pendingClosed != null) reopenPending()
        val slot = if (current != null) current!! else openSlot()
        slot.keys += linkedMapOf(
            "t" to elapsed(slot),
            "c" to touch.character,
            "k" to touch.key,
            "xn" to touch.xn,
            "yn" to touch.yn,
            "dx" to touch.dx,
            "dy" to touch.dy,
            "px" to touch.px,
            "py" to touch.py,
            "dur" to touch.durationMs,
            "src" to touch.source,
        )
        slot.eventCount++
        enforceBound(slot)
    }

    @Synchronized
    fun typed(text: String) {
        if (text.isEmpty() || text.all { it.isWhitespace() }) return
        val app = context ?: return
        if (app.isHarvestBlocked) return
        val slot = current ?: openSlot()
        if (slot.totalBackspaces > 0 && !slot.typedAfterBackspace) {
            slot.attempts++
            slot.typedAfterBackspace = true
        }
        slot.currentBackspaceBurst = 0
        slot.logicalText.append(text)
        slot.eventCount += text.length
        enforceBound(slot)
    }

    @Synchronized
    fun edit(operation: String, count: Int = 1, left: String? = null) {
        val app = context ?: return
        if (app.isHarvestBlocked) return
        if (current == null && pendingClosed != null) reopenPending()
        val slot = current ?: openSlot()
        val safeCount = count.coerceAtLeast(1)
        slot.edits += linkedMapOf(
            "t" to elapsed(slot),
            "op" to operation,
            "n" to safeCount,
            "left" to left,
        )
        if (operation == "BKSP" || operation == "BKSP_WORD") {
            slot.totalBackspaces += safeCount
            slot.currentBackspaceBurst += safeCount
            slot.maxBackspaceBurst = maxOf(slot.maxBackspaceBurst, slot.currentBackspaceBurst)
            slot.typedAfterBackspace = false
            repeat(safeCount.coerceAtMost(slot.logicalText.length)) {
                slot.logicalText.deleteCharAt(slot.logicalText.lastIndex)
            }
        }
        slot.eventCount += safeCount
        enforceBound(slot)
    }

    @Synchronized
    fun offer(prefix: String, candidates: List<Pair<String, Double>>, shown: Int) {
        if (prefix.isEmpty() || candidates.isEmpty()) return
        val app = context ?: return
        if (app.isHarvestBlocked) return
        val slot = current ?: openSlot()
        slot.offers += linkedMapOf(
            "t" to elapsed(slot),
            "prefix" to prefix,
            "eng" to "symspell+scorer",
            "cands" to candidates,
            "shown" to shown,
        )
        slot.eventCount++
        enforceBound(slot)
    }

    @Synchronized
    fun shadow(
        heuristicTop: String?,
        heuristicRanked: List<Pair<String, Double>>?,
        neuralTop: String,
        neuralRanked: List<Pair<String, Float>>?,
        neuralMargin: Float,
        wouldFire: Boolean,
        agrees: Boolean,
        policyBlockers: List<String>? = null,
    ) {
        val app = context ?: return
        if (app.isHarvestBlocked) return
        val slot = current ?: openSlot()
        slot.shadow = linkedMapOf(
            "heuristicTop" to heuristicTop,
            "heuristicRanked" to heuristicRanked,
            "neuralTop" to neuralTop,
            "neuralRanked" to neuralRanked,
            "neuralMargin" to neuralMargin,
            "wouldFire" to wouldFire,
            "agrees" to agrees,
            "policyBlockers" to policyBlockers,
        )
    }

    @Synchronized
    fun commit(
        final: String,
        route: String,
        prevWord: String? = null,
        prevPrevWord: String? = null,
        autoFrom: String? = null,
        barIndex: Int? = null,
        commitChar: String? = null,
    ) {
        if (final.isEmpty()) return
        val app = context ?: return
        if (app.isHarvestBlocked) return
        // A v3 mirror may report the same word immediately after a richer semantic outcome.
        if (current == null && pendingClosed?.outcome?.get("final") == final) return
        val slot = current ?: openSlot()
        if (slot.keys.isEmpty() && (route == HarvestRoute.GLIDE || route == HarvestRoute.PASTE)) {
            slot.keys += linkedMapOf(
                "t" to 0L, "c" to final, "k" to route,
                "xn" to null, "yn" to null, "dx" to null, "dy" to null,
                "px" to null, "py" to null, "dur" to 0L, "src" to route,
            )
        }
        if (slot.logicalText.isEmpty() && autoFrom != null) slot.logicalText.append(autoFrom)
        if (slot.logicalText.isEmpty()) slot.logicalText.append(final)
        slot.previous = listOfNotNull(prevPrevWord, prevWord).takeLast(2)
        val reach = reachability(slot, final)
        val now = clockMs()
        pruneRejectedTargets(now)
        if (route == HarvestRoute.AUTO_APPLIED || route == HarvestRoute.BAR_PICK) {
            val rejectedAt = recentRejectedTargets.remove(final.lowercase(Locale.ROOT))
            slot.capitulation = rejectedAt != null && now - rejectedAt <= REJECTION_HORIZON_MS
        }
        slot.shadow = LinkedHashMap(slot.shadow).apply {
            put("reachable", reach.first)
            put("reachableAt", reach.second)
        }
        slot.outcome = linkedMapOf(
            "final" to final,
            "route" to route,
            "barIndex" to barIndex,
            "autoFrom" to autoFrom,
            "reverted" to false,
            "revertedTo" to null,
            "revertedFrom" to null,
            "returnEdited" to slot.returnEdited,
            "committedAt" to timestamp(now),
            "commitChar" to commitChar,
            "reachable" to reach.first,
            "reachableAt" to reach.second,
        )
        pendingClosed?.let(::emitSlot)
        pendingClosed = slot
        current = null
    }

    @Synchronized
    fun externalText(text: String, route: String, prevWords: List<String> = emptyList()) {
        val app = context ?: return
        if (app.isHarvestBlocked) return
        var previous = prevWords.takeLast(2).toMutableList()
        for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val slot = openSlot()
            if (route == HarvestRoute.GLIDE || route == HarvestRoute.PASTE) {
                slot.keys += linkedMapOf(
                    "t" to 0L, "c" to word, "k" to route,
                    "xn" to null, "yn" to null, "dx" to null, "dy" to null,
                    "px" to null, "py" to null, "dur" to 0L, "src" to route,
                )
            }
            slot.logicalText.append(word)
            commit(
                final = word,
                route = route,
                prevWord = previous.lastOrNull(),
                prevPrevWord = previous.dropLast(1).lastOrNull(),
            )
            previous += word
            previous = previous.takeLast(2).toMutableList()
        }
    }

    @Synchronized
    fun revert(typed: String, rejected: String) {
        val slot = pendingClosed ?: current ?: return
        val outcome = slot.outcome ?: return
        if (!(outcome["final"] as? String).orEmpty().equals(rejected, ignoreCase = true)) return
        outcome["final"] = typed
        outcome["reverted"] = true
        outcome["revertedTo"] = typed
        outcome["revertedFrom"] = rejected
        val now = clockMs()
        outcome["committedAt"] = timestamp(now)
        val reach = reachability(slot, typed)
        outcome["reachable"] = reach.first
        outcome["reachableAt"] = reach.second
        slot.shadow = LinkedHashMap(slot.shadow).apply {
            put("reachable", reach.first)
            put("reachableAt", reach.second)
        }
        recentRejectedTargets[rejected.lowercase(Locale.ROOT)] = now
        slot.logicalText.setLength(0)
        slot.logicalText.append(typed)
        if (current === slot) {
            current = null
            pendingClosed = slot
        }
    }

    @Synchronized
    fun returnEdit(before: String, after: String) {
        val app = context ?: return
        if (app.isHarvestBlocked) return
        if (current == null && pendingClosed != null) reopenPending()
        val slot = current ?: openSlot()
        slot.returnEdited = true
        slot.edits += linkedMapOf(
            "t" to elapsed(slot), "op" to "RETURN_EDIT", "n" to 0,
            "left" to after, "before" to before,
        )
    }

    @Synchronized
    fun finish() {
        flushAll(partialActive = true)
        context = null
        endVoice()
    }

    @Synchronized
    fun flush() {
        flushAll(partialActive = true)
    }

    @Synchronized
    fun slotForLegacy(): String? = current?.slotId ?: pendingClosed?.slotId

    private fun openSlot(): V4Slot {
        pendingClosed?.let(::emitSlot)
        pendingClosed = null
        val app = requireNotNull(context) { "harvest slot opened before editor attachment" }
        val now = clockMs()
        slotOrdinal++
        val voice = voiceMetadata?.toMutableMap()?.also { metadata ->
            metadata["segIndex"] = voiceSegmentIndex++
        }
        return V4Slot(
            openedMs = now,
            openedAt = timestamp(now),
            session = session,
            slotId = "$session:$slotOrdinal",
            context = app,
            layout = layout,
            voice = voice,
        ).also { current = it }
    }

    private fun reopenPending() {
        val slot = pendingClosed ?: return
        pendingClosed = null
        slot.returnEdited = true
        current = slot
    }

    private fun elapsed(slot: V4Slot): Long = (clockMs() - slot.openedMs).coerceAtLeast(0L)

    private fun enforceBound(slot: V4Slot) {
        if (elapsed(slot) >= 60_000L || slot.eventCount >= 512) {
            closePartial(slot)
        }
    }

    private fun closePartial(slot: V4Slot) {
        if (current !== slot) return
        slot.outcome = linkedMapOf(
            "final" to slot.logicalText.toString(),
            "route" to HarvestRoute.ABANDONED,
            "barIndex" to null,
            "autoFrom" to null,
            "reverted" to false,
            "revertedTo" to null,
            "revertedFrom" to null,
            "returnEdited" to slot.returnEdited,
            "committedAt" to timestamp(clockMs()),
            "commitChar" to null,
            "reachable" to null,
            "reachableAt" to null,
        )
        emitSlot(slot, partial = true)
        current = null
    }

    private fun flushAll(partialActive: Boolean) {
        current?.let { slot ->
            if (partialActive) closePartial(slot) else emitSlot(slot)
        }
        current = null
        pendingClosed?.let(::emitSlot)
        pendingClosed = null
    }

    private fun reachability(slot: V4Slot, final: String): Pair<Boolean?, Int?> {
        if (slot.offers.isEmpty()) return null to null
        var best: Int? = null
        for (offer in slot.offers) {
            @Suppress("UNCHECKED_CAST")
            val candidates = offer["cands"] as? List<Pair<String, Double>> ?: continue
            candidates.forEachIndexed { index, candidate ->
                if (candidate.first.equals(final, ignoreCase = true)) {
                    best = minOf(best ?: Int.MAX_VALUE, index)
                }
            }
        }
        return (best != null) to best
    }

    private fun pruneRejectedTargets(now: Long) {
        val iterator = recentRejectedTargets.iterator()
        while (iterator.hasNext()) {
            val (_, rejectedAt) = iterator.next()
            if (now - rejectedAt > REJECTION_HORIZON_MS) iterator.remove()
        }
    }

    private fun emitSlot(slot: V4Slot, partial: Boolean = false) {
        if (slot.context.isHarvestBlocked) return
        val reach = slot.outcome?.get("reachable") as? Boolean
        val signals = linkedMapOf<String, Any?>(
            "attempts" to slot.attempts,
            "totalBksp" to slot.totalBackspaces,
            "maxBkspBurst" to slot.maxBackspaceBurst,
            "struggle" to (slot.attempts > 1 || slot.totalBackspaces >= 3),
            "capitulation" to slot.capitulation,
            "unreachable" to reach?.not(),
            "dwellMs" to elapsed(slot),
            "partial" to partial,
        )
        val ctx = linkedMapOf<String, Any?>(
            "app" to slot.context.packageName,
            "fieldId" to slot.context.fieldId,
            "inputType" to slot.context.inputType,
            "inputVariation" to slot.context.inputVariation,
            "flags" to slot.context.flags,
            "imeOptions" to slot.context.imeOptions,
            "hint" to slot.context.hint,
            "label" to slot.context.label,
            "actionLabel" to slot.context.actionLabel,
            "privateImeOptions" to slot.context.privateImeOptions,
            "extrasKeys" to slot.context.extrasKeys,
            "inputSession" to inputSession,
            "register" to null,
        )
        emit(
            slot.context,
            slot.openedAt,
            slot.session,
            listOf(
                "slot" to slot.slotId,
                "prev" to slot.previous,
                "ctx" to ctx,
                "layout" to slot.layout,
                "keys" to slot.keys,
                "offers" to slot.offers,
                "edits" to slot.edits,
                "outcome" to (slot.outcome ?: emptyMap<String, Any?>()),
                "shadow" to slot.shadow,
                "signals" to signals,
                "voice" to slot.voice,
            ),
        )
    }

    private companion object {
        const val REJECTION_HORIZON_MS = 60_000L
    }
}

object HarvestV4 {
    private val state = HarvestV4State(
        emit = { app, openedAt, session, fields ->
            HarvestJsonl.wordSlot(app, openedAt, session, fields)
        },
    )

    fun attach(context: AppContext, restarting: Boolean) =
        state.attach(context, restarting, HarvestJsonl.sessionFor(context))

    fun updateLayout(fingerprint: LayoutFingerprint) = state.updateLayout(fingerprint)
    fun beginVoice(audioRef: String, transcriptKind: String, asrConfidence: Float? = null) =
        state.beginVoice(audioRef, transcriptKind, asrConfidence)
    fun endVoice() = state.endVoice()
    fun touch(touch: HarvestTouch) = state.touch(touch)
    fun typed(text: String) = state.typed(text)
    fun edit(operation: String, count: Int = 1, left: String? = null) = state.edit(operation, count, left)
    fun offer(prefix: String, candidates: List<Pair<String, Double>>, shown: Int) =
        state.offer(prefix, candidates, shown)

    fun shadow(
        heuristicTop: String?,
        heuristicRanked: List<Pair<String, Double>>?,
        neuralTop: String,
        neuralRanked: List<Pair<String, Float>>?,
        neuralMargin: Float,
        wouldFire: Boolean,
        agrees: Boolean,
        policyBlockers: List<String>? = null,
    ) = state.shadow(
        heuristicTop, heuristicRanked, neuralTop, neuralRanked,
        neuralMargin, wouldFire, agrees, policyBlockers,
    )

    fun commit(
        final: String,
        route: String,
        prevWord: String? = null,
        prevPrevWord: String? = null,
        autoFrom: String? = null,
        barIndex: Int? = null,
        commitChar: String? = null,
    ) = state.commit(final, route, prevWord, prevPrevWord, autoFrom, barIndex, commitChar)

    fun externalText(text: String, route: String, prevWords: List<String> = emptyList()) =
        state.externalText(text, route, prevWords)

    fun revert(typed: String, rejected: String) = state.revert(typed, rejected)
    fun returnEdit(before: String, after: String) = state.returnEdit(before, after)
    fun finish() = state.finish()
    fun flush() = state.flush()
    fun slotForLegacy(): String? = state.slotForLegacy()
}
