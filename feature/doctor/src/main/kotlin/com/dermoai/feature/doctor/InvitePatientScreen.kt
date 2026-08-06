package com.dermoai.feature.doctor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import com.dermoai.core.data.sync.SyncSkipReason
import com.dermoai.core.domain.model.DoctorInvite
import com.dermoai.core.ui.components.DermoGlassCard
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.feature.doctor.invite.InviteCodes
import com.dermoai.feature.doctor.invite.QrCodes

/**
 * Where a doctor produces a code for the patient sitting in front of them.
 *
 * Both forms are offered because neither covers the room on its own: the QR is
 * faster when the patient's camera works and the lighting is decent, and the
 * typed code is the one that still works when it does not. They encode the same
 * credential — the QR carries `dermoai://invite/<CODE>` — so a patient who
 * scans and a patient who types end up in the same place.
 *
 * Expiry and a use cap are always set and always visible. An eight-character
 * code with neither is guessable given enough attempts and enough time, and the
 * doctor should be able to see at a glance which of their outstanding codes are
 * still live.
 *
 * @param userId the signed-in doctor's `AuthUser.id`.
 */
@Composable
fun InvitePatientScreen(
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvitePatientViewModel = hiltViewModel(),
) {
    LaunchedEffect(userId) { viewModel.load(userId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedInviteId.collectAsStateWithLifecycle()
    val expiryDays by viewModel.expiryDays.collectAsStateWithLifecycle()
    val maxUses by viewModel.maxUses.collectAsStateWithLifecycle()
    val generationFailed by viewModel.generationFailed.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.doctor_invite_title),
            subtitle = stringResource(R.string.doctor_invite_subtitle),
        )
        MedicalDisclaimerBar()

        when (val s = state) {
            is InviteUiState.Loading -> PatientRowSkeletons(Modifier.weight(1f), count = 2)

            is InviteUiState.NoProfile -> DoctorMessage(
                icon = Icons.Outlined.PersonOff,
                title = stringResource(R.string.doctor_no_profile_title),
                body = stringResource(R.string.doctor_no_profile_body),
                modifier = Modifier.weight(1f),
            )

            is InviteUiState.Ready -> {
                val selected = s.invites.firstOrNull { it.id == selectedId }
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        GenerateCard(
                            expiryDays = expiryDays,
                            maxUses = maxUses,
                            onExpiryChange = viewModel::setExpiryDays,
                            onUsesChange = viewModel::setMaxUses,
                            onGenerate = viewModel::createInvite,
                            failed = generationFailed,
                        )
                    }
                    if (selected != null) {
                        item { InviteCodeCard(selected) }
                        item { InviteSyncBanner(syncState) }
                    }
                    item { SectionTitle(stringResource(R.string.doctor_invite_active_title)) }
                    if (s.invites.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.doctor_invite_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DermoColors.Slate,
                            )
                        }
                    } else {
                        items(s.invites, key = { it.id }) { invite ->
                            InviteRow(
                                invite = invite,
                                selected = invite.id == selectedId,
                                onSelect = { viewModel.selectInvite(invite.id) },
                                onRevoke = { viewModel.revokeInvite(invite.id) },
                            )
                        }
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
}

/** Expiry and use-cap pickers plus the generate action. */
@Composable
private fun GenerateCard(
    expiryDays: Int,
    maxUses: Int,
    onExpiryChange: (Int) -> Unit,
    onUsesChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    failed: Boolean,
) {
    DermoGlassCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.doctor_invite_expiry_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvitePatientViewModel.EXPIRY_OPTIONS.forEach { days ->
                OptionChip(
                    label = stringResource(R.string.doctor_invite_days, days),
                    selected = days == expiryDays,
                    onClick = { onExpiryChange(days) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.doctor_invite_uses_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvitePatientViewModel.USES_OPTIONS.forEach { uses ->
                OptionChip(
                    label = uses.toString(),
                    selected = uses == maxUses,
                    onClick = { onUsesChange(uses) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        NeuButton(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            containerColor = DermoColors.Teal,
            contentColor = Color.White,
        ) {
            Icon(Icons.Outlined.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.doctor_invite_generate))
        }
        if (failed) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.doctor_invite_error),
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.CoralText,
            )
        }
    }
}

/**
 * A selectable option. Uses `pressedForce` for the selected state so selection
 * reads as a carved well rather than as a colour swap — colour alone would be
 * the only cue for a colour-blind reader.
 */
@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    NeuSurface(
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        pressedForce = selected,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) DermoColors.TealText else DermoColors.Slate,
        )
    }
}

/** The code itself, big, with its QR and a copy action. */
@Composable
private fun InviteCodeCard(invite: DoctorInvite) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var copied by remember(invite.id) { mutableStateOf(false) }
    val sizePx = with(density) { QR_SIZE_DP.dp.roundToPx() }
    val bitmap = remember(invite.code, sizePx) {
        QrCodes.encode(InviteCodes.deepLink(invite.code), sizePx)
    }

    DermoGlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = InviteCodes.formatForDisplay(invite.code),
                style = MaterialTheme.typography.headlineMedium,
                color = DermoColors.TealText,
            )
            Spacer(Modifier.height(12.dp))
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.doctor_invite_qr_desc),
                    modifier = Modifier
                        .size(QR_SIZE_DP.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                // Degrade to the typed code rather than to a blank card.
                NeuSurface(
                    Modifier.size(QR_SIZE_DP.dp),
                    style = NeuSurfaceStyle.Inset,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.QrCode2, null, tint = DermoColors.Slate)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.doctor_invite_qr_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = DermoColors.Slate,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.doctor_invite_qr_caption),
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.Slate,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            NeuButton(
                onClick = {
                    // Platform clipboard rather than Compose's LocalClipboardManager,
                    // which is deprecated in this Compose version in favour of a
                    // suspend API this one-shot copy does not need.
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    manager?.setPrimaryClip(
                        ClipData.newPlainText(CLIP_LABEL, invite.code),
                    )
                    copied = manager != null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (copied) {
                        stringResource(R.string.doctor_invite_copied)
                    } else {
                        stringResource(R.string.doctor_invite_copy)
                    },
                )
            }
        }
    }
}

/**
 * Whether the selected code above has actually left this device.
 *
 * [InviteSyncState.Idle] renders nothing — a code carried over from a
 * previous session with no fresh push attempt has no new information to
 * show, and a permanent banner would just be noise. Every other state says,
 * plainly, whether a patient on a different phone can find this code yet.
 */
@Composable
private fun InviteSyncBanner(state: InviteSyncState) {
    val (icon, tint, message) = when (state) {
        is InviteSyncState.Idle -> return
        is InviteSyncState.Syncing -> Triple(
            Icons.Outlined.Sync,
            DermoColors.Slate,
            stringResource(R.string.doctor_invite_sync_syncing),
        )
        is InviteSyncState.Synced -> Triple(
            Icons.Outlined.CheckCircle,
            DermoColors.SageText,
            stringResource(R.string.doctor_invite_sync_synced),
        )
        is InviteSyncState.NotSynced -> Triple(
            Icons.Outlined.CloudOff,
            DermoColors.AmberText,
            stringResource(
                when (state.reason) {
                    SyncSkipReason.OFFLINE -> R.string.doctor_invite_sync_not_synced_offline
                    SyncSkipReason.NO_SESSION -> R.string.doctor_invite_sync_not_synced_no_session
                    SyncSkipReason.NOT_CONFIGURED,
                    SyncSkipReason.IDENTITY_MISMATCH,
                    -> R.string.doctor_invite_sync_not_synced_other
                },
            ),
        )
        is InviteSyncState.Failed -> Triple(
            Icons.Outlined.ErrorOutline,
            DermoColors.CoralText,
            stringResource(R.string.doctor_invite_sync_failed),
        )
    }
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        style = NeuSurfaceStyle.Inset,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = tint)
        }
    }
}

/** One outstanding code: uses left, expiry, state, and a cancel action. */
@Composable
private fun InviteRow(
    invite: DoctorInvite,
    selected: Boolean,
    onSelect: () -> Unit,
    onRevoke: () -> Unit,
) {
    val dateFormat = rememberDateFormat()
    val now = System.currentTimeMillis()
    val usable = invite.isUsable(now)
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onSelect,
        pressedForce = selected,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    InviteCodes.formatForDisplay(invite.code),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (usable) DermoColors.TealText else DermoColors.Slate,
                )
                Text(
                    stringResource(
                        R.string.doctor_invite_remaining,
                        invite.remainingUses,
                        invite.maxUses,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when {
                        invite.revoked -> stringResource(R.string.doctor_invite_revoked)
                        now >= invite.expiresAt -> stringResource(R.string.doctor_invite_expired)
                        invite.remainingUses == 0 -> stringResource(R.string.doctor_invite_spent)
                        else -> stringResource(
                            R.string.doctor_invite_expires,
                            formatDate(dateFormat, invite.expiresAt),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (usable) DermoColors.SageText else DermoColors.CoralText,
                )
            }
            if (usable) {
                NeuButton(
                    onClick = onRevoke,
                    contentColor = DermoColors.CoralText,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.Block, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.doctor_invite_revoke),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private const val QR_SIZE_DP = 220

/** Clipboard entry label. Not user-visible on any supported version. */
private const val CLIP_LABEL = "DermoAI invite code"
