package com.dermoai.feature.doctor.invite

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Decodes QR codes out of CameraX preview frames, on a background thread.
 *
 * Scoped to QR only (not a general barcode reader) and to the invite deep
 * link specifically — [onDecoded] only fires for a payload starting with
 * [InviteCodes.DEEP_LINK_PREFIX], so a patient's camera pointed at an
 * unrelated QR code (a wifi sticker, a poster) is silently ignored rather
 * than producing a confusing "invalid code" bounce.
 *
 * @param onDecoded called on whatever thread CameraX's analysis executor
 *   runs on — callers must hop back to the main thread themselves. Stops
 *   being invoked once [enabled] returns false, which the screen sets after
 *   the first successful scan so a still-open camera does not keep firing
 *   into a screen that has already navigated on.
 */
class QrScanAnalyzer(
    private val enabled: () -> Boolean,
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    // QRCodeReader is not documented thread-safe for concurrent decodes, but
    // CameraX guarantees a single analysis frame in flight at a time on the
    // analyzer's executor, so one instance reused across frames is safe and
    // avoids re-allocating its internal decoder state 30 times a second.
    private val reader = QRCodeReader()

    override fun analyze(image: ImageProxy) {
        if (!enabled()) {
            image.close()
            return
        }
        try {
            val payload = decode(image)
            if (payload != null && payload.startsWith(InviteCodes.DEEP_LINK_PREFIX)) {
                onDecoded(payload.removePrefix(InviteCodes.DEEP_LINK_PREFIX))
            }
        } finally {
            // Must run every time, success or failure — CameraX stalls the
            // analysis stream until the frame is released.
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        // Plane 0 of YUV_420_888 is the full-resolution luma channel, which is
        // exactly what a luminance-based reader needs — no colour conversion,
        // no copy of the chroma planes.
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val source = PlanarYUVLuminanceSource(
            bytes,
            plane.rowStride,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decode(bitmap).text
        } catch (_: NotFoundException) {
            null
        } catch (_: ChecksumException) {
            null
        } catch (_: FormatException) {
            null
        } finally {
            reader.reset()
        }
    }
}
