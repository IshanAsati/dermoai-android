package com.dermoai.feature.doctor.invite

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders an invite deep link to a QR bitmap, entirely on-device.
 *
 * Local generation rather than an image service is not an optimisation: the
 * code is a credential that grants a clinician sight of someone's medical
 * photos, and sending it to a third party to be drawn would leak it to a party
 * that has no business holding it. `zxing:core` is pure Java and adds no
 * permissions.
 *
 * Generation only. There is deliberately no scanner here — a camera reader is a
 * separate feature with its own permission story, and the doctor screen shows a
 * code the patient can equally well type.
 */
object QrCodes {

    /**
     * @param sizePx square edge in pixels. Callers should pass the measured
     *   layout size, not a constant, so the matrix is rendered at the density
     *   it will be displayed at rather than upscaled into blur.
     * @return the bitmap, or null if encoding failed. Null rather than a throw
     *   because a missing QR must degrade to "read the code out loud", never to
     *   a crash mid-consultation.
     */
    fun encode(
        content: String,
        sizePx: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
    ): Bitmap? = runCatching {
        val hints = mapOf(
            // M tolerates ~15% damage — enough for a phone screen photographed
            // off another phone screen, without inflating the module count so
            // far that the code stops scanning at small sizes.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            // The quiet zone is drawn by the surrounding padding in Compose, so
            // the built-in margin is trimmed to the spec minimum.
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val edge = sizePx.coerceAtLeast(MIN_EDGE_PX)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, edge, edge, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = if (matrix[x, y]) foreground else background
            }
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** Below this the writer starts refusing to fit the matrix at all. */
    private const val MIN_EDGE_PX = 96
}
