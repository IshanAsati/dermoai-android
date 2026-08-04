package com.dermoai.feature.treatment

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.ShimmerBox
import com.dermoai.core.ui.theme.DermoColors
import kotlinx.coroutines.launch

@Composable
fun TreatmentScreen(
    userId: String,
    viewModel: TreatmentViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(userId) { viewModel.loadRoutines(userId) }
    val routines by viewModel.routines.collectAsState()
    val selectedId by viewModel.selectedRoutineId.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val completedIds by viewModel.completedStepIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    var renameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var addStepDialog by remember { mutableStateOf(false) }
    var newStepName by remember { mutableStateOf("") }
    var newStepTime by remember { mutableStateOf("Morning") }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Treatment Tracker")
        MedicalDisclaimerBar()

        if (isLoading && routines.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShimmerBox(Modifier.size(44.dp), RoundedCornerShape(12.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBox(Modifier.fillMaxWidth(0.55f).height(14.dp), RoundedCornerShape(7.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.35f).height(12.dp), RoundedCornerShape(6.dp))
                            }
                        }
                    }
                }
            }
        } else if (selectedId != null) {
            val routine = routines.find { it.id == selectedId }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.selectRoutine(null) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                        Text(routine?.name ?: "Routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renameText = routine?.name ?: ""; renameDialog = true }) { Icon(Icons.Outlined.Edit, "Rename") }
                        IconButton(onClick = { routine?.let { viewModel.deleteRoutine(it.id) } }) { Icon(Icons.Outlined.Delete, "Delete", tint = DermoColors.SoftCoral) }
                    }
                }
                items(steps) { step ->
                    val done = step.id in completedIds
                    val index = steps.indexOf(step)
                    NeuSurface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (done) DermoColors.Sage.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (done) "Completed" else "Mark complete", Modifier.size(48.dp).clickable { viewModel.toggleStep(step.id) }, tint = if (done) DermoColors.Sage else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(step.productName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(step.timeOfDay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { if (index > 0) { viewModel.reorderSteps(selectedId!!, index, index - 1) } }) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { if (index < steps.size - 1) { viewModel.reorderSteps(selectedId!!, index, index + 1) } }) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { viewModel.deleteStep(step.id) }) { Icon(Icons.Outlined.Delete, "Delete step", tint = DermoColors.SoftCoral) }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    NeuButton(
                        onClick = { newStepName = ""; newStepTime = "Morning"; addStepDialog = true },
                        containerColor = DermoColors.Teal,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Outlined.Add, null, Modifier.padding(end = 8.dp)); Text("Add step") }
                    Spacer(Modifier.height(16.dp))
                }
            }
        } else if (routines.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Spa, null, Modifier.size(64.dp), tint = DermoColors.TealAccent.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("No routines yet", style = MaterialTheme.typography.headlineSmall)
                    Text("Create a skincare routine to track treatments.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(24.dp))
                    AddRoutineForm(viewModel, userId)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(routines) { routine ->
                    NeuSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        onClick = { viewModel.selectRoutine(routine.id) },
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            NeuSurface(
                                Modifier.size(44.dp),
                                style = NeuSurfaceStyle.Inset,
                                shape = RoundedCornerShape(14.dp),
                                color = DermoColors.Amber.copy(alpha = 0.12f),
                            ) { Icon(Icons.Outlined.Spa, null, Modifier.padding(10.dp), tint = DermoColors.Amber) }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) { Text(routine.name, style = MaterialTheme.typography.titleSmall); Text("Steps · Completions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                item { AddRoutineForm(viewModel, userId); Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (renameDialog) {
        AlertDialog(onDismissRequest = { renameDialog = false }, title = { Text("Rename routine") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) { viewModel.renameRoutine(selectedId!!, renameText); renameDialog = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } })
    }

    if (addStepDialog) {
        AlertDialog(onDismissRequest = { addStepDialog = false }, title = { Text("Add step") },
            text = {
                Column {
                    OutlinedTextField(value = newStepName, onValueChange = { newStepName = it }, label = { Text("Product / action") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newStepTime, onValueChange = { newStepTime = it }, label = { Text("Time (Morning/Evening)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { if (newStepName.isNotBlank()) { viewModel.addStep(selectedId!!, newStepName, newStepTime); addStepDialog = false } }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addStepDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun AddRoutineForm(viewModel: TreatmentViewModel, userId: String) {
    val scope = rememberCoroutineScope()
    val name by viewModel.newRoutineName.collectAsState()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = name, onValueChange = { viewModel.newRoutineName.value = it }, label = { Text("Routine name") }, placeholder = { Text("e.g. Acne treatment") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true)
        Spacer(Modifier.width(12.dp))
        NeuButton(
            onClick = { scope.launch { viewModel.createRoutine(userId) } },
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(56.dp),
            containerColor = DermoColors.TealAccent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Icon(Icons.Outlined.Add, stringResource(R.string.treatment_add_routine)) }
    }
}
