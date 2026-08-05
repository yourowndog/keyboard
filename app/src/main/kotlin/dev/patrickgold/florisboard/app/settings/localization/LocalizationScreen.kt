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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.observeAsNonNullState
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.serialization.json.Json
import org.florisboard.lib.compose.stringRes

internal val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

internal data class LocalizationSubtypeEntry(
    val subtype: Subtype,
    val isImplicitDefault: Boolean,
)

internal fun localizationSubtypeEntries(
    configuredSubtypes: List<Subtype>,
    activeSubtype: Subtype,
): List<LocalizationSubtypeEntry> {
    return if (configuredSubtypes.isEmpty()) {
        listOf(LocalizationSubtypeEntry(activeSubtype, isImplicitDefault = true))
    } else {
        configuredSubtypes.map { LocalizationSubtypeEntry(it, isImplicitDefault = false) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalizationScreen() = FlorisScreen {
    title = stringRes(R.string.settings__localization__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            text = {
                Text(
                    text = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            shape = FloatingActionButtonDefaults.extendedFabShape,
            onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
        )
    }

    content {
        val subtypes by subtypeManager.subtypesFlow.collectAsState()
        val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
        val subtypeEntries = localizationSubtypeEntries(subtypes, activeSubtype)
        val currencySets by keyboardManager.resources.currencySets.observeAsNonNullState()
        val layouts by keyboardManager.resources.layouts.observeAsNonNullState()
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.observeAsState()
        for (entry in subtypeEntries) {
            val subtype = entry.subtype
            val cMeta = layouts[LayoutType.CHARACTERS]?.get(subtype.layoutMap.characters)
            val sMeta = layouts[LayoutType.SYMBOLS]?.get(subtype.layoutMap.symbols)
            val s2Meta = layouts[LayoutType.SYMBOLS2]?.get(subtype.layoutMap.symbols2)
            val currMeta = currencySets[subtype.currencySet]
            val summary = stringRes(
                id = R.string.settings__localization__subtype_summary,
                "characters_name" to (cMeta?.label ?: "null"),
                "symbols_name" to (sMeta?.label ?: "null"),
                "symbols2_name" to (s2Meta?.label ?: "null"),
                "currency_set_name" to (currMeta?.label ?: "null"),
            )
            val languageName = when (displayLanguageNamesIn) {
                DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayName()
                DisplayLanguageNamesIn.NATIVE_LOCALE -> subtype.primaryLocale.displayName(subtype.primaryLocale)
            }
            Preference(
                title = if (entry.isImplicitDefault) {
                    stringRes(
                        R.string.settings__localization__subtype_implicit_default_title,
                        "subtype_name" to languageName,
                    )
                } else {
                    languageName
                },
                summary = summary,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        navController.navigate(
                            Routes.Settings.SubtypeEdit(subtype.id)
                        )
                    },
                    onLongClick = if (entry.isImplicitDefault) {
                        null
                    } else {
                        { chosenSubtypeToDelete = subtype }
                    },
                )
            )
        }
    }

    DeleteSubtypeConfirmationDialog(
        subtypeToDelete = chosenSubtypeToDelete,
        onDismiss = {
            chosenSubtypeToDelete = null
        },
        onConfirm = {
            chosenSubtypeToDelete?.let { subtypeManager.removeSubtype(subtypeToRemove = it) }
            chosenSubtypeToDelete = null
        }
    )

}

@Composable
fun DeleteSubtypeConfirmationDialog(
    subtypeToDelete: Subtype?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
)   {
    subtypeToDelete?.let {
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__localization__subtype_delete_confirmation_title),
            confirmLabel = stringRes(R.string.action__yes),
            dismissLabel = stringRes(R.string.action__no),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            ) {
                Text(stringRes(R.string.settings__localization__subtype_delete_confirmation_warning))
            }
    }
}
