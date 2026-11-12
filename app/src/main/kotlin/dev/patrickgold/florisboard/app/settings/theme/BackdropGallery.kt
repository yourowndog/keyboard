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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropId
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropOption
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropStore
import dev.patrickgold.florisboard.ime.theme.backdrop.drawBuiltin

/**
 * One-tap thumbnail gallery for backdrops, like the Sci-Fi Keyboard's Background and Animation
 * tabs. Tiles use the key field's rough proportions so a crop looks the way it will behind keys.
 * Thumbnails are first frames: animated backdrops only move on the keyboard itself.
 */
@Composable
fun BackdropGalleryDialog(
    store: BackdropStore,
    options: List<BackdropOption>,
    selected: BackdropId,
    onSelect: (BackdropId) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = options.groupBy { option ->
        when (val id = option.id) {
            BackdropId.None, is BackdropId.Builtin -> "Built in"
            is BackdropId.PrivateAsset -> id.fileName.substringBefore('-', "bundled").replace('_', ' ').replaceFirstChar { it.uppercase() }
            is BackdropId.Imported -> "Imported"
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
                            BackdropTile(store, option, option.id == selected) { onSelect(option.id) }
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
private fun BackdropTile(store: BackdropStore, option: BackdropOption, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .clip(shape)
                .background(Color.Black)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray,
                    shape = shape,
                )
                .clickable(onClick = onClick),
        ) {
            when (val id = option.id) {
                BackdropId.None -> Text("None", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                is BackdropId.Builtin -> Canvas(Modifier.fillMaxSize()) { drawBuiltin(id.kind) }
                else -> store.imageModel(id)?.let { model ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(model).allowHardware(false).size(480).build(),
                        contentDescription = option.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
