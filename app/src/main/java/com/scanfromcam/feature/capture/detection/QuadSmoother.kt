package com.scanfromcam.feature.capture.detection

import com.scanfromcam.domain.geometry.Quads
import com.scanfromcam.domain.model.Point

/**
 * Smooths the detected quad across frames and reports when it has been stable
 * long enough to trigger an automatic capture.
 *
 * A per-corner dead zone (a corner only moves when it really moved) plus a
 * short ring buffer of recent quads, so "stable" means "every corner barely
 * changed over the last few frames" instead of "the last frame happened to
 * be close".
 */
class QuadSmoother(
    private val alpha: Float = 0.4f,
    private val stableFrames: Int = 4,
    private val iouThreshold: Float = 0.9f,
    /** Corner movement (normalized) below which the old corner is kept. */
    private val deadZone: Float = 0.012f,
    /** Max per-corner drift (normalized) across the window to call it stable. */
    private val tolerance: Float = 0.02f,
) {
    data class Result(val quad: List<Point>?, val isStable: Boolean, val covered: Boolean)

    private var current: List<Point>? = null
    private var stableCount = 0
    private var lastSeenAt = 0L
    private val window = ArrayDeque<List<Point>>()

    /** Last known quad, but only if it was observed recently. A capture that
     *  happens after the user moved the phone must not reuse an old quad.
     */
    fun latestIfFresh(maxAgeMs: Long, now: Long): List<Point>? {
        if (current == null) return null
        if (lastSeenAt == 0L) return null
        return if (now - lastSeenAt <= maxAgeMs) current else null
    }

    /** True when the document fills enough of the frame: cropping a page that
     *  covers less than [minFraction] of a side would upscale a tiny quad. */
    fun covers(minFraction: Float = 0.5f): Boolean {
        val quad = current ?: return false
        if (window.size < 3) return false
        val recent = window.takeLast(5).count { coverage(it) >= minFraction }
        return recent >= 3
    }

    fun add(incoming: List<Point>?, now: Long = 0L): Result {
        if (incoming == null) {
            stableCount = 0
            return Result(current, false, covers())
        }

        lastSeenAt = now

        val previous = current
        if (previous == null) {
            current = incoming
            stableCount = 1
            push(incoming)
            return Result(current, false, covers())
        }

        val overlap = Quads.iou(previous, incoming)
        val smoothed = List(4) { i ->
            val from = previous[i]
            val to = incoming[i]
            if (distance(from, to) < deadZone) from
            else Point(
                x = from.x + (to.x - from.x) * alpha,
                y = from.y + (to.y - from.y) * alpha,
            )
        }
        current = smoothed
        push(smoothed)
        stableCount = if (overlap >= iouThreshold) stableCount + 1 else 1
        val steady = stableCount >= stableFrames && drift() <= tolerance
        return Result(current, steady, covers())
    }

    private fun push(quad: List<Point>) {
        window.addLast(quad)
        while (window.size > 5) window.removeFirst()
    }

    /** Largest per-corner movement inside the window, in normalized units. */
    private fun drift(): Float {
        if (window.size < stableFrames) return Float.MAX_VALUE
        var worst = 0f
        for (corner in 0 until 4) {
            val xs = window.map { it[corner].x }
            val ys = window.map { it[corner].y }
            worst = maxOf(worst, xs.max() - xs.min(), ys.max() - ys.min())
        }
        return worst
    }

    private fun coverage(quad: List<Point>): Float {
        val (min, max) = Quads.bounds(quad)
        return maxOf(max.x - min.x, max.y - min.y)
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
