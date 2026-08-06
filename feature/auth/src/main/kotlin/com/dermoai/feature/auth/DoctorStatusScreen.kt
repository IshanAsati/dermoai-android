package com.dermoai.feature.auth

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.domain.model.VerificationStatus
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.OutlinedNeuButton
import com.dermoai.core.ui.theme.DermoColors

/**
 * Holding screen for a doctor account that is not through verification.
 *
 * This screen exists because the alternative — dropping an unverified doctor
 * into a dashboard with everything greyed out — leaves them guessing whether the
 * app is broken, whether they missed a step, or whether tapping something again
 * might work. So it states the process (a person reads the claim), the current
 * position, and an explicit two-column account of what is and is not available.
 *
 * There is deliberately no action here that changes [VerificationStatus]. A
 * "verify me" button that flips its own flag is not verification; it is a
 * checkbox that says "trust me", and the data on the other side of it is other
 * people's medical history.
 */
@Composable
fun DoctorStatusScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onContactSupport: (() -> Unit)? = null,
    viewModel: DoctorSessionViewModel = hiltViewModel(),
) {
    val session by viewModel.doctorSession.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.doctor_status_title),
            subtitle = stringResource(R.string.doctor_status_subtitle),
        )

        if (session.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else {
            DoctorStatusContent(
                session = session,
                onSignOut = onSignOut,
                onContactSupport = onContactSupport,
                onDebugApprove = if (BuildConfig.DEBUG && session.profile != null) {
                    { viewModel.approveOwnClaimForDebug() }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun DoctorStatusContent(
    session: DoctorSessionState,
    onSignOut: () -> Unit,
    onContactSupport: (() -> Unit)?,
    /**
     * Non-null only in a debug build. See
     * [DoctorSessionViewModel.approveOwnClaimForDebug] for why this exists at
     * all; the short version is that review is out-of-band, so without it the
     * dashboard could only be reached by editing the device database over adb.
     */
    onDebugApprove: (() -> Unit)? = null,
) {
    val profile = session.profile
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Where the claim stands ──────────────────────────────────────────
        NeuSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DoctorVerificationBadge(status = session.verificationStatus)
                Text(
                    text = stringResource(session.verificationStatus.explanationRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DermoColors.Slate,
                )
            }
        }

        // ── Capabilities, stated plainly ────────────────────────────────────
        // Split into two lists rather than one prose paragraph: the thing a
        // clinician most needs from this screen is a fast answer to "can I open
        // a patient record yet", and prose buries that.
        CapabilityList(
            title = stringResource(R.string.doctor_status_allowed_title),
            icon = Icons.Outlined.Check,
            accent = DermoColors.SageText,
            items = listOf(
                stringResource(R.string.doctor_status_allowed_scan),
                stringResource(R.string.doctor_status_allowed_profile),
            ),
        )
        CapabilityList(
            title = stringResource(R.string.doctor_status_blocked_title),
            icon = Icons.Outlined.Block,
            accent = DermoColors.CoralText,
            items = listOf(
                stringResource(R.string.doctor_status_blocked_patients),
                stringResource(R.string.doctor_status_blocked_invites),
            ),
            footnote = stringResource(R.string.doctor_status_blocked_reason),
        )

        // ── The claim itself, read-only ─────────────────────────────────────
        SectionTitle(stringResource(R.string.doctor_status_credentials_title))
        if (profile == null) {
            NeuSurface(
                modifier = Modifier.fillMaxWidth(),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.doctor_status_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DermoColors.Slate,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        } else {
            SubmittedCredentials(profile)
        }

        Spacer(Modifier.height(4.dp))
        if (onDebugApprove != null) {
            // Labelled as a debug affordance, not dressed up as a feature: a
            // button here that merely said "Continue" would read to a reviewer
            // like the app verifies doctors itself, which it must never do.
            Text(
                text = stringResource(R.string.doctor_status_debug_note),
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.CoralText,
            )
            OutlinedNeuButton(
                onClick = onDebugApprove,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.doctor_status_debug_approve))
            }
        }
        if (onContactSupport != null) {
            OutlinedNeuButton(
                onClick = onContactSupport,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.doctor_status_contact_support))
            }
        }
        OutlinedNeuButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.doctor_status_sign_out))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The credentials exactly as submitted.
 *
 * Read-only, and labelled as such, because letting a doctor edit the very fields
 * under review would either invalidate a review in progress or — worse — let an
 * approved registration number be swapped for a different one after approval.
 */
@Composable
private fun SubmittedCredentials(profile: DoctorProfile) {
    val notProvided = stringResource(R.string.doctor_status_not_provided)
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.doctor_status_credentials_readonly),
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.Slate,
            )
            CredentialRow(
                label = stringResource(R.string.doctor_field_full_name),
                value = profile.fullName.ifBlank { notProvided },
            )
            CredentialRow(
                label = stringResource(R.string.doctor_field_registration_number),
                value = profile.registrationNumber.ifBlank { notProvided },
            )
            CredentialRow(
                label = stringResource(R.string.doctor_field_qualifications),
                value = profile.qualifications.joinToString(", ").ifBlank { notProvided },
            )
            CredentialRow(
                label = stringResource(R.string.doctor_field_specialty),
                value = profile.specialty.ifBlank { notProvided },
            )
            CredentialRow(
                label = stringResource(R.string.doctor_field_institution),
                value = profile.institution.ifBlank { notProvided },
            )
            CredentialRow(
                label = stringResource(R.string.doctor_field_years_experience),
                value = profile.yearsExperience.toString(),
            )
            if (profile.bio.isNotBlank()) {
                CredentialRow(
                    label = stringResource(R.string.doctor_field_bio),
                    value = profile.bio,
                )
            }
        }
    }
}

@Composable
private fun CredentialRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DermoColors.Slate,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.3f),
        )
    }
}

@Composable
private fun CapabilityList(
    title: String,
    icon: ImageVector,
    accent: Color,
    items: List<String>,
    footnote: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    // The heading above already says whether this list is the
                    // allowed or the blocked one; repeating it per bullet is noise.
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.Slate,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = DermoColors.TealText,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * The sentence that goes with each status.
 *
 * VERIFIED has no entry because a verified doctor never reaches this screen —
 * navigation sends them to the dashboard — but the branch must still say
 * something truthful if routing ever slips, so it reuses the pending copy's
 * neighbour rather than claiming access that this screen does not grant.
 */
private fun VerificationStatus.explanationRes(): Int = when (this) {
    VerificationStatus.PENDING -> R.string.doctor_status_pending_body
    VerificationStatus.REJECTED -> R.string.doctor_status_rejected_body
    VerificationStatus.UNVERIFIED, VerificationStatus.VERIFIED ->
        R.string.doctor_status_unverified_body
}
