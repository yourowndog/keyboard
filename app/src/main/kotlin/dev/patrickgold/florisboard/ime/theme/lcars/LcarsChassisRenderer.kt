/*
 * Copyright (C) 2026 The OmniBoard Authors
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

package dev.patrickgold.florisboard.ime.theme.lcars

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.cos
import kotlin.math.sin

/**
 * Converts the pure [LcarsChassisGeometry] to Compose [Path] objects.
 *
 * This is the rendering bridge between the analytic model and the
 * Canvas / drawBehind drawing surface. Paths are built by placing the
 * first segment's start point, then drawing each subsequent segment from
 * where the previous one ended — no gaps or unintended diagonal connectors.
 */
class LcarsChassisPaths(
    val primary: Path,
    val secondary: Path,
    val deck: Path,
    val viewportClip: Path,
    /** The geometry the paths were actually built from (post-snap). */
    val sourceGeometry: LcarsChassisGeometry,
)

/** Degrees-to-radians conversion factor. */
private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

/**
 * Build [LcarsChassisPaths] from a [LcarsChassisGeometry] model.
 *
 * @param geometry  the pure geometry model in logical dp units.
 * @param density   the Compose density for pixel snapping of thin lines.
 * @param snapped   when true, pixel-snaps the geometry first so thin channels
 *                  land on integral pixels (see [LcarsChassisGeometry.snappedToPixels]).
 *                  The returned [sourceGeometry] is the post-snap geometry, so
 *                  callers can confirm that snapping materially changed the
 *                  rendered geometry rather than copying values back.
 */
fun LcarsChassisPaths(
    geometry: LcarsChassisGeometry,
    density: Float,
    snapped: Boolean = true,
): LcarsChassisPaths {
    val effective = if (snapped) geometry.snappedToPixels(density) else geometry
    fun buildPath(descriptor: LcarsChassisGeometry.PathDescriptor): Path {
        return Path().apply {
            if (descriptor.segments.isEmpty()) return@apply

            // Move to the first segment's start point
            val first = descriptor.segments.first()
            when (first) {
                is LcarsChassisGeometry.Segment.Line -> {
                    moveTo(first.x1 * density, first.y1 * density)
                    lineTo(first.x2 * density, first.y2 * density)
                }
                is LcarsChassisGeometry.Segment.Arc -> {
                    val startAngleRad = first.startAngle * DEG_TO_RAD
                    val sx = first.cx + first.radius * cos(startAngleRad)
                    val sy = first.cy + first.radius * sin(startAngleRad)
                    moveTo(sx * density, sy * density)
                    arcTo(
                        rect = Rect(
                            left = (first.cx - first.radius) * density,
                            top = (first.cy - first.radius) * density,
                            right = (first.cx + first.radius) * density,
                            bottom = (first.cy + first.radius) * density,
                        ),
                        startAngleDegrees = first.startAngle,
                        sweepAngleDegrees = first.sweepAngle,
                        forceMoveTo = false,
                    )
                }
            }

            // Append remaining segments — each starts where the prior ended
            for (i in 1 until descriptor.segments.size) {
                val seg = descriptor.segments[i]
                when (seg) {
                    is LcarsChassisGeometry.Segment.Line -> {
                        lineTo(seg.x2 * density, seg.y2 * density)
                    }
                    is LcarsChassisGeometry.Segment.Arc -> {
                        arcTo(
                            rect = Rect(
                                left = (seg.cx - seg.radius) * density,
                                top = (seg.cy - seg.radius) * density,
                                right = (seg.cx + seg.radius) * density,
                                bottom = (seg.cy + seg.radius) * density,
                            ),
                            startAngleDegrees = seg.startAngle,
                            sweepAngleDegrees = seg.sweepAngle,
                            forceMoveTo = false,
                        )
                    }
                }
            }

            close()
        }
    }

    return LcarsChassisPaths(
        primary = buildPath(effective.primaryRail()),
        secondary = buildPath(effective.secondaryRail()),
        deck = buildPath(effective.deckPath()),
        viewportClip = buildPath(effective.viewportClip()),
        sourceGeometry = effective,
    )
}

/**
 * Draws the LCARS chassis using semantic role colors.
 *
 * The deck is painted **inside** the contoured [viewportClip] so the backing
 * hugs the elbow silhouette instead of bleeding square past the shoulder —
 * the clip is genuinely applied, not built and discarded. The two rail bands
 * are then painted over it from the analytic geometry.
 *
 * @param geometry            the pure geometry model.
 * @param primaryColor        color for the primary (outer) rail.
 * @param secondaryColor      color for the secondary (inner) rail.
 * @param deckColor           color for the keyboard deck.
 * @param strokeWidth         optional stroke width (dp); 0 = fill only.
 * @param snapChannelsToPixels  if true, snap thin channels to physical pixels.
 */
fun DrawScope.drawLcarsChassis(
    geometry: LcarsChassisGeometry,
    primaryColor: Color,
    secondaryColor: Color,
    deckColor: Color,
    strokeWidth: Dp = 0.dp,
    snapChannelsToPixels: Boolean = true,
    extendDeckToBottom: Boolean = false,
) {
    val density = this.density

    val paths = LcarsChassisPaths(
        geometry = geometry,
        density = density,
        snapped = snapChannelsToPixels,
    )

    // Deck background first (largest, behind everything), clipped to the
    // contoured viewport region so it does not paint past the chassis.
    clipPath(path = paths.viewportClip) {
        drawPath(
            path = paths.deck,
            color = deckColor,
            style = if (strokeWidth > 0.dp) Stroke(width = strokeWidth.toPx()) else Fill,
        )
    }

    // The model describes the reserved header. Keep the key field full width
    // with a single backing, rather than extending rail shoulders under keys.
    if (extendDeckToBottom) {
        val top = (geometry.viewportHeight * density).coerceIn(0f, size.height)
        drawRect(deckColor, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
    }

    // Secondary rail
    drawPath(
        path = paths.secondary,
        color = secondaryColor,
        style = if (strokeWidth > 0.dp) Stroke(width = strokeWidth.toPx()) else Fill,
    )

    // Primary rail (on top)
    drawPath(
        path = paths.primary,
        color = primaryColor,
        style = if (strokeWidth > 0.dp) Stroke(width = strokeWidth.toPx()) else Fill,
    )
}

// ── Snygg theme role adapter ──────────────────────────────────────────

/**
 * Resolved colors for the LCARS chassis from the active Snygg theme.
 *
 * Rails must be explicitly themed. A missing semantic color selects the
 * classic layout, rather than silently substituting a hardcoded code palette.
 */
data class LcarsChassisThemeColors(
    val primaryRail: Color,
    val secondaryRail: Color,
    val deck: Color,
) {
    /** Missing semantic roles select the classic renderer, not a code palette. */
    val isSpecified: Boolean get() = primaryRail.isSpecified && secondaryRail.isSpecified && deck.isSpecified
}

/**
 * Resolves LCARS chassis colors from the active Snygg theme.
 *
 * Queries the active Snygg theme for dedicated rail roles and a deck backing.
 * Only the deck may derive from the existing window background. Missing rail
 * roles keep older themes on the classic renderer.
 *
 * - `keyboard-chassis-rail-primary`    → explicit background
 * - `keyboard-chassis-rail-secondary`  → explicit background
 * - `keyboard-chassis`                 → `window` background fallback
 *
 * @param attributes Snygg query attributes (mode, shift state, etc.)
 */
@Composable
fun rememberLcarsChassisThemeColors(
    attributes: Map<String, Any> = emptyMap(),
): LcarsChassisThemeColors {
    // Primary rail: an absent role is left unspecified for classic fallback.
    val primaryRailStyle = rememberSnyggThemeQuery(
        elementName = "keyboard-chassis-rail-primary",
        attributes = attributes,
    )
    val primaryRailColor = primaryRailStyle.background(default = Color.Unspecified)

    // Secondary rail: query keyboard-chassis-rail-secondary
    val secondaryRailStyle = rememberSnyggThemeQuery(
        elementName = "keyboard-chassis-rail-secondary",
        attributes = attributes,
    )
    val secondaryRailColor = secondaryRailStyle.background(default = Color.Unspecified)

    // Deck: use existing keyboard-chassis element, fall back to window
    val deckStyle = rememberSnyggThemeQuery(
        elementName = "keyboard-chassis",
        attributes = attributes,
    )
    val deckColor = deckStyle.background(default = Color.Unspecified)
        .let { bg ->
            if (bg.isSpecified) bg
            else {
                val windowStyle = rememberSnyggThemeQuery(
                    elementName = "window",
                    attributes = attributes,
                )
                windowStyle.background(default = Color.Unspecified)
            }
        }

    return LcarsChassisThemeColors(
        primaryRail = primaryRailColor,
        secondaryRail = secondaryRailColor,
        deck = deckColor,
    )
}