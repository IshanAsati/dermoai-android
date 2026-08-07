package com.dermoai.feature.settings.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.dermoai.core.domain.model.ConditionSeverity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders a small synthetic "macro skin photo" per seeded scan so timeline
 * and dashboard thumbnails show an actual picture instead of the placeholder
 * tile [DemoDataSeeder] otherwise falls back to.
 *
 * Deliberately **not** a real photograph — sourcing or fabricating genuine
 * clinical images was out of scope, and presenting a stock/AI skin-condition
 * photo as "this patient's scan" would be actively misleading in a demo.
 * Instead this procedurally draws a plausible close-up: a skin-tone gradient
 * base with fine texture noise, plus a lesion-like blob whose size, color and
 * border irregularity scale with [ConditionSeverity] — a CRITICAL scan
 * visibly looks more concerning than a LOW one (irregular, asymmetric border
 * = a real ABCDE dermoscopy cue) without claiming to depict anyone real.
 *
 * Deterministic by (ownerSeed, scanId): re-running the seeder the night
 * before a demo regenerates the exact same images rather than shuffling them.
 */
internal object DemoPhotoGenerator {

    private const val SIZE = 512

    /** A handful of plausible base skin tones; picked per-owner so a patient's own scans are consistent. */
    private val SKIN_TONES = listOf(
        Triple(255, 224, 189),
        Triple(241, 194, 157),
        Triple(224, 172, 105),
        Triple(198, 134, 66),
        Triple(141, 85, 36),
    )

    fun render(outFile: File, ownerSeed: String, scanId: String, topSeverity: ConditionSeverity) {
        outFile.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ownerRandom = Random(ownerSeed.hashCode())
        val scanRandom = Random(scanId.hashCode())

        val base = SKIN_TONES[ownerRandom.nextInt(SKIN_TONES.size)]
        drawSkinBase(canvas, base)
        drawTexture(canvas, base, scanRandom)
        drawLesion(canvas, topSeverity, scanRandom)

        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap.recycle()
    }

    private fun drawSkinBase(canvas: Canvas, base: Triple<Int, Int, Int>) {
        val cx = SIZE / 2f
        val cy = SIZE / 2f
        val light = Color.rgb(
            (base.first + 18).coerceAtMost(255),
            (base.second + 18).coerceAtMost(255),
            (base.third + 18).coerceAtMost(255),
        )
        val dark = Color.rgb(
            (base.first - 28).coerceAtLeast(0),
            (base.second - 28).coerceAtLeast(0),
            (base.third - 28).coerceAtLeast(0),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, SIZE * 0.75f, light, dark, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
    }

    /** Light speckling so the base doesn't read as a flat gradient swatch. */
    private fun drawTexture(canvas: Canvas, base: Triple<Int, Int, Int>, random: Random) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(SPECKLE_COUNT) {
            val x = random.nextFloat() * SIZE
            val y = random.nextFloat() * SIZE
            val shade = if (random.nextBoolean()) 1 else -1
            paint.color = Color.argb(
                18 + random.nextInt(18),
                (base.first + shade * 22).coerceIn(0, 255),
                (base.second + shade * 22).coerceIn(0, 255),
                (base.third + shade * 22).coerceIn(0, 255),
            )
            canvas.drawCircle(x, y, 1f + random.nextFloat() * 2.5f, paint)
        }
    }

    /**
     * A lesion-like blob. LOW/MEDIUM are near-circular and small; HIGH/CRITICAL
     * are larger, darker, with a perturbed (irregular) border and a faint
     * reddish halo suggesting inflammation — real dermoscopy heuristics
     * (asymmetry, border irregularity), applied for visual differentiation
     * only, not diagnostic content.
     */
    private fun drawLesion(canvas: Canvas, severity: ConditionSeverity, random: Random) {
        val cx = SIZE * (0.38f + random.nextFloat() * 0.24f)
        val cy = SIZE * (0.38f + random.nextFloat() * 0.24f)
        val spec = LESION_SPECS.getValue(severity)
        val radius = SIZE * spec.radiusFraction

        if (spec.haloAlpha > 0) {
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(spec.haloAlpha, 200, 60, 50)
                maskFilter = android.graphics.BlurMaskFilter(
                    SIZE * 0.04f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            }
            canvas.drawCircle(cx, cy, radius * 1.6f, haloPaint)
        }

        val path = Path()
        val points = 14
        for (i in 0..points) {
            val angle = (i % points) * (2 * Math.PI / points)
            val jitter = 1f + (random.nextFloat() - 0.5f) * spec.irregularity
            val r = radius * jitter
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(spec.r, spec.g, spec.b)
            alpha = spec.alpha
        }
        canvas.drawPath(path, fillPaint)
    }

    private const val SPECKLE_COUNT = 900

    private data class LesionSpec(
        val radiusFraction: Float,
        val irregularity: Float,
        val r: Int,
        val g: Int,
        val b: Int,
        val alpha: Int,
        val haloAlpha: Int,
    )

    private val LESION_SPECS = mapOf(
        ConditionSeverity.LOW to LesionSpec(0.05f, 0.06f, 120, 74, 46, 200, 0),
        ConditionSeverity.MEDIUM to LesionSpec(0.08f, 0.16f, 96, 58, 38, 220, 0),
        ConditionSeverity.HIGH to LesionSpec(0.12f, 0.30f, 74, 38, 30, 235, 40),
        ConditionSeverity.CRITICAL to LesionSpec(0.16f, 0.42f, 40, 18, 16, 250, 70),
    )
}
