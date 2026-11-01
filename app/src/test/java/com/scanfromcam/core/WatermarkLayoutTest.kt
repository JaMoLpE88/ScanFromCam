package com.scanfromcam.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watermark has to cover the whole page after its rotation. */
class WatermarkLayoutTest {

    @Test
    fun gridCoversEveryPointOfTheRotatedPage() {
        val width = 1654f
        val height = 2339f
        val textSize = 110f
        val textWidth = 460f
        val tiles = WatermarkLayout.tiles(width, height, textSize, textWidth)

        assertTrue("no se repite la marca", tiles.size > 20)

        // Cell size measured from the grid itself, not copied from the layout.
        val rowYs = tiles.map { it.y }.distinct().sorted()
        val stepY = rowYs.zipWithNext().minOf { (a, b) -> b - a }
        val stepX = tiles.groupBy { it.y }.values
            .flatMap { row -> row.map { it.x }.sorted().zipWithNext().map { (a, b) -> b - a } }
            .min()

        val angle = Math.toRadians(WatermarkLayout.ANGLE.toDouble())
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        val cx = width / 2f
        val cy = height / 2f

        for (i in 0..20) {
            for (j in 0..20) {
                val px = width * i / 20f - cx
                val py = height * j / 20f - cy
                val rx = cx + px * cosA - py * sinA
                val ry = cy + px * sinA + py * cosA
                val covered = tiles.any {
                    abs(it.x - rx) <= stepX / 2f + 1f && abs(it.y - ry) <= stepY / 2f + 1f
                }
                assertTrue("sin marca en ($rx, $ry)", covered)
            }
        }
    }

    @Test
    fun emptyInputDrawsNothing() {
        assertTrue(WatermarkLayout.tiles(0f, 100f, 10f, 10f).isEmpty())
        assertTrue(WatermarkLayout.tiles(100f, 100f, 0f, 10f).isEmpty())
    }
}
