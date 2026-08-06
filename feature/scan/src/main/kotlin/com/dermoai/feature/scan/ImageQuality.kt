package com.dermoai.feature.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Image quality check — runs on the cropped photo right before inference
 * (see AGENTS.md "Image quality check (capture gate)" for the spec).
 *
 * The check is deliberately a *soft gate*: poor quality never hard-blocks
 * the user — it surfaces an amber warning with guidance and an
 * "Analyze anyway" path, matching the app's screening-aid philosophy.
 */
enum class ImageQualityIssue(val message: String, val guidance: String) {
    BLURRY(
        "Photo looks blurry",
        "Hold the camera steady and retake, or pick a sharper photo.",
    ),
    TOO_DARK(
        "Photo is too dark",
        "Move to better lighting or use the flash, then retake.",
    ),
    TOO_BRIGHT(
        "Photo is overexposed",
        "Reduce glare or move out of direct light, then retake.",
    ),
    TOO_SMALL(
        "Photo is too small",
        "Zoom in closer so the area of concern fills the frame.",
    ),
}

/** Tuning knobs for the check. Defaults live here so tests can probe boundaries. */
data class QualityThresholds(
    /** Blur = variance of the 3x3 Laplacian on the ≤ [maxAnalysisEdge]px grayscale. */
    val blurVariance: Float = 80f,
    /** Mean grayscale luminance 0..255 below which the photo counts as too dark. */
    val darkLuminance: Float = 50f,
    /** Mean grayscale luminance above which the photo counts as overexposed. */
    val brightLuminance: Float = 215f,
    /** Smallest allowed crop edge in px — the model input is 224x224. */
    val minDimensionPx: Int = 480,
    /** Analysis is run on a copy downscaled to this max edge for speed + stable thresholds. */
    val maxAnalysisEdge: Int = 256,
)

data class ImageQualityReport(
    val minDimension: Int,
    /** Mean grayscale luminance 0..255. */
    val luminance: Float,
    /** Variance of the 3x3 Laplacian on the downscaled grayscale (0 = no edges = blurry). */
    val blurScore: Float,
    val issues: List<ImageQualityIssue>,
)

/** Pure pixel math — no android.graphics types, unit-testable on the JVM. */
object ImageQualityMetrics {

    /** Rec.601 luma of an ARGB pixel, 0..255. */
    fun luminance(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    fun meanLuminance(gray: IntArray): Float {
        if (gray.isEmpty()) return 0f
        var sum = 0L
        for (v in gray) sum += v
        return sum.toFloat() / gray.size
    }

    /**
     * Variance of the 3x3 Laplacian response over interior pixels.
     * A flat/blurry image yields ~0; sharp edges push it up. Returns 0 for
     * images narrower than 3px in either dimension.
     */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3 || gray.size != width * height) return 0f
        var sum = 0L
        var sumSq = 0L
        var n = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val center = row + x
                val response = gray[center - width] + gray[center + width] +
                    gray[center - 1] + gray[center + 1] - 4 * gray[center]
                sum += response
                sumSq += response.toLong() * response
                n++
            }
        }
        if (n == 0) return 0f
        val mean = sum.toDouble() / n
        return ((sumSq.toDouble() / n) - mean * mean).toFloat().coerceAtLeast(0f)
    }
}

/** Pure decision logic — maps metrics to issues. Unit-testable on the JVM. */
object ImageQualityDecision {

    fun issues(
        minDimension: Int,
        meanLuminance: Float,
        blurScore: Float,
        thresholds: QualityThresholds = QualityThresholds(),
    ): List<ImageQualityIssue> = buildList {
        if (minDimension < thresholds.minDimensionPx) add(ImageQualityIssue.TOO_SMALL)
        if (meanLuminance < thresholds.darkLuminance) add(ImageQualityIssue.TOO_DARK)
        if (meanLuminance > thresholds.brightLuminance) add(ImageQualityIssue.TOO_BRIGHT)
        if (blurScore < thresholds.blurVariance) add(ImageQualityIssue.BLURRY)
    }
}

/**
 * Bitmap/path-facing entry point. Decodes the photo *downsampled* (never
 * allocates the full-resolution bitmap — see the ANR note in ScanScreens.kt),
 * then runs the metrics and the decision on a ≤ [QualityThresholds.maxAnalysisEdge]px
 * grayscale. The decoded and downscaled copies are always recycled.
 */
class ImageQualityChecker(
    private val thresholds: QualityThresholds = QualityThresholds(),
) {

    /**
     * Assesses a photo file. Fail-open: an unreadable file returns a report
     * with no issues (the inference error state handles it downstream).
     */
    fun assessFile(path: String): ImageQualityReport {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val minDimension = minOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = inSampleSizeFor(bounds.outWidth, bounds.outHeight, thresholds.maxAnalysisEdge)
        }
        val bitmap = BitmapFactory.decodeFile(path, options)
            ?: return ImageQualityReport(minDimension, 0f, 0f, emptyList())
        return try {
            assess(bitmap, minDimension)
        } finally {
            bitmap.recycle()
        }
    }

    private fun assess(bitmap: Bitmap, originalMinDimension: Int): ImageQualityReport {
        val scaled = scaleToMaxEdge(bitmap, thresholds.maxAnalysisEdge)
        try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            val gray = IntArray(pixels.size) { ImageQualityMetrics.luminance(pixels[it]) }
            val meanLum = ImageQualityMetrics.meanLuminance(gray)
            val blur = ImageQualityMetrics.laplacianVariance(gray, scaled.width, scaled.height)
            val issues = ImageQualityDecision.issues(originalMinDimension, meanLum, blur, thresholds)
            return ImageQualityReport(originalMinDimension, meanLum, blur, issues)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    /** Largest power of two so the decoded longest edge stays in [targetEdge, 2*targetEdge). */
    private fun inSampleSizeFor(width: Int, height: Int, targetEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= targetEdge) sample *= 2
        return sample
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
