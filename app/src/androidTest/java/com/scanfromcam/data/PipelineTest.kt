package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.abs

/**
 * End to end check of the scanning promise without a camera: a synthetic photo
 * of a skewed page goes through detection, perspective correction, the single
 * enhancement pass and the PDF writer.
 */
@RunWith(AndroidJUnit4::class)
class PipelineTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        OpenCVLoader.initLocal()
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun detectsRectsAndExportsASearchablePdf() {
        val photo = photoOfSkewedPage()

        val gray = Mat()
        Utils.bitmapToMat(photo, gray)
        val single = Mat()
        Imgproc.cvtColor(gray, single, Imgproc.COLOR_RGBA2GRAY)
        val quad = Cv.detectQuad(single)
        gray.release()
        single.release()

        assertNotNull("No se detectó la página en la foto sintética", quad)

        val warped = Cv.warp(photo, quad!!)
        try {
            val ratio = warped.width.toFloat() / warped.height
            assertTrue(
                "Relación de aspecto inesperada tras el encare: $ratio",
                ratio in 0.62f..0.88f,
            )

            // The four corners must be page, not background: the crop is complete.
            listOf(6 to 6, warped.width - 7 to 6, 6 to warped.height - 7, warped.width - 7 to warped.height - 7)
                .forEach { (x, y) ->
                    val pixel = warped.getPixel(x, y)
                    assertTrue(
                        "Esquina ($x,$y) no es papel: #${Integer.toHexString(pixel)}",
                        Color.red(pixel) > 150 && Color.green(pixel) > 150,
                    )
                }

            val optimized = Cv.optimize(warped)
            try {
                assertEquals(warped.width, optimized.width)
                assertEquals(warped.height, optimized.height)
                assertTrue("La versión optimizada no se puede leer", optimized.width > 0)

                val file = File(context.cacheDir, "pipeline_page.jpg")
                file.outputStream().use { optimized.compress(Bitmap.CompressFormat.JPEG, 90, it) }

                val ocr = OcrResult(
                    text = "ACTA 2026",
                    lines = listOf(OcrLine("ACTA 2026", Rect(40, 60, 420, 120))),
                )
                val bytes = PdfWriter.build(listOf(PdfPageSource(file.absolutePath, ocr)))

                PDDocument.load(bytes.inputStream()).use { document ->
                    assertEquals(1, document.numberOfPages)
                    val text = PDFTextStripper().getText(document)
                    assertTrue("Sin capa de texto: '$text'", text.contains("ACTA"))
                }
            } finally {
                optimized.recycle()
            }
        } finally {
            warped.recycle()
            photo.recycle()
        }
    }

    /** White page drawn as a slightly skewed trapezoid over a dark desk. */
    private fun photoOfSkewedPage(): Bitmap {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(48, 46, 44))

        val page = Path().apply {
            moveTo(500f, 150f)
            lineTo(1150f, 215f)
            lineTo(1120f, 1050f)
            lineTo(470f, 980f)
            close()
        }
        canvas.drawPath(page, Paint().apply { color = Color.WHITE; isAntiAlias = true })

        // A few "text" lines so the page is not a flat surface.
        val ink = Paint().apply {
            color = Color.rgb(30, 30, 30)
            strokeWidth = 6f
            isAntiAlias = true
        }
        for (i in 0..5) {
            val y = 320f + i * 90f
            canvas.drawLine(560f + i * 4f, y, 1040f + i * 6f, y + 8f, ink)
        }
        return bitmap
    }
}
