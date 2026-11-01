package com.scanfromcam.domain.geometry

import com.scanfromcam.domain.model.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure polygon helpers. No Android or OpenCV types, so they are unit-testable.
 */
object Quads {

    /** Orders four corners as top-left, top-right, bottom-right, bottom-left. */
    fun order(points: List<Point>): List<Point> {
        require(points.size == 4) { "A quad needs exactly four points" }
        val bySum = points.sortedBy { it.x + it.y }
        val byDiff = points.sortedBy { it.y - it.x }
        val topLeft = bySum.first()
        val bottomRight = bySum.last()
        val topRight = byDiff.first()
        val bottomLeft = byDiff.last()
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    fun area(points: List<Point>): Float {
        if (points.size < 3) return 0f
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    /** IoU of two convex quads, used to decide whether the frame is stable. */
    fun iou(a: List<Point>, b: List<Point>): Float {
        val inter = intersectionArea(a, b)
        val union = area(a) + area(b) - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun intersectionArea(subject: List<Point>, clip: List<Point>): Float {
        var output = subject
        for (i in clip.indices) {
            if (output.isEmpty()) break
            val edgeStart = clip[i]
            val edgeEnd = clip[(i + 1) % clip.size]
            val input = output
            output = buildList {
                for (j in input.indices) {
                    val currentPoint = input[j]
                    val previousPoint = input[(j + input.size - 1) % input.size]
                    val currentInside = isInside(currentPoint, edgeStart, edgeEnd)
                    val previousInside = isInside(previousPoint, edgeStart, edgeEnd)
                    if (currentInside) {
                        if (!previousInside) {
                            lineIntersection(previousPoint, currentPoint, edgeStart, edgeEnd)
                                ?.let { add(it) }
                        }
                        add(currentPoint)
                    } else if (previousInside) {
                        lineIntersection(previousPoint, currentPoint, edgeStart, edgeEnd)
                            ?.let { add(it) }
                    }
                }
            }
        }
        return area(output)
    }

    private fun isInside(p: Point, a: Point, b: Point): Boolean {
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        return cross >= 0f
    }

    private fun lineIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Point? {
        val d = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
        if (abs(d) < 1e-6f) return null
        val a = p1.x * p2.y - p1.y * p2.x
        val b = p3.x * p4.y - p3.y * p4.x
        val x = (a * (p3.x - p4.x) - (p1.x - p2.x) * b) / d
        val y = (a * (p3.y - p4.y) - (p1.y - p2.y) * b) / d
        return Point(x, y)
    }

    /** Blend of two quads, corner by corner. */
    fun blend(from: List<Point>, to: List<Point>, alpha: Float): List<Point> =
        from.indices.map { i ->
            Point(
                x = from[i].x + (to[i].x - from[i].x) * alpha,
                y = from[i].y + (to[i].y - from[i].y) * alpha,
            )
        }

    fun scale(points: List<Point>, width: Int, height: Int): List<Point> =
        points.map { Point(it.x * width, it.y * height) }

    fun bounds(points: List<Point>): Pair<Point, Point> {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        return Point(minX, minY) to Point(max(maxX, minX), max(maxY, minY))
    }

    /** True when the four points are in order and the polygon does not cross itself. */
    fun isConvex(points: List<Point>): Boolean {
        if (points.size != 4) return false
        var sign = 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            when {
                cross > 1e-6f -> {
                    if (sign < 0) return false
                    sign = 1
                }
                cross < -1e-6f -> {
                    if (sign > 0) return false
                    sign = -1
                }
            }
        }
        return sign != 0
    }

    fun clampUnit(points: List<Point>): List<Point> =
        points.map { Point(min(max(it.x, 0f), 1f), min(max(it.y, 0f), 1f)) }
}

