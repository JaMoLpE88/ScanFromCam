package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfWriterTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun pdfKeepsPageCountAndInvisibleTextLayer() {
        val file = writePage("factura.jpg", "FACTURA 2026")
        val ocr = OcrResult("FACTURA 2026", listOf(OcrLine("FACTURA 2026", Rect(40, 40, 320, 96))))

        val bytes = PdfWriter.build(listOf(PdfPageSource(file.absolutePath, ocr)))

        PDDocument.load(bytes.inputStream()).use { document ->
            assertEquals(1, document.numberOfPages)
            val extracted = PDFTextStripper().getText(document)
            assertTrue("Texto extraído: '$extracted'", extracted.contains("FACTURA"))
        }
    }

    @Test
    fun pdfSurvivesPagesWithoutText() {
        val file = writePage("sin_ocr.jpg", "SIN OCR")
        val bytes = PdfWriter.build(
            listOf(
                PdfPageSource(file.absolutePath, null),
                PdfPageSource(file.absolutePath, null),
            ),
        )

        PDDocument.load(bytes.inputStream()).use { document ->
            assertEquals(2, document.numberOfPages)
        }
    }

    @Test
    fun handlesSeveralPagesWithTextWithoutKeepingThemInMemory() {
        val files = (1..5).map { index -> writePage("pagina_$index.jpg", "PAGINA $index") }
        val sources = files.mapIndexed { index, file ->
            PdfPageSource(
                file.absolutePath,
                OcrResult(
                    "PAGINA ${index + 1}",
                    listOf(OcrLine("PAGINA ${index + 1}", Rect(30, 40, 340, 90))),
                ),
            )
        }

        val bytes = PdfWriter.build(sources)

        PDDocument.load(bytes.inputStream()).use { document ->
            assertEquals(5, document.numberOfPages)
            val text = PDFTextStripper().getText(document)
            assertTrue(text, text.contains("PAGINA 1"))
            assertTrue(text, text.contains("PAGINA 5"))
        }
    }

    private fun writePage(name: String, text: String): File {
        val bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 52f
            isAntiAlias = true
        }
        canvas.drawText(text, 40f, 90f, paint)
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }
}

