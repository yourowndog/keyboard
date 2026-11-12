/*
 * Copyright (C) 2026 The OmniBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkin
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkinId
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkinOption
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkinSlot
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkinStore
import dev.patrickgold.florisboard.ime.theme.keyskin.drawKeySkinTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-tap gallery for key skins. Each tile is a miniature keyboard drawn with the skin's own
 * textures (ordinary keys, shift, delete, space, enter), so the special-key contrast a skin gives is
 * visible before picking it. Swatches skin the ordinary key only.
 */
@Composable
fun KeySkinGalleryDialog(
    store: KeySkinStore,
    options: List<KeySkinOption>,
    selected: KeySkinId,
    onSelect: (KeySkinId) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = options.groupBy { option ->
        when (option.id) {
            KeySkinId.None -> "Theme"
            is KeySkinId.Pack -> "Skins"
            is KeySkinId.Swatch -> "Key swatches"
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groups.forEach { (group, items) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "$group (${items.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            )
                        }
                        items(items, key = { it.id.storedValue.ifEmpty { "none" } }) { option ->
                            KeySkinTile(store, option, option.id == selected) { onSelect(option.id) }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun KeySkinTile(store: KeySkinStore, option: KeySkinOption, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val skin by produceState<KeySkin?>(initialValue = null, option.id) {
        value = withContext(Dispatchers.IO) { store.load(option.id) }
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(shape)
                .background(Color(0xFF0A0A0C))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray,
                    shape = shape,
                )
                .clickable(onClick = onClick),
        ) {
            val current = skin
            if (option.id == KeySkinId.None) {
                Text("Theme keys", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else if (current != null) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) { drawMiniKeyboard(current) }
            }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, start = 2.dp),
        )
    }
}

/** Three rows: four letters; shift, two letters, delete; space and enter. */
private fun DrawScope.drawMiniKeyboard(skin: KeySkin) {
    val rows = listOf(
        listOf(KeySkinSlot.KEY to 1f, KeySkinSlot.KEY to 1f, KeySkinSlot.KEY to 1f, KeySkinSlot.KEY to 1f),
        listOf(KeySkinSlot.SHIFT to 1.4f, KeySkinSlot.KEY to 1f, KeySkinSlot.KEY_PRESSED to 1f, KeySkinSlot.DELETE to 1.4f),
        listOf(KeySkinSlot.SPACE to 3f, KeySkinSlot.ENTER to 1.4f),
    )
    val gap = 3.dp.toPx()
    val rowHeight = (size.height - gap * (rows.size - 1)) / rows.size
    rows.forEachIndexed { r, row ->
        val units = row.sumOf { it.second.toDouble() }.toFloat()
        val unit = (size.width - gap * (row.size - 1)) / units
        var x = 0f
        row.forEach { (slot, weight) ->
            val w = unit * weight
            val fallback = if (slot == KeySkinSlot.KEY_PRESSED) {
                listOf(KeySkinSlot.KEY_PRESSED, KeySkinSlot.KEY)
            } else {
                listOf(slot, KeySkinSlot.KEY)
            }
            skin.resolve(fallback)?.let { (resolved, texture) ->
                translate(x, r * (rowHeight + gap)) {
                    drawKeySkinTexture(texture, darken = resolved != slot && slot == KeySkinSlot.KEY_PRESSED, keySize = Size(w, rowHeight))
                }
            }
            x += w + gap
        }
    }
}
