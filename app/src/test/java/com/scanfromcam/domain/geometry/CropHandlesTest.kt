package com.scanfromcam.domain.geometry

import com.scanfromcam.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropHandlesTest {

    private val quad = listOf(
        Point(0.1f, 0.1f),
        Point(0.9f, 0.1f),
        Point(0.9f, 0.9f),
        Point(0.1f, 0.9f),
    )

    @Test
    fun `a corner wins over the side next to it`() {
        val hit = CropHandles.hitTest(quad, 0.1f, 0.1f, cornerRadius = 0.08f, sideTolerance = 0.06f)
        assertEquals(CropHandle.Corner(0), hit)
    }

    @Test
    fun `the middle of an edge grabs the whole side`() {
        val hit = CropHandles.hitTest(quad, 0.5f, 0.1f, cornerRadius = 0.08f, sideTolerance = 0.06f)
        assertEquals(CropHandle.Side(0), hit)
        val right = CropHandles.hitTest(quad, 0.9f, 0.5f, cornerRadius = 0.08f, sideTolerance = 0.06f)
        assertEquals(CropHandle.Side(1), right)
    }

    @Test
    fun `the middle of the quad grabs nothing`() {
        assertEquals(null, CropHandles.hitTest(quad, 0.5f, 0.5f, 0.08f, 0.06f))
    }

    @Test
    fun `dragging the top side moves both top corners vertically only`() {
        val moved = CropHandles.moveSide(quad, side = 0, dx = 0.2f, dy = 0.05f)
        assertEquals(0.1f, moved[0].x, 1e-6f)
        assertEquals(0.9f, moved[1].x, 1e-6f)
        assertEquals(0.15f, moved[0].y, 1e-6f)
        assertEquals(0.15f, moved[1].y, 1e-6f)
        assertEquals(0.9f, moved[2].y, 1e-6f)
        assertEquals(0.9f, moved[3].y, 1e-6f)
    }

    @Test
    fun `dragging the right side moves both right corners horizontally only`() {
        val moved = CropHandles.moveSide(quad, side = 1, dx = -0.1f, dy = 0.3f)
        assertEquals(0.8f, moved[1].x, 1e-6f)
        assertEquals(0.8f, moved[2].x, 1e-6f)
        assertEquals(0.1f, moved[1].y, 1e-6f)
        assertEquals(0.9f, moved[2].y, 1e-6f)
    }

    @Test
    fun `a side cannot cross the opposite one`() {
        val moved = CropHandles.moveSide(quad, side = 0, dx = 0f, dy = 5f)
        assertTrue(moved[0].y <= 0.9f - CropHandles.MIN_GAP + 1e-6f)
        assertTrue(moved[1].y <= 0.9f - CropHandles.MIN_GAP + 1e-6f)
    }

    @Test
    fun `a corner cannot leave the image`() {
        val moved = CropHandles.moveCorner(quad, index = 0, dx = -2f, dy = -2f)
        assertEquals(0f, moved[0].x, 1e-6f)
        assertEquals(0f, moved[0].y, 1e-6f)
    }

    @Test
    fun `side midpoints are the average of their corners`() {
        val midpoints = CropHandles.sideMidpoints(quad)
        assertEquals(0.5f, midpoints[0].x, 1e-6f)
        assertEquals(0.1f, midpoints[0].y, 1e-6f)
        assertEquals(0.9f, midpoints[1].x, 1e-6f)
        assertEquals(0.5f, midpoints[1].y, 1e-6f)
    }
}
