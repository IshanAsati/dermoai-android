package com.dermoai.navigation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dermoai.R
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

/**
 * More hub — scrollable list of secondary feature destinations.
 */
@Composable
fun MoreHubScreen(
    onNavigate: (Any) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.more_title),
        )
        MedicalDisclaimerBar()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoreHubRow(
                icon = Icons.Outlined.Spa,
                label = stringResource(R.string.more_treatment),
                onClick = { onNavigate(TreatmentRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.SelfImprovement,
                label = stringResource(R.string.more_wellness),
                onClick = { onNavigate(WellnessRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.BarChart,
                label = stringResource(R.string.more_analytics),
                onClick = { onNavigate(AnalyticsRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.Description,
                label = stringResource(R.string.more_reports),
                onClick = { onNavigate(ReportsRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.more_settings),
                onClick = { onNavigate(SettingsRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.QuestionMark,
                label = stringResource(R.string.more_faq),
                onClick = { onNavigate(FaqRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.LocationOn,
                label = stringResource(R.string.more_find_dermatologist),
                onClick = { onNavigate(FindDermatologistRoute) },
            )
            // Patient-side halves of the doctor flow. They live here rather than
            // behind a role check because any patient may be invited by a doctor,
            // and everyone should be able to reach their own access log.
            MoreHubRow(
                icon = Icons.Outlined.GroupAdd,
                label = stringResource(R.string.more_redeem_invite),
                onClick = { onNavigate(RedeemInviteRoute) },
            )
            MoreHubRow(
                icon = Icons.Outlined.Shield,
                label = stringResource(R.string.more_privacy),
                onClick = { onNavigate(PatientPrivacyRoute) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(com.dermoai.feature.auth.R.string.auth_sign_out))
            }
        }

    }
}

@Composable
private fun MoreHubRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    NeuSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuSurface(
                modifier = Modifier.size(36.dp),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(12.dp),
                color = DermoColors.TealAccent.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                    tint = DermoColors.TealAccent,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
