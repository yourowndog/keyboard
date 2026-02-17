/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.typing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.nlp.NlpLogEvent
import dev.patrickgold.florisboard.ime.nlp.NlpStatus
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.nlpManager
import org.florisboard.lib.compose.stringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NlpDebugScreen() = FlorisScreen {
    title = "NLP Debug & Logs"
    previewFieldVisible = true

    val context = LocalContext.current
    val nlpManager by context.nlpManager()
    var status by remember { mutableStateOf(nlpManager.getStatus()) }
    val logs by nlpManager.nlpLogs.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    actions {
        IconButton(onClick = { status = nlpManager.getStatus() }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
        }
        IconButton(onClick = {
            val logText = logs.joinToString("
") { log ->
                val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(log.timestamp))
                "[$time] Typed: '${log.typed}' | Prev: '${log.prevWord}' | Suggestions: ${log.suggestions.joinToString(", ")}"
            }
            val statusText = """
                SymSpell Ready: ${status.isSymSpellReady} (${status.symSpellWordCount} words, ${status.symSpellPrefixIndexSize} prefixes)
                Ngram Ready: ${status.isNgramEngineReady} (${status.ngramUnigramCount} unigrams)
                Bigrams Ready: ${status.isBigramTableReady} (${status.bigramFirstWordCount} first-words)
            """.trimIndent()
            clipboardManager.setText(AnnotatedString("$statusText

Logs:
$logText"))
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy all info")
        }
    }

    content {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Engine Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            StatusRow("SymSpell (Dictionary)", status.isSymSpellReady, "${status.symSpellWordCount} words, ${status.symSpellPrefixIndexSize} prefixes")
            StatusRow("N-gram Engine", status.isNgramEngineReady, "${status.ngramUnigramCount} unigrams")
            StatusRow("Bigram Table", status.isBigramTableReady, "${status.bigramFirstWordCount} first-words")
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Recent Suggestion Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (logs.isEmpty()) {
                Text("No activity logged yet. Type something in the preview field above!", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(logs) { log ->
                        LogItem(log)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, isReady: Boolean, details: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isReady) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(details, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LogItem(log: NlpLogEvent) {
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(log.timestamp))
    Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Text(time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text("Typed: '${log.typed}' | Prev: '${log.prevWord ?: "None"}'", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text("Result: ${log.suggestions.take(5).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
    }
}
