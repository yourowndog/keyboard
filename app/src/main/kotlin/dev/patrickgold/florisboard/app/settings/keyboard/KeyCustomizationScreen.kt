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

package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.KeyCustomization
import dev.patrickgold.florisboard.ime.keyboard.KeyCustomizationManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefDropdown
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyCustomizationScreen() = FlorisScreen {
    title = stringRes(R.string.pref__keyboard__key_customization__title)
    previewFieldVisible = true

    val customizableKeys = KeyCustomizationManager.customizableKeys
    var selectedKeyIndex by remember { mutableIntStateOf(0) }
    val selectedKey = customizableKeys[selectedKeyIndex]

    content {
        val scope = rememberCoroutineScope()
        var showRestoreConfirmation by remember { mutableStateOf(false) }
        val keyCustomizationsJson by prefs.keyboard.keyCustomizations.observeAsState()
        
        val currentCustomization = remember(keyCustomizationsJson, selectedKey.code) {
            KeyCustomizationManager.getForKey(keyCustomizationsJson, selectedKey.code) ?: KeyCustomization()
        }
        
        fun updateCustomization(newCustomization: KeyCustomization) {
            val newJson = KeyCustomizationManager.setForKey(keyCustomizationsJson, selectedKey.code, newCustomization)
            scope.launch { prefs.keyboard.keyCustomizations.set(newJson) }
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__key_customization__select_key)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                JetPrefDropdown(
                    options = customizableKeys.map { it.label },
                    selectedOptionIndex = selectedKeyIndex,
                    onSelectOption = { selectedKeyIndex = it },
                )
            }
        }
        
        PreferenceGroup(title = stringRes(R.string.pref__keyboard__key_customization__height)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = stringRes(R.string.pref__keyboard__key_customization__height_factor, "v" to (currentCustomization.heightFactor * 100).toInt()))
                Slider(
                    value = currentCustomization.heightFactor,
                    onValueChange = { updateCustomization(currentCustomization.copy(heightFactor = it)) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
            }
        }
        
        PreferenceGroup(title = stringRes(R.string.pref__keyboard__key_customization__width)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = stringRes(R.string.pref__keyboard__key_customization__width_factor, "v" to (currentCustomization.widthFactor * 100).toInt()))
                Slider(
                    value = currentCustomization.widthFactor,
                    onValueChange = { updateCustomization(currentCustomization.copy(widthFactor = it)) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
            }
        }
        
        PreferenceGroup(title = stringRes(R.string.pref__keyboard__key_customization__padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = stringRes(R.string.pref__keyboard__key_customization__padding_top, "v" to currentCustomization.paddingTop.toInt()))
                Slider(
                    value = currentCustomization.paddingTop,
                    onValueChange = { updateCustomization(currentCustomization.copy(paddingTop = it)) },
                    valueRange = 0f..20f,
                    steps = 19,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = stringRes(R.string.pref__keyboard__key_customization__padding_bottom, "v" to currentCustomization.paddingBottom.toInt()))
                Slider(
                    value = currentCustomization.paddingBottom,
                    onValueChange = { updateCustomization(currentCustomization.copy(paddingBottom = it)) },
                    valueRange = 0f..20f,
                    steps = 19,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = stringRes(R.string.pref__keyboard__key_customization__padding_left, "v" to currentCustomization.paddingLeft.toInt()))
                Slider(
                    value = currentCustomization.paddingLeft,
                    onValueChange = { updateCustomization(currentCustomization.copy(paddingLeft = it)) },
                    valueRange = 0f..20f,
                    steps = 19,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = stringRes(R.string.pref__keyboard__key_customization__padding_right, "v" to currentCustomization.paddingRight.toInt()))
                Slider(
                    value = currentCustomization.paddingRight,
                    onValueChange = { updateCustomization(currentCustomization.copy(paddingRight = it)) },
                    valueRange = 0f..20f,
                    steps = 19,
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__key_customization__restore__title)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = stringRes(R.string.pref__keyboard__key_customization__restore__summary))
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showRestoreConfirmation = true },
                    enabled = keyCustomizationsJson != KeyCustomizationManager.NO_CUSTOMIZATIONS,
                ) {
                    Text(text = stringRes(R.string.pref__keyboard__key_customization__restore__action))
                }
            }
        }

        if (showRestoreConfirmation) {
            JetPrefAlertDialog(
                title = stringRes(R.string.pref__keyboard__key_customization__restore__action),
                confirmLabel = stringRes(R.string.pref__keyboard__key_customization__restore__action),
                onConfirm = {
                    // Only this preference is written. Nothing else about the keyboard changes.
                    scope.launch {
                        prefs.keyboard.keyCustomizations.set(KeyCustomizationManager.NO_CUSTOMIZATIONS)
                    }
                    showRestoreConfirmation = false
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showRestoreConfirmation = false },
            ) {
                Text(text = stringRes(R.string.pref__keyboard__key_customization__restore__confirm))
            }
        }
    }
}
