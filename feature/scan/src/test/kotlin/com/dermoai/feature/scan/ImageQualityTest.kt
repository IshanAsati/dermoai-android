package com.dermoai.feature.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math + decision tests for the image quality check. No android.graphics
 * on the JVM — these cover [ImageQualityMetrics] and [ImageQualityDecision].
 */
class ImageQualityTest {

    // ---------- ImageQualityMetrics.luminance ----------

    @Test
    fun `luminance of black is 0 and white is 255`() {
        assertEquals(0, ImageQualityMetrics.luminance(0xFF000000.toInt()))
        assertEquals(255, ImageQualityMetrics.luminance(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `luminance uses rec601 weights`() {
        // Pure red: 0.299 * 255 = 76.2 -> 76
        assertEquals(76, ImageQualityMetrics.luminance(0xFFFF0000.toInt()))
        // Pure green: 0.587 * 255 = 149.7 -> 149
        assertEquals(149, ImageQualityMetrics.luminance(0xFF00FF00.toInt()))
        // Pure blue: 0.114 * 255 = 29.07 -> 29
        assertEquals(29, ImageQualityMetrics.luminance(0xFF0000FF.toInt()))
    }

    @Test
    fun `mean luminance of a flat gray image is that gray`() {
        val gray = IntArray(400) { 128 }
        assertEquals(128f, ImageQualityMetrics.meanLuminance(gray), 1e-3f)
    }

    // ---------- ImageQualityMetrics.laplacianVariance ----------

    @Test
    fun `laplacian variance of a flat image is zero`() {
        val gray = IntArray(100) { 100 }
        assertEquals(0f, ImageQualityMetrics.laplacianVariance(gray, 10, 10), 1e-3f)
    }

    @Test
    fun `laplacian variance of a vertical edge is positive`() {
        // 10x10, left half black, right half white -> strong edge responses.
        val width = 10
        val height = 10
        val gray = IntArray(width * height) { i -> if ((i % width) < width / 2) 0 else 255 }
        assertTrue(
            "expected variance > 0, got " + ImageQualityMetrics.laplacianVariance(gray, width, height),
            ImageQualityMetrics.laplacianVariance(gray, width, height) > 0f,
        )
    }

    @Test
    fun `laplacian variance of a too-small image is zero`() {
        assertEquals(0f, ImageQualityMetrics.laplacianVariance(IntArray(4) { 10 }, 2, 2), 1e-3f)
    }

    @Test
    fun `sharp image scores higher than a smoothed one`() {
        val width = 32
        val height = 32
        val sharp = IntArray(width * height) { i ->
            val x = i % width
            if (x < 16) 0 else 255
        }
        // Smear the edge across a 3px ramp -> much lower high-frequency energy.
        val blurry = IntArray(width * height) { i ->
            val x = i % width
            when {
                x < 14 -> 0
                x < 17 -> 85
                x < 20 -> 170
                else -> 255
            }
        }
        val sharpVar = ImageQualityMetrics.laplacianVariance(sharp, width, height)
        val blurryVar = ImageQualityMetrics.laplacianVariance(blurry, width, height)
        assertTrue("sharp=$sharpVar blurry=$blurryVar", sharpVar > blurryVar)
    }

    // ---------- ImageQualityDecision.issues ----------

    @Test
    fun `good photo has no issues`() {
        val issues = ImageQualityDecision.issues(
            minDimension = 1200,
            meanLuminance = 128f,
            blurScore = 400f,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `small crop is flagged as too small`() {
        assertEquals(
            listOf(ImageQualityIssue.TOO_SMALL),
            ImageQualityDecision.issues(minDimension = 479, meanLuminance = 128f, blurScore = 400f),
        )
        assertTrue(
            ImageQualityDecision.issues(minDimension = 480, meanLuminance = 128f, blurScore = 400f).isEmpty(),
        )
    }

    @Test
    fun `dark photo is flagged below threshold and not at it`() {
        assertEquals(
            listOf(ImageQualityIssue.TOO_DARK),
            ImageQualityDecision.issues(minDimension = 1200, meanLuminance = 49.9f, blurScore = 400f),
        )
        assertTrue(
            ImageQualityDecision.issues(minDimension = 1200, meanLuminance = 50f, blurScore = 400f).isEmpty(),
        )
    }

    @Test
    fun `overexposed photo is flagged above threshold and not at it`() {
        assertEquals(
            listOf(ImageQualityIssue.TOO_BRIGHT),
            ImageQualityDecision.issues(minDimension = 1200, meanLuminance = 215.1f, blurScore = 400f),
        )
        assertTrue(
            ImageQualityDecision.issues(minDimension = 1200, meanLuminance = 215f, blurScore = 400f).isEmpty(),
        )
    }

    @Test
    fun `blurry photo is flagged below threshold and not at it`() {
        assertEquals(
            listOf(ImageQualityIssue.BLURRY),
            ImageQualityDecision.issues(minDimension = 1200, meanLuminance = 128f, blurScore = 79.9f),
        )
        assertTrue(
            ImageQualityDecision.issues(minDimension = 1200, meanLuminance = 128f, blurScore = 80f).isEmpty(),
        )
    }

    @Test
    fun `multiple issues are all reported`() {
        val issues = ImageQualityDecision.issues(
            minDimension = 100,
            meanLuminance = 10f,
            blurScore = 5f,
        )
        assertEquals(
            listOf(ImageQualityIssue.TOO_SMALL, ImageQualityIssue.TOO_DARK, ImageQualityIssue.BLURRY),
            issues,
        )
    }
}
