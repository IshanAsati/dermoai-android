package com.dermoai.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.database.entity.TreatmentRoutineEntity
import com.dermoai.core.domain.insights.InsightType
import com.dermoai.core.domain.insights.SkinInsight
import com.dermoai.core.environment.EnvironmentAlert
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.ShimmerBox
import com.dermoai.core.ui.theme.DermoColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    displayName: String,
    userId: String,
    onNavigateToScan: () -> Unit,
    onNavigateToSkinMind: () -> Unit,
    onScanClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.refresh(userId) }

    LaunchedEffect(userId) {
        viewModel.refresh(userId)
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    val skinMindCompleted by viewModel.skinMindCompleted.collectAsState()
    val skinMindStreak by viewModel.skinMindStreak.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val currentAlert by viewModel.currentAlert.collectAsState()
    val envLabel by viewModel.cachedEnvLabel.collectAsState()
    val treatmentRoutines by viewModel.treatmentRoutines.collectAsState()
    val latestScan by viewModel.latestScan.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = resolveGreeting(displayName),
            trailing = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    NeuSurface(
                        Modifier.size(44.dp),
                        style = NeuSurfaceStyle.Inset,
                        shape = CircleShape,
                        color = DermoColors.TealLight,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                displayName.firstOrNull()?.uppercase() ?: "D",
                                style = MaterialTheme.typography.titleMedium,
                                color = DermoColors.TealText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            },
        )
        MedicalDisclaimerBar()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScanHeroCard(onClick = onNavigateToScan)
            if (currentAlert != null) EnvironmentAlertCard(alert = currentAlert!!, timeLabel = envLabel)
            if (isLoading) {
                NeuSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(Modifier.size(56.dp), RoundedCornerShape(16.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            ShimmerBox(Modifier.fillMaxWidth(0.6f).height(14.dp), RoundedCornerShape(7.dp))
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp), RoundedCornerShape(6.dp))
                        }
                    }
                }
            } else {
                LatestScanCard(
                    scan = latestScan,
                    onClick = { latestScan?.let { onScanClick(it.id) } },
                )
            }
            SkinMindChip(skinMindCompleted, skinMindStreak, onClick = onNavigateToSkinMind)
            if (insights.isNotEmpty()) {
                insights.take(2).forEach { insight -> InsightCard(insight) }
            }
            TreatmentCard(routines = treatmentRoutines)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun resolveGreeting(displayName: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val name = displayName.ifBlank { "DermoAI" }
    return when (hour) {
        in 0..11 -> stringResource(R.string.home_greeting_morning, name)
        in 12..16 -> stringResource(R.string.home_greeting_afternoon, name)
        in 17..20 -> stringResource(R.string.home_greeting_evening, name)
        else -> stringResource(R.string.home_greeting_default, name)
    }
}

/** Hero CTA: pale-pine raised card with an inset pine icon well — the focal point. */
@Composable
private fun ScanHeroCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    NeuSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = DermoColors.TealLight,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuSurface(
                Modifier.size(56.dp),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(20.dp),
                color = DermoColors.Teal.copy(alpha = 0.18f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(28.dp), tint = DermoColors.TealText)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_cta_scan), style = MaterialTheme.typography.titleLarge, color = DermoColors.TealText, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_cta_scan_subtitle), style = MaterialTheme.typography.bodyMedium, color = DermoColors.Slate, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = DermoColors.TealText)
        }
    }
}

@Composable
private fun LatestScanCard(scan: com.dermoai.core.database.entity.SkinScanEntity?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeuSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        if (scan == null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                NeuSurface(
                    Modifier.size(48.dp),
                    style = NeuSurfaceStyle.Inset,
                    shape = RoundedCornerShape(16.dp),
                    color = DermoColors.TealLight,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhotoCamera, null, tint = DermoColors.TealText)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_latest_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.home_no_scans_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val bmp = remember(scan.imagePath) {
                runCatching { BitmapFactory.decodeFile(scan.imagePath) }.getOrNull()
            }
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.home_latest_thumb_desc),
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    NeuSurface(
                        Modifier.size(56.dp),
                        style = NeuSurfaceStyle.Inset,
                        shape = RoundedCornerShape(16.dp),
                        color = DermoColors.TealLight,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PhotoCamera, null, tint = DermoColors.TealText)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_latest_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }
                    Text(dateFormat.format(Date(scan.capturedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.home_latest_view), style = MaterialTheme.typography.labelMedium, color = DermoColors.TealText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SkinMindChip(completed: Boolean, streak: Int, onClick: () -> Unit) {
    val accent = if (completed) DermoColors.HealthGreen else DermoColors.TealAccent
    val accentText = if (completed) DermoColors.SageText else DermoColors.TealText
    val icon = if (completed) Icons.Outlined.Favorite else Icons.Outlined.Psychology

    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuSurface(
                Modifier.size(44.dp),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.14f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentText)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (completed) "Done for today" else stringResource(R.string.home_skinnmind_today), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (completed) pluralStringResource(R.plurals.home_streak_days, streak, streak)
                    else stringResource(R.string.home_skinnmind_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (streak > 0) {
                NeuSurface(
                    style = NeuSurfaceStyle.Inset,
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.14f),
                ) {
                    Text(
                        "🔥 $streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EnvironmentAlertCard(alert: EnvironmentAlert, timeLabel: String) {
    val (accent, icon) = when (alert) {
        EnvironmentAlert.RED_COMBINED -> DermoColors.SoftCoral to Icons.Outlined.Warning
        EnvironmentAlert.HIGH_UV -> DermoColors.WarmAmber to Icons.Outlined.Warning
        EnvironmentAlert.EXTREME_HEAT -> DermoColors.SoftCoral to Icons.Outlined.Warning
        EnvironmentAlert.HIGH_HUMIDITY -> DermoColors.TealAccent to Icons.Outlined.Warning
    }
    val textColor = DermoColors.textOnLight(accent)
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = accent.copy(alpha = 0.08f),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuSurface(
                Modifier.size(44.dp),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.14f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = textColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.label(), style = MaterialTheme.typography.labelLarge, color = textColor, fontWeight = FontWeight.SemiBold)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (timeLabel.isNotEmpty()) {
                    Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: SkinInsight) {
    val pair = when (insight.type) {
        InsightType.SLEEP_CORRELATION -> DermoColors.TealAccent to Icons.Outlined.Bedtime
        InsightType.STRESS_CORRELATION -> DermoColors.SoftCoral to Icons.Outlined.Warning
        InsightType.SYMPTOM_IMPROVING -> DermoColors.HealthGreen to Icons.Outlined.Favorite
        InsightType.ADHERENCE_IMPROVEMENT -> DermoColors.TealAccent to Icons.Outlined.Lightbulb
        else -> DermoColors.WarmAmber to Icons.Outlined.Lightbulb
    }
    val color = pair.first; val icon = pair.second
    val textColor = DermoColors.textOnLight(color)
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = color.copy(alpha = 0.06f)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
            NeuSurface(
                Modifier.size(40.dp),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(14.dp),
                color = color.copy(alpha = 0.14f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = textColor)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    insight.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(insight.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Possible pattern — not medical advice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TreatmentCard(routines: List<TreatmentRoutineEntity>, modifier: Modifier = Modifier) {
    NeuSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.home_treatment_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (routines.isEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NeuSurface(
                        Modifier.size(44.dp),
                        style = NeuSurfaceStyle.Inset,
                        shape = RoundedCornerShape(14.dp),
                        color = DermoColors.WarmAmber.copy(alpha = 0.14f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Spa, null, tint = DermoColors.AmberText)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_no_treatment), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.home_no_treatment_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            } else {
                routines.forEach { routine ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        NeuSurface(
                            Modifier.size(36.dp),
                            style = NeuSurfaceStyle.Inset,
                            shape = RoundedCornerShape(12.dp),
                            color = DermoColors.Teal.copy(alpha = 0.14f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Spa, null, Modifier.size(18.dp), tint = DermoColors.TealText)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(routine.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
