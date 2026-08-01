package com.dermoai.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Standard neumorphic press animation duration. */
internal const val NEU_PRESS_MILLIS = 150

/**
 * Neumorphic raised surface. Draws a soft dual-shadow edge: a dark band offset
 * toward bottom-right and a light band offset toward top-left, both clipped to
 * [shape]. Alphas/widths are deliberately subtle — full-strength white strokes
 * read as a harsh halo on warm cream surfaces.
 *
 * Note: Compose's [Modifier.shadow] always casts shadows in one direction,
 * so two chained calls can't produce the opposing neumorphic light pair.
 * This draws the two colored edge bands explicitly instead.
 */
fun Modifier.neumorphElevated(
    shape: Shape,
    shadowHi: Color,
    shadowLo: Color,
    elevation: Dp = 5.dp,
): Modifier = drawBehind {
    val e = elevation.toPx()
    // `this` is the DrawScope, which itself implements Density.
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = outline.toPath()
    val strokeWidth = e * 1.1f

    // Light edge along the top + left interior (light source: top-left).
    // Translating the path down-right sweeps its top/left edges into the shape.
    clipPath(path) {
        translate(left = e, top = e) {
            drawPath(path, color = shadowHi.copy(alpha = 0.35f), style = Stroke(strokeWidth))
        }
    }
    // Dark edge along the bottom + right interior.
    clipPath(path) {
        translate(left = -e, top = -e) {
            drawPath(path, color = shadowLo.copy(alpha = 0.30f), style = Stroke(strokeWidth))
        }
    }
}

/**
 * Neumorphic inset (pressed / well). Draws a dark inner edge along the top-left
 * and a light inner edge along the bottom-right, both inside [shape].
 * Alphas are capped so wells read as carved, not outlined in white.
 */
fun Modifier.neumorphInset(
    shape: Shape,
    shadowHi: Color,
    shadowLo: Color,
    depth: Dp = 1.5.dp,
): Modifier = drawBehind {
    // `this` is the DrawScope, which itself implements Density.
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = outline.toPath()
    val bounds = outline.bounds
    val d = depth.toPx()
    val strokeWidth = d * 2f
    val dark = shadowLo.copy(alpha = 0.55f)
    val light = shadowHi.copy(alpha = 0.55f)

    // Dark sliver along the top + left inner edges.
    clipPath(path) {
        clipRect(bounds.left, bounds.top, bounds.right, bounds.center.y) {
            translate(left = d, top = d) {
                drawPath(path, color = dark, style = Stroke(strokeWidth))
            }
        }
        clipRect(bounds.left, bounds.top, bounds.center.x, bounds.bottom) {
            translate(left = d, top = d) {
                drawPath(path, color = dark, style = Stroke(strokeWidth))
            }
        }
    }
    // Light sliver along the bottom + right inner edges.
    clipPath(path) {
        clipRect(bounds.left, bounds.center.y, bounds.right, bounds.bottom) {
            translate(left = -d, top = -d) {
                drawPath(path, color = light, style = Stroke(strokeWidth))
            }
        }
        clipRect(bounds.center.x, bounds.top, bounds.right, bounds.bottom) {
            translate(left = -d, top = -d) {
                drawPath(path, color = light, style = Stroke(strokeWidth))
            }
        }
    }
}

/** Converts a [Shape]'s [Outline] into a [Path]. */
internal fun Outline.toPath(): Path {
    val path = Path()
    when (this) {
        is Outline.Rounded -> path.addRoundRect(roundRect)
        is Outline.Generic -> path.addPath(this.path)
        is Outline.Rectangle -> path.addRect(rect)
    }
    return path
}
