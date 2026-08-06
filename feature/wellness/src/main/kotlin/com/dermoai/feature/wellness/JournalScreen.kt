package com.dermoai.feature.wellness

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.OutlinedNeuButton
import com.dermoai.core.ui.theme.DermoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(
    userId: String,
    viewModel: JournalViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    LaunchedEffect(userId) { viewModel.load(userId) }
    val entries by viewModel.entries.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var mood by remember { mutableIntStateOf(3) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Confidence Journal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        if (showEditor) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("How are you feeling?") }, modifier = Modifier.fillMaxWidth().height(140.dp), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        NeuSurface(
                            Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            style = if (n <= mood) NeuSurfaceStyle.Inset else NeuSurfaceStyle.Raised,
                            color = if (n <= mood) DermoColors.Teal else MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = { mood = n },
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$n") } }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedNeuButton(onClick = { showEditor = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    NeuButton(
                        onClick = { viewModel.save(userId, title, body, mood); title = ""; body = ""; mood = 3; showEditor = false },
                        modifier = Modifier.weight(1f),
                        containerColor = DermoColors.Teal,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text("Save") }
                }
            }
        } else {
            NeuButton(
                onClick = { showEditor = true },
                containerColor = DermoColors.Teal,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) { Icon(Icons.Outlined.Add, null, Modifier.padding(end = 8.dp)); Text("New entry") }
        }

        LazyColumn(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries) { entry ->
                NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(entry.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            Spacer(Modifier.height(4.dp))
                            Text(SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(entry.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${entry.mood}/5", style = MaterialTheme.typography.labelLarge, color = DermoColors.TealText)
                        IconButton(onClick = { viewModel.delete(entry.id) }) { Icon(Icons.Outlined.Delete, "Delete entry", tint = DermoColors.SoftCoral, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}
