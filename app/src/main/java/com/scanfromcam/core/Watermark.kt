package com.scanfromcam.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.hypot

/**
 * Tiled diagonal watermark used on every saved page (JPG and PDF share it,
 * because the PDF is built from the watermarked page files). The geometry lives
 * in [WatermarkLayout] so the coverage rule can be tested without Android.
 */
object Watermark {

    fun draw(
        canvas: Canvas,
        text: String,
        width: Float = canvas.width.toFloat(),
        height: Float = canvas.height.toFloat(),
    ) {
        if (text.isBlank() || width < 1f || height < 1f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 0, 0, 0)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = minOf(width, height) * 0.07f
        }
        val tiles = WatermarkLayout.tiles(width, height, paint.textSize, paint.measureText(text))
        canvas.save()
        canvas.rotate(WatermarkLayout.ANGLE, width / 2f, height / 2f)
        for (tile in tiles) canvas.drawText(text, tile.x, tile.y, paint)
        canvas.restore()
    }

    /**
     * Paints [text] over [source]: in place when the bitmap is mutable, on a
     * copy otherwise. Returns null when there is nothing to paint.
     */
    fun apply(source: Bitmap, text: String): Bitmap? {
        if (text.isBlank() || source.width < 1 || source.height < 1) return null
        val target = if (source.isMutable) source
        else source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        draw(Canvas(target), text)
        return target
    }
}

object WatermarkLayout {

    /** Down-right diagonal, the usual look of a scanned-document watermark. */
    const val ANGLE = -30f

    data class Tile(val x: Float, val y: Float)

    /**
     * Centres of every repeated label in the rotated canvas space. The grid
     * spans a square as wide as the page diagonal, so rotating it never leaves
     * a corner uncovered; odd rows shift half a cell for a denser fill.
     */
    fun tiles(width: Float, height: Float, textSize: Float, textWidth: Float): List<Tile> {
        if (width <= 0f || height <= 0f || textSize <= 0f) return emptyList()
        val cx = width / 2f
        val cy = height / 2f
        val radius = hypot(width, height)
        val stepX = maxOf(textWidth, textSize) + textSize * 1.5f
        val stepY = textSize * 3f
        val tiles = ArrayList<Tile>()
        var row = 0
        var y = cy - radius
        while (y <= cy + radius) {
            var x = cx - radius + if (row % 2 == 0) 0f else stepX / 2f
            while (x <= cx + radius) {
                tiles.add(Tile(x, y))
                x += stepX
            }
            y += stepY
            row++
        }
        return tiles
    }
}
