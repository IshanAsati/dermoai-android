package com.dermoai.feature.reports

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.DermoGlassCard
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
    val activePasscodes by viewModel.activePasscodes(userId).collectAsState()
    val clinicalNotes by viewModel.clinicalNotes(userId).collectAsState()
    val currentPasscode by viewModel.activePasscode.collectAsState()
    val context = LocalContext.current

    var showAddNote by remember { mutableStateOf(false) }
    var doctorNameInput by remember { mutableStateOf("") }
    var clinicNameInput by remember { mutableStateOf("") }
    var impressionInput by remember { mutableStateOf("") }
    var recommendationInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Doctor Share & Reports")
        MedicalDisclaimerBar()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Doctor access passcode
            DermoGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = DermoColors.Teal)
                    Spacer(Modifier.width(8.dp))
                    Text("Doctor Access Passcode", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Generate a 6-digit access code for your dermatologist to securely verify scan timeline and clinical data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DermoColors.Slate,
                )
                if (currentPasscode != null) {
                    Spacer(Modifier.height(12.dp))
                    NeuSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        style = NeuSurfaceStyle.Inset,
                        color = DermoColors.TealLight,
                    ) {
                        Text(
                            text = "Active Passcode: $currentPasscode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DermoColors.TealText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                NeuButton(
                    onClick = { viewModel.generatePasscode(userId) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = DermoColors.Teal,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentPasscode == null) "Generate Access Code" else "Regenerate Code")
                }

                val activeOnly = activePasscodes.filter { it.status == "ACTIVE" }
                if (activeOnly.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Active Share Permissions", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    activeOnly.forEach { code ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(code.passcode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Valid for ${code.dateRangeDays}d history",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DermoColors.Slate,
                                )
                            }
                            NeuButton(
                                onClick = { viewModel.revokePasscode(code.id) },
                                shape = RoundedCornerShape(12.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = DermoColors.CoralText,
                            ) { Text("Revoke") }
                        }
                    }
                }
            }

            // PDF report options
            Text("Report Settings", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    NeuSurface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        style = if (rangeDays == days) NeuSurfaceStyle.Inset else NeuSurfaceStyle.Raised,
                        color = if (rangeDays == days) DermoColors.TealLight
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { viewModel.selectedRangeDays.value = days; viewModel.reset() },
                    ) {
                        Text(
                            "${days}d",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rangeDays == days) DermoColors.TealText else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 12.dp).padding(horizontal = 12.dp),
                        )
                    }
                }
            }

            listOf(
                "Include Images" to includeImgs,
                "Include Predictions" to includePreds,
                "Include SkinMind" to includeMind,
            ).forEach { (label, checked) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = checked,
                        onCheckedChange = { v ->
                            when (label) {
                                "Include Images" -> viewModel.includeImages.value = v
                                "Include Predictions" -> viewModel.includePredictions.value = v
                                "Include SkinMind" -> viewModel.includeSkinMind.value = v
                            }
                            viewModel.reset()
                        },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }

            // Doctor visit notes
            DermoGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dermatologist Visit Notes", style = MaterialTheme.typography.titleMedium)
                    NeuButton(
                        onClick = { showAddNote = !showAddNote },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = DermoColors.Teal,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.NoteAdd,
                            contentDescription = if (showAddNote) "Hide clinical note form" else "Add clinical note",
                        )
                    }
                }

                if (showAddNote) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = doctorNameInput,
                        onValueChange = { doctorNameInput = it },
                        label = { Text("Doctor Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clinicNameInput,
                        onValueChange = { clinicNameInput = it },
                        label = { Text("Clinic / Hospital") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = impressionInput,
                        onValueChange = { impressionInput = it },
                        label = { Text("Clinical Impression / Diagnosis") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recommendationInput,
                        onValueChange = { recommendationInput = it },
                        label = { Text("Recommended Treatment Plan") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    NeuButton(
                        onClick = {
                            if (impressionInput.isNotBlank()) {
                                viewModel.addClinicalNote(userId, doctorNameInput, clinicNameInput, impressionInput, recommendationInput)
                                doctorNameInput = ""
                                clinicNameInput = ""
                                impressionInput = ""
                                recommendationInput = ""
                                showAddNote = false
                            }
                        },
                        enabled = impressionInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = DermoColors.Teal,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text("Save Doctor Note") }
                }

                Spacer(Modifier.height(12.dp))
                if (clinicalNotes.isEmpty()) {
                    Text(
                        "No doctor notes logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DermoColors.Slate,
                    )
                } else {
                    clinicalNotes.forEach { note ->
                        NeuSurface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            style = NeuSurfaceStyle.Inset,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(note.doctorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(note.clinicName, style = MaterialTheme.typography.bodySmall, color = DermoColors.Slate)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Impression: ${note.clinicalImpression}", style = MaterialTheme.typography.bodySmall)
                                if (note.recommendedTreatment.isNotEmpty()) {
                                    Text(
                                        "Treatment: ${note.recommendedTreatment}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DermoColors.SageText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            NeuButton(
                onClick = { viewModel.generate(userId, displayName) },
                enabled = state !is PdfUiState.Generating,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = DermoColors.Teal,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Generate PDF Report")
            }
            if (state is PdfUiState.Generating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Generating clinical PDF report…", style = MaterialTheme.typography.bodySmall, color = DermoColors.Slate)
            }
            val readyState = state as? PdfUiState.Ready
            if (readyState != null) {
                val uri = FileProvider.getUriForFile(context, "com.dermoai.fileprovider", readyState.file)
                Text("PDF Report Ready", style = MaterialTheme.typography.bodyMedium, color = DermoColors.SageText, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = DermoColors.Teal,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text("Open PDF") }

                    NeuButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "DermoAI Clinical Report - $displayName")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Attached is the DermoAI skin analysis and clinical progress report for $displayName.",
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Report with Doctor"))
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = DermoColors.Teal,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
            val errState = state as? PdfUiState.Error
            if (errState != null) {
                Text(errState.message, style = MaterialTheme.typography.bodySmall, color = DermoColors.CoralText)
                Text("Tap Generate to retry.", style = MaterialTheme.typography.bodySmall, color = DermoColors.Slate)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Educational tool only — does not replace a clinical dermatologist diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.Slate,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
