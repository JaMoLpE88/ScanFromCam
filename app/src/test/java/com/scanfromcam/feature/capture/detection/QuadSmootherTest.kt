package com.scanfromcam.feature.capture.detection

import com.scanfromcam.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadSmootherTest {

    private val quad = listOf(
        Point(0.1f, 0.1f),
        Point(0.9f, 0.1f),
        Point(0.9f, 0.9f),
        Point(0.1f, 0.9f),
    )

    @Test
    fun `reports stable after four close quads`() {
        val smoother = QuadSmoother()
        repeat(3) { assertFalse(smoother.add(quad).isStable) }
        assertTrue(smoother.add(quad).isStable)
    }

    @Test
    fun `loses stability when the quad jumps`() {
        val smoother = QuadSmoother()
        repeat(4) { smoother.add(quad) }
        val moved = quad.map { Point(it.x * 0.3f, it.y * 0.3f) }
        assertFalse(smoother.add(moved).isStable)
    }

    @Test
    fun `keeps the last known quad when a frame has no detection`() {
        val smoother = QuadSmoother()
        smoother.add(quad, now = 1_000)
        val result = smoother.add(null, now = 1_100)
        assertFalse(result.isStable)
        assertNotNull(result.quad)
    }

    @Test
    fun `freshness window rejects a quad the user has moved away from`() {
        val smoother = QuadSmoother()
        smoother.add(quad, now = 1_000)
        assertNotNull(smoother.latestIfFresh(maxAgeMs = 500, now = 1_300))
        assertNull(smoother.latestIfFresh(maxAgeMs = 500, now = 1_900))
    }

    @Test
    fun `never seen quad is not fresh`() {
        val smoother = QuadSmoother()
        assertNull(smoother.latestIfFresh(maxAgeMs = 5_000, now = 1_000))
    }

    @Test
    fun `blend keeps the quad inside the frame after many frames`() {
        val smoother = QuadSmoother(alpha = 0.9f)
        val huge = listOf(
            Point(-2f, -2f),
            Point(3f, -2f),
            Point(3f, 3f),
            Point(-2f, 3f),
        )
        repeat(10) { smoother.add(huge, now = 1_000) }
        val result = smoother.add(quad, now = 1_000).quad!!
        assertTrue(result.all { it.x in -3f..4f })
        assertEquals(4, result.size)
    }
    @Test
    fun `asks to get closer when the sheet covers too little of the frame`() {
        val smoother = QuadSmoother()
        val far = listOf(
            Point(0.4f, 0.4f),
            Point(0.6f, 0.4f),
            Point(0.6f, 0.6f),
            Point(0.4f, 0.6f),
        )
        repeat(3) { smoother.add(far) }
        assertFalse(smoother.covers(0.5f))
    }

    @Test
    fun `covered once the sheet fills half the frame on several frames`() {
        val smoother = QuadSmoother()
        repeat(3) { smoother.add(quad) }
        assertTrue(smoother.covers(0.5f))
    }

    @Test
    fun `small jitter does not move the corners`() {
        val smoother = QuadSmoother()
        smoother.add(quad)
        val jitter = quad.map { Point(it.x + 0.002f, it.y - 0.002f) }
        repeat(5) { smoother.add(jitter) }
        val settled = smoother.add(jitter).quad!!
        assertEquals(quad[0].x, settled[0].x, 0.0001f)
        assertEquals(quad[0].y, settled[0].y, 0.0001f)
    }

}