package dev.patrickgold.florisboard.ime.voice

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.voiceManager
import java.io.File
import java.text.DateFormat
import java.util.Date
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

private fun VoiceTake.statusLabel(): String = when (takeState) {
    VoiceTakeState.RECORDING -> "Recording"
    VoiceTakeState.SAVED -> "Saved · waiting to transcribe"
    VoiceTakeState.TRANSCRIBING -> "Transcribing on Titan"
    VoiceTakeState.READY -> "Ready"
    VoiceTakeState.FAILED -> "Needs retry"
}

private fun formatVoiceDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
fun VoiceTranscriptionInputLayout(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val voiceManager by context.voiceManager()
    val editorInstance by context.editorInstance()
    val clipboard = LocalClipboardManager.current
    val takes by voiceManager.takes.collectAsState()
    val defaultMode by voiceManager.outputMode.collectAsState()

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        SnyggRow(
            elementName = FlorisImeUi.ClipboardHeader.elementName,
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
                text = "Voice Inbox",
            )
            SnyggButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                attributes = mapOf("state" to defaultMode.name.lowercase()),
                onClick = {
                    voiceManager.setOutputMode(
                        if (defaultMode == VoiceOutputMode.CLEANED) VoiceOutputMode.VERBATIM
                        else VoiceOutputMode.CLEANED,
                    )
                },
            ) {
                SnyggText(text = if (defaultMode == VoiceOutputMode.CLEANED) "Cleaned" else "Verbatim")
            }
        }

        SnyggBox(
            elementName = FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (takes.isEmpty()) {
                SnyggColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SnyggText(text = "Your recordings will appear here")
                    SnyggText(text = "Interrupted takes are saved automatically")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = takes, key = { it.id }) { take ->
                        var mode by remember(take.id, defaultMode) { mutableStateOf(defaultMode) }
                        var expanded by remember(take.id) { mutableStateOf(false) }
                        val date = remember(take.capturedAtMs) {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(take.capturedAtMs))
                        }
                        val duration = formatVoiceDuration(take.durationMs)

                        SnyggBox(
                            elementName = FlorisImeUi.ClipboardItem.elementName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SnyggText(text = take.statusLabel())
                                    Spacer(modifier = Modifier.weight(1f))
                                    SnyggText(
                                        text = listOf(date, duration)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                    )
                                }

                                when (take.takeState) {
                                    VoiceTakeState.READY -> {
                                        if (!take.verbatimText.isNullOrBlank()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                SnyggButton(
                                                    elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                                    attributes = mapOf(
                                                        "state" to if (mode == VoiceOutputMode.CLEANED) "active" else "inactive",
                                                    ),
                                                    onClick = { mode = VoiceOutputMode.CLEANED },
                                                ) { SnyggText(text = "Cleaned") }
                                                SnyggButton(
                                                    elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                                    attributes = mapOf(
                                                        "state" to if (mode == VoiceOutputMode.VERBATIM) "active" else "inactive",
                                                    ),
                                                    onClick = { mode = VoiceOutputMode.VERBATIM },
                                                ) { SnyggText(text = "Verbatim") }
                                            }
                                        }
                                        val selectedText = take.textFor(mode)
                                        val preview = if (!expanded && selectedText.length > 280) {
                                            selectedText.take(280).trimEnd() + "…"
                                        } else {
                                            selectedText
                                        }
                                        SnyggBox(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            clickAndSemanticsModifier = Modifier.combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(),
                                                onClick = { expanded = !expanded },
                                                onLongClick = { clipboard.setText(AnnotatedString(selectedText)) },
                                            ),
                                        ) {
                                            SnyggText(text = preview)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                        ) {
                                            SnyggButton(
                                                elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                                onClick = { clipboard.setText(AnnotatedString(selectedText)) },
                                            ) { SnyggText(text = "Copy") }
                                            SnyggButton(
                                                elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                                onClick = {
                                                    editorInstance.commitText(selectedText)
                                                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                                                },
                                            ) { SnyggText(text = "Insert") }
                                        }
                                    }

                                    VoiceTakeState.FAILED, VoiceTakeState.SAVED -> {
                                        take.error?.takeIf { it.isNotBlank() }?.let { SnyggText(text = it) }
                                        if (take.audioPath?.let { File(it).isFile } == true) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                SnyggButton(
                                                    elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                                    onClick = { keyboardManager.retryVoiceTake(take) },
                                                ) { SnyggText(text = "Retry") }
                                            }
                                        }
                                    }

                                    VoiceTakeState.RECORDING, VoiceTakeState.TRANSCRIBING -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
