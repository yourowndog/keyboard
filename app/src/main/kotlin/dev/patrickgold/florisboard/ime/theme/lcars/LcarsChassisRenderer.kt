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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Converts the pure [LcarsChassisGeometry] to Compose [Path] objects.
 *
 * This is the rendering bridge between the analytic model and the
 * Canvas / drawBehind drawing surface.
 */
class LcarsChassisPaths(
    val primary: Path,
    val secondary: Path,
    val deck: Path,
    val viewportClip: Path,
)

/** Degrees-to-radians conversion factor. */
private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

/**
 * Build [LcarsChassisPaths] from a [LcarsChassisGeometry] model.
 *
 * @param geometry  the pure geometry model in logical dp units.
 * @param density   the Compose density for pixel snapping of thin lines.
 */
fun LcarsChassisPaths(
    geometry: LcarsChassisGeometry,
    density: Float,
): LcarsChassisPaths {
    val px = density // scale factor: one dp = density physical pixels

    fun buildPath(descriptor: LcarsChassisGeometry.PathDescriptor): Path {
        return Path().apply {
            if (descriptor.segments.isEmpty()) return@apply

            // Move to the first segment's start point
            val first = descriptor.segments.first()
            when (first) {
                is LcarsChassisGeometry.Segment.Line -> {
                    moveTo(first.x1 * px, first.y1 * px)
                }
                is LcarsChassisGeometry.Segment.Arc -> {
                    val startAngleRad = first.startAngle * DEG_TO_RAD
                    val sx = first.cx + first.radius * cos(startAngleRad)
                    val sy = first.cy + first.radius * sin(startAngleRad)
                    moveTo(sx * px, sy * px)
                }
            }

            // Append remaining segments
            for (seg in descriptor.segments) {
                when (seg) {
                    is LcarsChassisGeometry.Segment.Line -> {
                        lineTo(seg.x2 * px, seg.y2 * px)
                    }
                    is LcarsChassisGeometry.Segment.Arc -> {
                        arcTo(
                            rect = Rect(
                                left = (seg.cx - seg.radius) * px,
                                top = (seg.cy - seg.radius) * px,
                                right = (seg.cx + seg.radius) * px,
                                bottom = (seg.cy + seg.radius) * px,
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
        primary = buildPath(geometry.primaryRail()),
        secondary = buildPath(geometry.secondaryRail()),
        deck = buildPath(geometry.deckPath()),
        viewportClip = buildPath(geometry.viewportClip()),
    )
}

/**
 * Draws the LCARS chassis using semantic role colors.
 *
 * @param geometry       the pure geometry model.
 * @param primaryColor   color for the primary (outer) rail.
 * @param secondaryColor color for the secondary (inner) rail.
 * @param deckColor      color for the keyboard deck.
 * @param strokeWidth    optional stroke width (dp); 0 = fill only.
 */
fun DrawScope.drawLcarsChassis(
    geometry: LcarsChassisGeometry,
    primaryColor: Color,
    secondaryColor: Color,
    deckColor: Color,
    strokeWidth: Dp = 0.dp,
) {
    val density = this.density
    val paths = LcarsChassisPaths(geometry, density)

    // Deck background first (largest, behind everything)
    drawPath(
        path = paths.deck,
        color = deckColor,
        style = if (strokeWidth > 0.dp) Stroke(width = strokeWidth.toPx()) else Fill,
    )

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

/**
 * Snap a thin dimension to an integer number of physical pixels.
 *
 * For channel gaps and keylines thinner than ~1.5 dp, this ensures
 * they render at an integral pixel width rather than a fractional
 * anti-aliased blend.
 */
fun Float.snapToPixel(density: Float): Float {
    val px = this * density
    val snapped = if (px < 1.5f) px.roundToInt().toFloat() else px
    return snapped / density
}