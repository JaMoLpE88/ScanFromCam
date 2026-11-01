package com.scanfromcam.domain.geometry

import com.scanfromcam.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadsTest {

    @Test
    fun `orders corners as top-left, top-right, bottom-right, bottom-left`() {
        val ordered = Quads.order(
            listOf(
                Point(0.9f, 0.9f),
                Point(0.1f, 0.1f),
                Point(0.1f, 0.9f),
                Point(0.9f, 0.1f),
            )
        )
        assertEquals(
            listOf(
                Point(0.1f, 0.1f),
                Point(0.9f, 0.1f),
                Point(0.9f, 0.9f),
                Point(0.1f, 0.9f),
            ),
            ordered,
        )
    }

    @Test
    fun `unit square area is one`() {
        val square = listOf(
            Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f),
        )
        assertEquals(1f, Quads.area(square), 0.001f)
    }

    @Test
    fun `identical quads have iou one`() {
        val square = listOf(
            Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f),
        )
        assertEquals(1f, Quads.iou(square, square), 0.001f)
    }

    @Test
    fun `disjoint quads have iou zero`() {
        val left = listOf(
            Point(0f, 0f), Point(0.3f, 0f), Point(0.3f, 0.3f), Point(0f, 0.3f),
        )
        val right = listOf(
            Point(0.6f, 0.6f), Point(1f, 0.6f), Point(1f, 1f), Point(0.6f, 1f),
        )
        assertEquals(0f, Quads.iou(left, right), 0.001f)
    }

    @Test
    fun `half overlapping squares give one third iou`() {
        val a = listOf(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f))
        val b = listOf(Point(0.5f, 0f), Point(1.5f, 0f), Point(1.5f, 1f), Point(0.5f, 1f))
        assertTrue(Quads.iou(a, b) in 0.32f..0.34f)
    }

    @Test
    fun `a square in order is convex`() {
        assertTrue(
            Quads.isConvex(
                listOf(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f))
            )
        )
    }

    @Test
    fun `a self crossing quad is not convex`() {
        assertTrue(
            !Quads.isConvex(
                listOf(Point(0f, 0f), Point(1f, 1f), Point(1f, 0f), Point(0f, 1f))
            )
        )
    }

    @Test
    fun `clamp keeps points inside the frame`() {
        val clamped = Quads.clampUnit(listOf(Point(-0.2f, 0.5f), Point(1.4f, 0.5f)))
        assertEquals(Point(0f, 0.5f), clamped[0])
        assertEquals(Point(1f, 0.5f), clamped[1])
    }
}

