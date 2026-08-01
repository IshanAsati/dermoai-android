package com.dermoai.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.core.ui.theme.LocalNeuShadowColors

/**
 * Neumorphic screen header. Mid-tone base with a carved bottom edge (inner
 * shadow) and the teal accent bar. Signature unchanged.
 */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val bgColor = MaterialTheme.colorScheme.surface
    val neuShadows = LocalNeuShadowColors.current
    val headerShape: Shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(headerShape)
            .background(bgColor)
            .neumorphInset(
                shape = headerShape,
                shadowHi = neuShadows.shadowHi,
                shadowLo = neuShadows.shadowLo,
                depth = 2.dp,
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        trailing?.invoke(this)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DermoColors.Teal),
        )
    }
}
