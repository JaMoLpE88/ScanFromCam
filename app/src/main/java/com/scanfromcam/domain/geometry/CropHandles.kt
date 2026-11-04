package com.scanfromcam.domain.geometry

import com.scanfromcam.domain.model.Point
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What the user grabbed in the crop editor: a corner or a whole side. */
sealed interface CropHandle {
    data class Corner(val index: Int) : CropHandle
    data class Side(val index: Int) : CropHandle
}

/**
 * Hit testing and movement of the crop quad. Pure geometry so the behaviour is
 * testable without a device; the dialog only converts pixels and gestures.
 *
 * Sides are axis locked, which is what makes them usable with a finger: the top
 * and bottom sides only move vertically and the left/right sides horizontally,
 * moving both of their corners together. A corner still moves freely.
 */
object CropHandles {

    /** Minimum separation between opposite sides, in normalized units. */
    const val MIN_GAP = 0.03f

    /**
     * Returns the corner under the finger, else the side, else null.
     * [pointsPx] are the quad corners in the same pixel space as [x] and [y].
     * Corners win over sides so grabbing a vertex never drags an edge.
     */
    fun hitTest(
        pointsPx: List<Point>,
        x: Float,
        y: Float,
        cornerRadius: Float,
        sideTolerance: Float,
    ): CropHandle? {
        if (pointsPx.size != 4) return null

        var bestCorner = -1
        var bestDistance = Float.MAX_VALUE
        pointsPx.forEachIndexed { index, point ->
            val distance = hypot(point.x - x, point.y - y)
            if (distance <= cornerRadius && distance < bestDistance) {
                bestCorner = index
                bestDistance = distance
            }
        }
        if (bestCorner >= 0) return CropHandle.Corner(bestCorner)

        var bestSide = -1
        var bestSideDistance = Float.MAX_VALUE
        for (side in 0 until 4) {
            val a = pointsPx[side]
            val b = pointsPx[(side + 1) % 4]
            val distance = distanceToSegment(x, y, a, b)
            if (distance <= sideTolerance && distance < bestSideDistance) {
                bestSide = side
                bestSideDistance = distance
            }
        }
        return if (bestSide >= 0) CropHandle.Side(bestSide) else null
    }

    /** Moves one corner freely, inside the image. */
    fun moveCorner(points: List<Point>, index: Int, dx: Float, dy: Float): List<Point> =
        points.toMutableList().also {
            val corner = it[index]
            it[index] = Point(
                x = (corner.x + dx).coerceIn(0f, 1f),
                y = (corner.y + dy).coerceIn(0f, 1f),
            )
        }

    /**
     * Moves a whole side: 0 = top, 1 = right, 2 = bottom, 3 = left.
     * The side keeps its own slant (both corners shift by the same amount) and
     * cannot cross the opposite side.
     */
    fun moveSide(points: List<Point>, side: Int, dx: Float, dy: Float): List<Point> {
        if (points.size != 4) return points
        val result = points.toMutableList()
        when (side) {
            0 -> {
                val limit = min(points[2].y, points[3].y) - MIN_GAP
                val delta = clamp(dy, -min(points[0].y, points[1].y), limit - max(points[0].y, points[1].y))
                result[0] = points[0].copy(y = points[0].y + delta)
                result[1] = points[1].copy(y = points[1].y + delta)
            }
            2 -> {
                val limit = max(points[0].y, points[1].y) + MIN_GAP
                val delta = clamp(dy, limit - min(points[2].y, points[3].y), 1f - max(points[2].y, points[3].y))
                result[2] = points[2].copy(y = points[2].y + delta)
                result[3] = points[3].copy(y = points[3].y + delta)
            }
            3 -> {
                val limit = min(points[1].x, points[2].x) - MIN_GAP
                val delta = clamp(dx, -min(points[3].x, points[0].x), limit - max(points[3].x, points[0].x))
                result[3] = points[3].copy(x = points[3].x + delta)
                result[0] = points[0].copy(x = points[0].x + delta)
            }
            1 -> {
                val limit = max(points[0].x, points[3].x) + MIN_GAP
                val delta = clamp(dx, limit - min(points[1].x, points[2].x), 1f - max(points[1].x, points[2].x))
                result[1] = points[1].copy(x = points[1].x + delta)
                result[2] = points[2].copy(x = points[2].x + delta)
            }
        }
        return result
    }

    /** One point per side, used to draw the side handles. */
    fun sideMidpoints(points: List<Point>): List<Point> =
        (0 until 4).map { side ->
            val a = points[side]
            val b = points[(side + 1) % 4]
            Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        }

    /**
     * The point of one side closest to ([x], [y]): where a finger on that edge
     * actually sits. The magnifier aims there, so what it shows is what moves.
     */
    fun closestOnSide(pointsPx: List<Point>, side: Int, x: Float, y: Float): Point =
        project(x, y, pointsPx[side], pointsPx[(side + 1) % 4])

    /** A side already crossing the opposite one cannot move any further. */
    private fun clamp(delta: Float, low: Float, high: Float): Float =
        if (high < low) 0f else delta.coerceIn(low, high)

    /** The point of segment [a]-[b] closest to ([x], [y]). */
    private fun project(x: Float, y: Float, a: Point, b: Point): Point {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared <= 1e-6f) return a
        val t = (((x - a.x) * abx + (y - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
        return Point(a.x + t * abx, a.y + t * aby)
    }

    private fun distanceToSegment(x: Float, y: Float, a: Point, b: Point): Float {
        val closest = project(x, y, a, b)
        return hypot(x - closest.x, y - closest.y)
    }
}
