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

package dev.patrickgold.florisboard.lib.compose

import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.console.ConsoleHeader
import dev.patrickgold.florisboard.app.console.ConsolePanel
import dev.patrickgold.florisboard.app.console.ConsoleRail
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisAppBar
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.autoMirrorForRtl
import org.florisboard.lib.compose.florisVerticalScroll

@Composable
fun FlorisScreen(builder: @Composable FlorisScreenScope.() -> Unit) {
    val scope = remember { FlorisScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias FlorisScreenActions = @Composable RowScope.() -> Unit
typealias FlorisScreenBottomBar = @Composable () -> Unit
typealias FlorisScreenContent = PreferenceUiContent<FlorisPreferenceModel>
typealias FlorisScreenFab = @Composable () -> Unit
typealias FlorisScreenNavigationIcon = @Composable () -> Unit

interface FlorisScreenScope {
    var title: String

    var navigationIconVisible: Boolean

    var previewFieldVisible: Boolean

    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    /** Which settings panel this screen belongs to; sets the colour of its LCARS elbow and rail. */
    var panel: ConsolePanel

    /**
     * Open the real keyboard on the preview field when the screen appears, so changes to look,
     * sound or geometry show on the live keyboard docked under the controls as they are made.
     */
    var dockKeyboard: Boolean

    fun actions(actions: FlorisScreenActions)

    fun bottomBar(bottomBar: FlorisScreenBottomBar)

    fun content(content: FlorisScreenContent)

    fun floatingActionButton(fab: FlorisScreenFab)

    fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon)
}

private class FlorisScreenScopeImpl : FlorisScreenScope {
    override var title: String by mutableStateOf("")
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var previewFieldVisible: Boolean by mutableStateOf(false)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(true)
    override var panel: ConsolePanel by mutableStateOf(ConsolePanel.HOME)
    override var dockKeyboard: Boolean by mutableStateOf(false)

    private var actions: FlorisScreenActions = @Composable { }
    private var bottomBar: FlorisScreenBottomBar = @Composable { }
    private var content: FlorisScreenContent = @Composable { }
    private var fab: FlorisScreenFab = @Composable { }
    private var customNavigationIcon: FlorisScreenNavigationIcon? = null
    private var navigationIcon: FlorisScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        FlorisIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.autoMirrorForRtl(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
    }

    override fun actions(actions: FlorisScreenActions) {
        this.actions = actions
    }

    override fun bottomBar(bottomBar: FlorisScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: FlorisScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: FlorisScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon) {
        this.customNavigationIcon = navigationIcon
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val previewFieldController = LocalPreviewFieldController.current
        val colorScheme = MaterialTheme.colorScheme

        SideEffect {
            val window = (context as Activity).window
            previewFieldController?.isVisible = previewFieldVisible
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = true
            } else {
                window.navigationBarColor = colorScheme.scrim.toArgb()
            }
        }

        val navController = LocalNavController.current
        val color = panel.color

        if (dockKeyboard && previewFieldVisible && previewFieldController != null) {
            LaunchedEffect(Unit) {
                // Wait for the preview field's enter animation before asking for focus.
                delay(350)
                runCatching { previewFieldController.focusRequester.requestFocus() }
            }
        }

        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                ConsoleHeader(
                    title = title,
                    color = color,
                    navigation = customNavigationIcon.takeIf { navigationIconVisible },
                    onBack = if (navigationIconVisible && customNavigationIcon == null) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                    actions = actions,
                    modifier = Modifier.statusBarsPadding().padding(top = 8.dp),
                )
            },
            bottomBar = bottomBar,
            floatingActionButton = fab,
        ) { innerPadding ->
            val scrollModifier = if (scrollable) {
                Modifier.florisVerticalScroll()
            } else {
                Modifier
            }
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                ConsoleRail(color = color, modifier = Modifier.padding(bottom = 8.dp))
                PreferenceLayout(
                    FlorisPreferenceStore,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(scrollModifier),
                    iconSpaceReserved = iconSpaceReserved,
                    content = content,
                )
            }
        }
    }
}
