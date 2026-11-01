package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * One page of the exported PDF. The image is read from disk during the build so
 * only a single full-size bitmap is alive at any moment, no matter how many
 * pages the document has.
 */
data class PdfPageSource(val path: String, val ocr: OcrResult?)

/**
 * Builds the PDF that ships with a document. Each page holds the full-size image
 * plus an invisible text layer, so the result stays searchable and selectable.
 */
object PdfWriter {

    private const val DPI = 200f

    fun build(sources: List<PdfPageSource>): ByteArray {
        val document = PdfDocument()
        val output = ByteArrayOutputStream(1 shl 20)
        var pageNumber = 1

        try {
            for (source in sources) {
                val bitmap = BitmapFactory.decodeFile(source.path)
                    ?: throw IllegalStateException("No se pudo leer la página $pageNumber")
                try {
                    drawPage(document, bitmap, source.ocr, pageNumber)
                } finally {
                    bitmap.recycle()
                }
                pageNumber++
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
        return output.toByteArray()
    }

    private fun drawPage(
        document: PdfDocument,
        bitmap: Bitmap,
        ocr: OcrResult?,
        pageNumber: Int,
    ) {
        val widthPoints = (bitmap.width * 72f / DPI).roundToInt().coerceAtLeast(1)
        val heightPoints = (bitmap.height * 72f / DPI).roundToInt().coerceAtLeast(1)
        val pageInfo = PdfDocument.PageInfo.Builder(widthPoints, heightPoints, pageNumber)
            .setContentRect(android.graphics.Rect(0, 0, widthPoints, heightPoints))
            .create()

        val page = document.startPage(pageInfo)
        try {
            val canvas = page.canvas
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, widthPoints.toFloat(), heightPoints.toFloat()),
                null,
            )

            val scaleX = widthPoints.toFloat() / bitmap.width
            val scaleY = heightPoints.toFloat() / bitmap.height
            ocr?.lines?.forEach { line ->
                val paint = Paint().apply {
                    color = Color.BLACK
                    // Alpha 1 keeps the text in the content stream (extractable)
                    // while staying invisible over the scanned page.
                    alpha = 1
                    isAntiAlias = true
                    textSize = (line.box.height() * scaleY).coerceAtLeast(4f)
                }
                canvas.drawText(
                    line.text,
                    line.box.left * scaleX,
                    line.box.bottom * scaleY,
                    paint,
                )
            }
        } finally {
            document.finishPage(page)
        }
    }
}


