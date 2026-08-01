package com.dermoai.core.reports

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.database.entity.DailyCheckInEntity
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ReportInput(
    val patientName: String,
    val dateRangeLabel: String,
    val includeImages: Boolean,
    val includePredictions: Boolean,
    val includeSkinMind: Boolean,
    val scans: List<ScanWithPredictions>,
    val checkIns: List<DailyCheckInEntity> = emptyList(),
)

data class ScanWithPredictions(
    val scan: SkinScanEntity,
    val predictions: List<ScanPredictionEntity>,
)

@Singleton
class DoctorReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun generate(input: ReportInput): AppResult<File> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            var y = 72f

            val titlePaint = Paint().apply { textSize = 24f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#14B8A6") }
            val headingPaint = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.DKGRAY }
            val bodyPaint = Paint().apply { textSize = 11f; color = Color.DKGRAY }
            val smallPaint = Paint().apply { textSize = 9f; color = Color.GRAY }
            val disclaimPaint = Paint().apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.CENTER }
            val imagePaint = Paint().apply { isFilterBitmap = true }

            // Header
            canvas.drawText("DermoAI Doctor Report", 48f, y, titlePaint)
            y += 36f
            canvas.drawText("Patient: ${input.patientName}", 48f, y, bodyPaint)
            y += 18f
            canvas.drawText("Period: ${input.dateRangeLabel}", 48f, y, bodyPaint)
            y += 18f
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
            canvas.drawText("Generated: $dateStr", 48f, y, bodyPaint)
            y += 36f

            canvas.drawText("───── Scan Timeline ─────", 48f, y, headingPaint)
            y += 24f

            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            for ((idx, entry) in input.scans.withIndex()) {
                if (y > PAGE_HEIGHT - 80f) {
                    drawPageNumber(canvas, idx + 1, input.scans.size, disclaimPaint)
                    document.finishPage(page)
                    val newPage = document.startPage(pageInfo)
                    val newCanvas = newPage.canvas
                    newCanvas.drawText("Educational only — not a medical diagnosis", PAGE_WIDTH / 2f, 12f, disclaimPaint)
                }

                // Scan entry
                val scan = entry.scan
                canvas.drawText("${sdf.format(Date(scan.capturedAt))}  —  ${scan.bodyArea.ifEmpty { "Area not specified" }}", 48f, y, bodyPaint)
                y += 16f

                // Thumbnail
                if (input.includeImages) {
                    try {
                        val bmp = BitmapFactory.decodeFile(scan.imagePath)
                        if (bmp != null) {
                            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 72, 72, true)
                            canvas.drawBitmap(scaled, 48f, y, imagePaint)
                            y += 80f
                        }
                    } catch (_: Exception) {}
                }

                // Predictions
                if (input.includePredictions && entry.predictions.isNotEmpty()) {
                    for ((pi, pred) in entry.predictions.take(3).withIndex()) {
                        val barWidth = 150f * pred.confidence
                        canvas.drawText("${pi + 1}. ${pred.label}  (%.0f%%)".format(pred.confidence * 100), 132f, y - 4f, bodyPaint)
                        val barColor = when (pred.concernBand) {
                            "CRITICAL", "HIGH" -> android.graphics.Color.parseColor("#F87171")
                            "MEDIUM" -> android.graphics.Color.parseColor("#F59E0B")
                            else -> android.graphics.Color.parseColor("#34D399")
                        }
                        val barPaint = Paint().apply { color = barColor; style = Paint.Style.FILL }
                        canvas.drawRect(132f, y + 2f, 132f + barWidth, y + 8f, barPaint)
                        y += 16f
                    }
                    y += 8f
                }

                // Notes
                if (scan.note.isNotEmpty()) {
                    canvas.drawText("  Note: ${scan.note}", 48f, y, smallPaint)
                    y += 14f
                }
                y += 8f
            }

            // SkinMind summary
            if (input.includeSkinMind && input.checkIns.isNotEmpty()) {
                y += 12f
                canvas.drawText("───── SkinMind Summary ─────", 48f, y, headingPaint)
                y += 24f
                val avgFeel = input.checkIns.map { it.skinFeel }.average().let { "%.1f".format(it) }
                val avgSleep = input.checkIns.map { it.sleepQuality }.average().let { "%.1f".format(it) }
                val avgStress = input.checkIns.map { it.stressLevel }.average().let { "%.1f".format(it) }
                canvas.drawText("  $avgFeel / 5 avg skin feel  •  $avgSleep / 5 avg sleep  •  $avgStress / 5 avg stress", 48f, y, bodyPaint)
                y += 24f
            }

            // Educate footer
            y = PAGE_HEIGHT - 48f
            canvas.drawText("Educational only — not a medical diagnosis", PAGE_WIDTH / 2f, y, disclaimPaint)
            y += 14f
            canvas.drawText("DermoAI is an awareness tool and does not replace a dermatologist.", PAGE_WIDTH / 2f, y, disclaimPaint)
            drawPageNumber(canvas, 1, 1, disclaimPaint)

            document.finishPage(page)
            val output = File(context.filesDir, "reports").apply { mkdirs() }
            val file = File(output, "report_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            file
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it, it.message ?: "PDF generation failed") },
        )
    }

    private fun drawPageNumber(canvas: Canvas, current: Int, total: Int, paint: Paint) {
        canvas.drawText("Page $current of $total", PAGE_WIDTH - 60f, PAGE_HEIGHT - 24f, paint)
    }

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
    }
}
