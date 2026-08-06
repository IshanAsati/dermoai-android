package com.dermoai.feature.doctor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dermoai.core.domain.model.DoctorInvite
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.ui.components.DermoGlassCard
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

/**
 * The patient's side: type a code, see exactly what it grants, then decide.
 *
 * The consent panel is not a formality. It names the doctor, lists the four
 * categories of data the link exposes, and says how to end it — and the grant
 * button is the only thing on this screen that writes anything. A patient
 * cannot end up linked by typing; they end up linked by reading that list and
 * pressing the affirmative action.
 *
 * The field normalises as you type (uppercase, separators dropped) because the
 * code alphabet deliberately excludes I, L, O, 0 and 1, which makes forgiving
 * input safe: nothing a patient plausibly types is a near-miss for a character
 * we discard.
 *
 * @param patientUserId the signed-in patient's `AuthUser.id`.
 * @param patientDisplayName snapshotted onto the link for the doctor's list.
 * @param onLinked called after access is granted, so navigation can leave.
 */
@Composable
fun RedeemInviteScreen(
    patientUserId: String,
    patientDisplayName: String,
    onLinked: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RedeemInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()

    // Fallback pairing path: a scanned QR feeds the exact same
    // onCodeChanged/checkCode path a typed code does — see QrScanScreen's doc.
    // Local to this screen rather than view-model state because it is pure
    // navigation within one flow, not a redemption outcome.
    var showScanner by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.doctor_redeem_title),
            subtitle = stringResource(R.string.doctor_redeem_subtitle),
        )
        MedicalDisclaimerBar()

        if (showScanner) {
            QrScanScreen(
                modifier = Modifier.weight(1f),
                onCodeScanned = { scannedCode ->
                    showScanner = false
                    viewModel.onCodeChanged(scannedCode)
                    viewModel.checkCode()
                },
                onCancel = { showScanner = false },
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val s = state) {
                    is RedeemUiState.Entry -> CodeEntry(
                        code = code,
                        rejection = s.rejection,
                        canSubmit = viewModel.isCodeComplete,
                        onCodeChanged = viewModel::onCodeChanged,
                        onSubmit = viewModel::checkCode,
                        onScanQr = { showScanner = true },
                        onBack = onBack,
                    )

                    is RedeemUiState.Checking -> Busy(stringResource(R.string.doctor_redeem_checking))

                    is RedeemUiState.Consent -> ConsentPanel(
                        invite = s.invite,
                        doctor = s.doctor,
                        onAgree = { viewModel.grantConsent(patientUserId, patientDisplayName) },
                        onDecline = viewModel::declineConsent,
                    )

                    is RedeemUiState.Linking -> Busy(stringResource(R.string.doctor_redeem_linking))

                    is RedeemUiState.Linked -> LinkedPanel(
                        doctorName = s.doctorName,
                        alreadyHadAccess = s.alreadyHadAccess,
                        onDone = onLinked,
                    )
                }
            }
        }
    }
}

@Composable
private fun Busy(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CodeEntry(
    code: String,
    rejection: RedeemRejection?,
    canSubmit: Boolean,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChanged,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.doctor_redeem_field_label)) },
        supportingText = { Text(stringResource(R.string.doctor_redeem_hint)) },
        isError = rejection != null,
        // Monospaced and widely tracked: the patient is comparing character by
        // character against something written down.
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
    )

    if (rejection != null) {
        RejectionBanner(rejection)
    }

    NeuButton(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = canSubmit,
        containerColor = if (canSubmit) DermoColors.Teal else DermoColors.Line,
        contentColor = if (canSubmit) Color.White else DermoColors.Slate,
    ) {
        Text(stringResource(R.string.doctor_redeem_check))
    }

    NeuButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.QrCodeScanner, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.doctor_redeem_scan_qr))
    }

    NeuButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.doctor_back))
    }
}

/**
 * Why the code did not work.
 *
 * [RedeemRejection.Unusable] renders the domain's own sentence unaltered — it
 * already distinguishes cancelled from expired from spent, and each implies a
 * different next step for the patient.
 */
@Composable
private fun RejectionBanner(rejection: RedeemRejection) {
    val message = when (rejection) {
        is RedeemRejection.NotFound -> stringResource(R.string.doctor_redeem_not_found)
        is RedeemRejection.Unusable -> rejection.reason
        is RedeemRejection.LostRace -> stringResource(R.string.doctor_redeem_race)
        is RedeemRejection.Failed -> stringResource(R.string.doctor_redeem_error)
        is RedeemRejection.Offline -> stringResource(R.string.doctor_redeem_offline)
    }
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        style = NeuSurfaceStyle.Inset,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = DermoColors.CoralText)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = DermoColors.CoralText)
        }
    }
}

/**
 * The disclosure. Everything the link exposes, named, before anything is
 * written.
 */
@Composable
private fun ConsentPanel(
    invite: DoctorInvite,
    doctor: DoctorProfile,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    DermoGlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Visibility, null, tint = DermoColors.TealText)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.doctor_consent_title, doctor.fullName),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (doctor.institution.isNotEmpty() || doctor.specialty.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(doctor.specialty, doctor.institution).filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!doctor.isVerified) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.doctor_consent_unverified),
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.AmberText,
            )
        }

        Spacer(Modifier.height(16.dp))
        ConsentItem(stringResource(R.string.doctor_consent_item_photos))
        ConsentItem(stringResource(R.string.doctor_consent_item_findings))
        ConsentItem(stringResource(R.string.doctor_consent_item_dates))
        ConsentItem(stringResource(R.string.doctor_consent_item_notes))

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.doctor_consent_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (invite.remainingUses > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.doctor_consent_uses_left, invite.remainingUses),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    NeuButton(
        onClick = onAgree,
        modifier = Modifier.fillMaxWidth(),
        containerColor = DermoColors.Teal,
        contentColor = Color.White,
    ) {
        Icon(Icons.Outlined.CheckCircle, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.doctor_consent_agree))
    }
    NeuButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.doctor_consent_decline))
    }
}

@Composable
private fun ConsentItem(text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.CheckCircle,
            null,
            Modifier.size(18.dp),
            tint = DermoColors.TealText,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkedPanel(doctorName: String, alreadyHadAccess: Boolean, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            null,
            Modifier.size(56.dp),
            tint = DermoColors.SageText,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                if (alreadyHadAccess) {
                    R.string.doctor_redeem_already_linked
                } else {
                    R.string.doctor_redeem_linked
                },
                doctorName,
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.doctor_redeem_linked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        NeuButton(
            onClick = onDone,
            containerColor = DermoColors.Teal,
            contentColor = Color.White,
        ) {
            Text(stringResource(R.string.doctor_redeem_done))
        }
    }
}
