/*
 * HarvestManager - Developer Usage Harvesting
 * 
 * Writes keyboard usage events to a file for developer review.
 * This helps a developer iterating on builds to capture their actual usage patterns
 * (accepted corrections, rejected corrections, new words) without losing data on reinstall.
 * 
 * Output: /sdcard/Documents/usage_harvest.md
 * Copy to repo: cp /sdcard/Documents/usage_harvest.md ~/vault/projects/keyboard/
 */
package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Context information about the app/field where typing is occurring.
 * Used for per-app dictionary learning and behavior customization.
 */
data class AppContext(
    val packageName: String,
    val fieldId: Int,
    val inputVariation: String,
    val flags: String,
)

object HarvestManager {
    private const val FILENAME = "usage_harvest.md"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    private var harvestFile: File? = null
    private var isInitialized = false
    
    private val sessionBuffer = StringBuilder()
    private var sessionWordCount = 0
    private var currentSessionSource = "TYPING"  // Track session source: "TYPING" or "VOICE"

    // Track multi-attempt sequences
    private var currentAttemptSequence = mutableListOf<String>()
    private var lastTypedWord: String? = null
    
    /**
     * Initialize the harvest file location.
     * Tries /sdcard/Documents/ first, falls back to app-private storage.
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir.exists() || documentsDir.mkdirs()) {
                harvestFile = File(documentsDir, FILENAME)
                
                // Create file with header if it doesn't exist
                if (!harvestFile!!.exists()) {
                    writeHeader()
                }
                
                android.util.Log.i("HarvestManager", "Harvest file: ${harvestFile?.absolutePath}")
                isInitialized = true
            }
        } catch (e: Exception) {
            android.util.Log.w("HarvestManager", "Can't write to Documents, trying app storage", e)
            
            // Fallback to app-private storage
            harvestFile = File(context.filesDir, FILENAME)
            if (!harvestFile!!.exists()) {
                writeHeader()
            }
            isInitialized = true
        }
    }
    
    private fun writeHeader() {
        harvestFile?.let { file ->
            try {
                PrintWriter(FileWriter(file, false)).use { out ->
                    out.println("# OmniBoard Usage Harvest")
                    out.println()
                    out.println("This file is written to by the keyboard during use.")
                    out.println("Review periodically to update dictionary, ignore lists, etc.")
                    out.println()
                    out.println("Copy to repo: `cp /sdcard/Documents/usage_harvest.md ~/vault/projects/keyboard/`")
                    out.println()
                    out.println("---")
                    out.println()
                }
            } catch (e: Exception) {
                android.util.Log.e("HarvestManager", "Failed to write header", e)
            }
        }
    }
    
    /**
     * Log when an autocorrect was accepted (user continued typing after correction).
     * @param typed What the user originally typed
     * @param correctedTo What it was corrected to
     * @param prevWord The word before (context)
     * @param prevPrevWord The word before that (trigram context)
     * @param appContext App/field context for per-app learning
     */
    fun logAccepted(typed: String, correctedTo: String, prevWord: String?, prevPrevWord: String? = null, appContext: AppContext? = null) {
        if (typed == correctedTo) return // Not actually a correction
        append("ACCEPTED", "$typed → $correctedTo", prevWord, prevPrevWord, appContext)
    }
    
    /**
     * Log when an autocorrect was rejected (user backspaced to revert).
     * @param typed What the user originally typed
     * @param rejectedCorrection What correction they rejected
     * @param prevWord The word before (context)
     * @param prevPrevWord The word before that (trigram context)
     * @param appContext App/field context for per-app learning
     */
    fun logRejected(typed: String, rejectedCorrection: String, prevWord: String?, prevPrevWord: String? = null, appContext: AppContext? = null) {
        append("REJECTED", "$typed ← $rejectedCorrection (reverted)", prevWord, prevPrevWord, appContext)
    }
    
    /**
     * Log when user typed a word that wasn't in the dictionary.
     * These are candidates for dictionary addition.
     * @param word The word that wasn't recognized
     * @param prevWord The word before (context)
     * @param appContext App/field context for per-app learning
     */
    fun logNewWord(word: String, prevWord: String?, appContext: AppContext? = null) {
        // Skip very short words and obvious garbage
        if (word.length < 2) return
        if (word.all { it.isDigit() }) return
        if (word.contains("@") || word.contains("://")) return // URLs/emails

        append("NEW_WORD", word, prevWord, null, appContext)
    }
    
    /**
     * Log when user manually backspaced and retyped a word differently.
     * This captures missed autocorrect opportunities — the keyboard offered no suggestion
     * for a typo and the user had to fix it themselves.
     * @param original What was committed before the user backspaced
     * @param corrected What the user retyped (their intended word)
     * @param prevWord Context word before the corrected word
     * @param appContext App/field context for per-app learning
     */
    fun logManualCorrection(original: String, corrected: String, prevWord: String?, appContext: AppContext? = null) {
        if (original.isEmpty() || corrected.isEmpty()) return
        if (original.equals(corrected, ignoreCase = true)) return
        append("MANUAL_FIX", "\"$original\" → \"$corrected\"", prevWord, null, appContext)
    }

    /**
     * Log when user explicitly picked their typed word over a suggestion.
     * This is a strong signal that this word should be in the dictionary.
     * @param word The word user insisted on
     * @param prevWord The word before (context)
     * @param appContext App/field context for per-app learning
     */
    fun logInsisted(word: String, prevWord: String?, appContext: AppContext? = null) {
        // Smart Check: If the user insisted on a word that ISN'T in our dict, it's a NEW_WORD candidate.
        if (!dev.patrickgold.florisboard.ime.nlp.SymSpellManager.hasWord(word)) {
            logNewWord(word, prevWord, appContext)
        } else {
            append("INSISTED", word, prevWord, null, appContext)
        }
    }
    
    /**
     * Log when user picked a specific suggestion from the smartbar.
     * @param typed What user typed
     * @param picked What they picked from suggestions
     * @param prevWord Context
     */
    fun logPicked(typed: String, picked: String, prevWord: String?) {
        if (typed == picked) {
            logInsisted(typed, prevWord)
        } else {
            append("PICKED", "$typed → $picked (manual)", prevWord)
        }
    }

    /**
     * Log the user's intent after a rejection.
     * @param typed Original typed input (e.g. "s")
     * @param rejected What it was corrected to (e.g. "so")
     * @param intent What the user typed after rejecting (e.g. "a")
     */
    fun logIntent(typed: String, rejected: String, intent: String) {
        append("INTENT", "Typed '$typed' → Auto-corrected to '$rejected' → User reverted & typed '$intent'. (Conclusion: '$typed' meant '$intent')", null)
    }

    /**
     * Log when autocorrect offers NO suggestions for a typed word.
     * Critical for identifying dictionary gaps and autocorrect blind spots.
     */
    fun logNoSuggestion(typed: String, prevWord: String? = null) {
        append("NO_SUGGESTION", "typed: \"$typed\" | no corrections offered", prevWord)
    }

    /**
     * Log when user makes multiple attempts to type the same word.
     * Indicates struggle - either dictionary gap or poor suggestion quality.
     *
     * @param attempts List of typing attempts (e.g. ["wrng", "worng", "wrong"])
     * @param finalWord What user finally settled on
     * @param prevWord Context
     */
    fun logMultiAttempt(attempts: List<String>, finalWord: String, prevWord: String? = null) {
        val attemptsStr = attempts.joinToString(" → ")
        append("MULTI_ATTEMPT", "attempts: [$attemptsStr] | final: \"$finalWord\"", prevWord)
    }

    /**
     * Log when suggestions are shown but user ignores ALL of them.
     * Indicates either: wrong suggestions, or user didn't see/trust them.
     *
     * @param typed What user typed
     * @param suggestions What was offered in smartbar
     * @param finalTyped What user ended up with (after ignoring suggestions)
     */
    fun logSuggestionsIgnored(typed: String, suggestions: List<String>, finalTyped: String, prevWord: String? = null) {
        val suggestionsStr = suggestions.joinToString(", ")
        append("IGNORED_SUGGESTIONS", "typed: \"$typed\" | offered: [$suggestionsStr] | ignored all | final: \"$finalTyped\"", prevWord)
    }

    /**
     * Log when user makes many backspaces on a single word (struggle indicator).
     * High-effort words should get better suggestions or be added to dictionary.
     */
    fun logBackspaceStorm(word: String, backspaceCount: Int, finalWord: String) {
        append("BACKSPACE_STORM", "word: \"$word\" | backspaces: $backspaceCount | final: \"$finalWord\"", null)
    }

    /**
     * Start tracking a multi-attempt sequence for a word position.
     * Call when user starts typing after backspacing.
     */
    fun startAttemptTracking(word: String) {
        currentAttemptSequence.clear()
        currentAttemptSequence.add(word)
        lastTypedWord = word
    }

    /**
     * Add attempt to current sequence.
     */
    fun addAttempt(word: String) {
        if (word != lastTypedWord) {
            currentAttemptSequence.add(word)
            lastTypedWord = word
        }
    }

    /**
     * Finalize attempt sequence and log if multiple attempts were made.
     */
    fun finalizeAttemptSequence(finalWord: String, prevWord: String? = null) {
        if (currentAttemptSequence.size > 1) {
            logMultiAttempt(currentAttemptSequence.toList(), finalWord, prevWord)
        }
        currentAttemptSequence.clear()
        lastTypedWord = null
    }

    private var currentAppContext: AppContext? = null  // Track active field context

    fun addToSession(word: String, appContext: AppContext? = null) {
        synchronized(sessionBuffer) {
            // Update context if provided
            if (appContext != null) {
                currentAppContext = appContext
            }

            if (sessionBuffer.isNotEmpty() && !word.matches(Regex("^[.,?!;:]$"))) {
                sessionBuffer.append(" ")
            }
            sessionBuffer.append(word)

            // Auto-flush logic for users who don't use punctuation
            if (!word.matches(Regex("^[.,?!;:]$"))) {
                sessionWordCount++
                if (sessionWordCount >= 5) {
                    flushSession()
                }
            }
        }
    }

    /**
     * Set the source of upcoming session data.
     * Call before voice input commits, reset to TYPING after.
     */
    fun setSessionSource(source: String) {
        currentSessionSource = source
    }

    fun flushSession(terminator: String = "", appContext: AppContext? = null) {
        synchronized(sessionBuffer) {
            if (terminator.isNotEmpty()) {
                sessionBuffer.append(terminator)
            }
            if (sessionBuffer.isNotEmpty()) {
                val sentence = sessionBuffer.toString()
                sessionBuffer.setLength(0) // clear
                sessionWordCount = 0
                // Use provided context or fall back to tracked context
                val ctx = appContext ?: currentAppContext
                // Tag session with source (TYPING or VOICE)
                append("SESSION:$currentSessionSource", "\"$sentence\"", null, null, ctx)
                currentAppContext = null  // Clear after flush
            }
        }
    }
    
    private fun append(category: String, content: String, context: String?, prevPrevWord: String? = null, appContext: AppContext? = null) {
        val file = harvestFile ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = dateFormat.format(Date())
                val ctx = context?.let { " | ctx: \"$it\"" } ?: ""
                val trigram = if (prevPrevWord != null && context != null) {
                    " | trigram: \"$prevPrevWord $context\""
                } else ""

                // Append app context if available
                val appCtx = if (appContext != null) {
                    " | app: \"${appContext.packageName}\" | field: ${appContext.fieldId} | inputType: ${appContext.inputVariation} | flags: ${appContext.flags}"
                } else ""

                val line = "[$category] $timestamp | $content$ctx$trigram$appCtx"

                PrintWriter(FileWriter(file, true)).use { out ->
                    out.println(line)
                }
            } catch (e: Exception) {
                android.util.Log.e("HarvestManager", "Failed to append: $content", e)
            }
        }
    }
    
    /**
     * Write a review marker to segment data.
     * Call this after processing harvest data to mark what's been reviewed.
     * New events will appear after this marker.
     */
    fun logReviewMarker(reviewNote: String = "") {
        val file = harvestFile ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = dateFormat.format(Date())
                PrintWriter(FileWriter(file, true)).use { out ->
                    out.println()
                    out.println("---")
                    out.println("<!-- REVIEW MARKER: $timestamp -->")
                    if (reviewNote.isNotEmpty()) {
                        out.println("<!-- Note: $reviewNote -->")
                    }
                    out.println("<!-- Data below this line is NEW since last review -->")
                    out.println("---")
                    out.println()
                }
            } catch (e: Exception) {
                android.util.Log.e("HarvestManager", "Failed to write review marker", e)
            }
        }
    }
    
    /**
     * Get the path to the harvest file for display in settings.
     */
    fun getFilePath(): String {
        return harvestFile?.absolutePath ?: "Not initialized"
    }
}
