package com.dermoai.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A capture was requested while another one is still running; callers should just ignore the tap. */
class CaptureBusyException : IllegalStateException("A capture is already in progress")

/**
 * CameraX capture coordinator — creates use cases, manages flash, saves images.
 *
 * This is an app-wide singleton, but the scan screens that use it come and go, so it is
 * written to survive that:
 *  - it remembers which screen owns the camera and ignores a late [release] from a
 *    previous screen (which used to kill the *new* screen's camera: live preview, dead shutter);
 *  - a binding that failed or was lost is re-created on demand, so [capture] heals itself;
 *  - a failed capture is retried once from a fresh camera session, with a timeout so the
 *    shutter can never hang;
 *  - overlapping taps are rejected instead of queued.
 */
@Singleton
class CameraCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var imageCapture: ImageCapture? = null
    private var boundExecutor: ExecutorService? = null

    // Weak: this is a singleton and must never keep a destroyed screen alive.
    private var ownerRef: WeakReference<LifecycleOwner>? = null
    private var previewRef: WeakReference<PreviewView>? = null
    @Volatile private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF

    private val bindMutex = Mutex()
    private val capturing = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Bind CameraX preview + capture to a lifecycle and [PreviewView]. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        executor: ExecutorService,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    ) {
        boundExecutor = executor
        ownerRef = WeakReference(lifecycleOwner)
        previewRef = WeakReference(previewView)
        this.lensFacing = lensFacing
        scope.launch { rebind() }
    }

    /**
     * (Re)creates the preview and capture use cases and binds them. Serialised, so two binds
     * can never interleave. Returns false (and leaves [imageCapture] null) if binding failed.
     */
    private suspend fun rebind(): Boolean = bindMutex.withLock {
        val owner = ownerRef?.get()
        val view = previewRef?.get()
        if (owner == null || view == null || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            Log.w(TAG, "Nothing to bind to (screen gone)")
            imageCapture = null
            return@withLock false
        }
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val ok = try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    // Latency-first: quality-first mode can stall in indoor light, and the model only
                    // looks at a 224 px image anyway.
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        // Front cameras have no flash unit; a stored ON/AUTO would only confuse CameraX.
                        .setFlashMode(if (isFrontCamera) ImageCapture.FLASH_MODE_OFF else flashMode)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        owner,
                        CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview,
                        capture,
                    )
                    // Only publish the capture use case once it is really bound.
                    imageCapture = capture
                    Log.d(TAG, "Camera bound successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                    imageCapture = null
                    false
                }
                if (cont.isActive) cont.resume(ok)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * Cycles the flash (OFF -> ON -> AUTO -> OFF) and returns the new CameraX mode
     * (AUTO = 0, ON = 1, OFF = 2) so the UI can show exactly what the camera is doing.
     */
    fun toggleFlash(): Int {
        if (isFrontCamera) return flashMode // no flash on the selfie camera; keep the rear setting
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        return flashMode
    }

    /** True while the selfie (front) camera is in use. */
    val isFrontCamera: Boolean get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    /**
     * Flips between the rear and the front camera and returns whether the front camera is now
     * in use. Does nothing (and returns the current state) if the device has no other camera or
     * a photo is being taken right now.
     */
    fun switchCamera(): Boolean {
        if (capturing.get()) return isFrontCamera
        val target = if (isFrontCamera) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        if (!hasCamera(target)) return isFrontCamera
        lensFacing = target
        imageCapture = null // capture() waits for the rebind instead of using the old camera
        scope.launch { rebind() }
        return isFrontCamera
    }

    private fun hasCamera(lens: Int): Boolean = runCatching {
        val future = ProcessCameraProvider.getInstance(context)
        future.isDone && future.get().hasCamera(CameraSelector.Builder().requireLensFacing(lens).build())
    }.getOrDefault(false)

    /** Capture a photo to app-private storage. Returns the file path. */
    suspend fun capture(): Result<String> {
        if (!capturing.compareAndSet(false, true)) return Result.failure(CaptureBusyException())
        try {
            var last: Throwable = IllegalStateException("Camera not bound")
            for (attempt in 1..MAX_ATTEMPTS) {
                val ic = imageCapture ?: run { rebind(); imageCapture }
                if (ic == null) {
                    last = IllegalStateException("Camera could not be started")
                    delay(RETRY_DELAY_MS)
                    continue
                }
                val result = takeOnce(ic)
                if (result.isSuccess) return result
                last = result.exceptionOrNull() ?: last
                Log.w(TAG, "Capture attempt $attempt failed: ${last.message}")
                // Retry from a clean camera session rather than reusing whatever just failed.
                imageCapture = null
                delay(RETRY_DELAY_MS)
            }
            return Result.failure(last)
        } finally {
            capturing.set(false)
        }
    }

    private suspend fun takeOnce(ic: ImageCapture): Result<String> {
        val photoFile = createPhotoFile()
        val deferred = CompletableDeferred<Result<String>>()
        try {
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(photoFile)
                    // Selfies are saved mirrored, matching what the live preview showed.
                    .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = isFrontCamera })
                    .build(),
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
        } catch (e: Exception) { // e.g. "Not bound to a Camera"
            photoFile.delete()
            return Result.failure(e)
        }
        val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { deferred.await() }
            ?: Result.failure(TimeoutException("Capture timed out"))
        if (result.isFailure) photoFile.delete()
        return result
    }

    /**
     * Release camera resources. Only the screen that currently owns the camera may do this:
     * a late release from a screen that was already replaced must not tear down its successor.
     */
    fun release(executor: ExecutorService) {
        if (boundExecutor !== executor) return
        imageCapture = null
        boundExecutor = null
        ownerRef = null
        previewRef = null
    }

    /** Generate a unique file path in app-private storage. */
    private fun createPhotoFile(): File {
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        return File(dir, "scan_${System.currentTimeMillis()}.jpg")
    }

    companion object {
        private const val TAG = "CameraCaptureManager"
        private const val MAX_ATTEMPTS = 2
        private const val CAPTURE_TIMEOUT_MS = 10_000L
        private const val RETRY_DELAY_MS = 300L
    }
}
