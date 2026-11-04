package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanfromcam.domain.model.Point
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import kotlin.math.abs

/**
 * The two measurements the capture path relies on: how crisp a page reads, and
 * where the real border is under a side the user dropped by hand.
 */
@RunWith(AndroidJUnit4::class)
class FocusAndSnapTest {

    @Before
    fun setUp() {
        OpenCVLoader.initLocal()
    }

    @Test
    fun aCrispPageReadsAsFocusedAndASoftOneDoesNot() {
        val page = pageWithText()
        val soft = blur(page, radius = 4)

        val crispGray = Cv.grayscale(page)
        val softGray = Cv.grayscale(soft)
        try {
            val crisp = Cv.measureFocus(crispGray)
            val blurred = Cv.measureFocus(softGray)

            assertTrue("crisp ${crisp.sharpness}/${crisp.detail}", crisp.isSharp)
            assertFalse("soft ${blurred.sharpness}/${blurred.detail}", blurred.isSharp)
        } finally {
            crispGray.release()
            softGray.release()
            page.recycle()
            soft.recycle()
        }
    }

    @Test
    fun aBlankSheetIsNotBlamedForHavingNothingOnIt() {
        val blank = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        Canvas(blank).drawColor(Color.rgb(247, 246, 244))
        val gray = Cv.grayscale(blank)
        try {
            assertTrue(Cv.measureFocus(gray).isSharp)
        } finally {
            gray.release()
            blank.recycle()
        }
    }

    @Test
    fun aSideEndsOnTheRealBorder() {
        val width = 600
        val height = 800
        val top = 120f
        val page = sheetOnTable(width, height, top)
        val gray = Cv.grayscale(page)
        try {
            // Dropped by hand a little above the sheet: the sheet starts at 0.15.
            val dropped = listOf(
                Point(0.14f, 0.13f),
                Point(0.86f, 0.13f),
                Point(0.86f, 0.93f),
                Point(0.14f, 0.93f),
            )
            val snapped = Cv.snapSide(gray, dropped, 0)
            assertNotNull("No edge found under the top side", snapped)
            val landed = snapped!![0].y * height
            assertTrue("Top side landed at $landed instead of $top", abs(landed - top) < 4f)
        } finally {
            gray.release()
            page.recycle()
        }
    }

    private fun pageWithText(): Bitmap {
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 246, 244))
        val ink = Paint().apply {
            color = Color.rgb(28, 28, 30)
            strokeWidth = 6f
        }
        for (line in 0 until 18) {
            val y = 60f + line * 30f
            canvas.drawLine(40f, y, 440f, y, ink)
        }
        return bitmap
    }

    private fun sheetOnTable(width: Int, height: Int, top: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(70, 72, 78))
        canvas.drawRect(80f, top, width - 80f, height - 60f, Paint().apply {
            color = Color.rgb(246, 245, 242)
        })
        return bitmap
    }

    /**
     * Out of focus on purpose. Two box passes each way approximate a Gaussian,
     * which is what a lens or a moving hand does; scaling down and up would
     * leave hard stair steps behind and keep the picture looking crisp.
     */
    private fun blur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val start = IntArray(width * height)
        source.getPixels(start, 0, width, 0, 0, width, height)

        var pixels = start
        repeat(2) {
            pixels = boxPass(pixels, width, height, radius, horizontal = true)
            pixels = boxPass(pixels, width, height, radius, horizontal = false)
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun boxPass(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean,
    ): IntArray {
        val out = IntArray(pixels.size)
        val outer = if (horizontal) height else width
        val inner = if (horizontal) width else height
        for (line in 0 until outer) {
            for (index in 0 until inner) {
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (step in -radius..radius) {
                    val neighbour = (index + step).coerceIn(0, inner - 1)
                    val pixel = if (horizontal) {
                        pixels[line * width + neighbour]
                    } else {
                        pixels[neighbour * width + line]
                    }
                    red += (pixel shr 16) and 0xFF
                    green += (pixel shr 8) and 0xFF
                    blue += pixel and 0xFF
                    count++
                }
                val value = (0xFF shl 24) or
                    ((red / count) shl 16) or
                    ((green / count) shl 8) or
                    (blue / count)
                if (horizontal) out[line * width + index] = value else out[index * width + line] = value
            }
        }
        return out
    }
}
