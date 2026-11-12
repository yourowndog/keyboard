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

package dev.patrickgold.florisboard.app.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The settings app's LCARS console: palette, type and the frame pieces every screen shares.
 *
 * Settings follow the keyboard's LCARS Jefferies look rather than Material You, so the app reads
 * as the same device as the keyboard. Each panel owns one colour, used on its elbow, rail and home
 * tile, so the colour alone tells you where you are. See docs/theming/settings-console.md.
 */
object Lcars {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF0D0C12)
    val PanelRaised = Color(0xFF17151E)
    val Ink = Color(0xFFF3E7D3)
    val InkQuiet = Color(0xFFB4A694)
    val Rule = Color(0xFF3A3442)

    val Amber = Color(0xFFFF9C2A)
    val Peach = Color(0xFFFF9D7A)
    val Lilac = Color(0xFFC79BD6)
    val Blue = Color(0xFF8FA8FF)
    val Sky = Color(0xFF8CD3F2)
    val Tan = Color(0xFFE8BE8A)
    val Teal = Color(0xFF6FD3BE)
    val Coral = Color(0xFFFF6F61)
    val Steel = Color(0xFF8E8A9C)
}

/** One colour per settings panel; sub-screens use their panel's colour. */
enum class ConsolePanel(val color: Color) {
    HOME(Lcars.Amber),
    LOOK(Lcars.Amber),
    SOUND_TOUCH(Lcars.Lilac),
    LAYOUT(Lcars.Peach),
    COMMAND_STRIP(Lcars.Sky),
    TYPING(Lcars.Tan),
    GESTURES(Lcars.Teal),
    SYSTEM(Lcars.Steel),
}

fun lcarsColorScheme(): ColorScheme = darkColorScheme(
    primary = Lcars.Amber,
    onPrimary = Lcars.Black,
    primaryContainer = Color(0xFF3B2610),
    onPrimaryContainer = Lcars.Tan,
    secondary = Lcars.Lilac,
    onSecondary = Lcars.Black,
    secondaryContainer = Color(0xFF2D2236),
    onSecondaryContainer = Lcars.Ink,
    tertiary = Lcars.Sky,
    onTertiary = Lcars.Black,
    tertiaryContainer = Color(0xFF14303C),
    onTertiaryContainer = Lcars.Ink,
    error = Lcars.Coral,
    onError = Lcars.Black,
    errorContainer = Color(0xFF3C1714),
    onErrorContainer = Lcars.Ink,
    background = Lcars.Black,
    onBackground = Lcars.Ink,
    surface = Lcars.Black,
    onSurface = Lcars.Ink,
    surfaceVariant = Lcars.PanelRaised,
    onSurfaceVariant = Lcars.InkQuiet,
    surfaceContainerLowest = Lcars.Black,
    surfaceContainerLow = Lcars.Panel,
    surfaceContainer = Lcars.Panel,
    surfaceContainerHigh = Lcars.PanelRaised,
    surfaceContainerHighest = Color(0xFF221F2B),
    outline = Lcars.Rule,
    outlineVariant = Color(0xFF26222D),
    scrim = Lcars.Black,
    inverseSurface = Lcars.Ink,
    inverseOnSurface = Lcars.Black,
    inversePrimary = Color(0xFF7A4A00),
)

/** Chakra Petch for names and headings, Oxanium for values; both ship in the theme assets. */
@Composable
fun rememberLcarsFonts(): Pair<FontFamily, FontFamily> {
    val assets = LocalContext.current.assets
    return remember(assets) {
        val base = "ime/theme/org.florisboard.themes/fonts"
        val chakra = runCatching {
            FontFamily(Font("$base/ChakraPetch-SemiBold.ttf", assets, FontWeight.SemiBold))
        }.getOrDefault(FontFamily.Default)
        val oxanium = runCatching {
            FontFamily(Font("$base/Oxanium-SemiBold.ttf", assets, FontWeight.SemiBold))
        }.getOrDefault(FontFamily.Default)
        chakra to oxanium
    }
}

@Composable
fun lcarsTypography(): Typography {
    val (chakra, oxanium) = rememberLcarsFonts()
    val base = Typography()
    fun TextStyle.chakra() = copy(fontFamily = chakra, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    fun TextStyle.oxanium() = copy(fontFamily = oxanium, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    return remember(chakra, oxanium) {
        Typography(
            displayLarge = base.displayLarge.chakra(),
            displayMedium = base.displayMedium.chakra(),
            displaySmall = base.displaySmall.chakra(),
            headlineLarge = base.headlineLarge.chakra(),
            headlineMedium = base.headlineMedium.chakra(),
            headlineSmall = base.headlineSmall.chakra(),
            titleLarge = base.titleLarge.chakra(),
            titleMedium = base.titleMedium.chakra(),
            titleSmall = base.titleSmall.chakra().copy(letterSpacing = 1.2.sp),
            bodyLarge = base.bodyLarge.chakra().copy(fontSize = 17.sp),
            bodyMedium = base.bodyMedium.oxanium(),
            bodySmall = base.bodySmall.oxanium(),
            labelLarge = base.labelLarge.chakra().copy(letterSpacing = 1.sp),
            labelMedium = base.labelMedium.chakra(),
            labelSmall = base.labelSmall.chakra(),
        )
    }
}

private val ElbowOuter = 34.dp
private val ElbowInner = 14.dp
val ConsoleRailWidth = 22.dp
val ConsoleBarHeight = 30.dp
private val ElbowWidth = 64.dp

/**
 * The LCARS elbow header: a coloured elbow on the left (the back button when [onBack] is set), a
 * bar across the top, the screen title in the panel colour and an end cap. The rail below it is
 * drawn by [ConsoleRail], so the two read as one frame.
 */
@Composable
fun ConsoleHeader(
    title: String,
    color: Color,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    navigation: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ElbowOuter + ConsoleBarHeight + 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(ElbowWidth)
                .fillMaxHeight()
                .then(if (onBack != null) Modifier.clickable(onClick = onBack) else Modifier),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawPath(elbowPath(size, ElbowOuter.toPx(), ElbowInner.toPx(), ConsoleBarHeight.toPx(),
                    ConsoleRailWidth.toPx()), color)
            }
            if (navigation != null) {
                Box(Modifier.padding(start = 2.dp)) {
                    CompositionLocalProvider(LocalContentColor provides Lcars.Black) { navigation() }
                }
            } else if (onBack != null) {
                Text(
                    text = "‹",
                    color = Lcars.Black,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 10.dp, top = 0.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(ConsoleBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                color = color,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 24.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(3f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            Box(
                Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp))
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

private fun elbowPath(size: Size, outer: Float, inner: Float, bar: Float, rail: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(0f, h)
    lineTo(0f, outer)
    quadraticTo(0f, 0f, outer, 0f)
    lineTo(w, 0f)
    lineTo(w, bar)
    lineTo(rail + inner, bar)
    quadraticTo(rail, bar, rail, bar + inner)
    lineTo(rail, h)
    close()
}

/** The vertical rail that continues the elbow down the left edge of the content. */
@Composable
fun ConsoleRail(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(ConsoleRailWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(bottomStart = 11.dp, bottomEnd = 11.dp))
            .background(color),
    )
}

/** A home-screen panel block: label in black on the panel colour, summary beneath in quiet ink. */
@Composable
fun ConsoleTile(
    label: String,
    summary: String,
    color: Color,
    code: String,
    tall: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 6.dp, bottomEnd = 6.dp))
            .background(Lcars.PanelRaised)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (tall) 64.dp else 48.dp)
                .background(color)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                color = Lcars.Black,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = if (tall) 20.sp else 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = code,
                color = Lcars.Black.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = summary,
            color = Lcars.InkQuiet,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** A one-line status readout pill, for example the input-method state on the home screen. */
@Composable
fun ConsoleStatus(text: String, color: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text.uppercase(), color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** A small label-over-value readout used in the home status strip. */
@Composable
fun ConsoleReadout(label: String, value: String, color: Color, modifier: Modifier = Modifier, width: Dp? = null) {
    Column(modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier)) {
        Text(text = label.uppercase(), color = Lcars.InkQuiet, style = MaterialTheme.typography.labelSmall)
        Text(
            text = value.uppercase(),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
