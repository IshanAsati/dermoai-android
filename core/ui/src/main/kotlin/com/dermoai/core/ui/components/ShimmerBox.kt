package com.dermoai.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Neumorphic shimmer placeholder for loading skeletons.
 *
 * An inset-fill box (`surfaceContainerLow` → TintSweep in light, dark slate in
 * dark) with a light sweep traveling left → right — visible against the raised
 * card fill behind it. Composes with [NeuSurface] for whole-card skeletons.
 *
 * Purely decorative — no semantics, so TalkBack skips it.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val sweep = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    Box(
        modifier
            .clip(shape)
            .background(base)
            .drawBehind {
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, sweep, Color.Transparent),
                        start = Offset(w * (progress - 0.35f), 0f),
                        end = Offset(w * (progress + 0.35f), size.height),
                    ),
                )
            },
    )
}
