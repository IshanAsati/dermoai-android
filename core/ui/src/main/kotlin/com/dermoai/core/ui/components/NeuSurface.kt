package com.dermoai.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dermoai.core.ui.theme.LocalNeuShadowColors

/**
 * Raised vs. flat/inset surface state.
 * - [Raised]: embossed with dual shadows, fill = [raisedFill].
 * - [Inset]: carved well, fill = [insetFill] + inner shadows.
 */
enum class NeuSurfaceStyle { Raised, Inset }

/**
 * Neumorphic surface primitive. Mirrors Material [Surface] semantics
 * ([Role], enabled, onClick) so TalkBack behavior is unchanged.
 *
 * @param style [Raised] by default — swap to [Inset] for wells, progress
 *   tracks, and pressed states.
 * @param pressedForce force the pressed (inset) appearance even when the
 *   surface isn't clickable — used to style already-selected segments.
 */
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    style: NeuSurfaceStyle = NeuSurfaceStyle.Raised,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    pressedForce: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val neuShadows = LocalNeuShadowColors.current

    val raisedFill = color ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val insetFill = color ?: MaterialTheme.colorScheme.surfaceContainerLow

    val interactionSource = remember { MutableInteractionSource() }
    val pressed = if (onClick != null) {
        interactionSource.collectIsPressedAsState().value
    } else false
    val effectivePressed = style == NeuSurfaceStyle.Inset || pressed || pressedForce

    val fill by animateColorAsState(
        targetValue = if (effectivePressed) insetFill else raisedFill,
        animationSpec = tween(NEU_PRESS_MILLIS),
        label = "neuSurfaceFill",
    )

    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = role ?: Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .clip(shape)
            .background(fill)
            .then(
                if (effectivePressed) {
                    Modifier
                        .neumorphInset(
                            shape = shape,
                            shadowHi = neuShadows.shadowHi,
                            shadowLo = neuShadows.shadowLo,
                        )
                } else {
                    Modifier
                        .neumorphElevated(
                            shape = shape,
                            shadowHi = neuShadows.shadowHi,
                            shadowLo = neuShadows.shadowLo,
                        )
                }
            ),
        content = content,
    )
}
