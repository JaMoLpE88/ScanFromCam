package com.scanfromcam.domain.geometry

import com.scanfromcam.domain.model.Point

/**
 * Maps normalized quad coordinates from camera orientation to upright orientation.
 * Kept pure so the mapping can be tested without a device.
 */
object QuadRotation {

    fun rotate(points: List<Point>, degrees: Int): List<Point> =
        when (((degrees % 360) + 360) % 360) {
            90 -> points.map { Point(1f - it.y, it.x) }
            180 -> points.map { Point(1f - it.x, 1f - it.y) }
            270 -> points.map { Point(it.y, 1f - it.x) }
            else -> points
        }
}
