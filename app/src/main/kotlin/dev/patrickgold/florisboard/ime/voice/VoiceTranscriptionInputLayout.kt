package dev.patrickgold.florisboard.ime.voice

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.voiceManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

@Composable
fun VoiceTranscriptionInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val voiceManager by context.voiceManager()
    val editorInstance by context.editorInstance()
    val history by voiceManager.history.collectAsState()

    @Composable
    fun HeaderRow() {
        SnyggRow(FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack)
            }
            SnyggText(
                elementName = FlorisImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = "Voice History",
            )
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { voiceManager.clearHistory() },
                enabled = history.isNotEmpty(),
            ) {
                SnyggIcon(imageVector = Icons.Default.DeleteSweep)
            }
        }
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        HeaderRow()
        SnyggBox(FlorisImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxSize()) {
            if (history.isEmpty()) {
                SnyggColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SnyggText(text = "No voice transcriptions yet")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(history) { index, text ->
                        SnyggBox(
                            elementName = FlorisImeUi.ClipboardItem.elementName,
                            modifier = Modifier.fillMaxWidth(),
                            clickAndSemanticsModifier = Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onLongClick = { voiceManager.deleteTranscription(index) },
                                onClick = { editorInstance.commitText(text) }
                            )
                        ) {
                            SnyggText(
                                modifier = Modifier.padding(8.dp),
                                text = text
                            )
                        }
                    }
                }
            }
        }
    }
}
