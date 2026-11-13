/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.math.min

/**
 * Line art drawn inside a key's cap: the shift dial, the delete grille, the spacebar dot run and the
 * Enter corner. This is the artwork Snygg cannot express.
 *
 * A theme opts in per key by giving the `key-ornament` element a foreground for that key code. The
 * foreground is the accent (coral, cyan) and the background is the structure colour (recessed grey),
 * both read from the resolved Snygg style, so ornaments re-theme with the stylesheet and no hex lives
 * in Kotlin. Ornaments never change a key's bounds or touch behavior.
 */
internal enum class KeyOrnament(
    val code: Int,
    /** True when the ornament stands in for the key's own label or icon. */
    val replacesGlyph: Boolean,
    /** Where the key's own glyph moves so the art has room, when it keeps one. */
    val glyphAlignment: Alignment,
) {
    SHIFT_DIAL(KeyCode.SHIFT, replacesGlyph = true, glyphAlignment = Alignment.Center),
    DELETE_GRILLE(KeyCode.DELETE, replacesGlyph = false, glyphAlignment = BiasAlignment(0.55f, 0.55f)),
    SPACE_DOT_RUN(KeyCode.SPACE, replacesGlyph = true, glyphAlignment = Alignment.Center),
    ENTER_CORNER(KeyCode.ENTER, replacesGlyph = false, glyphAlignment = BiasAlignment(0.75f, -0.6f));

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun forCode(code: Int): KeyOrnament? = byCode[code]
    }
}

/** Colours and state an ornament draws with. */
internal data class KeyOrnamentPaint(
    val accent: Color,
    val structure: Color,
    val isPressed: Boolean,
    val shiftState: InputShiftState,
    /** Ctrl is latched: the shift dial shows a caret instead of its core. */
    val isCtrlLatched: Boolean = false,
    /** Alt is latched from the dial: a chevron pointing right, the way the finger slid. */
    val isAltLatched: Boolean = false,
    /** How far a delete swipe has reached, in units of [deleteSwipeLitDots]. Spacebar only. */
    val deleteSwipeLevel: Int = 0,
    /** The colour a delete swipe lights the spacebar dots in: the delete key's own accent. */
    val deleteAccent: Color = Color.Unspecified,
    /** The delete swipe removes text after the cursor (from Shift), so the gauge fills from the left. */
    val deleteSwipeForward: Boolean = false,
    /** Signed characters a spacebar cursor slide has travelled; 0 when no slide is in progress. */
    val cursorTravel: Int = 0,
)

/** Number of indicator dots in the spacebar run. */
internal const val SPACE_DOT_COUNT = 8

/**
 * Live readout of a delete swipe, shared from the touch controller to the spacebar ornament. The level
 * is how many characters (or words) the swipe currently has selected for deletion; 0 means no delete
 * swipe is in progress. Only the spacebar reads it, so only the spacebar recomposes as it changes.
 */
internal object DeleteSwipeReadout {
    var level by mutableIntStateOf(0)

    /** The swipe deletes forward (a drag right from Shift) rather than back from Delete. */
    var isForward by mutableStateOf(false)

    /**
     * Signed distance, in cursor steps, of the spacebar slide in progress: negative is left. The dot run
     * shows it as a travelling cyan light, the same gauge a delete swipe fills in coral.
     */
    var cursorTravel by mutableIntStateOf(0)
}

/** Which dot of the run is lit for a cursor slide of [travel] steps: the light walks with the cursor. */
internal fun cursorTravelDot(travel: Int): Int = Math.floorMod(SPACE_DOT_COUNT / 2 - 1 + travel, SPACE_DOT_COUNT)

/** How many spacebar dots a delete swipe of [level] lights, filling the run at [SPACE_DOT_COUNT]. */
internal fun deleteSwipeLitDots(level: Int): Int = level.coerceIn(0, SPACE_DOT_COUNT)

@Composable
internal fun BoxScope.KeyOrnamentArt(ornament: KeyOrnament, paint: KeyOrnamentPaint) {
    Canvas(modifier = Modifier.matchParentSize()) {
        when (ornament) {
            KeyOrnament.SHIFT_DIAL -> drawShiftDial(paint)
            KeyOrnament.DELETE_GRILLE -> drawDeleteGrille(paint)
            KeyOrnament.SPACE_DOT_RUN -> drawSpaceDotRun(paint)
            KeyOrnament.ENTER_CORNER -> drawEnterCorner(paint)
        }
    }
}

/** How much of the dial is lit for a shift state: off, one capital, caps lock. */
internal fun shiftDialFill(state: InputShiftState): Float = when (state) {
    InputShiftState.UNSHIFTED -> 0f
    InputShiftState.CAPS_LOCK -> 1f
    else -> 0.5f
}

private fun Color.structureOr(fallback: Color): Color = if (this.isSpecifiedColor()) this else fallback

private fun Color.isSpecifiedColor(): Boolean = this != Color.Unspecified

/** An empty ring when off, a half arc for one capital, a full ring and solid core for caps lock. */
private fun DrawScope.drawShiftDial(paint: KeyOrnamentPaint) {
    val fill = shiftDialFill(paint.shiftState)
    val radius = min(size.width, size.height) * 0.30f
    val stroke = radius * 0.16f
    val center = this.center
    val ringAlpha = if (fill == 0f) 0.5f else 0.9f
    drawCircle(paint.accent.copy(alpha = ringAlpha), radius, center, style = Stroke(stroke))
    if (fill > 0f) {
        drawArc(
            color = paint.accent,
            startAngle = -90f,
            sweepAngle = 360f * fill,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(stroke * 1.6f, cap = StrokeCap.Round),
        )
    }
    if (paint.isCtrlLatched) {
        // The dial has turned to Ctrl: a caret, the terminal's own spelling of Ctrl, replaces the core.
        val caret = Path().apply {
            moveTo(center.x - radius * 0.5f, center.y + radius * 0.25f)
            lineTo(center.x, center.y - radius * 0.35f)
            lineTo(center.x + radius * 0.5f, center.y + radius * 0.25f)
        }
        drawPath(caret, paint.accent, style = Stroke(stroke * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    if (paint.isAltLatched) {
        // The dial slid right to Alt: the same stroke turned to point the way the finger went.
        val chevron = Path().apply {
            moveTo(center.x - radius * 0.25f, center.y - radius * 0.5f)
            lineTo(center.x + radius * 0.35f, center.y)
            lineTo(center.x - radius * 0.25f, center.y + radius * 0.5f)
        }
        drawPath(chevron, paint.accent, style = Stroke(stroke * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    val coreAlpha = when {
        paint.isPressed -> 1f
        fill == 0f -> 0.35f
        fill < 1f -> 0.75f
        else -> 1f
    }
    drawCircle(paint.accent.copy(alpha = coreAlpha), radius * 0.6f, center)
}

/** A coral rule over a recessed grille, in the band where letter keys carry their plates. */
private fun DrawScope.drawDeleteGrille(paint: KeyOrnamentPaint) {
    val inset = size.width * 0.08f
    val ruleHeight = 2.dp.toPx()
    val top = size.height * 0.08f
    drawRoundRect(
        color = paint.accent.copy(alpha = if (paint.isPressed) 1f else 0.75f),
        topLeft = Offset(inset, top),
        size = Size(size.width - inset * 2f, ruleHeight),
        cornerRadius = CornerRadius(ruleHeight / 2f),
    )
    val grille = Rect(
        left = inset,
        top = top + ruleHeight * 2.5f,
        right = size.width - inset,
        bottom = size.height * 0.40f,
    )
    if (grille.height <= 0f) return
    drawRoundRect(
        color = paint.structure.structureOr(Color.Black.copy(alpha = 0.3f)),
        topLeft = grille.topLeft,
        size = grille.size,
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
    val cols = 8
    val rows = 2
    val cell = min(grille.width / (cols * 2f), grille.height / (rows * 2.2f))
    val stepX = grille.width / cols
    val stepY = grille.height / rows
    val cellColor = if (paint.isPressed) paint.accent else paint.accent.copy(alpha = 0.22f)
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val cx = grille.left + stepX * (c + 0.5f)
            val cy = grille.top + stepY * (r + 0.5f)
            drawRect(cellColor, Offset(cx - cell / 2f, cy - cell / 2f), Size(cell, cell))
        }
    }
}

/**
 * Machinery line art with a run of eight indicator dots. While a delete swipe is in progress the run
 * becomes a deletion gauge: dots light in the delete key's accent from the right end toward the left,
 * following the finger, one per character selected. Past a full run the bar and slab light as well.
 */
private fun DrawScope.drawSpaceDotRun(paint: KeyOrnamentPaint) {
    val w = size.width
    val h = size.height
    val idleStructure = paint.structure.structureOr(paint.accent.copy(alpha = 0.3f))
    val deleteAccent = paint.deleteAccent.structureOr(paint.accent)
    val litDots = deleteSwipeLitDots(paint.deleteSwipeLevel)
    val isSwiping = litDots > 0
    val isOverflow = paint.deleteSwipeLevel > SPACE_DOT_COUNT
    val structure = if (isOverflow) deleteAccent.copy(alpha = 0.55f) else idleStructure
    val line = 1.dp.toPx()
    // Upper bar.
    drawRect(structure, Offset(w * 0.04f, h * 0.20f), Size(w * 0.62f, h * 0.14f))
    // Lower slab, open on the left.
    val slab = Path().apply {
        moveTo(w * 0.04f, h * 0.50f)
        lineTo(w * 0.62f, h * 0.50f)
        quadraticTo(w * 0.66f, h * 0.50f, w * 0.66f, h * 0.58f)
        lineTo(w * 0.66f, h * 0.78f)
        quadraticTo(w * 0.66f, h * 0.86f, w * 0.62f, h * 0.86f)
        lineTo(w * 0.04f, h * 0.86f)
    }
    drawPath(slab, structure, style = Stroke(line))
    // Posts on the right.
    val postW = w * 0.035f
    drawRect(idleStructure, Offset(w * 0.86f, h * 0.16f), Size(postW, h * 0.68f))
    drawRect(idleStructure, Offset(w * 0.86f + postW * 1.6f, h * 0.16f), Size(postW, h * 0.68f))
    // Dot run inside the slab.
    val r = min(h * 0.08f, w * 0.012f)
    val idleAlpha = if (paint.isPressed) 1f else 0.6f
    for (i in 0 until SPACE_DOT_COUNT) {
        val center = Offset(w * 0.10f + i * r * 3.2f, h * 0.68f)
        if (!isSwiping && paint.cursorTravel != 0) {
            if (i == cursorTravelDot(paint.cursorTravel)) {
                drawCircle(paint.accent.copy(alpha = 0.3f), r * 2.1f, center)
                drawCircle(paint.accent, r * 1.15f, center)
            } else {
                drawCircle(paint.accent.copy(alpha = 0.22f), r, center)
            }
            continue
        }
        if (!isSwiping) {
            drawCircle(paint.accent.copy(alpha = idleAlpha), r, center)
            continue
        }
        // A delete swipe lights from the end nearest the text it eats: the right end for Delete (text
        // before the cursor), the left end for Shift's forward delete (text after it).
        val isLit = if (paint.deleteSwipeForward) i < litDots else i >= SPACE_DOT_COUNT - litDots
        if (isLit) {
            drawCircle(deleteAccent.copy(alpha = 0.28f), r * 2.1f, center)
            drawCircle(deleteAccent, r * 1.15f, center)
        } else {
            drawCircle(deleteAccent.copy(alpha = 0.18f), r, center)
        }
    }
}

/** An inner line with one oversized rounded corner, echoing the reference's Enter key. */
private fun DrawScope.drawEnterCorner(paint: KeyOrnamentPaint) {
    val w = size.width
    val h = size.height
    val left = w * 0.16f
    val top = h * 0.46f
    val right = w * 0.90f
    val radius = min(w, h) * 0.22f
    val path = Path().apply {
        moveTo(left, h * 0.90f)
        lineTo(left, top + radius)
        quadraticTo(left, top, left + radius, top)
        lineTo(right, top)
    }
    val color = if (paint.isPressed) paint.accent else paint.accent.copy(alpha = 0.7f)
    drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}
