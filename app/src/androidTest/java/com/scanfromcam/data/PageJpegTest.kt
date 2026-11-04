package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Storing a page: a sheet of black type on white goes out in one channel, and
 * anything with colour in it keeps all three, because a stamp or a signature
 * must come back as it was photographed.
 */
@RunWith(AndroidJUnit4::class)
class PageJpegTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "page-test.jpg")

    @Before
    fun setUp() {
        OpenCVLoader.initLocal()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun blackTypeOnWhiteIsStoredInOneChannel() {
        val page = page(redStamp = false)
        try {
            assertTrue(Cv.savePageJpeg(page, file, 88))
            assertTrue("No se escribió el fichero", file.length() > 0)
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull("El JPEG de un canal no se pudo leer", decoded)
            decoded?.recycle()
        } finally {
            page.recycle()
        }
    }

    @Test
    fun aColouredPageIsLeftToTheCaller() {
        val page = page(redStamp = true)
        try {
            assertFalse(Cv.savePageJpeg(page, file, 88))
        } finally {
            page.recycle()
        }
    }

    private fun page(redStamp: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 245, 242))
        val ink = Paint().apply {
            color = Color.rgb(30, 30, 32)
            strokeWidth = 6f
        }
        for (line in 0 until 20) {
            canvas.drawLine(40f, 60f + line * 34f, 560f, 60f + line * 34f, ink)
        }
        if (redStamp) {
            canvas.drawRect(380f, 700f, 520f, 780f, Paint().apply { color = Color.rgb(198, 32, 32) })
        }
        return bitmap
    }
}
