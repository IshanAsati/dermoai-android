package com.dermoai.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dermoai.core.ui.theme.LocalNeuShadowColors

/**
 * Neumorphic filled button. Raised by default; presses down to an inset well
 * over [NEU_PRESS_MILLIS]. Preserves click semantics and enabled state.
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val neuShadows = LocalNeuShadowColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value

    val fill by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(NEU_PRESS_MILLIS),
        label = "neuButtonFill",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (pressed) {
                    Modifier.neumorphInset(
                        shape = shape,
                        shadowHi = neuShadows.shadowHi,
                        shadowLo = neuShadows.shadowLo,
                    )
                } else {
                    Modifier.neumorphElevated(
                        shape = shape,
                        shadowHi = neuShadows.shadowHi,
                        shadowLo = neuShadows.shadowLo,
                    )
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * Neumorphic outlined-style button: raised base fill with default-onSurface
 * content — the "ghost" counterpart to [NeuButton].
 */
@Composable
fun OutlinedNeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    NeuButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Neumorphic icon button. Raised circle that presses to an inset well.
 */
@Composable
fun NeuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector,
    contentDescription: String?,
) {
    val neuShadows = LocalNeuShadowColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value

    val fill by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(NEU_PRESS_MILLIS),
        label = "neuIconButtonFill",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (pressed) {
                    Modifier.neumorphInset(
                        shape = CircleShape,
                        shadowHi = neuShadows.shadowHi,
                        shadowLo = neuShadows.shadowLo,
                    )
                } else {
                    Modifier.neumorphElevated(
                        shape = CircleShape,
                        shadowHi = neuShadows.shadowHi,
                        shadowLo = neuShadows.shadowLo,
                    )
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
        )
    }
}
