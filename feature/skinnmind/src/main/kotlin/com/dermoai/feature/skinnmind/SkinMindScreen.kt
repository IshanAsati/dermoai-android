package com.dermoai.feature.skinnmind

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MoodBad
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.theme.DermoColors
import kotlin.math.roundToInt

@Composable
fun SkinMindScreen(
    userId: String,
    viewModel: SkinMindViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(userId) { viewModel.refresh(userId) }
    val todayCompleted by viewModel.todayCompleted.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val submitted by viewModel.submitted.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Daily SkinMind", subtitle = if (todayCompleted) "Done for today 🔥" else "~30 seconds")
        MedicalDisclaimerBar()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (submitted || todayCompleted) {
                CelebrationCard(streak)
            } else {
                // Skin feel
                EmojiSliderRow(
                    title = "How does your skin feel today?",
                    value = formState.skinFeel,
                    max = 5,
                    icons = listOf(Icons.Outlined.SentimentVeryDissatisfied, Icons.Outlined.SentimentDissatisfied, Icons.Outlined.SentimentSatisfied, Icons.Outlined.Mood, Icons.Outlined.SentimentVerySatisfied),
                    onChange = { viewModel.updateSkinFeel(it) },
                )

                Spacer(Modifier.height(20.dp))

                // Itch/discomfort slider
                LabeledSliderRow("Itch / discomfort", formState.itchDiscomfort, 0, 10) { viewModel.updateItch(it) }

                Spacer(Modifier.height(20.dp))

                // Sleep quality
                LabeledSliderRow("Sleep quality", formState.sleepQuality, 1, 5) { viewModel.updateSleep(it) }

                Spacer(Modifier.height(20.dp))

                // Stress level
                LabeledSliderRow("Stress level", formState.stressLevel, 1, 5) { viewModel.updateStress(it) }

                Spacer(Modifier.height(20.dp))

                // New product toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Used a new product today?", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = formState.newProductUsed, onCheckedChange = { viewModel.updateNewProduct(it) })
                }
                if (formState.newProductUsed) {
                    OutlinedTextField(
                        value = formState.newProductNote,
                        onValueChange = { viewModel.updateNewProductNote(it) },
                        label = { Text("Which product?") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Notes / voice
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = formState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    IconButton(onClick = { viewModel.toggleRecording(context.filesDir) }) {
                        Icon(
                            if (recording) Icons.Outlined.SelfImprovement else Icons.Outlined.Mic,
                            if (recording) "Recording" else "Record",
                            tint = if (recording) DermoColors.SoftCoral else DermoColors.VioletAccent,
                        )
                    }
                }
                if (recording) {
                    Text("Recording… tap the mic again to stop", style = MaterialTheme.typography.labelSmall, color = DermoColors.CoralText)
                }

                Spacer(Modifier.height(24.dp))

                NeuButton(
                    onClick = { viewModel.submit(userId) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = DermoColors.VioletAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text("Save Check-in", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CelebrationCard(streak: Int) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = DermoColors.HealthGreen.copy(alpha = 0.08f),
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Favorite, null, Modifier.size(48.dp), tint = DermoColors.HealthGreen)
            Spacer(Modifier.height(12.dp))
            Text("Great job!", style = MaterialTheme.typography.headlineSmall)
            Text(pluralStringResource(R.plurals.skinnmind_streak_days, streak, streak), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = DermoColors.WarmAmber)
            Spacer(Modifier.height(8.dp))
            Text("Your daily check-in helps track patterns over time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val MoodLabels = listOf(
    "Very dissatisfied",
    "Dissatisfied",
    "Satisfied",
    "Neutral",
    "Very satisfied",
)

@Composable
private fun EmojiSliderRow(title: String, value: Int, max: Int, icons: List<ImageVector>, onChange: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icons.forEachIndexed { idx, icon ->
                val isSelected = idx + 1 <= value
                IconButton(
                    onClick = { onChange(idx + 1) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        icon,
                        MoodLabels.getOrElse(idx) { "Mood ${idx + 1} of $max" } +
                            if (isSelected) ", selected" else ", not selected",
                        Modifier.size(32.dp),
                        tint = if (isSelected) DermoColors.VioletAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSliderRow(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("$value / $max", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
