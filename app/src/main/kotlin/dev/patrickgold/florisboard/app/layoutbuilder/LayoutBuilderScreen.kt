package dev.patrickgold.florisboard.app.layoutbuilder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackDocument
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackProblem
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackValidation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.layoutPackRepository
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private data class LayoutBuilderUiState(
    val workingPack: LayoutPackDocument,
    val undoStack: List<LayoutPackDocument> = emptyList(),
    val redoStack: List<LayoutPackDocument> = emptyList(),
    /** Problems reported by the last import, as opposed to problems with the working pack itself. */
    val importProblems: List<LayoutPackProblem> = emptyList(),
)

@Composable
fun LayoutBuilderScreen() = FlorisScreen {
    title = stringRes(R.string.layout_builder__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val keyboardManager by context.keyboardManager()
    val repo = context.layoutPackRepository
    val activePack by keyboardManager.layoutFlow.collectAsState()

    // When no pack is selected the builder opens on the bundled pack as an editable starting point.
    // Opening on the empty "nothing selected" document would show the user a keyboard with no rows
    // and nothing to edit.
    val openingPack = remember(activePack) {
        if (activePack.document.isEmpty) {
            repo.loadBundled()?.document ?: activePack.document
        } else {
            activePack.document
        }
    }
    var state by remember(openingPack) { mutableStateOf(LayoutBuilderUiState(openingPack)) }

    val validationErrors = remember(state.workingPack) {
        LayoutPackValidation.validate(state.workingPack)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val outcome = repo.load(uri)
            val document = outcome.document
            state = if (document != null) {
                LayoutBuilderUiState(document, importProblems = outcome.problems)
            } else {
                state.copy(importProblems = outcome.problems)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            repo.export(state.workingPack, uri)
            // TODO: Provide user-visible feedback for export outcomes.
        }
    }

    fun mutatePack(block: (LayoutPackDocument) -> LayoutPackDocument) {
        val next = block(state.workingPack)
        if (next != state.workingPack) {
            state = state.copy(
                workingPack = next,
                undoStack = state.undoStack + state.workingPack,
                redoStack = emptyList(),
            )
        }
    }

    fun undo() {
        if (state.undoStack.isNotEmpty()) {
            val previous = state.undoStack.last()
            state = state.copy(
                workingPack = previous,
                undoStack = state.undoStack.dropLast(1),
                redoStack = state.redoStack + state.workingPack,
            )
        }
    }

    fun redo() {
        if (state.redoStack.isNotEmpty()) {
            val next = state.redoStack.last()
            state = state.copy(
                workingPack = next,
                redoStack = state.redoStack.dropLast(1),
                undoStack = state.undoStack + state.workingPack,
            )
        }
    }

    // Applying writes a file and a preference, both of which suspend, so it runs in the screen's own
    // scope rather than on the click.
    fun applyToIme() {
        uiScope.launch {
            keyboardManager.setLayout(state.workingPack).onFailure {
                // TODO: Provide user-visible feedback for apply failures.
            }
        }
    }

    actions {
        TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
            Text(stringRes(R.string.layout_builder__action_import))
        }
        TextButton(onClick = { exportLauncher.launch("lcars_hacker_en_us.json") }) {
            Text(stringRes(R.string.layout_builder__action_export))
        }
        IconButton(onClick = { undo() }, enabled = state.undoStack.isNotEmpty()) {
            Icon(Icons.Default.Undo, contentDescription = stringRes(R.string.layout_builder__action_undo))
        }
        IconButton(onClick = { redo() }, enabled = state.redoStack.isNotEmpty()) {
            Icon(Icons.Default.Redo, contentDescription = stringRes(R.string.layout_builder__action_redo))
        }
        ElevatedButton(onClick = { applyToIme() }, enabled = validationErrors.isEmpty()) {
            Text(stringRes(R.string.layout_builder__action_apply))
        }
    }

    content {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Use Import to load a JSON layout pack and Apply to activate it.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Export saves your current pack as a JSON file for backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Undo and Redo let you review previous imports during this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Current layout",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${state.workingPack.id} — ${state.workingPack.label}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Schema ${state.workingPack.schemaVersion}, " +
                        "${state.workingPack.rows.size} rows",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.importProblems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The imported file could not be used as-is:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.importProblems.forEach { problem ->
                        Text(
                            text = problem.describe(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (validationErrors.isEmpty()) {
                Text(
                    text = "No validation issues detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringRes(R.string.layout_builder__validation_failed_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    validationErrors.forEach { problem ->
                        Text(
                            text = problem.describe(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
