package com.dermoai.feature.wellness

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BreathPhase { INHALE, HOLD_IN, EXHALE, HOLD_OUT }

data class BreathState(
    val phase: BreathPhase = BreathPhase.INHALE,
    val progress: Float = 0f,
    val totalCycles: Int = 0,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
)

@HiltViewModel
class BreathingViewModel @Inject constructor() : ViewModel() {
    companion object {
        const val MAX_CYCLES = 5
    }

    private val _state = MutableStateFlow(BreathState())
    val state: StateFlow<BreathState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (_state.value.isRunning) return
        job = viewModelScope.launch {
            _state.value = _state.value.copy(isRunning = true, isComplete = false)
            for (cycle in 1..MAX_CYCLES) {
                animatePhase(BreathPhase.INHALE, 4000L)
                animatePhase(BreathPhase.HOLD_IN, 4000L)
                animatePhase(BreathPhase.EXHALE, 4000L)
                animatePhase(BreathPhase.HOLD_OUT, 4000L)
                _state.value = _state.value.copy(totalCycles = cycle)
            }
            _state.value = _state.value.copy(
                isRunning = false,
                isComplete = true,
                phase = BreathPhase.INHALE,
                progress = 0f,
            )
        }
    }

    fun stop() {
        job?.cancel(); job = null
        _state.value = BreathState()
    }

    private suspend fun animatePhase(phase: BreathPhase, durationMs: Long) {
        val steps = 60
        val stepMs = durationMs / steps
        for (i in 0..steps) {
            _state.value = _state.value.copy(phase = phase, progress = i.toFloat() / steps)
            delay(stepMs)
        }
    }
}

@Composable
fun BreathingScreen(
    onBack: () -> Unit,
    viewModel: BreathingViewModel = viewModel(),
) {
    val s by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { IconButton(onClick = { viewModel.stop(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
        Spacer(Modifier.height(20.dp))
        Text("Box Breathing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Inhale 4s · Hold 4s · Exhale 4s · Hold 4s", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))

        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            val animProgress by animateFloatAsState(targetValue = s.progress, animationSpec = tween(durationMillis = 100))
            NeuSurface(
                modifier = Modifier.fillMaxSize(),
                style = NeuSurfaceStyle.Inset,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    drawCircle(color = DermoColors.Line.copy(alpha = 0.3f), style = Stroke(6.dp.toPx()))
                    drawArc(DermoColors.Teal, -90f, 360f * animProgress, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val phaseLabel = when (s.phase) {
                    BreathPhase.INHALE -> "Breathe In"
                    BreathPhase.HOLD_IN -> "Hold"
                    BreathPhase.EXHALE -> "Breathe Out"
                    BreathPhase.HOLD_OUT -> "Hold"
                }
                val cycleShown = if (s.isComplete) s.totalCycles else s.totalCycles + 1
                Text(
                    phaseLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DermoColors.Teal,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "$phaseLabel. Cycle ${cycleShown} of ${BreathingViewModel.MAX_CYCLES}"
                    },
                )
                Text("Cycle $cycleShown of ${BreathingViewModel.MAX_CYCLES}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.isComplete) {
                    Spacer(Modifier.height(8.dp))
                    Text("Session complete — great job!", style = MaterialTheme.typography.labelMedium, color = DermoColors.SageText)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        NeuButton(
            onClick = { if (s.isRunning) viewModel.stop() else viewModel.start() },
            containerColor = DermoColors.Teal,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(if (s.isRunning) "Stop" else if (s.isComplete) "Start again" else "Start", style = MaterialTheme.typography.titleMedium)
        }
    }
}
