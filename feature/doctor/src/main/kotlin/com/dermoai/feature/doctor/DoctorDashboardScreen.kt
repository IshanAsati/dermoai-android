package com.dermoai.feature.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuIconButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.feature.doctor.triage.TriageRow

/**
 * The doctor's triage inbox — the centrepiece of the feature.
 *
 * Two decisions drive everything on this screen:
 *
 *  1. **The list is ordered by clinical urgency, never alphabetically.** See
 *     [com.dermoai.feature.doctor.triage.TriageRanking] for the ordering and
 *     why. The screen deliberately does no sorting of its own; if it did, the
 *     ordering would exist in two places and only one of them would be tested.
 *  2. **Verification gates the whole screen.** An unverified clinician sees a
 *     locked state and nothing else — the ViewModel never even subscribes to
 *     the patient query, so there is no patient data on the device to leak
 *     through a mis-drawn branch.
 *
 * @param userId the signed-in account's `AuthUser.id`.
 * @param onPatientClick receives the patient's `AuthUser.id`, for
 *   [PatientDetailScreen].
 * @param onInvitePatient opens [InvitePatientScreen].
 * @param onOpenSettings opens the shared Settings screen. The doctor
 *   dashboard is not one of the bottom-bar tabs (a verified doctor never sees
 *   the patient tab bar at all), so without this the settings screen — and
 *   sign-out — would be unreachable once a doctor is verified.
 */
@Composable
fun DoctorDashboardScreen(
    userId: String,
    onPatientClick: (patientUserId: String) -> Unit,
    onInvitePatient: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
) {
    LaunchedEffect(userId) { viewModel.load(userId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        val profile = (state as? DoctorDashboardUiState.Ready)?.profile
            ?: (state as? DoctorDashboardUiState.Locked)?.profile
        val activeCount = (state as? DoctorDashboardUiState.Ready)?.rows?.size ?: 0

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            NeuIconButton(
                onClick = onOpenSettings,
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.doctor_open_settings),
            )
        }
        GradientHeader(
            title = profile?.fullName ?: stringResource(R.string.doctor_dashboard_title),
            subtitle = when (state) {
                is DoctorDashboardUiState.Ready -> pluralStringResource(
                    R.plurals.doctor_active_patients, activeCount, activeCount,
                )
                else -> stringResource(R.string.doctor_dashboard_subtitle)
            },
            trailing = {
                if (profile?.isVerified == true) {
                    Spacer(Modifier.height(12.dp))
                    VerifiedBadge()
                }
            },
        )
        MedicalDisclaimerBar()

        when (val s = state) {
            is DoctorDashboardUiState.Loading -> PatientRowSkeletons(Modifier.weight(1f))

            is DoctorDashboardUiState.NoProfile -> DoctorMessage(
                icon = Icons.Outlined.PersonOff,
                title = stringResource(R.string.doctor_no_profile_title),
                body = stringResource(R.string.doctor_no_profile_body),
                modifier = Modifier.weight(1f),
            )

            is DoctorDashboardUiState.Locked -> LockedState(
                profile = s.profile,
                modifier = Modifier.weight(1f),
            )

            is DoctorDashboardUiState.Ready -> if (s.rows.isEmpty()) {
                DoctorMessage(
                    icon = Icons.Outlined.Groups,
                    title = stringResource(R.string.doctor_empty_title),
                    body = stringResource(R.string.doctor_empty_body),
                    modifier = Modifier.weight(1f),
                    action = {
                        NeuButton(
                            onClick = onInvitePatient,
                            containerColor = DermoColors.Teal,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Outlined.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.doctor_invite_cta))
                        }
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.doctor_dashboard_ranked_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(s.rows, key = { it.patientUserId }) { row ->
                        PatientTriageCard(row = row, onClick = { onPatientClick(row.patientUserId) })
                    }
                    item {
                        NeuButton(
                            onClick = onInvitePatient,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = DermoColors.Teal,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Outlined.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.doctor_invite_cta))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown instead of — never alongside — patient data when the clinician is not
 * verified. Names the status so a doctor knows whether to wait or to act.
 */
@Composable
private fun LockedState(profile: DoctorProfile, modifier: Modifier = Modifier) {
    DoctorMessage(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.doctor_locked_title),
        body = stringResource(
            R.string.doctor_locked_body,
            verificationLabel(profile.verificationStatus),
        ),
        modifier = modifier,
        tint = DermoColors.amberText,
    )
}

/**
 * One patient row: name, last scan date, top finding + severity, adherence, and
 * a trend chip.
 *
 * Chips wrap in a [FlowRow] rather than being squeezed onto one line — at large
 * font scales three chips will not fit, and truncating "Inactive" to "Inact…"
 * on a triage screen is worse than a second line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatientTriageCard(row: TriageRow, onClick: () -> Unit) {
    val dateFormat = rememberDateFormat()
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.lastScanAt
                            ?.let {
                                stringResource(
                                    R.string.doctor_last_scan,
                                    formatDate(dateFormat, it),
                                )
                            }
                            ?: stringResource(R.string.doctor_no_scans),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.doctor_open_patient, row.displayName),
                    Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (row.latestFinding != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = row.latestFinding,
                    style = MaterialTheme.typography.bodyMedium,
                    color = severityColor(row.latestSeverity),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (row.latestSeverity != null) SeverityChip(row.latestSeverity)
                AdherenceChip(row.adherence)
                TrendChip(row.trend)
            }
        }
    }
}
