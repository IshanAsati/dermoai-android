package com.dermoai.feature.reports

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

@Composable
fun ReportScreen(
    userId: String,
    displayName: String,
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val includeImgs by viewModel.includeImages.collectAsState()
    val includePreds by viewModel.includePredictions.collectAsState()
    val includeMind by viewModel.includeSkinMind.collectAsState()
    val rangeDays by viewModel.selectedRangeDays.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Doctor Report")
        MedicalDisclaimerBar()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Date range", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    NeuSurface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        style = if (rangeDays == days) NeuSurfaceStyle.Inset else NeuSurfaceStyle.Raised,
                        color = if (rangeDays == days) DermoColors.TealAccent.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { viewModel.selectedRangeDays.value = days; viewModel.reset() },
                    ) {
                        Text("${days}d", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Include:", style = MaterialTheme.typography.titleMedium)
            listOf("Images" to includeImgs, "Predictions" to includePreds, "SkinMind" to includeMind).forEach { (label, checked) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = checked,
                        onCheckedChange = { v ->
                            when (label) { "Images" -> viewModel.includeImages.value = v; "Predictions" -> viewModel.includePredictions.value = v; "SkinMind" -> viewModel.includeSkinMind.value = v }
                            viewModel.reset()
                        },
                        modifier = Modifier.semantics { contentDescription = "Include $label in report" },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            NeuButton(
                onClick = { viewModel.generate(userId, displayName) },
                enabled = state !is PdfUiState.Generating,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = DermoColors.TealAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Description, null)
                Spacer(Modifier.size(8.dp))
                Text("Generate PDF")
            }
            if (state is PdfUiState.Generating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Generating report…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val readyState = state as? PdfUiState.Ready
            if (readyState != null) {
                val filepath = readyState.file.absolutePath
                Text("Report ready: $filepath", style = MaterialTheme.typography.bodySmall, color = DermoColors.SageText)
                NeuButton(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "com.dermoai.fileprovider", readyState.file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = DermoColors.VioletAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("Open PDF") }
            }
            val errState = state as? PdfUiState.Error
            if (errState != null) {
                Text(errState.message, style = MaterialTheme.typography.bodySmall, color = DermoColors.CoralText)
                Text("Tap Generate to retry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text("Educational only — not a medical diagnosis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
