package com.dermoai.feature.doctor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dermoai.core.domain.model.AdherenceBand
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.PatientAdherence
import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.domain.model.TrendDirection
import com.dermoai.core.domain.model.VerificationStatus
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.ShimmerBox
import com.dermoai.core.ui.theme.DermoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The small shared pieces every doctor screen draws: severity, adherence and
 * trend chips, plus the message/skeleton states.
 *
 * They live together because they encode judgements that must not drift between
 * screens — which colour a CRITICAL finding gets, that an unconfident trend is
 * never rendered as a reassuring "Stable", that a chip announces its meaning to
 * TalkBack rather than relying on its colour. Re-implementing any of those per
 * screen is how a dashboard ends up telling two different stories about the
 * same patient.
 *
 * Icons are always `material.icons` vectors. Emoji are never used as icons:
 * they render inconsistently across devices and TalkBack reads them as their
 * Unicode name, which is not the label a clinician needs.
 */

/** Severity → the AA-safe accent used everywhere in the app for that band. */
internal fun severityColor(severity: ConditionSeverity?): Color = when (severity) {
    ConditionSeverity.CRITICAL -> DermoColors.CoralText
    ConditionSeverity.HIGH -> DermoColors.AmberText
    else -> DermoColors.SageText
}

@Composable
internal fun severityLabel(severity: ConditionSeverity?): String = when (severity) {
    ConditionSeverity.CRITICAL -> stringResource(R.string.doctor_severity_critical)
    ConditionSeverity.HIGH -> stringResource(R.string.doctor_severity_high)
    ConditionSeverity.MEDIUM -> stringResource(R.string.doctor_severity_medium)
    ConditionSeverity.LOW -> stringResource(R.string.doctor_severity_low)
    null -> stringResource(R.string.doctor_severity_unknown)
}

@Composable
internal fun verificationLabel(status: VerificationStatus): String = when (status) {
    VerificationStatus.VERIFIED -> stringResource(R.string.doctor_status_verified)
    VerificationStatus.PENDING -> stringResource(R.string.doctor_status_pending)
    VerificationStatus.REJECTED -> stringResource(R.string.doctor_status_rejected)
    VerificationStatus.UNVERIFIED -> stringResource(R.string.doctor_status_unverified)
}

/** Shared date rendering so no screen invents its own format. */
@Composable
internal fun rememberDateFormat(): SimpleDateFormat =
    remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

@Composable
internal fun rememberDateTimeFormat(): SimpleDateFormat =
    remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

/**
 * A small carved pill. Inset rather than raised so chips read as labels on the
 * card rather than as further tappable things.
 */
@Composable
internal fun DoctorChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    contentDescription: String? = null,
) {
    NeuSurface(
        modifier = modifier.semantics {
            contentDescription?.let { this.contentDescription = it }
        },
        style = NeuSurfaceStyle.Inset,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

/** Top finding of the newest scan, coloured by its concern band. */
@Composable
internal fun SeverityChip(severity: ConditionSeverity?, modifier: Modifier = Modifier) {
    val label = severityLabel(severity)
    DoctorChip(
        text = label,
        color = severityColor(severity),
        modifier = modifier,
        contentDescription = stringResource(R.string.doctor_severity_desc, label),
    )
}

/**
 * "12/14 days" plus the band.
 *
 * The raw count is shown next to the band because a doctor deciding whether to
 * chase someone needs to know whether SLIPPING means six scans or one.
 */
@Composable
internal fun AdherenceChip(adherence: PatientAdherence, modifier: Modifier = Modifier) {
    val bandLabel = when (adherence.band) {
        AdherenceBand.GOOD -> stringResource(R.string.doctor_band_good)
        AdherenceBand.SLIPPING -> stringResource(R.string.doctor_band_slipping)
        AdherenceBand.INACTIVE -> stringResource(R.string.doctor_band_inactive)
    }
    val color = when (adherence.band) {
        AdherenceBand.GOOD -> DermoColors.SageText
        AdherenceBand.SLIPPING -> DermoColors.AmberText
        AdherenceBand.INACTIVE -> DermoColors.CoralText
    }
    val counts = stringResource(
        R.string.doctor_adherence_days,
        adherence.scansLast14Days,
        PatientAdherence.WINDOW_DAYS,
    )
    DoctorChip(
        text = "$counts · $bandLabel",
        color = color,
        modifier = modifier,
        contentDescription = stringResource(R.string.doctor_adherence_desc, counts, bandLabel),
    )
}

/**
 * Direction of travel.
 *
 * An unconfident trend renders as "Not enough data" with a neutral glyph, never
 * as STABLE — [PatientTrend.isConfident] is false below three scans, and
 * showing a reassuring word there would be inventing reassurance.
 */
@Composable
internal fun TrendChip(trend: PatientTrend, modifier: Modifier = Modifier) {
    val (icon, color, label) = when {
        !trend.isConfident -> Triple(
            Icons.Outlined.Remove,
            DermoColors.Slate,
            stringResource(R.string.doctor_trend_unknown),
        )
        trend.direction == TrendDirection.WORSENING -> Triple(
            Icons.Outlined.ArrowUpward,
            DermoColors.CoralText,
            stringResource(R.string.doctor_trend_worsening),
        )
        trend.direction == TrendDirection.IMPROVING -> Triple(
            Icons.Outlined.ArrowDownward,
            DermoColors.SageText,
            stringResource(R.string.doctor_trend_improving),
        )
        else -> Triple(
            Icons.Outlined.Remove,
            DermoColors.Slate,
            stringResource(R.string.doctor_trend_stable),
        )
    }
    DoctorChip(
        text = label,
        color = color,
        modifier = modifier,
        leadingIcon = icon,
        contentDescription = stringResource(R.string.doctor_trend_desc, label),
    )
}

/** "Verified clinician" badge. Only ever shown for [VerificationStatus.VERIFIED]. */
@Composable
internal fun VerifiedBadge(modifier: Modifier = Modifier) {
    DoctorChip(
        text = stringResource(R.string.doctor_verified_badge),
        color = DermoColors.TealText,
        modifier = modifier,
        leadingIcon = Icons.Outlined.VerifiedUser,
    )
}

/**
 * Full-width centred message with an optional action — the shape every empty,
 * locked and error state on these screens takes.
 */
@Composable
internal fun DoctorMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = DermoColors.Slate,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(56.dp), tint = tint.copy(alpha = 0.6f))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}

/**
 * Loading skeletons shaped like the real patient rows.
 *
 * Decorative only — `clearAndSetSemantics {}` keeps TalkBack from reading four
 * empty cards as content while the real list is still resolving.
 */
@Composable
internal fun PatientRowSkeletons(modifier: Modifier = Modifier, count: Int = 4) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp).clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShimmerBox(Modifier.fillMaxWidth(0.55f).height(16.dp), RoundedCornerShape(8.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.35f).height(12.dp), RoundedCornerShape(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(Modifier.width(84.dp).height(24.dp), RoundedCornerShape(10.dp))
                        ShimmerBox(Modifier.width(110.dp).height(24.dp), RoundedCornerShape(10.dp))
                    }
                }
            }
        }
    }
}

/** Section label above a group of cards. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = DermoColors.Ink,
        modifier = modifier,
    )
}

internal fun formatDate(format: SimpleDateFormat, millis: Long): String =
    format.format(Date(millis))
