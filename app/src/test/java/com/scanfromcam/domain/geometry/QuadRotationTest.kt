package com.scanfromcam.domain.geometry

import com.scanfromcam.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadRotationTest {

    private val topLeft = Point(0f, 0f)
    private val topRight = Point(1f, 0f)
    private val bottomRight = Point(1f, 1f)
    private val bottomLeft = Point(0f, 1f)

    @Test
    fun `zero degrees keeps every corner in place`() {
        assertEquals(listOf(topLeft, topRight, bottomRight, bottomLeft),
            QuadRotation.rotate(listOf(topLeft, topRight, bottomRight, bottomLeft), 0))
    }

    @Test
    fun `ninety degrees moves the top-left corner to the top-right`() {
        val rotated = QuadRotation.rotate(listOf(topLeft), 90)
        assertEquals(topRight.x, rotated[0].x, 0.0001f)
        assertEquals(topRight.y, rotated[0].y, 0.0001f)
    }

    @Test
    fun `one hundred eighty degrees swaps opposite corners`() {
        val rotated = QuadRotation.rotate(listOf(topLeft), 180)
        assertEquals(bottomRight.x, rotated[0].x, 0.0001f)
        assertEquals(bottomRight.y, rotated[0].y, 0.0001f)
    }

    @Test
    fun `two hundred seventy degrees moves the top-left corner to the bottom-left`() {
        val rotated = QuadRotation.rotate(listOf(topLeft), 270)
        assertEquals(bottomLeft.x, rotated[0].x, 0.0001f)
        assertEquals(bottomLeft.y, rotated[0].y, 0.0001f)
    }

    @Test
    fun `rotating the whole quad then ordering keeps it well formed`() {
        val quad = listOf(topLeft, topRight, bottomRight, bottomLeft)
        for (degrees in listOf(0, 90, 180, 270)) {
            val ordered = Quads.order(QuadRotation.rotate(quad, degrees))
            assertEquals(4, ordered.size)
            assertTrue(Quads.area(ordered) > 0.9f)
            assertEquals(ordered.sortedBy { it.x + it.y }.first(), ordered[0])
        }
    }

    @Test
    fun `negative angles are normalized`() {
        val a = QuadRotation.rotate(listOf(topLeft), -90)
        val b = QuadRotation.rotate(listOf(topLeft), 270)
        assertEquals(b[0].x, a[0].x, 0.0001f)
        assertEquals(b[0].y, a[0].y, 0.0001f)
    }
}
