package com.dermoai.core.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CameraX capture coordinator — creates use cases, manages flash, saves images.
 */
@Singleton
class CameraCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null

    /** Bind CameraX preview + capture to a lifecycle and [PreviewView]. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        executor: ExecutorService,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    ) {
        cameraExecutor = executor
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                )
                Log.d(TAG, "Camera bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Toggle flash between ON, OFF, and AUTO. Returns the new mode. */
    fun toggleFlash(): Int {
        val ic = imageCapture ?: return ImageCapture.FLASH_MODE_OFF
        val next = when (ic.flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        ic.flashMode = next
        return next
    }

    /** Capture a photo to app-private storage. Returns the file path. */
    suspend fun capture(): Result<String> {
        val ic = imageCapture ?: return Result.failure(IllegalStateException("Camera not bound"))
        val deferred = CompletableDeferred<Result<String>>()
        val photoFile = createPhotoFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        ic.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    deferred.complete(Result.success(photoFile.absolutePath))
                }
                override fun onError(exc: ImageCaptureException) {
                    deferred.complete(Result.failure(exc))
                }
            },
        )
        return deferred.await()
    }

    /** Release camera resources. */
    fun release() {
        imageCapture = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    /** Generate a unique file path in app-private storage. */
    private fun createPhotoFile(): File {
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        return File(dir, "scan_${System.currentTimeMillis()}.jpg")
    }

    companion object {
        private const val TAG = "CameraCaptureManager"
    }
}
