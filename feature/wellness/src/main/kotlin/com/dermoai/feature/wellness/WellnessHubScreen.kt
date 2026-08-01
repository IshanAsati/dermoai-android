package com.dermoai.feature.wellness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

@Composable
fun WellnessHubScreen(
    userId: String = "",
    onBreathing: () -> Unit = {},
    onJournal: () -> Unit = {},
    onStreaks: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Wellness", subtitle = "Mind & body care")
        MedicalDisclaimerBar()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                Triple(Icons.Outlined.SelfImprovement, "Box breathing", "1-minute calming exercise") to onBreathing,
                Triple(Icons.Outlined.EditNote, "Confidence journal", "Track how you feel") to onJournal,
                Triple(Icons.Outlined.FavoriteBorder, "Streaks", "Track your habits") to onStreaks,
            ).forEach { (info, onClick) ->
                val (icon, title, subtitle) = info
                NeuSurface(
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "$title: $subtitle" },
                    shape = RoundedCornerShape(24.dp),
                    onClick = onClick,
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        NeuSurface(
                            Modifier.size(48.dp),
                            style = NeuSurfaceStyle.Inset,
                            shape = RoundedCornerShape(16.dp),
                            color = DermoColors.Teal.copy(alpha = 0.1f),
                        ) {
                            Icon(icon, null, Modifier.padding(12.dp), tint = DermoColors.Teal)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
