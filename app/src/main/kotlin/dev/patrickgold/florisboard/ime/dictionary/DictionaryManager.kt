/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.FlorisLocale
import java.lang.ref.WeakReference

/**
 * TODO: document
 */
class DictionaryManager private constructor(context: Context) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)
    private val prefs by FlorisPreferenceStore

    private var florisUserDictionaryDatabase: FlorisUserDictionaryDatabase? = null
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null

    companion object {
        private var defaultInstance: DictionaryManager? = null

        fun init(applicationContext: Context): DictionaryManager {
            val instance = DictionaryManager(applicationContext)
            defaultInstance = instance
            return instance
        }

        fun default(): DictionaryManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${DictionaryManager::class.simpleName} has not been initialized previously. Make sure to call init(applicationContext) before using default()."
                )
            }
        }
    }

    fun queryUserDictionary(word: String, locale: FlorisLocale): List<SuggestionCandidate> {
        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) {
            return emptyList()
        }
        return buildList {
            if (prefs.dictionary.enableFlorisUserDictionary.get()) {
                florisDao?.query(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
                florisDao?.queryShortcut(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
            }
            if (prefs.dictionary.enableSystemUserDictionary.get()) {
                systemDao?.query(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
                systemDao?.queryShortcut(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
            }
        }

    }

    fun spell(word: String, locale: FlorisLocale): Boolean {
        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) {
            return false
        }
        var ret = false
        if (prefs.dictionary.enableFlorisUserDictionary.get()) {
            ret = ret || florisDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false
            ret = ret || florisDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            ret = ret || systemDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false
            ret = ret || systemDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false
        }
        return ret
    }

    fun learnUserIgnore(original: String, rejected: String) {
        val dao = florisUserDictionaryIgnoreDao() ?: return
        val entry = dao.get(original, rejected)
        if (entry != null) {
            dao.increment(original, rejected)
        } else {
            dao.insert(UserDictionaryIgnoreEntry(original, rejected))
        }
    }

    fun isUserIgnored(original: String, rejected: String): Boolean {
        val dao = florisUserDictionaryIgnoreDao() ?: return false
        return dao.get(original, rejected) != null
    }

    @Synchronized
    fun florisUserDictionaryDao(): UserDictionaryDao? {
        return if (prefs.dictionary.enableFlorisUserDictionary.get()) {
            florisUserDictionaryDatabase?.userDictionaryDao()
        } else {
            null
        }
    }

    @Synchronized
    fun florisUserDictionaryIgnoreDao(): UserDictionaryIgnoreDao? {
        return if (prefs.dictionary.enableFlorisUserDictionary.get()) {
            florisUserDictionaryDatabase?.userDictionaryIgnoreDao()
        } else {
            null
        }
    }

    @Synchronized
    fun florisUserDictionaryDatabase(): FlorisUserDictionaryDatabase? {
        return if (prefs.dictionary.enableFlorisUserDictionary.get()) {
            florisUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun systemUserDictionaryDao(): UserDictionaryDao? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase?.userDictionaryDao()
        } else {
            null
        }
    }

    @Synchronized
    fun systemUserDictionaryDatabase(): SystemUserDictionaryDatabase? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun loadUserDictionariesIfNecessary() {
        val context = applicationContext.get() ?: return

        if (florisUserDictionaryDatabase == null && prefs.dictionary.enableFlorisUserDictionary.get()) {
            florisUserDictionaryDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
            maybeImportFromVault(context)
        }
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
    }

    @Synchronized
    fun unloadUserDictionariesIfNecessary() {
        if (florisUserDictionaryDatabase != null) {
            florisUserDictionaryDatabase?.close()
            florisUserDictionaryDatabase = null
        }
        if (systemUserDictionaryDatabase != null) {
            systemUserDictionaryDatabase = null
        }
    }

    // --- Vault backup/restore helpers ---------------------------------------------------------

    private fun vaultUri(): Uri? {
        val uriStr = prefs.dictionary.userDictionaryVaultUri.get()
        return uriStr.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    private fun resolveVaultFile(context: Context, createIfMissing: Boolean): DocumentFile? {
        val treeUri = vaultUri() ?: return null
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val fileName = "user_dict.txt"
        val existing = tree.findFile(fileName)
        if (existing != null && existing.isFile) return existing
        return if (createIfMissing) tree.createFile("text/plain", fileName) else null
    }

    fun exportUserDictionaryToVault(): Boolean {
        val context = applicationContext.get() ?: return false
        val db = florisUserDictionaryDatabase ?: return false
        val target = resolveVaultFile(context, createIfMissing = true) ?: return false
        return runCatching {
            db.exportCombinedList(context, target.uri)
            true
        }.getOrElse { false }
    }

    fun importUserDictionaryFromVault(): Boolean {
        val context = applicationContext.get() ?: return false
        val db = florisUserDictionaryDatabase ?: return false
        val target = resolveVaultFile(context, createIfMissing = false) ?: return false
        return runCatching {
            db.importCombinedList(context, target.uri)
            true
        }.getOrElse { false }
    }

    private fun maybeImportFromVault(context: Context) {
        val db = florisUserDictionaryDatabase ?: return
        val dao = db.userDictionaryDao()
        if (dao.queryAll().isNotEmpty()) return
        importUserDictionaryFromVault()
    }
}
