package com.dermoai.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dermoai.core.ui.components.DermoGlassCard

/**
 * Phase 1 home shell. Full home dashboard arrives in Phase 4.
 */
@Composable
fun HomePlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DermoGlassCard {
            Text(
                text = "You're signed in",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Authentication is live. Full Home dashboard arrives in Phase 4; navigation tabs in Phase 3.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        DermoGlassCard {
            Text(
                text = "Model Repository",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "ConvNeXt-Base 12-class PyTorch weights from dermoai-final are staged for TFLite conversion in Phase 6.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}