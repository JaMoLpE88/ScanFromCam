package com.scanfromcam.feature.capture.detection

import androidx.camera.core.ImageProxy
import com.scanfromcam.data.Cv
import com.scanfromcam.data.Focus
import com.scanfromcam.domain.geometry.QuadRotation
import com.scanfromcam.domain.geometry.Quads
import com.scanfromcam.domain.model.Point
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Turns a camera analysis frame into a document quad in normalized upright
 * coordinates (0..1), independent from the capture resolution, together with a
 * focus reading of the page it found.
 */
object FrameDetector {

    /** What one analysis frame has to say: the quad, if any, and how crisp it is. */
    data class Detection(val quad: List<Point>?, val focus: Focus?)

    /** Reused between frames: the analyser is single threaded and this avoids
     *  allocating ~1 MB per analysed frame. */
    private var frameBuffer = ByteArray(0)

    /** Backing store of the frame: the returned mat is only a view of it. */
    private var frameBase: Mat? = null

    fun detect(proxy: ImageProxy): Detection {
        val gray = grayMat(proxy) ?: return Detection(null, null)
        return try {
            val quad = Cv.detectQuad(gray) ?: return Detection(null, null)
            val focus = runCatching { Cv.measureFocus(gray, quad) }.getOrNull()
            val rotated = QuadRotation.rotate(quad, proxy.imageInfo.rotationDegrees)
            Detection(Quads.order(Quads.clampUnit(rotated)), focus)
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

    /**
     * Luma plane as a matrix. The rows are padded to the plane stride, so the
     * frame is read as it arrives and only a view of the useful width is handed
     * out: copying it per frame is one megabyte of work for nothing.
     */
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

        val cached = frameBase
        val base = if (cached != null && cached.rows() == height && cached.cols() == rowStride) {
            cached
        } else {
            cached?.release()
            Mat(height, rowStride, CvType.CV_8UC1).also { frameBase = it }
        }
        base.put(0, 0, bytes)
        return base.submat(0, height, 0, width)
    }
}
