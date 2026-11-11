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

package dev.patrickgold.florisboard.ime.keyboard

import android.content.Context
import android.os.Environment
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.collectIn
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

object KeyCustomizationExporter {
    private const val FILENAME = "key_customizations.json"
    private var exportFile: File? = null
    private var isInitialized = false
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun init(context: Context, scope: CoroutineScope, prefs: FlorisPreferenceModel) {
        if (isInitialized) return
        
        setupFile(context)
        
        // Customizations became profile-scoped in Stage 04. The export file holds one profile's
        // worth of JSON, so it must follow the active profile rather than a fixed one — switching
        // profiles republishes, and edits to the profile that is not showing are ignored.
        prefs.keyboard.activeProfileId.asFlow()
            .flatMapLatest { id ->
                prefs.keyboard.profile(KeyboardProfile.fromId(id)).keyCustomizations.asFlow()
            }
            .collectIn(scope) { json: String ->
                export(json)
            }
        
        isInitialized = true
    }
    
    private fun setupFile(context: Context) {
        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir.exists() || documentsDir.mkdirs()) {
                exportFile = File(documentsDir, FILENAME)
            }
        } catch (e: Exception) {
            android.util.Log.e("KeyCustomizationExporter", "Failed to setup export file", e)
        }
    }
    
    private fun export(json: String) {
        val file = exportFile ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrintWriter(FileWriter(file, false)).use { out ->
                    out.print(json)
                }
                android.util.Log.i("KeyCustomizationExporter", "Exported to ${file.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("KeyCustomizationExporter", "Failed to export customizations", e)
            }
        }
    }
}
