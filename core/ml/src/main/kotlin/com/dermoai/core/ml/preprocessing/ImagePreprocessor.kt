package com.dermoai.core.ml.preprocessing

import android.graphics.Bitmap
import android.graphics.Matrix
import com.dermoai.core.domain.model.ModelConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resizes and normalizes camera/gallery images for ConvNeXt TFLite input.
 */
@Singleton
class ImagePreprocessor @Inject constructor() {

    fun prepareInput(bitmap: Bitmap, config: ModelConfig): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, config.inputWidth, config.inputHeight, true)
        val buffer = ByteBuffer.allocateDirect(4 * config.inputWidth * config.inputHeight * config.inputChannels)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(config.inputWidth * config.inputHeight)
        resized.getPixels(pixels, 0, config.inputWidth, 0, 0, config.inputWidth, config.inputHeight)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            buffer.putFloat((r - config.normalizeMean[0]) / config.normalizeStd[0])
            buffer.putFloat((g - config.normalizeMean[1]) / config.normalizeStd[1])
            buffer.putFloat((b - config.normalizeMean[2]) / config.normalizeStd[2])
        }
        if (resized !== bitmap) resized.recycle()
        buffer.rewind()
        return buffer
    }

    fun horizontalFlip(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}