package com.dermoai.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.dermoai.core.camera.CameraCaptureManager
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.database.entity.UserProfileDetailsEntity
import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.ml.SkinInferenceEngine
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.rules.RuleAdjustment
import com.dermoai.core.domain.rules.RuleBasedFilterEngine
import com.dermoai.core.domain.rules.SkinProfile
import com.dermoai.core.domain.severity.SeverityMessageEngine
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.components.OutlinedNeuButton
import com.dermoai.core.ui.theme.DermoColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val cameraManager: CameraCaptureManager,
    private val inferenceEngine: SkinInferenceEngine,
    private val skinScanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
    private val userProfileDetailsDao: UserProfileDetailsDao,
    private val ruleEngine: RuleBasedFilterEngine,
) : ViewModel() {

    private val imageQualityChecker = ImageQualityChecker()

    var capturedPhotoPath by mutableStateOf<String?>(null)
        private set
    var inferenceState by mutableStateOf<InferenceUiState>(InferenceUiState.Idle)
        private set

    var inferenceResult by mutableStateOf<InferenceResult?>(null)
        private set

    /** Rules the demographic filter applied to this result (shown in the UI). */
    var ruleAdjustments by mutableStateOf<List<RuleAdjustment>>(emptyList())
        private set

    /** True when the result should suggest visiting a dermatologist (drives the consult CTA). */
    var referralFlagged by mutableStateOf(false)
        private set

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var previewView: PreviewView? = null

    fun onPreviewReady(view: PreviewView, lifecycleOwner: LifecycleOwner) {
        if (previewView === view) return
        previewView = view
        cameraManager.bind(lifecycleOwner, view, cameraExecutor)
    }

    fun toggleFlash() {
        cameraManager.toggleFlash()
    }

    suspend fun capturePhoto(): Result<String> {
        val result = cameraManager.capture()
        result.onSuccess { path ->
            capturedPhotoPath = path
            Log.d("ScanViewModel", "Photo saved: $path")
        }
        return result
    }

    fun setPhotoPath(path: String) {
        capturedPhotoPath = path
    }

    /**
     * Runs the image quality check on a saved photo. The checker decodes a
     * downsampled copy (never the full-resolution bitmap) and fails open on
     * unreadable files, so the existing inference error state handles those.
     */
    suspend fun assessImage(photoPath: String): ImageQualityReport = withContext(Dispatchers.Default) {
        imageQualityChecker.assessFile(photoPath)
    }

    fun cropAndSave(sourcePath: String, rect: CropRect, rotation: Float): String {
        val src = decodeUpright(sourcePath, MAX_CROP_PX)
            ?: throw IllegalArgumentException("Failed to decode image")
        val bw = src.width.toFloat()
        val bh = src.height.toFloat()
        val left = (rect.offsetX * bw).toInt().coerceIn(0, src.width - 1)
        val top = (rect.offsetY * bh).toInt().coerceIn(0, src.height - 1)
        val w = (rect.width * bw).toInt().coerceAtMost(src.width - left).coerceAtLeast(1)
        val h = (rect.height * bh).toInt().coerceAtMost(src.height - top).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(src, left, top, w, h)
        val rotated = if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
        } else cropped
        val outFile = File(sourcePath).let { f -> File(f.parent, "cropped_${f.name}") }
        FileOutputStream(outFile).use { out -> rotated.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        return outFile.absolutePath
    }

    suspend fun runInference(photoPath: String, userId: String) {
        inferenceState = InferenceUiState.Loading
        ruleAdjustments = emptyList()
        referralFlagged = false
        try {
            // Decode the full-resolution capture off the main thread — an in-memory
            // decode here can take hundreds of ms and ANR on low-memory devices.
            val bitmap = withContext(Dispatchers.IO) { decodeUpright(photoPath, MAX_CROP_PX) }
            if (bitmap == null) {
                inferenceState = InferenceUiState.Error("Failed to load image")
                return
            }
            if (!inferenceEngine.isReady) {
                when (val init = inferenceEngine.initialize()) {
                    is AppResult.Error -> {
                        inferenceState = InferenceUiState.Error("Model not available")
                        return
                    }
                    else -> {}
                }
            }
            when (val result = inferenceEngine.predict(bitmap)) {
                is AppResult.Success -> {
                    val profile = userProfileDetailsDao.getById(userId)?.toSkinProfile()
                    if (profile != null && profile.isPopulated) {
                        val filtered = ruleEngine.apply(result.data, profile)
                        inferenceResult = filtered.result
                        ruleAdjustments = filtered.adjustments
                        referralFlagged = filtered.referralFlagged
                    } else {
                        inferenceResult = result.data
                        referralFlagged = result.data.topPrediction.severity >= ConditionSeverity.HIGH
                    }
                    inferenceState = InferenceUiState.Ready
                }
                is AppResult.Error -> {
                    inferenceState = InferenceUiState.Error(result.message ?: "Unknown error")
                }
                else -> {}
            }
        } catch (e: Exception) {
            // Any unexpected failure must land on the error state — never leave
            // the spinner stuck in Loading forever.
            inferenceState = InferenceUiState.Error("Analysis failed")
        }
    }

    suspend fun saveScan(userId: String, photoPath: String) {
        val result = inferenceResult ?: return
        val scanId = "scan_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        skinScanDao.upsert(
            SkinScanEntity(
                id = scanId,
                userId = userId,
                imagePath = photoPath,
                thumbnailPath = photoPath,
                capturedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        )
        val predictions = result.allPredictions.mapIndexed { idx, pred ->
            ScanPredictionEntity(
                id = "${scanId}_pred_$idx",
                scanId = scanId,
                label = pred.label,
                labelCode = pred.code,
                confidence = pred.confidence,
                rank = idx + 1,
                concernBand = pred.severity.name,
                createdAt = now,
            )
        }
        predictionDao.upsertAll(predictions)
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.release()
        cameraExecutor.shutdown()
    }
}

sealed interface InferenceUiState {
    data object Idle : InferenceUiState
    data object Loading : InferenceUiState
    data object Ready : InferenceUiState
    data class Error(val message: String) : InferenceUiState
}

data class CropRect(
    val offsetX: Float = 0.1f,
    val offsetY: Float = 0.15f,
    val width: Float = 0.8f,
    val height: Float = 0.7f,
)

/** Longest edge kept when decoding for on-screen preview / for the crop source. */
private const val MAX_PREVIEW_PX = 1280
private const val MAX_CROP_PX = 2048

/**
 * Decodes [path] downscaled to roughly [maxDimension] px on its longest edge (subsampling
 * is limited to powers of two, so the result is never *smaller* than that), with any EXIF
 * orientation baked in — [BitmapFactory] ignores EXIF, which otherwise leaves gallery and
 * camera photos sideways. A rotated photo reaches the model rotated, and the resulting
 * misclassification looks like a model fault rather than a decoding one.
 */
private fun decodeUpright(path: String, maxDimension: Int): Bitmap? {
    if (path.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }) ?: return null

    val matrix = exifMatrix(path) ?: return decoded
    return runCatching {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    }.getOrDefault(decoded)
}

/** Transform that puts [path] upright, or null when no correction is needed. */
private fun exifMatrix(path: String): Matrix? {
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    return Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            else -> return null
        }
    }
}

@Composable
fun ScanEntryScreen(
    onNavigateToCapture: () -> Unit,
    onPhotoPicked: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionDenied by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionDenied = false
            onNavigateToCapture()
        } else {
            permissionDenied = true
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { imgUri ->
            val dest = File(context.filesDir, "photos").also { it.mkdirs() }
            val outPath = File(dest, "gallery_${System.currentTimeMillis()}.jpg")
            // Stream the (potentially large) gallery image off the main thread.
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(imgUri)?.use { i ->
                            FileOutputStream(outPath).use { o -> i.copyTo(o) }
                        }
                        outPath.length() > 0
                    }.getOrDefault(false)
                }
                if (ok) {
                    galleryError = false
                    onPhotoPicked(outPath.absolutePath)
                } else {
                    galleryError = true
                }
            }
        }
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Scan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.weight(1f))
        NeuSurface(
            modifier = Modifier.size(80.dp),
            style = NeuSurfaceStyle.Inset,
            shape = CircleShape,
            color = DermoColors.TealAccent.copy(alpha = 0.1f),
        ) {
            Icon(Icons.Outlined.CameraAlt, null, Modifier.padding(20.dp), tint = DermoColors.TealAccent)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (hasPermission) "Ready to scan" else "Camera Permission Needed",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (hasPermission) "Use the camera or pick a photo from your gallery."
            else "DermoAI needs camera access for skin scanning. Photos stay on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeuButton(
                onClick = {
                    if (hasPermission) onNavigateToCapture()
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                containerColor = DermoColors.TealAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.CameraAlt, null, Modifier.padding(end = 8.dp))
                Text(if (hasPermission) "Open Camera" else "Grant Camera Access")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedNeuButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.padding(end = 8.dp))
                Text("Upload from Gallery")
            }
        }
        if (permissionDenied) {
            Text(
                "Camera permission denied. Enable it in system settings to scan with the camera, or upload a photo from your gallery.",
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.CoralText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
            )
        }
        if (galleryError) {
            Text(
                "Couldn't load that photo. Please pick another image.",
                style = MaterialTheme.typography.bodySmall,
                color = DermoColors.CoralText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1.5f))
    }
}

@Composable
fun ScanCaptureScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onPhotoCaptured: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var flashMode by remember { mutableIntStateOf(0) }
    var captureError by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView -> viewModel.onPreviewReady(previewView, lifecycleOwner) },
        )

        // Subtle focus brackets in center — no crop box, just a guide
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val s = size.width.coerceAtMost(size.height) * 0.65f
            val half = s / 2
            val gap = 24f
            val stroke = 2.5f
            val color = Color.White.copy(alpha = 0.7f)
            // Top-left corner
            drawLine(color, Offset(cx - half, cy - half + gap), Offset(cx - half, cy - half), stroke)
            drawLine(color, Offset(cx - half + gap, cy - half), Offset(cx - half, cy - half), stroke)
            // Top-right
            drawLine(color, Offset(cx + half, cy - half + gap), Offset(cx + half, cy - half), stroke)
            drawLine(color, Offset(cx + half - gap, cy - half), Offset(cx + half, cy - half), stroke)
            // Bottom-left
            drawLine(color, Offset(cx - half, cy + half - gap), Offset(cx - half, cy + half), stroke)
            drawLine(color, Offset(cx - half + gap, cy + half), Offset(cx - half, cy + half), stroke)
            // Bottom-right
            drawLine(color, Offset(cx + half, cy + half - gap), Offset(cx + half, cy + half), stroke)
            drawLine(color, Offset(cx + half - gap, cy + half), Offset(cx + half, cy + half), stroke)
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).align(Alignment.TopCenter),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TopPill(onClick = onBack, icon = Icons.AutoMirrored.Outlined.ArrowBack, desc = "Back")
                TopPill(
                    // Camera cycle is AUTO→OFF→ON; keep the icon in sync.
                    onClick = { flashMode = (flashMode + 1) % 3; viewModel.toggleFlash() },
                    icon = when (flashMode) {
                        0 -> Icons.Outlined.FlashAuto; 1 -> Icons.Outlined.FlashOff; else -> Icons.Outlined.FlashOn
                    },
                    desc = "Flash",
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TipChip("Center skin area in brackets", Color.White)
            Spacer(Modifier.height(16.dp))
            captureError?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Box(Modifier.size(72.dp).clip(CircleShape).clickable {
                scope.launch {
                    captureError = null
                    viewModel.capturePhoto().onSuccess { path ->
                        onPhotoCaptured(path)
                    }.onFailure {
                        captureError = "Couldn't capture photo. Please try again."
                    }
                }
            }, contentAlignment = Alignment.Center) {
                Surface(Modifier.size(72.dp).clip(CircleShape), CircleShape,
                    color = Color.White.copy(alpha = 0.3f),
                    border = BorderStroke(3.dp, DermoColors.TealAccent),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(Modifier.size(56.dp).clip(CircleShape), CircleShape, color = Color.White) {
                            Icon(Icons.Outlined.Circle, "Capture", Modifier.padding(14.dp), tint = DermoColors.TealAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPill(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
    Surface(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, desc, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun TipChip(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun CropOverlay(
    cropRect: CropRect,
    rotation: Float,
    onCropChange: (CropRect) -> Unit,
    onRotate: (Float) -> Unit,
    modifier: Modifier,
    transparent: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        val density = LocalDensity.current

        Canvas(Modifier.fillMaxSize()) {
            val x = cropRect.offsetX * size.width
            val y = cropRect.offsetY * size.height
            val w = cropRect.width * size.width
            val h = cropRect.height * size.height

            if (!transparent) {
                drawRect(Color.Black.copy(alpha = 0.35f))
                drawRect(Color.Transparent, Offset(x, y), Size(w, h), blendMode = BlendMode.Clear)
            }

            // Dotted outline
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            drawRect(
                DermoColors.TealAccent,
                Offset(x, y),
                Size(w, h),
                style = Stroke(3f, pathEffect = dash),
            )

            // Corner L-shapes (solid, not dotted)
            val lLen = 28f
            val lThick = 3f
            drawLine(DermoColors.TealAccent, Offset(x, y), Offset(x + lLen, y), lThick)
            drawLine(DermoColors.TealAccent, Offset(x, y), Offset(x, y + lLen), lThick)
            drawLine(DermoColors.TealAccent, Offset(x + w, y), Offset(x + w - lLen, y), lThick)
            drawLine(DermoColors.TealAccent, Offset(x + w, y), Offset(x + w, y + lLen), lThick)
            drawLine(DermoColors.TealAccent, Offset(x, y + h), Offset(x + lLen, y + h), lThick)
            drawLine(DermoColors.TealAccent, Offset(x, y + h), Offset(x, y + h - lLen), lThick)
            drawLine(DermoColors.TealAccent, Offset(x + w, y + h), Offset(x + w - lLen, y + h), lThick)
            drawLine(DermoColors.TealAccent, Offset(x + w, y + h), Offset(x + w, y + h - lLen), lThick)
        }

        val handleSize = 48.dp
        val halfHandle = handleSize / 2

        fun pxX(fraction: Float) = with(density) { (fraction * boxW - halfHandle.value).dp.toPx() }.toInt()
        fun pxY(fraction: Float) = with(density) { (fraction * boxH - halfHandle.value).dp.toPx() }.toInt()

        // Drag handlers must read the CURRENT rect — pointerInput(Unit) keeps a
        // first-composition closure, so dragging would snap back on every event.
        val currentCropRect by rememberUpdatedState(cropRect)

        // Top-left handle
        Box(
            Modifier.offset { IntOffset(pxX(cropRect.offsetX), pxY(cropRect.offsetY)) }
                .size(handleSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / (boxW * density.density)
                        val dy = dragAmount.y / (boxH * density.density)
                        val newL = (currentCropRect.offsetX + dx).coerceAtLeast(0.01f)
                        val newT = (currentCropRect.offsetY + dy).coerceAtLeast(0.01f)
                        val newW = (currentCropRect.width - dx).coerceAtLeast(0.15f)
                        val newH = (currentCropRect.height - dy).coerceAtLeast(0.15f)
                        if (newL + newW <= 0.98f && newT + newH <= 0.98f) {
                            onCropChange(CropRect(newL, newT, newW, newH))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(Modifier.size(20.dp), CircleShape, color = Color.White, border = BorderStroke(2.dp, DermoColors.TealAccent)) {}
        }

        // Top-right handle
        Box(
            Modifier.offset { IntOffset(pxX(cropRect.offsetX + cropRect.width), pxY(cropRect.offsetY)) }
                .size(handleSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / (boxW * density.density)
                        val dy = dragAmount.y / (boxH * density.density)
                        val newT = (currentCropRect.offsetY + dy).coerceAtLeast(0.01f)
                        val newW = (currentCropRect.width + dx).coerceAtLeast(0.15f)
                        val newH = (currentCropRect.height - dy).coerceAtLeast(0.15f)
                        if (currentCropRect.offsetX + newW <= 0.98f && newT + newH <= 0.98f) {
                            onCropChange(CropRect(currentCropRect.offsetX, newT, newW, newH))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(Modifier.size(20.dp), CircleShape, color = Color.White, border = BorderStroke(2.dp, DermoColors.TealAccent)) {}
        }

        // Bottom-left handle
        Box(
            Modifier.offset { IntOffset(pxX(cropRect.offsetX), pxY(cropRect.offsetY + cropRect.height)) }
                .size(handleSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / (boxW * density.density)
                        val dy = dragAmount.y / (boxH * density.density)
                        val newL = (currentCropRect.offsetX + dx).coerceAtLeast(0.01f)
                        val newW = (currentCropRect.width - dx).coerceAtLeast(0.15f)
                        val newH = (currentCropRect.height + dy).coerceAtLeast(0.15f)
                        if (newL + newW <= 0.98f && currentCropRect.offsetY + newH <= 0.98f) {
                            onCropChange(CropRect(newL, currentCropRect.offsetY, newW, newH))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(Modifier.size(20.dp), CircleShape, color = Color.White, border = BorderStroke(2.dp, DermoColors.TealAccent)) {}
        }

        // Bottom-right handle
        Box(
            Modifier.offset { IntOffset(pxX(cropRect.offsetX + cropRect.width), pxY(cropRect.offsetY + cropRect.height)) }
                .size(handleSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / (boxW * density.density)
                        val dy = dragAmount.y / (boxH * density.density)
                        val newW = (currentCropRect.width + dx).coerceAtLeast(0.15f)
                        val newH = (currentCropRect.height + dy).coerceAtLeast(0.15f)
                        if (currentCropRect.offsetX + newW <= 0.98f && currentCropRect.offsetY + newH <= 0.98f) {
                            onCropChange(CropRect(currentCropRect.offsetX, currentCropRect.offsetY, newW, newH))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(Modifier.size(20.dp), CircleShape, color = Color.White, border = BorderStroke(2.dp, DermoColors.TealAccent)) {}
        }

        // Move whole box from center
        val centerSize = 80.dp
        val halfCenter = centerSize / 2
        fun cpxX(fraction: Float) = with(density) { (fraction * boxW - halfCenter.value).dp.toPx() }.toInt()
        fun cpxY(fraction: Float) = with(density) { (fraction * boxH - halfCenter.value).dp.toPx() }.toInt()
        Box(
            Modifier.offset {
                IntOffset(
                    cpxX(cropRect.offsetX + cropRect.width / 2),
                    cpxY(cropRect.offsetY + cropRect.height / 2),
                )
            }
                .size(centerSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / (boxW * density.density)
                        val dy = dragAmount.y / (boxH * density.density)
                        val newL = (currentCropRect.offsetX + dx).coerceAtLeast(0.01f)
                        val newT = (currentCropRect.offsetY + dy).coerceAtLeast(0.01f)
                        if (newL + currentCropRect.width <= 0.98f && newT + currentCropRect.height <= 0.98f) {
                            onCropChange(CropRect(newL, newT, currentCropRect.width, currentCropRect.height))
                        }
                    }
                },
        )

        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable { onRotate(-90f) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("L", style = MaterialTheme.typography.labelLarge) }
            }
            Surface(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable { onRotate(90f) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("R", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun ScanReviewScreen(
    photoPath: String,
    viewModel: ScanViewModel = hiltViewModel(),
    onUsePhoto: (String) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cropRect by remember { mutableStateOf(CropRect(0.15f, 0.2f, 0.7f, 0.5f)) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // Image quality gate: set when the cropped photo fails the quality check.
    var qualityWarning by remember { mutableStateOf<ImageQualityReport?>(null) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    // Guards against double-taps while the (fast) quality check is running.
    var assessing by remember { mutableStateOf(false) }

    fun confirmAndAnalyze(cropped: String) {
        qualityWarning = null
        pendingPhotoPath = null
        assessing = false
        onUsePhoto(cropped)
    }

    val nudge = 0.03f
    fun wider() = cropRect.copy(width = (cropRect.width + nudge).coerceAtMost(0.95f - cropRect.offsetX))
    fun narrower() = cropRect.copy(width = (cropRect.width - nudge).coerceAtLeast(0.15f))
    fun taller() = cropRect.copy(height = (cropRect.height + nudge).coerceAtMost(0.95f - cropRect.offsetY))
    fun shorter() = cropRect.copy(height = (cropRect.height - nudge).coerceAtLeast(0.15f))
    fun left() = cropRect.copy(offsetX = (cropRect.offsetX - nudge).coerceAtLeast(0.01f))
    fun right() = cropRect.copy(offsetX = (cropRect.offsetX + nudge).coerceAtMost(0.95f - cropRect.width))
    fun up() = cropRect.copy(offsetY = (cropRect.offsetY - nudge).coerceAtLeast(0.01f))
    fun down() = cropRect.copy(offsetY = (cropRect.offsetY + nudge).coerceAtMost(0.95f - cropRect.height))

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRetake) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retake")
            }
            Text(text = "Adjust Crop", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            // Must stay EXIF-aware in lockstep with cropAndSave: CropRect is fractional,
            // expressed in the coordinate space of whatever bitmap this screen displays.
            // If the preview stayed raw while the crop went upright, the box drawn over a
            // lesion would silently crop background on any rotated photo.
            val bmp = remember(photoPath) { if (photoPath.isNotEmpty()) decodeUpright(photoPath, MAX_PREVIEW_PX) else null }
            if (bmp != null) {
                Image(bmp.asImageBitmap(), "Captured photo", Modifier.fillMaxSize())
                CropOverlay(
                    cropRect = cropRect,
                    rotation = rotation,
                    onCropChange = { cropRect = it },
                    onRotate = { rotation = (rotation + it) % 360f },
                    modifier = Modifier.fillMaxSize(),
                    transparent = true,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Photo not available", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Nudge buttons for touchpad users
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NudgeButton("Reset") { cropRect = CropRect(0.05f, 0.05f, 0.9f, 0.9f) }
                NudgeButton("Left") { cropRect = left() }
                NudgeButton("Right") { cropRect = right() }
                NudgeButton("Up") { cropRect = up() }
                NudgeButton("Down") { cropRect = down() }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NudgeButton("Wider") { cropRect = wider() }
                NudgeButton("Narrow") { cropRect = narrower() }
                NudgeButton("Taller") { cropRect = taller() }
                NudgeButton("Shorter") { cropRect = shorter() }
                NudgeButton("Rot L") { rotation = (rotation - 90f) % 360f }
                NudgeButton("Rot R") { rotation = (rotation + 90f) % 360f }
            }
        }

        MedicalDisclaimerBar()

        qualityWarning?.let { report ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                color = DermoColors.Bloom,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Warning,
                            null,
                            tint = DermoColors.AmberText,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Improve photo quality",
                            style = MaterialTheme.typography.titleSmall,
                            color = DermoColors.AmberText,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        report.issues.joinToString(" · ") { it.message },
                        style = MaterialTheme.typography.bodyMedium,
                        color = DermoColors.AmberText,
                    )
                    Text(
                        report.issues.first().guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = DermoColors.AmberText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeuButton(
                            onClick = { pendingPhotoPath?.let { confirmAndAnalyze(it) } },
                            modifier = Modifier.weight(1f),
                            containerColor = DermoColors.TealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Text("Analyze anyway") }
                        OutlinedNeuButton(
                            onClick = {
                                qualityWarning = null
                                pendingPhotoPath = null
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Adjust crop") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedNeuButton(onRetake, Modifier.weight(1f)) { Text("Retake") }
            NeuButton(
                onClick = {
                    if (assessing) return@NeuButton
                    assessing = true
                    val cropped = try {
                        viewModel.cropAndSave(photoPath, cropRect, rotation)
                    } catch (_: Exception) { photoPath }
                    scope.launch {
                        try {
                            val report = viewModel.assessImage(cropped)
                            if (report.issues.isEmpty()) {
                                confirmAndAnalyze(cropped)
                            } else {
                                pendingPhotoPath = cropped
                                qualityWarning = report
                                assessing = false
                            }
                        } catch (_: Exception) {
                            // Never trap the user on a stuck button — fall back to analyzing.
                            assessing = false
                            confirmAndAnalyze(cropped)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !assessing,
                containerColor = DermoColors.TealAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(if (assessing) "Checking…" else "Analyze") }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    NeuSurface(
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
fun ScanResultsScreen(
    photoPath: String,
    userId: String,
    viewModel: ScanViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSavedToTimeline: () -> Unit,
    onFindDermatologist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = viewModel.inferenceResult
    val state = viewModel.inferenceState
    val scope = rememberCoroutineScope()

    LaunchedEffect(photoPath) {
        if (state is InferenceUiState.Idle) {
            viewModel.runInference(photoPath, userId)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Educational Estimates")
        MedicalDisclaimerBar()

        Box(Modifier.weight(1f)) {
            when (state) {
                is InferenceUiState.Loading -> {
                    Column(
                        Modifier.fillMaxSize().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Analyzing patterns for education…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is InferenceUiState.Error -> {
                    Column(
                        Modifier.fillMaxSize().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Analysis unavailable",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        NeuButton(
                            onClick = { scope.launch { viewModel.runInference(photoPath, userId) } },
                            containerColor = DermoColors.TealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Text("Retry") }
                    }
                }
                else -> {
                    if (result != null) {
                        ResultsContent(
                            photoPath = photoPath,
                            result = result,
                            adjustments = viewModel.ruleAdjustments,
                            showDoctorCta = viewModel.referralFlagged,
                            onFindDermatologist = onFindDermatologist,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Preparing analysis…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedNeuButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Retake") }
            NeuButton(
                onClick = {
                    scope.launch {
                        runCatching { viewModel.saveScan(userId, photoPath) }
                        onSavedToTimeline()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = result != null,
                containerColor = DermoColors.TealAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text("Save to Timeline") }
        }
    }
}

@Composable
private fun ResultsContent(
    photoPath: String,
    result: InferenceResult,
    adjustments: List<RuleAdjustment>,
    showDoctorCta: Boolean,
    onFindDermatologist: () -> Unit,
) {
    val bmp = remember(photoPath) { decodeUpright(photoPath, MAX_PREVIEW_PX) }
    val scrollState = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), "Scanned area", Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)))
        }
        Spacer(Modifier.height(20.dp))

        // Top prediction header
        Text("Top estimate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(result.topPrediction.label, style = MaterialTheme.typography.headlineSmall)
        ConfidenceBar(result.topPrediction.confidence, Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 8.dp))

        // Concern band
        Spacer(Modifier.height(16.dp))
        ConcernBandChip(result.topPrediction.severity)

        // Severity-aware, probability-aware summary line
        Spacer(Modifier.height(16.dp))
        SeveritySummaryLine(result)

        // Consult CTA — shown when the result (or the rule layer) says to see a doctor
        if (showDoctorCta) {
            Spacer(Modifier.height(12.dp))
            NeuButton(
                onClick = onFindDermatologist,
                modifier = Modifier.fillMaxWidth(),
                containerColor = DermoColors.TealAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.severity_consult_cta))
            }
        }

        // Rule-based filter notes (age / skin / gender / sun exposure)
        if (adjustments.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                adjustments.forEach { adj ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = DermoColors.TealAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            adj.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // All predictions ranked
        Text("All estimates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        result.allPredictions.forEachIndexed { idx, pred ->
            val rowColor = if (idx % 2 == 0) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow
            NeuSurface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = rowColor,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${idx + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(pred.label, style = MaterialTheme.typography.bodyMedium)
                        ConfidenceBar(pred.confidence)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "This app is an educational and awareness tool and does not replace a dermatologist. " +
                "Estimates are for education only — not a diagnosis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConfidenceBar(confidence: Float, modifier: Modifier = Modifier) {
    val barColor = when {
        confidence >= 0.7f -> DermoColors.HealthGreen
        confidence >= 0.4f -> DermoColors.WarmAmber
        else -> DermoColors.SoftCoral
    }
    Row(modifier = modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(confidence.coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(3.dp)).background(barColor),
        )
        Box(
            Modifier.weight((1f - confidence).coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(3.dp)).background(barColor.copy(alpha = 0.15f)),
        )
    }
}

@Composable
private fun ConcernBandChip(severity: ConditionSeverity) {
    val (color, text) = when (severity) {
        ConditionSeverity.LOW -> DermoColors.HealthGreen to "Low concern — educational information"
        ConditionSeverity.MEDIUM -> DermoColors.WarmAmber to "Monitor — possible common condition"
        ConditionSeverity.HIGH -> DermoColors.WarmAmber to "Elevated — consider consulting a dermatologist"
        ConditionSeverity.CRITICAL -> DermoColors.SoftCoral to "Urgent — please consult a clinician"
    }
    NeuSurface(
        modifier = Modifier.padding(top = 4.dp),
        style = NeuSurfaceStyle.Inset,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

/**
 * The severity-aware, probability-aware summary line ("say a line depending on
 * how serious the condition is and the probabilities"). Built from the engine
 * payload so tier selection stays unit-testable; phrasing is localized.
 */
@Composable
private fun SeveritySummaryLine(result: InferenceResult) {
    val engine = remember { SeverityMessageEngine() }
    val message = remember(result) { engine.build(result) }

    val summary = stringResource(
        when (message.tier) {
            ConditionSeverity.LOW -> R.string.severity_summary_low
            ConditionSeverity.MEDIUM -> R.string.severity_summary_medium
            ConditionSeverity.HIGH -> R.string.severity_summary_high
            ConditionSeverity.CRITICAL -> R.string.severity_summary_critical
        },
        message.conditionLabel,
        message.confidencePercent,
    )

    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = when (message.tier) {
            ConditionSeverity.CRITICAL -> DermoColors.SoftCoral.copy(alpha = 0.08f)
            ConditionSeverity.HIGH -> DermoColors.WarmAmber.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            message.runnerUpLabel?.let { runnerUp ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.severity_runner_up, runnerUp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Maps the persisted profile row to the rule engine's domain input. */
private fun UserProfileDetailsEntity.toSkinProfile() = SkinProfile(
    age = age,
    gender = gender,
    skinType = skinType,
    skinTone = skinTone,
    sunExposure = sunExposure,
)
