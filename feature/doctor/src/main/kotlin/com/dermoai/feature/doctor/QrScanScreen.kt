package com.dermoai.feature.doctor

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.feature.doctor.invite.InviteCodes
import com.dermoai.feature.doctor.invite.QrScanAnalyzer
import java.util.concurrent.Executors

/**
 * The patient's fallback pairing path: point the camera at the doctor's QR
 * instead of typing an 8-character code.
 *
 * Deliberately thin. It does exactly one thing — decode a
 * `dermoai://invite/<CODE>` payload and hand the extracted code back — and
 * does not touch [com.dermoai.feature.doctor.RedeemInviteViewModel] itself.
 * The caller feeds the result through the same `onCodeChanged` /
 * `checkCode()` path a typed code goes through, so scanning and typing are
 * two entry points into one lookup, never two.
 *
 * @param onCodeScanned called once, on the main thread, with a normalised
 *   8-character code. The caller is responsible for stopping the scan (e.g.
 *   by leaving this composable) — analysis keeps running otherwise.
 * @param onCancel the patient chose to type the code instead, or backed out.
 */
@Composable
fun QrScanScreen(
    onCodeScanned: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (hasPermission) {
            QrScanner(onCodeScanned = onCodeScanned, onCancel = onCancel)
        } else {
            PermissionRationale(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun QrScanner(
    onCodeScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // A code is only ever reported once: `armed` flips false the instant a
    // decode succeeds, so a camera that is still bound while the caller is
    // navigating away does not fire a second, stale callback.
    var armed by remember { mutableStateOf(true) }
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    // Owned by this composable's lifetime, not by AndroidView's factory (which
    // only ever runs once per instance) — so it can be shut down on dispose
    // instead of leaking a thread every time this screen is entered.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(
                        analysisExecutor,
                        QrScanAnalyzer(
                            enabled = { armed },
                            onDecoded = { rawCode ->
                                val normalised = InviteCodes.normalise(rawCode)
                                if (armed && InviteCodes.isComplete(normalised)) {
                                    armed = false
                                    currentOnCodeScanned(normalised)
                                }
                            },
                        ),
                    )
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (_: Exception) {
                        // A camera bind can fail on a device already using the
                        // camera elsewhere. The scanner just stays blank; the
                        // "type it instead" button below is always available.
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            NeuSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.doctor_redeem_scan_hint),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = DermoColors.Slate,
                )
            }
            NeuButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.doctor_redeem_scan_cancel))
            }
        }
    }
}

@Composable
private fun PermissionRationale(onGrant: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.QrCodeScanner,
            null,
            modifier = Modifier.size(48.dp),
            tint = DermoColors.Slate,
        )
        Text(
            stringResource(R.string.doctor_redeem_scan_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        NeuButton(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
            containerColor = DermoColors.Teal,
        ) {
            Text(stringResource(R.string.doctor_redeem_scan_grant))
        }
        NeuButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.doctor_redeem_scan_cancel))
        }
    }
}
