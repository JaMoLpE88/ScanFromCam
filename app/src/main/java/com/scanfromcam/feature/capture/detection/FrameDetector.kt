package com.scanfromcam.feature.capture.detection

import androidx.camera.core.ImageProxy
import com.scanfromcam.data.Cv
import com.scanfromcam.domain.geometry.QuadRotation
import com.scanfromcam.domain.geometry.Quads
import com.scanfromcam.domain.model.Point
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Turns a camera analysis frame into a document quad in normalized upright
 * coordinates (0..1), independent from the capture resolution.
 */
object FrameDetector {

    /** Reused between frames: the analyser is single threaded and this avoids
     *  allocating ~1 MB per analysed frame. */
    private var frameBuffer = ByteArray(0)

    fun detect(proxy: ImageProxy): List<Point>? {
        val gray = grayMat(proxy) ?: return null
        return try {
            val quad = Cv.detectQuad(gray) ?: return null
            val rotated = QuadRotation.rotate(quad, proxy.imageInfo.rotationDegrees)
            Quads.order(Quads.clampUnit(rotated))
        } finally {
            gray.release()
        }
    }

    /** Width/height ratio of the upright frame. */
    fun uprightAspect(proxy: ImageProxy): Float {
        val rotation = ((proxy.imageInfo.rotationDegrees % 360) + 360) % 360
        val width = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
        val height = if (rotation == 90 || rotation == 270) proxy.width else proxy.height
        return if (height == 0) 3f / 4f else width.toFloat() / height.toFloat()
    }


    private fun grayMat(proxy: ImageProxy): Mat? {
        val plane = proxy.planes.firstOrNull() ?: return null
        if (plane.pixelStride != 1) return null

        val width = proxy.width
        val height = proxy.height
        if (width <= 0 || height <= 0) return null

        val rowStride = plane.rowStride
        val buffer = plane.buffer
        val size = rowStride * height
        val bytes = if (frameBuffer.size == size) frameBuffer else ByteArray(size).also { frameBuffer = it }
        buffer.rewind()
        val readable = minOf(buffer.remaining(), size)
        buffer.get(bytes, 0, readable)

        val full = Mat(height, rowStride, CvType.CV_8UC1)
        try {
            full.put(0, 0, bytes)
            return full.submat(0, height, 0, width).clone()
        } finally {
            full.release()
        }
    }
}


