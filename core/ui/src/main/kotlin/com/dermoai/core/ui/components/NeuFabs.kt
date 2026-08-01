package com.dermoai.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * Neumorphic floating action button. Raised circle that presses to an inset
 * well. [emphasized] fills the button with the accent tone.
 */
@Composable
fun NeuFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 56.dp,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector,
    contentDescription: String?,
    emphasized: Boolean = false,
) {
    val neuShadows = LocalNeuShadowColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value

    val fill = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        containerColor
    }
    val iconTint = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        contentColor
    }

    Box(
        modifier = modifier
            .size(size)
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
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
        )
    }
}
