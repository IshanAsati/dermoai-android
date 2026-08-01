package com.dermoai.feature.analytics

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.ShimmerBox
import com.dermoai.core.ui.theme.DermoColors

@Composable
fun AnalyticsScreen(
    userId: String,
    viewModel: AnalyticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(userId) { viewModel.refresh(userId) }
    val itch by viewModel.itchOverTime.collectAsState()
    val stress by viewModel.stressOverTime.collectAsState()
    val scans by viewModel.scansByWeek.collectAsState()
    val concern by viewModel.concernDistribution.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Analytics", subtitle = "Your trends & patterns")
        MedicalDisclaimerBar()

        if (isLoading) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(3) {
                    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            ShimmerBox(Modifier.width(120.dp).height(16.dp), RoundedCornerShape(8.dp))
                            Spacer(Modifier.height(16.dp))
                            ShimmerBox(Modifier.fillMaxWidth().height(160.dp), RoundedCornerShape(16.dp))
                        }
                    }
                }
            }
        } else if (itch.isEmpty() && scans.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.BarChart, null, Modifier.size(64.dp), tint = DermoColors.TealAccent.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("No data yet", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Complete check-ins and scans to see your trends.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChartCard("Itch over time") {
                    LineChart(itch, DermoColors.SoftCoral, modifier = Modifier.fillMaxWidth().height(160.dp))
                }

                ChartCard("Stress over time") {
                    LineChart(stress, DermoColors.WarmAmber, modifier = Modifier.fillMaxWidth().height(160.dp))
                }

                if (scans.isNotEmpty()) {
                    ChartCard("Scans per week") {
                        BarChart(scans, modifier = Modifier.fillMaxWidth().height(160.dp))
                    }
                }

                if (concern.isNotEmpty()) {
                    ChartCard("Concern band distribution") {
                        DonutChart(concern, modifier = Modifier.fillMaxWidth().height(160.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Possible patterns — not medical advice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Raised neumorphic card hosting a chart — the canvas sits in an inset well. */
@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = DermoColors.TealAccent)
            Spacer(Modifier.height(12.dp))
            NeuSurface(
                modifier = Modifier.fillMaxWidth(),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LineChart(data: List<Pair<String, Float>>, lineColor: Color, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    Canvas(modifier = modifier, contentDescription = "Line chart with ${data.size} data points") {
        if (data.size < 2) return@Canvas
        val maxVal = data.maxOf { it.second }.coerceAtLeast(1f)
        val xs = data.indices.map { i -> (size.width - 40f) * i / (data.size - 1).coerceAtLeast(1) + 20f }
        val ys = data.map { size.height - 20f - (it.second / maxVal) * (size.height - 40f) }
        for (i in 0 until xs.size - 1) {
            drawLine(lineColor, Offset(xs[i], ys[i]), Offset(xs[i + 1], ys[i + 1]), strokeWidth = 3f, cap = StrokeCap.Round)
        }
        val label = data.lastOrNull()?.first?.takeLast(5) ?: ""
        val result = textMeasurer.measure(label, labelStyle)
        drawText(result.copy(result.layoutInput), topLeft = Offset(20f, size.height - 16f))
    }
}

@Composable
private fun BarChart(data: List<WeekBucket>, modifier: Modifier) {
    Canvas(modifier = modifier, contentDescription = "Bar chart with ${data.size} weeks") {
        if (data.isEmpty()) return@Canvas
        val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
        val barW = (size.width - 40f) / data.size.coerceAtLeast(1) * 0.6f
        val gap = (size.width - 40f) / data.size.coerceAtLeast(1) * 0.4f
        data.forEachIndexed { i, b ->
            val barH = (b.count.toFloat() / maxCount) * (size.height - 40f)
            drawRect(DermoColors.TealAccent, Offset(20f + i * (barW + gap), size.height - 20f - barH), androidx.compose.ui.geometry.Size(barW, barH))
        }
    }
}

@Composable
private fun DonutChart(data: Map<String, Int>, modifier: Modifier) {
    val colors = mapOf("CRITICAL" to DermoColors.SoftCoral, "HIGH" to DermoColors.WarmAmber, "MEDIUM" to DermoColors.WarmAmber, "LOW" to DermoColors.HealthGreen)
    val total = data.values.sum().toFloat().coerceAtLeast(1f)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(140.dp), contentDescription = "Donut chart showing ${data.size} bands") {
            var startAngle = -90f
            data.forEach { (band, count) ->
                val sweep = (count / total) * 360f
                drawArc(colors[band] ?: Color.Gray, startAngle, sweep, useCenter = false, style = Stroke(width = 28f), topLeft = Offset(4f, 4f), size = Size(size.width - 8f, size.height - 8f))
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            data.forEach { (band, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).padding(end = 4.dp).also { /* color dot */ }) {
                        Canvas(Modifier.fillMaxSize()) { drawCircle(colors[band] ?: Color.Gray) }
                    }
                    Text("$band: $count", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
