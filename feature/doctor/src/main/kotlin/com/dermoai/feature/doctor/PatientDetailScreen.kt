package com.dermoai.feature.doctor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dermoai.core.domain.model.PatientAdherence
import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.ui.components.DermoGlassCard
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors
import kotlin.math.roundToInt

/**
 * One patient's record, as their doctor sees it.
 *
 * The trend card shows [PatientTrend.explanation] verbatim rather than a
 * re-worded summary. That string exists because the number beside it comes from
 * a classifier run on phone photos under uncontrolled lighting, and a bare
 * "Worsening" next to a patient's name gets read as a clinical finding it is
 * not entitled to be. Paraphrasing it in the UI would put the hedging one edit
 * away from being lost.
 *
 * Opening this screen writes an audit row — see [PatientDetailViewModel].
 *
 * @param doctorUserId the signed-in doctor's `AuthUser.id`.
 * @param patientUserId the patient's `AuthUser.id`.
 * @param patientDisplayName shown in the header so it renders before the link
 *   row loads. Display only — never treated as identity.
 * @param onBack invoked by the back action and after a successful revoke.
 */
@Composable
fun PatientDetailScreen(
    doctorUserId: String,
    patientUserId: String,
    patientDisplayName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(doctorUserId, patientUserId) {
        viewModel.load(doctorUserId, patientUserId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRevokeDialog by remember { mutableStateOf(false) }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(stringResource(R.string.doctor_revoke_title)) },
            text = { Text(stringResource(R.string.doctor_revoke_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeDialog = false
                    viewModel.revokeAccess()
                }) { Text(stringResource(R.string.doctor_revoke_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(R.string.doctor_cancel))
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = (state as? PatientDetailUiState.Ready)?.link?.patientDisplayName
                ?: patientDisplayName,
            subtitle = stringResource(R.string.doctor_patient_subtitle),
        )
        MedicalDisclaimerBar()

        when (val s = state) {
            is PatientDetailUiState.Loading -> PatientRowSkeletons(Modifier.weight(1f), count = 3)

            is PatientDetailUiState.NotLinked -> DoctorMessage(
                icon = Icons.Outlined.LinkOff,
                title = stringResource(R.string.doctor_not_linked_title),
                body = stringResource(R.string.doctor_not_linked_body),
                modifier = Modifier.weight(1f),
                action = { BackButton(onBack) },
            )

            is PatientDetailUiState.Revoked -> DoctorMessage(
                icon = Icons.Outlined.Block,
                title = stringResource(R.string.doctor_revoked_done_title),
                body = stringResource(R.string.doctor_revoked_done_body),
                modifier = Modifier.weight(1f),
                tint = DermoColors.CoralText,
                action = { BackButton(onBack) },
            )

            is PatientDetailUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item { AdherenceCard(s.adherence) }
                item { TrendCard(s.trend) }
                item { SectionTitle(stringResource(R.string.doctor_patient_scans_title)) }

                if (s.scans.isEmpty()) {
                    item {
                        NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.EventBusy, null, tint = DermoColors.Slate)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.doctor_patient_no_scans),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DermoColors.Slate,
                                )
                            }
                        }
                    }
                } else {
                    items(s.scans, key = { it.scanId }) { scan -> PatientScanCard(scan) }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    NeuButton(
                        onClick = { showRevokeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = DermoColors.Coral,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Outlined.Block, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.doctor_revoke_title))
                    }
                }
                item { BackButton(onBack, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    NeuButton(onClick = onBack, modifier = modifier) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.doctor_back))
    }
}

/** Scanning consistency, with the raw counts beside the band. */
@Composable
private fun AdherenceCard(adherence: PatientAdherence) {
    val dateFormat = rememberDateFormat()
    DermoGlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.doctor_patient_adherence_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        AdherenceChip(adherence)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                R.string.doctor_patient_adherence_body,
                adherence.scansLast14Days,
                PatientAdherence.WINDOW_DAYS,
                adherence.expectedCadenceDays,
                adherence.streakDays,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        adherence.lastScanAt?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.doctor_last_scan, formatDate(dateFormat, it)),
                style = MaterialTheme.typography.labelMedium,
                color = DermoColors.Slate,
            )
        }
    }
}

/**
 * Direction of travel, with the use case's own words.
 *
 * [PatientTrend.explanation] already ends with [PatientTrend.DISCLAIMER], so
 * the disclaimer is appended separately only when it is somehow absent — the
 * guarantee is that the sentence is always on screen, not that it is on screen
 * twice.
 */
@Composable
private fun TrendCard(trend: PatientTrend) {
    DermoGlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.doctor_patient_trend_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        TrendChip(trend)
        Spacer(Modifier.height(10.dp))
        Text(
            text = trend.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!trend.explanation.contains(PatientTrend.DISCLAIMER)) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = PatientTrend.DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.Slate,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.doctor_patient_trend_basis, trend.basisScans),
            style = MaterialTheme.typography.labelMedium,
            color = DermoColors.Slate,
        )
    }
}

/** Mirrors the Timeline card layout so a scan looks the same to both parties. */
@Composable
private fun PatientScanCard(scan: PatientScanRow) {
    val dateFormat = rememberDateTimeFormat()
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(scan.imagePath) {
                runCatching { BitmapFactory.decodeFile(scan.imagePath) }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    stringResource(R.string.doctor_scan_photo_desc),
                    Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                NeuSurface(
                    Modifier.size(72.dp),
                    style = NeuSurfaceStyle.Inset,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Image,
                            stringResource(R.string.doctor_scan_no_photo),
                            tint = DermoColors.Slate,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDate(dateFormat, scan.capturedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (scan.bodyArea.isNotEmpty()) {
                    Text(
                        scan.bodyArea,
                        style = MaterialTheme.typography.bodySmall,
                        color = DermoColors.TealText,
                    )
                }
                if (scan.finding != null) {
                    Text(
                        scan.finding,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        scan.confidence?.let {
                            Text(
                                stringResource(R.string.doctor_confidence, (it * 100).roundToInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = DermoColors.Slate,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            severityLabel(scan.severity),
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor(scan.severity),
                        )
                    }
                }
                if (scan.note.isNotEmpty()) {
                    Text(
                        scan.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
