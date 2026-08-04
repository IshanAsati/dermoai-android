package com.dermoai.feature.timeline

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.ShimmerBox
import com.dermoai.core.ui.theme.DermoColors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    userId: String,
    onScanClick: (String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(userId) { viewModel.loadTimeline(userId) }
    val scans by viewModel.scans.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(title = stringResource(R.string.timeline_title), subtitle = stringResource(R.string.timeline_subtitle))
        MedicalDisclaimerBar()

        if (isLoading && scans.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) {
                    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShimmerBox(Modifier.size(72.dp), RoundedCornerShape(16.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp), RoundedCornerShape(7.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.45f).height(12.dp), RoundedCornerShape(6.dp))
                            }
                        }
                    }
                }
            }
        } else if (scans.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(64.dp), tint = DermoColors.TealAccent.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.timeline_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.timeline_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(scans, key = { it.scan.id }) { item ->
                    TimelineCard(
                        scan = item.scan,
                        topPrediction = item.topPrediction,
                        onClick = { onScanClick(item.scan.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineCard(
    scan: com.dermoai.core.database.entity.SkinScanEntity,
    topPrediction: ScanPredictionEntity?,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }

    NeuSurface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = remember(scan.imagePath) {
                runCatching { BitmapFactory.decodeFile(scan.imagePath) }.getOrNull()
            }
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), stringResource(R.string.timeline_scan_desc),
                    Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                NeuSurface(
                    Modifier.size(72.dp),
                    style = NeuSurfaceStyle.Inset,
                    shape = RoundedCornerShape(16.dp),
                    color = DermoColors.TealAccent.copy(alpha = 0.1f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FiberManualRecord, stringResource(R.string.timeline_no_image), tint = DermoColors.TealAccent) }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(dateFormat.format(Date(scan.capturedAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (scan.bodyArea.isNotEmpty()) {
                    Text(scan.bodyArea, style = MaterialTheme.typography.bodySmall, color = DermoColors.TealText)
                }
                if (topPrediction != null) {
                    Text(topPrediction.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val pct = "%.0f%%".format(topPrediction.confidence * 100)
                    Text(pct, style = MaterialTheme.typography.bodySmall, color = DermoColors.SageText)
                }
                if (scan.note.isNotEmpty()) {
                    Text(scan.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (scan.voiceNotePath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mic, stringResource(R.string.timeline_voice_note), Modifier.size(14.dp), tint = DermoColors.TealAccent)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.timeline_voice_note), style = MaterialTheme.typography.labelSmall, color = DermoColors.TealText)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineDetailScreen(
    scanId: String,
    onBack: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(scanId) {
        viewModel.loadPredictions(scanId)
        viewModel.loadScan(scanId)
    }
    val predictions by viewModel.predictions.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val scanLoading by viewModel.scanLoading.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var voiceNotePath by remember { mutableStateOf<String?>(null) }
    var recordingFailed by remember { mutableStateOf(false) }
    val scope2 = rememberCoroutineScope()

    fun startRecording() {
        try {
            val dir = File(context.filesDir, "voicenotes").also { it.mkdirs() }
            val file = File(dir, "note_${System.currentTimeMillis()}.mp3")
            val recorder = android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            voiceNotePath = file.absolutePath
            recording = true
            recordingFailed = false
        } catch (_: Exception) { recording = false; recordingFailed = true }
    }

    fun stopRecording() {
        mediaRecorder?.apply { try { stop() } catch (_: Exception) {}; release() }
        mediaRecorder = null
        recording = false
        // Persist the recorded note back to the scan so it survives navigation.
        voiceNotePath?.let { path -> viewModel.saveVoiceNote(scanId, path) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.timeline_delete_title)) },
            text = { Text(stringResource(R.string.timeline_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.deleteScan(scanId)) {
                            deleteFailed = false
                            onBack()
                        } else {
                            deleteFailed = true
                            showDeleteDialog = false
                        }
                    }
                }) { Text(stringResource(R.string.timeline_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.timeline_cancel)) } },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(stringResource(R.string.timeline_detail_title), subtitle = scan?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it.capturedAt)) }.orEmpty())
        MedicalDisclaimerBar()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (scanLoading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (scan == null) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.timeline_not_found_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.timeline_not_found_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                return@Column
            }
            // Photo
            val bmp = scan?.imagePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            if (bmp != null) {
                Image(bmp.asImageBitmap(), "Scan", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(16.dp))

            // Predictions
            if (predictions.isNotEmpty()) {
                Text(stringResource(R.string.timeline_predictions), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                predictions.forEach { pred ->
                    NeuSurface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pred.label, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.timeline_confidence, (pred.confidence * 100).roundToInt()), style = MaterialTheme.typography.bodySmall, color = DermoColors.SageText)
                            }
                            Text(pred.concernBand, style = MaterialTheme.typography.labelSmall, color = when (pred.concernBand) {
                                "CRITICAL" -> DermoColors.CoralText; "HIGH" -> DermoColors.AmberText; else -> DermoColors.SageText
                            })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Voice note section
            if (scan?.voiceNotePath != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mic, null, tint = DermoColors.TealAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.timeline_voice_attached), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Record new voice note button
            NeuButton(
                onClick = {
                    if (recording) {
                        stopRecording()
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startRecording()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                containerColor = DermoColors.TealAccent.copy(alpha = 0.1f),
                contentColor = DermoColors.TealAccent,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(if (recording) Icons.Outlined.SelfImprovement else Icons.Outlined.Mic, null)
                Spacer(Modifier.width(8.dp))
                Text(if (recording) stringResource(R.string.timeline_record_stop) else stringResource(R.string.timeline_record_add))
            }
            if (recordingFailed) {
                Text(
                    stringResource(R.string.timeline_record_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = DermoColors.CoralText,
                )
            }
            Spacer(Modifier.height(16.dp))

            // Disclaimer
            Text(
                stringResource(R.string.timeline_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (deleteFailed) {
                Text(
                    stringResource(R.string.timeline_delete_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = DermoColors.CoralText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (scan != null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton(onBack, Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.timeline_back)) }
                NeuButton(
                    { showDeleteDialog = true },
                    Modifier.weight(1f),
                    containerColor = DermoColors.SoftCoral,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.timeline_delete)) }
            }
        }
    }
}
