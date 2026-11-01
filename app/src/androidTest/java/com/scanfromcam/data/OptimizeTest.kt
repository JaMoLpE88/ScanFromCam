package com.scanfromcam.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import kotlin.math.abs

/**
 * "Optimizar" has to make a photographed page look like a scan: brighter and
 * more even paper, ink still dark, and colour (a stamp, a signature) still there.
 */
@RunWith(AndroidJUnit4::class)
class OptimizeTest {

    @Before
    fun setUp() {
        OpenCVLoader.initLocal()
    }

    @Test
    fun brightensThePaperKeepsInkAndKeepsColour() {
        val page = pageWithShadowInkAndStamp()

        val before = Cv.optimize(page)

        val (beforeLeft, beforeRight) = sideLuma(page)
        val (afterLeft, afterRight) = sideLuma(before)

        // The point is an even sheet, not a brighter one: paper is already light.
        assertTrue("El papel no llegó a blanco ($afterRight)", afterRight > 205.0)
        assertTrue(
            "La sombra no se corrigió (antes ${beforeLeft - beforeRight}, después ${afterLeft - afterRight})",
            abs(afterLeft - afterRight) < abs(beforeLeft - beforeRight) * 0.6,
        )

        val inkAfter = darkestLuma(before)
        assertTrue("La tinta no es lo bastante oscura (mínimo $inkAfter)", inkAfter < 60.0)

        val redAfter = maxRedness(before)
        assertTrue("El color del sello desapareció (rojo $redAfter)", redAfter > 60)

        before.recycle()
        page.recycle()
    }

    private fun pageWithShadowInkAndStamp(): Bitmap {
        val width = 400
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paper with a strong shadow on the left edge.
        val paper = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width * 0.55f, 0f,
                intArrayOf(Color.rgb(120, 118, 112), Color.rgb(246, 245, 242)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paper)

        // Ink: dark text lines.
        val ink = Paint().apply { color = Color.rgb(25, 25, 25); strokeWidth = 5f }
        for (i in 0..9) {
            val y = 90f + i * 42f
            canvas.drawLine(40f, y, width - 40f, y, ink)
        }

        // Colour: a red stamp.
        canvas.drawRect(250f, 500f, 350f, 570f, Paint().apply { color = Color.rgb(200, 30, 30) })
        return bitmap
    }

    private fun sideLuma(bitmap: Bitmap): Pair<Double, Double> {
        var left = 0.0
        var leftCount = 0
        var right = 0.0
        var rightCount = 0
        for (y in 0 until bitmap.height step 7) {
            for (x in 0 until bitmap.width step 7) {
                val luma = luma(bitmap.getPixel(x, y))
                if (x < bitmap.width / 4) {
                    left += luma; leftCount++
                } else if (x > bitmap.width * 3 / 4) {
                    right += luma; rightCount++
                }
            }
        }
        return (left / leftCount) to (right / rightCount)
    }

    private fun darkestLuma(bitmap: Bitmap): Double {
        var darkest = 255.0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                darkest = minOf(darkest, luma(bitmap.getPixel(x, y)))
            }
        }
        return darkest
    }

    private fun maxRedness(bitmap: Bitmap): Int {
        var redness = 0
        for (y in 480 until 590 step 2) {
            for (x in 230 until 370 step 2) {
                val pixel = bitmap.getPixel(x, y)
                redness = maxOf(redness, Color.red(pixel) - Color.green(pixel))
            }
        }
        return redness
    }

    private fun luma(pixel: Int): Double =
        0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)

    @Test
    fun flattensAWarmCastAndLeavesNeutralPaper() {
        val page = warmPageWithShadow()

        val after = Cv.optimize(page)

        val left = regionMean(after, 0.02f, 0.3f)
        val right = regionMean(after, 0.7f, 0.98f)
        assertTrue("Papel oscuro en sombra: ${left.luma}", left.luma > 210.0)
        assertTrue("Papel oscuro en luz: ${right.luma}", right.luma > 220.0)
        assertTrue(
            "El papel sigue amarillo (R ${right.r} B ${right.b})",
            abs(right.r - right.b) < 14.0,
        )
        assertTrue("La tinta se aclaró: ${darkestLuma(after)}", darkestLuma(after) < 70.0)

        after.recycle()
        page.recycle()
    }

    @Test
    fun sharpensTextEdgesWithoutAmplifyingPaperNoise() {
        val page = pageWithFineText()

        val after = Cv.optimize(page)

        val beforeEdge = edgeContrast(page)
        val afterEdge = edgeContrast(after)
        assertTrue(
            "El texto no ganó nitidez (antes $beforeEdge, después $afterEdge)",
            afterEdge > beforeEdge * 1.1,
        )
        assertTrue("El papel se llenó de ruido: ${paperNoise(after)}", paperNoise(after) < 14.0)

        after.recycle()
        page.recycle()
    }

    @Test
    fun keepsColouredStampsSaturated() {
        val page = pageWithShadowInkAndStamp()
        val before = redness(page)
        val after = Cv.optimize(page).also { }

        assertTrue("El sello perdió color ($before -> $after)", redness(after) > before * 0.75)
        after.recycle()
        page.recycle()
    }

    private data class Rgb(val r: Double, val g: Double, val b: Double) {
        val luma: Double get() = 0.299 * r + 0.587 * g + 0.114 * b
    }

    private fun regionMean(bitmap: Bitmap, fromX: Float, toX: Float): Rgb {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var count = 0
        val startX = (bitmap.width * fromX).toInt()
        val endX = (bitmap.width * toX).toInt()
        for (y in 0 until bitmap.height step 9) {
            for (x in startX until endX step 9) {
                val pixel = bitmap.getPixel(x, y)
                r += Color.red(pixel); g += Color.green(pixel); b += Color.blue(pixel)
                count++
            }
        }
        return Rgb(r / count, g / count, b / count)
    }

    /** Difference between the darkest ink and the paper right next to it. */
    private fun edgeContrast(bitmap: Bitmap): Double {
        var best = 0.0
        for (y in 0 until bitmap.height step 3) {
            var darkest = 255.0
            var brightest = 0.0
            for (x in 0 until bitmap.width) {
                val value = luma(bitmap.getPixel(x, y))
                darkest = minOf(darkest, value)
                brightest = maxOf(brightest, value)
            }
            best = maxOf(best, brightest - darkest)
        }
        return best
    }

    private fun paperNoise(bitmap: Bitmap): Double {
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        // Right half is paper without ink: that is where noise shows up.
        for (y in 0 until bitmap.height step 4) {
            for (x in bitmap.width / 2 until bitmap.width step 4) {
                val value = luma(bitmap.getPixel(x, y))
                if (value < 180.0) continue
                sum += value
                sumSquares += value * value
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return kotlin.math.sqrt(sumSquares / count - mean * mean)
    }

    private fun redness(bitmap: Bitmap): Double = maxRedness(bitmap).toDouble()

    private fun warmPageWithShadow(): Bitmap {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height * 0.15f,
                intArrayOf(Color.rgb(126, 108, 74), Color.rgb(244, 232, 196)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paper)
        val ink = Paint().apply { color = Color.rgb(35, 32, 28); strokeWidth = 4f }
        for (i in 0..12) {
            val y = 80f + i * 48f
            canvas.drawLine(50f, y, width - 50f, y, ink)
        }
        return bitmap
    }

    private fun pageWithFineText(): Bitmap {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 245, 241))
        val text = Paint().apply {
            color = Color.rgb(28, 28, 28)
            textSize = 22f
            isAntiAlias = true
        }
        for (i in 0..15) {
            canvas.drawText("Acta de la reunión número " + (i + 1), 40f, 70f + i * 42f, text)
        }
        return bitmap
    }

}

