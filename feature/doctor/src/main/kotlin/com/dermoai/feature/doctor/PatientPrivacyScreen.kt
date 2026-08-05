package com.dermoai.feature.doctor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dermoai.core.domain.model.AuditAction
import com.dermoai.core.domain.model.LinkStatus
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

/**
 * The patient's own view of who has seen their scans, and the control to stop
 * it.
 *
 * This screen is what makes the consent on [RedeemInviteScreen] more than a
 * checkbox. A grant the patient cannot inspect or withdraw is not consent, so
 * both live here: every recorded access, read from the append-only log the
 * doctor cannot edit, and a revoke that takes effect without asking the doctor.
 *
 * Revoked doctors stay in the list. "Dr X had access, and it ended on this
 * date" is the patient's record to keep.
 *
 * @param patientUserId the signed-in patient's own `AuthUser.id`.
 */
@Composable
fun PatientPrivacyScreen(
    patientUserId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientPrivacyViewModel = hiltViewModel(),
) {
    LaunchedEffect(patientUserId) { viewModel.load(patientUserId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRevoke by remember { mutableStateOf<LinkedDoctorRow?>(null) }

    pendingRevoke?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text(stringResource(R.string.doctor_privacy_revoke)) },
            text = { Text(stringResource(R.string.doctor_privacy_revoke_body, row.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeDoctor(row, patientUserId)
                    pendingRevoke = null
                }) { Text(stringResource(R.string.doctor_revoke_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text(stringResource(R.string.doctor_cancel))
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.doctor_privacy_title),
            subtitle = stringResource(R.string.doctor_privacy_subtitle),
        )
        MedicalDisclaimerBar()

        when (val s = state) {
            is PatientPrivacyUiState.Loading -> PatientRowSkeletons(Modifier.weight(1f), count = 3)

            is PatientPrivacyUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item { SectionTitle(stringResource(R.string.doctor_privacy_doctors)) }
                if (s.doctors.isEmpty()) {
                    item { EmptyNote(Icons.Outlined.Shield, stringResource(R.string.doctor_privacy_none)) }
                } else {
                    items(s.doctors, key = { it.linkId }) { row ->
                        LinkedDoctorCard(row = row, onRevoke = { pendingRevoke = row })
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle(stringResource(R.string.doctor_privacy_log))
                }
                if (s.log.isEmpty()) {
                    item {
                        EmptyNote(
                            Icons.Outlined.Visibility,
                            stringResource(R.string.doctor_privacy_log_empty),
                        )
                    }
                } else {
                    items(s.log, key = { it.id }) { entry -> AccessLogCard(entry) }
                }

                item {
                    NeuButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.doctor_back))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(icon: ImageVector, text: String) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        style = NeuSurfaceStyle.Inset,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DermoColors.Slate)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = DermoColors.Slate)
        }
    }
}

@Composable
private fun LinkedDoctorCard(row: LinkedDoctorRow, onRevoke: () -> Unit) {
    val dateFormat = rememberDateFormat()
    val dateTimeFormat = rememberDateTimeFormat()
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = row.displayName.ifEmpty {
                    stringResource(R.string.doctor_privacy_unknown_doctor)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (row.subtitle.isNotEmpty()) {
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DermoColors.Slate,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (row.status) {
                    LinkStatus.ACTIVE -> row.consentGrantedAt
                        ?.let {
                            stringResource(
                                R.string.doctor_privacy_active,
                                formatDate(dateFormat, it),
                            )
                        }
                        ?: stringResource(R.string.doctor_privacy_invited)
                    LinkStatus.INVITED -> stringResource(R.string.doctor_privacy_invited)
                    LinkStatus.REVOKED -> stringResource(R.string.doctor_privacy_revoked)
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (row.status) {
                    LinkStatus.ACTIVE -> DermoColors.SageText
                    LinkStatus.INVITED -> DermoColors.AmberText
                    LinkStatus.REVOKED -> DermoColors.CoralText
                },
            )
            Text(
                text = row.lastAccessAt
                    ?.let {
                        stringResource(
                            R.string.doctor_privacy_last_access,
                            formatDate(dateTimeFormat, it),
                        )
                    }
                    ?: stringResource(R.string.doctor_privacy_never_accessed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (row.status != LinkStatus.REVOKED) {
                Spacer(Modifier.height(12.dp))
                NeuButton(
                    onClick = onRevoke,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = DermoColors.CoralText,
                ) {
                    Icon(Icons.Outlined.Block, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.doctor_privacy_revoke))
                }
            }
        }
    }
}

@Composable
private fun AccessLogCard(entry: AccessLogRow) {
    val dateTimeFormat = rememberDateTimeFormat()
    val (icon, label) = when (entry.action) {
        AuditAction.VIEWED_PATIENT -> Icons.Outlined.Visibility to
            stringResource(R.string.doctor_action_viewed_patient)
        AuditAction.VIEWED_SCAN -> Icons.Outlined.Visibility to
            stringResource(R.string.doctor_action_viewed_scan)
        AuditAction.EXPORTED_REPORT -> Icons.Outlined.Description to
            stringResource(R.string.doctor_action_exported_report)
        AuditAction.LINKED_PATIENT -> Icons.Outlined.PersonAdd to
            stringResource(R.string.doctor_action_linked_patient)
        AuditAction.REVOKED_LINK -> Icons.Outlined.LinkOff to
            stringResource(R.string.doctor_action_revoked_link)
    }
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = DermoColors.Slate)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.doctorName?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.doctor_privacy_unknown_doctor),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(label, style = MaterialTheme.typography.bodySmall, color = DermoColors.Slate)
            }
            Text(
                formatDate(dateTimeFormat, entry.at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
