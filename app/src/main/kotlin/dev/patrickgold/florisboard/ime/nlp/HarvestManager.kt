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

object HarvestManager {
    private const val FILENAME = "usage_harvest.md"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    private var harvestFile: File? = null
    private var isInitialized = false
    
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
     */
    fun logAccepted(typed: String, correctedTo: String, prevWord: String?) {
        if (typed == correctedTo) return // Not actually a correction
        append("ACCEPTED", "$typed → $correctedTo", prevWord)
    }
    
    /**
     * Log when an autocorrect was rejected (user backspaced to revert).
     * @param typed What the user originally typed
     * @param rejectedCorrection What correction they rejected
     * @param prevWord The word before (context)
     */
    fun logRejected(typed: String, rejectedCorrection: String, prevWord: String?) {
        append("REJECTED", "$typed ← $rejectedCorrection (reverted)", prevWord)
    }
    
    /**
     * Log when user typed a word that wasn't in the dictionary.
     * These are candidates for dictionary addition.
     * @param word The word that wasn't recognized
     * @param prevWord The word before (context)
     */
    fun logNewWord(word: String, prevWord: String?) {
        // Skip very short words and obvious garbage
        if (word.length < 2) return
        if (word.all { it.isDigit() }) return
        if (word.contains("@") || word.contains("://")) return // URLs/emails
        
        append("NEW_WORD", word, prevWord)
    }
    
    /**
     * Log when user explicitly picked their typed word over a suggestion.
     * This is a strong signal that this word should be in the dictionary.
     * @param word The word user insisted on
     * @param prevWord The word before (context)
     */
    fun logInsisted(word: String, prevWord: String?) {
        append("INSISTED", word, prevWord)
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
    
    private fun append(category: String, content: String, context: String?) {
        val file = harvestFile ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = dateFormat.format(Date())
                val ctx = context?.let { " | ctx: \"$it\"" } ?: ""
                val line = "[$category] $timestamp | $content$ctx"
                
                PrintWriter(FileWriter(file, true)).use { out ->
                    out.println(line)
                }
            } catch (e: Exception) {
                android.util.Log.e("HarvestManager", "Failed to append: $content", e)
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
