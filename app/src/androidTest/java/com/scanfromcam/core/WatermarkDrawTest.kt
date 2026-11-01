package com.scanfromcam.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the real drawing of the watermark on a page bitmap. */
@RunWith(AndroidJUnit4::class)
class WatermarkDrawTest {

    @Test
    fun markReachesEveryQuarterOfThePage() {
        val width = 900
        val height = 1300
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(page).drawColor(Color.WHITE)

        val marked = Watermark.apply(page, "CONFIDENCIAL")
            ?: error("no se ha pintado la marca")

        val pixels = IntArray(width * height)
        marked.getPixels(pixels, 0, width, 0, 0, width, height)

        for (qx in 0..1) {
            for (qy in 0..1) {
                val x0 = qx * width / 2
                val x1 = x0 + width / 2
                val y0 = qy * height / 2
                val y1 = y0 + height / 2
                var painted = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        if (Color.blue(pixels[y * width + x]) < 250) painted++
                    }
                }
                assertTrue("sin marca en el cuadrante $qx,$qy", painted > 200)
            }
        }

        page.recycle()
        marked.recycle()
    }

    @Test
    fun blankTextLeavesThePageAlone() {
        val page = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        Canvas(page).drawColor(Color.WHITE)
        assertTrue(Watermark.apply(page, "  ") == null)
        page.recycle()
    }
}
