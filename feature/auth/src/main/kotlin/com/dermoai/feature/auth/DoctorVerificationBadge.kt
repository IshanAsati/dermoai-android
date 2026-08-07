package com.dermoai.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dermoai.core.domain.model.VerificationStatus
import com.dermoai.core.ui.theme.DermoColors

/**
 * The single rendering of [VerificationStatus] in the app.
 *
 * Public, and living in `:feature:auth` where the status is first written, so
 * the doctor surface and any patient-facing "who is this clinician" view show
 * the *same* pill. A duplicated badge is how PENDING eventually ends up looking
 * green somewhere, which is precisely the claim this component exists to avoid
 * overstating.
 *
 * Icons are Material vectors, never emoji: emoji render differently per device
 * font, are read aloud as their unicode name by TalkBack, and are an explicit
 * anti-pattern in this codebase.
 */
@Composable
fun DoctorVerificationBadge(
    status: VerificationStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val style = status.badgeStyle()
    val label = stringResource(style.labelRes)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(style.content.copy(alpha = 0.14f))
            .padding(horizontal = if (showLabel) 10.dp else 8.dp, vertical = 5.dp)
            // The icon repeats the label, so collapse the pill into one node
            // rather than announcing "check" and then "Verified".
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.content,
            modifier = Modifier.size(16.dp),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = style.content,
            )
        }
    }
}

/** Icon + accessible colour + wording for one status. */
private data class VerificationBadgeStyle(
    val icon: ImageVector,
    /**
     * Always a `*Text` variant (or [DermoColors.Slate]). The plain accents fail
     * AA at label sizes, and this text is deliberately small.
     */
    val content: Color,
    val labelRes: Int,
)

private fun VerificationStatus.badgeStyle(): VerificationBadgeStyle = when (this) {
    VerificationStatus.VERIFIED -> VerificationBadgeStyle(
        icon = Icons.Outlined.CheckCircle,
        content = DermoColors.SageText,
        labelRes = R.string.doctor_status_verified,
    )
    VerificationStatus.PENDING -> VerificationBadgeStyle(
        icon = Icons.Outlined.Schedule,
        content = DermoColors.AmberText,
        labelRes = R.string.doctor_status_pending,
    )
    VerificationStatus.UNVERIFIED -> VerificationBadgeStyle(
        icon = Icons.AutoMirrored.Outlined.HelpOutline,
        // Muted is documented as too light to sit on the app background, so the
        // neutral state uses Slate — muted in tone, still legible.
        content = DermoColors.Slate,
        labelRes = R.string.doctor_status_unverified,
    )
    VerificationStatus.REJECTED -> VerificationBadgeStyle(
        icon = Icons.Outlined.ErrorOutline,
        content = DermoColors.CoralText,
        labelRes = R.string.doctor_status_rejected,
    )
}
