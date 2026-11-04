package com.scanfromcam.data

import android.graphics.Bitmap
import com.scanfromcam.domain.geometry.CropHandles
import com.scanfromcam.domain.geometry.Quads
import com.scanfromcam.domain.model.Point
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as CvPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * All OpenCV work lives here: quad detection and refinement, perspective
 * correction and the single "Optimizar" enhancement pipeline.
 *
 * Detection runs two independent candidate generators (edge contours and the
 * bright page blob), scores each quad by area, rectangularity, edge support and
 * paper contrast, refines the winner down to sub-pixel corners, and the
 * enhancement pass removes the illumination of the room before it touches
 * contrast or colour.
 */
object Cv {

    private const val ANALYSIS_SIDE = 640.0
    private const val OUTPUT_MAX_SIDE = 2600.0
    private const val MIN_QUAD_AREA = 0.02f

    /** Side of the patch that [measureFocus] analyses; bigger costs time, smaller loses detail. */
    private const val FOCUS_SIDE = 480.0

    /** How far a side may travel to reach its edge, as a share of the shortest image side. */
    private const val SNAP_BAND = 0.045f

    /** Points sampled along a side when looking for the line under it. */
    private const val SNAP_SAMPLES = 24

    /** Gradient (8 bit) that counts as a real edge when snapping a side. */
    private const val SNAP_MIN_EDGE = 26.0

    /** Channel spread that separates colour from black and white printing. */
    private const val CHROMA_LIMIT = 14.0

    /** Share of coloured pixels below which a page is worth storing in one channel. */
    private const val COLOUR_SHARE = 0.004f

    private const val MIN_AREA_RATIO = 0.10
    private const val MAX_AREA_RATIO = 0.97
    private const val MIN_RECTANGULARITY = 0.70
    private const val MIN_EDGE_SUPPORT = 0.5
    private const val EDGE_SAMPLES = 20

    /**
     * Detects the document quad in a grayscale frame. Candidates come from two
     * independent generators and are scored by area, rectangularity, how well
     * their sides sit on real edges, how bright their paper is and how much it
     * differs from what surrounds it. That combination is what separates a
     * sheet of paper from a table or a door frame. Coordinates are normalized
     * 0..1.
     */
    fun detectQuad(gray: Mat): List<Point>? {
        val width = gray.cols()
        val height = gray.rows()
        if (width <= 0 || height <= 0) return null

        val scale = ANALYSIS_SIDE / max(width, height)
        val small = Mat()
        val blurred = Mat()
        val edges = Mat()
        val support = Mat()
        val page = Mat()
        val kernel = Mat()
        try {
            Imgproc.resize(gray, small, Size(width * scale, height * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)

            val median = medianValue(blurred)
            val lower = max(12.0, median * 0.55)
            val upper = min(255.0, max(lower + 25.0, median * 1.4))
            Imgproc.Canny(blurred, edges, lower, upper)

            // Edge support comes from the raw gradient, not from Canny: a page
            // whose border is too soft for Canny still has a measurable step.
            val gradient = gradientMagnitude(blurred)
            gradient.convertTo(support, CvType.CV_8U)
            gradient.release()
            Imgproc.threshold(support, support, 40.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.dilate(support, support, kernel, CvPoint(-1.0, -1.0), 2)

            // A sheet of paper is the largest bright blob in the frame. Otsu
            // finds it even when the printed border is too soft for Canny.
            Imgproc.threshold(blurred, page, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            val close = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
            Imgproc.morphologyEx(page, page, Imgproc.MORPH_CLOSE, close)
            close.release()

            val frameArea = (small.cols() * small.rows()).toDouble()
            var best: List<Point>? = null
            var bestScore = 0.0

            fun consider(contours: List<MatOfPoint>) {
                for (contour in contours) {
                    val contourArea = Imgproc.contourArea(contour)
                    if (contourArea <= 0.0) continue
                    val areaRatio = contourArea / frameArea
                    if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO) continue

                    val curve = MatOfPoint2f(*contour.toArray())
                    val approx = MatOfPoint2f()
                    try {
                        val perimeter = Imgproc.arcLength(curve, true)
                        Imgproc.approxPolyDP(curve, approx, 0.02 * perimeter, true)
                        if (approx.total() != 4L) continue

                        val corners = approx.toArray()
                        if (!Imgproc.isContourConvex(MatOfPoint(*corners))) continue

                        val quad = corners.map {
                            Point((it.x / small.cols()).toFloat(), (it.y / small.rows()).toFloat())
                        }
                        val ordered = Quads.order(quad)

                        val rectangularity = rectangularityOf(curve, contourArea)
                        if (rectangularity < MIN_RECTANGULARITY) continue

                        val supportRatio = edgeSupport(ordered, support)
                        if (supportRatio < MIN_EDGE_SUPPORT) continue

                        val aspect = apparentAspect(ordered)
                        if (aspect < 0.2f || aspect > 5.0f) continue

                        // A sheet you are scanning is fully inside the frame. A
                        // quad clipped by two or more borders is the table, the
                        // floor or the frame itself, and its real shape is unknown.
                        if (sidesTouchingBorder(ordered) >= 2) continue

                        val interior = interiorBrightness(ordered, blurred)
                        val contrast = abs(interior - ringBrightness(ordered, blurred))
                        val score = areaRatio * rectangularity * supportRatio *
                            (0.55 + 0.45 * interior) * (0.75 + 0.5 * contrast)
                        if (score > bestScore) {
                            bestScore = score
                            best = ordered
                        }
                    } finally {
                        curve.release()
                        approx.release()
                    }
                }
            }

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            consider(contours)
            contours.forEach { it.release() }

            val pageContours = ArrayList<MatOfPoint>()
            Imgproc.findContours(page, pageContours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            consider(pageContours)
            pageContours.forEach { it.release() }

            return best
        } finally {
            small.release()
            blurred.release()
            edges.release()
            support.release()
            page.release()
            kernel.release()
        }
    }

    /** Area of the contour divided by the area of its minimum rotated rectangle. */
    private fun rectangularityOf(curve: MatOfPoint2f, contourArea: Double): Double {
        val rect = Imgproc.minAreaRect(curve)
        val rectArea = rect.size.width * rect.size.height
        if (rectArea <= 0.0) return 0.0
        return (contourArea / rectArea).coerceIn(0.0, 1.0)
    }

    /** Fraction of the quad perimeter that lands on an edge pixel. */
    private fun edgeSupport(quad: List<Point>, support: Mat, tolerance: Int = 1): Float {
        if (quad.size != 4) return 0f
        val width = support.cols()
        val height = support.rows()
        var hits = 0
        var samples = 0
        for (i in quad.indices) {
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            for (step in 0 until EDGE_SAMPLES) {
                val t = (step + 0.5f) / EDGE_SAMPLES
                val x = ((a.x + (b.x - a.x) * t) * width).toInt()
                val y = ((a.y + (b.y - a.y) * t) * height).toInt()
                var found = false
                for (dy in -tolerance..tolerance) {
                    if (found) break
                    for (dx in -tolerance..tolerance) {
                        val px = x + dx
                        val py = y + dy
                        if (px !in 0 until width || py !in 0 until height) continue
                        if (support.get(py, px)[0] > 0.0) {
                            found = true
                            break
                        }
                    }
                }
                if (found) hits++
                samples++
            }
        }
        return if (samples == 0) 0f else hits.toFloat() / samples
    }

    private fun meanOn(src: Mat, mask: Mat): Double {
        val mean = Core.mean(src, mask)
        return mean.`val`[0] / 255.0
    }

    private fun interiorBrightness(quad: List<Point>, gray: Mat): Double {
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        return try {
            val points = quad.map {
                CvPoint((it.x * gray.cols()).toDouble(), (it.y * gray.rows()).toDouble())
            }.toTypedArray()
            Imgproc.fillConvexPoly(mask, MatOfPoint(*points), Scalar(255.0))
            meanOn(gray, mask)
        } catch (t: Throwable) {
            0.5
        } finally {
            mask.release()
        }
    }

    /** Mean brightness just outside the quad: furniture, floor, desk. */
    private fun ringBrightness(quad: List<Point>, gray: Mat): Double {
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        try {
            val outer = grow(quad, 0.06f).map {
                CvPoint((it.x * gray.cols()).toDouble(), (it.y * gray.rows()).toDouble())
            }.toTypedArray()
            val inner = quad.map {
                CvPoint((it.x * gray.cols()).toDouble(), (it.y * gray.rows()).toDouble())
            }.toTypedArray()
            Imgproc.fillConvexPoly(mask, MatOfPoint(*outer), Scalar(255.0))
            Imgproc.fillConvexPoly(mask, MatOfPoint(*inner), Scalar(0.0))
            return meanOn(gray, mask)
        } catch (t: Throwable) {
            return 0.5
        } finally {
            mask.release()
        }
    }

    private fun grow(quad: List<Point>, amount: Float): List<Point> {
        val (min, max) = Quads.bounds(quad)
        val cx = (min.x + max.x) / 2f
        val cy = (min.y + max.y) / 2f
        return quad.map { p ->
            Point(
                (p.x + (p.x - cx) * amount).coerceIn(0f, 1f),
                (p.y + (p.y - cy) * amount).coerceIn(0f, 1f),
            )
        }
    }

    private fun sidesTouchingBorder(quad: List<Point>, margin: Float = 0.02f): Int {
        var count = 0
        if (quad.any { it.x <= margin }) count++
        if (quad.any { it.x >= 1f - margin }) count++
        if (quad.any { it.y <= margin }) count++
        if (quad.any { it.y >= 1f - margin }) count++
        return count
    }

    private fun apparentAspect(quad: List<Point>): Float {
        val top = hypot((quad[1].x - quad[0].x).toDouble(), (quad[1].y - quad[0].y).toDouble())
        val right = hypot((quad[2].x - quad[1].x).toDouble(), (quad[2].y - quad[1].y).toDouble())
        val bottom = hypot((quad[3].x - quad[2].x).toDouble(), (quad[3].y - quad[2].y).toDouble())
        val left = hypot((quad[0].x - quad[3].x).toDouble(), (quad[0].y - quad[3].y).toDouble())
        val horizontal = (top + bottom) / 2.0
        val vertical = (left + right) / 2.0
        if (vertical <= 1e-6) return 0f
        return (horizontal / vertical).toFloat()
    }

    /**
     * Snaps the four sides of [quad] onto the strongest gradient near each
     * side and re-intersects the fitted lines. Doing it here is what makes the
     * crop sit exactly on the paper instead of two or three pixels outside it.
     *
     * Returns normalized corners, or null when the fit is not trustworthy.
     */
    fun refineQuad(gray: Mat, quad: List<Point>): List<Point>? {
        if (quad.size != 4 || gray.cols() <= 0 || gray.rows() <= 0) return null
        val width = gray.cols()
        val height = gray.rows()
        val magnitude = gradientMagnitude(gray)
        try {
            val radius = max(2, (min(width, height) * 0.012).toInt())
            val lines = arrayOfNulls<DoubleArray>(4)

            for (side in 0 until 4) {
                val a = quad[side]
                val b = quad[(side + 1) % 4]
                val ax = a.x * width
                val ay = a.y * height
                val bx = b.x * width
                val by = b.y * height
                val ex = bx - ax
                val ey = by - ay
                val length = hypot(ex, ey)
                if (length < 8.0) continue
                val nx = -ey / length
                val ny = ex / length

                val points = ArrayList<DoubleArray>(24)
                val strengths = ArrayList<Double>(24)
                for (step in 1 until 24) {
                    val t = 0.10 + 0.80 * step / 24.0
                    val px = ax + ex * t
                    val py = ay + ey * t
                    var bestStrength = 0.0
                    var bestOffset = 0.0
                    var offset = -radius.toDouble()
                    while (offset <= radius.toDouble()) {
                        val sx = (px + nx * offset).toInt()
                        val sy = (py + ny * offset).toInt()
                        if (sx in 1 until width - 1 && sy in 1 until height - 1) {
                            val value = magnitude.get(sy, sx)[0]
                            if (value > bestStrength) {
                                bestStrength = value
                                bestOffset = offset
                            }
                        }
                        offset += 1.0
                    }
                    if (bestStrength > 0.0) {
                        points.add(doubleArrayOf(px + nx * bestOffset, py + ny * bestOffset))
                        strengths.add(bestStrength)
                    }
                }
                if (points.size < 10) continue

                val sorted = strengths.sorted()
                val median = sorted[sorted.size / 2]
                val keep = points.indices.filter { strengths[it] >= max(median * 0.9, 6.0) }
                if (keep.size < 10) continue
                lines[side] = fitLine(keep.map { points[it] })
            }

            if (lines.any { it == null }) return null

            val refined = ArrayList<Point>(4)
            for (corner in 0 until 4) {
                val first = lines[(corner + 3) % 4] ?: return null
                val second = lines[corner] ?: return null
                val hit = intersect(first, second) ?: return null
                refined.add(Point((hit[0] / width).toFloat(), (hit[1] / height).toFloat()))
            }

            val ordered = Quads.order(Quads.clampUnit(refined))
            if (!Quads.isConvex(ordered)) return null
            val before = Quads.area(quad)
            val after = Quads.area(ordered)
            if (before <= 0f || after / before !in 0.82f..1.22f) return null
            // A refinement that moves a corner far is a misfit, not a fix.
            for (i in 0 until 4) {
                if (hypot((quad[i].x - ordered[i].x).toDouble(), (quad[i].y - ordered[i].y).toDouble()) > 0.05) {
                    return null
                }
            }
            return ordered
        } catch (t: Throwable) {
            return null
        } finally {
            magnitude.release()
        }
    }

    private fun gradientMagnitude(gray: Mat): Mat {
        val gx = Mat()
        val gy = Mat()
        val fx = Mat()
        val fy = Mat()
        try {
            Imgproc.Sobel(gray, gx, CvType.CV_16S, 1, 0, 3)
            Imgproc.Sobel(gray, gy, CvType.CV_16S, 0, 1, 3)
            gx.convertTo(fx, CvType.CV_32F)
            gy.convertTo(fy, CvType.CV_32F)
            val magnitude = Mat()
            Core.magnitude(fx, fy, magnitude)
            return magnitude
        } finally {
            gx.release()
            gy.release()
            fx.release()
            fy.release()
        }
    }

    /** Total least squares line through [points]: point + direction. */
    private fun fitLine(points: List<DoubleArray>): DoubleArray {
        var meanX = 0.0
        var meanY = 0.0
        for (point in points) {
            meanX += point[0]
            meanY += point[1]
        }
        meanX /= points.size
        meanY /= points.size

        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (point in points) {
            val dx = point[0] - meanX
            val dy = point[1] - meanY
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        val theta = 0.5 * atan2(2.0 * sxy, sxx - syy)
        return doubleArrayOf(meanX, meanY, cos(theta), sin(theta))
    }

    private fun intersect(first: DoubleArray, second: DoubleArray): DoubleArray? {
        val denominator = first[2] * second[3] - first[3] * second[2]
        if (abs(denominator) < 1e-6) return null
        val dx = second[0] - first[0]
        val dy = second[1] - first[1]
        val t = (dx * second[3] - dy * second[2]) / denominator
        return doubleArrayOf(first[0] + t * first[2], first[1] + t * first[3])
    }

    /** Values below the low percentile and above the high one, as ink and paper. */
    private fun percentileRange(gray: Mat, lowPct: Double, highPct: Double): Pair<Double, Double> {
        val total = gray.total().toInt()
        if (total <= 0) return 0.0 to 255.0
        val data = ByteArray(total)
        gray.get(0, 0, data)
        val histogram = IntArray(256)
        for (value in data) histogram[value.toInt() and 0xFF]++

        val lowTarget = (total * lowPct).toInt()
        var accumulated = 0
        var black = 0.0
        for (i in 0 until 256) {
            accumulated += histogram[i]
            if (accumulated >= lowTarget) {
                black = i.toDouble()
                break
            }
        }

        val highTarget = (total * (1.0 - highPct)).toInt()
        accumulated = 0
        var white = 255.0
        for (i in 255 downTo 0) {
            accumulated += histogram[i]
            if (accumulated >= highTarget) {
                white = i.toDouble()
                break
            }
        }
        return black to white
    }

    private fun medianValue(gray: Mat): Double {
        val total = gray.total().toInt()
        if (total <= 0) return 128.0
        val data = ByteArray(total)
        gray.get(0, 0, data)
        val histogram = IntArray(256)
        for (value in data) {
            histogram[value.toInt() and 0xFF]++
        }
        val half = total / 2
        var accumulated = 0
        for (i in 0 until 256) {
            accumulated += histogram[i]
            if (accumulated >= half) return i.toDouble()
        }
        return 128.0
    }

    /** Detects the document directly on a photo, so the crop never depends on
     *  the preview frame having exactly the same framing. */
    fun detectQuad(source: Bitmap): List<Point>? {
        val mat = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(source, mat)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            detectQuad(gray)
        } catch (t: Throwable) {
            null
        } finally {
            mat.release()
            gray.release()
        }
    }

    /** Detects on the still and snaps its sides onto the real edges. */
    fun detectQuadAndRefine(source: Bitmap): List<Point>? {
        val mat = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(source, mat)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            val quad = detectQuad(gray) ?: return null
            refineQuad(gray, quad) ?: quad
        } catch (t: Throwable) {
            null
        } finally {
            mat.release()
            gray.release()
        }
    }

    /** Refines [quad] against the still, falling back to the quad itself. */
    fun refineQuad(source: Bitmap, quad: List<Point>): List<Point> {
        val mat = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(source, mat)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            refineQuad(gray, quad) ?: quad
        } catch (t: Throwable) {
            quad
        } finally {
            mat.release()
            gray.release()
        }
    }

    /** Crops and rectifies [source] using [quad] (normalized, upright coordinates). */
    fun warp(source: Bitmap, quad: List<Point>): Bitmap {
        val area = Quads.area(quad)
        require(area >= MIN_QUAD_AREA) { "Cuadrilátero demasiado pequeño" }
        require(Quads.isConvex(quad)) { "Cuadrilátero no válido" }

        val mat = Mat()
        Utils.bitmapToMat(source, mat)
        val srcPoints = MatOfPoint2f()
        val dstPoints = MatOfPoint2f()
        var transform: Mat? = null
        val warped = Mat()
        try {
            val scaled = Quads.scale(quad, source.width, source.height)
            srcPoints.fromArray(
                *scaled.map { CvPoint(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
            )

            var outWidth = max(distance(scaled[0], scaled[1]), distance(scaled[3], scaled[2]))
            var outHeight = max(distance(scaled[0], scaled[3]), distance(scaled[1], scaled[2]))
            val longest = max(outWidth, outHeight)
            if (longest > OUTPUT_MAX_SIDE) {
                val factor = OUTPUT_MAX_SIDE / longest
                outWidth *= factor
                outHeight *= factor
            }
            val safeWidth = outWidth.coerceAtLeast(64.0)
            val safeHeight = outHeight.coerceAtLeast(64.0)

            // Downscaling without a prefilter aliases thin text strokes, which
            // is exactly what makes a scan look soft. Blur by the shrink factor.
            val shrinkX = safeWidth / max(1.0, srcQuadWidth(scaled))
            val shrinkY = safeHeight / max(1.0, srcQuadHeight(scaled))
            val shrink = max(shrinkX, shrinkY)
            if (shrink < 0.75) {
                Imgproc.GaussianBlur(mat, mat, Size(0.0, 0.0), (0.5 / shrink - 0.5).coerceIn(0.3, 2.0))
            }

            dstPoints.fromArray(
                CvPoint(0.0, 0.0),
                CvPoint(safeWidth, 0.0),
                CvPoint(safeWidth, safeHeight),
                CvPoint(0.0, safeHeight),
            )
            transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            Imgproc.warpPerspective(
                mat,
                warped,
                transform,
                Size(safeWidth, safeHeight),
                Imgproc.INTER_LINEAR,
                Core.BORDER_REPLICATE,
            )

            val output = Bitmap.createBitmap(
                safeWidth.toInt(), safeHeight.toInt(), Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(warped, output)
            return output
        } finally {
            mat.release()
            srcPoints.release()
            dstPoints.release()
            transform?.release()
            warped.release()
        }
    }

    private fun srcQuadWidth(scaled: List<Point>): Double =
        (distance(scaled[0], scaled[1]) + distance(scaled[3], scaled[2])) / 2.0

    private fun srcQuadHeight(scaled: List<Point>): Double =
        (distance(scaled[0], scaled[3]) + distance(scaled[1], scaled[2])) / 2.0

    /**
     * "Optimizar": illumination first, then the black and white point of this
     * page, then local contrast, and colour last.
     *
     * 1. Estimate the illumination of the scene and divide it out. Shadows,
     *    gradients and the warm cast of a lamp are low frequency; paper is
     *    uniform. This is the step that makes a photo read as a scan.
     * 2. Pin the black and white point of each channel to this page's ink and
     *    paper (white patch balance), which also neutralises a colour cast.
     * 3. Lift local contrast with CLAHE and sharpen only the luminance, so text
     *    gets crisper without fringing stamps or signatures.
     * 4. Bring the chroma back up: flattening luminance washes colour out.
     */
    fun optimize(source: Bitmap): Bitmap {
        val rgba = Mat()
        val rgb = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            val enhanced = enhanceDocument(rgb)
            val outRgba = Mat()
            try {
                Imgproc.cvtColor(enhanced, outRgba, Imgproc.COLOR_RGB2RGBA)
                enhanced.release()
                val output = Bitmap.createBitmap(
                    source.width, source.height, Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(outRgba, output)
                return output
            } finally {
                outRgba.release()
            }
        } finally {
            rgba.release()
            rgb.release()
        }
    }

    /**
     * Focus of one frame. When [quad] is known the measurement is taken inside
     * it, so a sharp desk around a blurred page cannot pass for a sharp page;
     * the outer rim is left out because the border of the sheet is a strong
     * edge that would keep a blurred page looking crisp.
     */
    fun measureFocus(gray: Mat, quad: List<Point>? = null): Focus {
        val patch = Mat(gray, focusRegion(gray, quad))
        val work = Mat()
        val laplacian = Mat()
        val lapMean = MatOfDouble()
        val lapDeviation = MatOfDouble()
        val edgeMean = MatOfDouble()
        val edgeDeviation = MatOfDouble()
        var edges: Mat? = null
        try {
            val scale = min(1.0, FOCUS_SIDE / max(1, max(patch.cols(), patch.rows())).toDouble())
            if (scale < 1.0) {
                Imgproc.resize(patch, work, Size(), scale, scale, Imgproc.INTER_AREA)
            } else {
                patch.copyTo(work)
            }

            Imgproc.Laplacian(work, laplacian, CvType.CV_64F, 3)
            Core.meanStdDev(laplacian, lapMean, lapDeviation)

            // Edge energy of the same patch: what the page has to lose when it
            // is soft, and the only reliable sign that it has nothing at all.
            val magnitude = gradientMagnitude(work)
            edges = magnitude
            Core.meanStdDev(magnitude, edgeMean, edgeDeviation)

            val lapSpread = lapDeviation.get(0, 0)[0]
            val edgeSpread = edgeDeviation.get(0, 0)[0]
            return Focus(sharpness = lapSpread * lapSpread, detail = edgeSpread * edgeSpread)
        } finally {
            patch.release()
            work.release()
            laplacian.release()
            lapMean.release()
            lapDeviation.release()
            edgeMean.release()
            edgeDeviation.release()
            edges?.release()
        }
    }

    /**
     * Moves one side of [quad] onto the strongest edge close to it. Sides are
     * axis locked in the editor, so the whole side travels along its own normal
     * and the median of what the samples see wins: a single dark speck cannot
     * drag the crop. Returns null when the image has no clear line there.
     */
    fun snapSide(gray: Mat, quad: List<Point>, side: Int, band: Float = SNAP_BAND): List<Point>? {
        if (quad.size != 4) return null
        val width = gray.cols()
        val height = gray.rows()
        if (width < 8 || height < 8) return null

        val horizontal = side == 0 || side == 2
        val reach = max(2, (band * if (horizontal) height else width).toInt())
        val magnitude = gradientMagnitude(gray)
        val edges = Mat()
        try {
            magnitude.convertTo(edges, CvType.CV_8U)

            val a = quad[side]
            val b = quad[(side + 1) % 4]
            val ax = a.x * width
            val ay = a.y * height
            val bx = b.x * width
            val by = b.y * height

            val offsets = ArrayList<Float>(SNAP_SAMPLES)
            for (sample in 1..SNAP_SAMPLES) {
                val t = sample.toFloat() / (SNAP_SAMPLES + 1)
                val px = ax + (bx - ax) * t
                val py = ay + (by - ay) * t
                var bestOffset = 0f
                var bestValue = 0.0
                for (step in -reach..reach) {
                    val x = (px + if (horizontal) 0f else step.toFloat()).toInt()
                    val y = (py + if (horizontal) step.toFloat() else 0f).toInt()
                    if (x < 0 || y < 0 || x >= width || y >= height) continue
                    val value = edges.get(y, x)[0]
                    if (value > bestValue) {
                        bestValue = value
                        bestOffset = step.toFloat()
                    }
                }
                if (bestValue >= SNAP_MIN_EDGE) offsets.add(bestOffset)
            }
            if (offsets.size * 2 < SNAP_SAMPLES) return null

            val shift = medianOf(offsets)
            if (abs(shift) < 1.5f) return null

            val normalised = shift / (if (horizontal) height else width)
            return CropHandles.moveSide(
                points = quad,
                side = side,
                dx = if (horizontal) 0f else normalised,
                dy = if (horizontal) normalised else 0f,
            )
        } finally {
            magnitude.release()
            edges.release()
        }
    }

    /** Grayscale copy of [source], for the editors that work on the full image. */
    fun grayscale(source: Bitmap): Mat {
        val rgba = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return gray
        } finally {
            rgba.release()
        }
    }

    /**
     * Writes a page as JPEG. A sheet with no colour worth keeping goes out in
     * one channel: the same picture, roughly a third fewer bytes and the flat
     * look of a scan. Returns false so the caller can store it as it is.
     */
    fun savePageJpeg(source: Bitmap, file: File, quality: Int): Boolean {
        val rgba = Mat()
        val rgb = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            if (!isAchromatic(rgb)) return false

            val gray = Mat()
            val options = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
            try {
                Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
                file.parentFile?.mkdirs()
                return Imgcodecs.imwrite(file.absolutePath, gray, options)
            } finally {
                gray.release()
                options.release()
            }
        } catch (_: Throwable) {
            return false
        } finally {
            rgba.release()
            rgb.release()
        }
    }

    /** True when almost every pixel is grey: black type on white paper. */
    private fun isAchromatic(rgb: Mat): Boolean {
        val channels = ArrayList<Mat>(3)
        Core.split(rgb, channels)
        val highest = Mat()
        val lowest = Mat()
        val chroma = Mat()
        val coloured = Mat()
        try {
            Core.max(channels[0], channels[1], highest)
            Core.max(highest, channels[2], highest)
            Core.min(channels[0], channels[1], lowest)
            Core.min(lowest, channels[2], lowest)
            Core.subtract(highest, lowest, chroma)
            Imgproc.threshold(chroma, coloured, CHROMA_LIMIT, 255.0, Imgproc.THRESH_BINARY)
            val pixels = max(1, chroma.cols() * chroma.rows())
            val share = Core.countNonZero(coloured).toFloat() / pixels
            return share < COLOUR_SHARE
        } finally {
            channels.forEach { runCatching { it.release() } }
            highest.release()
            lowest.release()
            chroma.release()
            coloured.release()
        }
    }

    private fun focusRegion(gray: Mat, quad: List<Point>?): Rect {
        val width = gray.cols()
        val height = gray.rows()
        if (quad == null || quad.size != 4) return Rect(0, 0, width, height)

        val (low, high) = Quads.bounds(quad)
        val left = (low.x * width).toInt().coerceIn(0, width - 1)
        val top = (low.y * height).toInt().coerceIn(0, height - 1)
        val right = (high.x * width).toInt().coerceIn(left + 1, width)
        val bottom = (high.y * height).toInt().coerceIn(top + 1, height)

        val marginX = ((right - left) * 0.06).toInt()
        val marginY = ((bottom - top) * 0.06).toInt()
        val cropWidth = right - left - 2 * marginX
        val cropHeight = bottom - top - 2 * marginY
        return if (cropWidth < 16 || cropHeight < 16) {
            Rect(left, top, right - left, bottom - top)
        } else {
            Rect(left + marginX, top + marginY, cropWidth, cropHeight)
        }
    }

    private fun medianOf(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }

    private fun enhanceDocument(rgb: Mat): Mat {
        // 1. Illumination flattening, driven by the luminance channel only.
        //    The background is a smooth float estimate: doing it in 8 bits is
        //    what turns paper into banded, grainy grey.
        val luminance = luminanceOf(rgb)
        val background = paperBackground(luminance)
        luminance.release()

        val ones = Mat(background.size(), CvType.CV_32F, Scalar(1.0))
        val gain = Mat()
        Core.divide(ones, background, gain, PAPER_TARGET / 255.0)
        ones.release()
        background.release()

        // A shadow can be lifted, but never by an unbounded factor: clipping the
        // gain keeps a dark object from turning into grey noise.
        val ceiling = Mat(gain.size(), CvType.CV_32F, Scalar(2.4))
        val floor = Mat(gain.size(), CvType.CV_32F, Scalar(0.65))
        Core.min(gain, ceiling, gain)
        Core.max(gain, floor, gain)
        ceiling.release()
        floor.release()

        // One channel at a time: a full float copy of the page costs four times
        // the memory of an 8-bit channel and this runs while a photo is open.
        val flatChannels = ArrayList<Mat>(3)
        Core.split(rgb, flatChannels)
        for (index in 0 until 3) {
            val channel = Mat()
            flatChannels[index].convertTo(channel, CvType.CV_32F, 1.0 / 255.0)
            Core.multiply(channel, gain, channel)
            val flattenedChannel = Mat()
            channel.convertTo(flattenedChannel, CvType.CV_8U, 255.0)
            channel.release()
            flatChannels[index].release()
            flatChannels[index] = flattenedChannel
        }
        gain.release()

        // 2. Grey world over the paper: the mean of the sheet is pushed back to
        //    grey, which is what removes the lamp's warm cast without touching
        //    the ink. The darkest quarter (text) is left out of the mean.
        val flattened8 = Mat()
        Core.merge(flatChannels, flattened8)

        val grey = Mat()
        Imgproc.cvtColor(flattened8, grey, Imgproc.COLOR_RGB2GRAY)
        flattened8.release()
        val (paperLow, _) = percentileRange(grey, 0.25, 0.999)
        val paperMask = Mat()
        Imgproc.threshold(grey, paperMask, paperLow, 255.0, Imgproc.THRESH_BINARY)
        grey.release()

        val paperMeans = (0 until 3).map { Core.mean(flatChannels[it], paperMask).`val`[0] }
        paperMask.release()
        val target = if (paperMeans.isEmpty()) 0.0 else paperMeans.average()
        if (target > 20.0) {
            for (index in 0 until 3) {
                val mean = paperMeans[index]
                if (mean <= 1.0) continue
                val scale = (target / mean).coerceIn(0.7, 1.4)
                if (abs(scale - 1.0) > 0.01) {
                    val balanced = Mat()
                    flatChannels[index].convertTo(balanced, flatChannels[index].type(), scale, 0.0)
                    flatChannels[index].release()
                    flatChannels[index] = balanced
                }
            }
        }

        val balanced = Mat()
        Core.merge(flatChannels, balanced)
        flatChannels.forEach { runCatching { it.release() } }

        // 3. Global levels per luminance, then sharpening restricted to edges.
        //    A global unsharp or CLAHE would amplify the paper's own texture;
        //    masking the sharpen to real edges leaves the sheet flat and clean.
        val lab = Mat()
        Imgproc.cvtColor(balanced, lab, Imgproc.COLOR_RGB2Lab)
        val channels = ArrayList<Mat>(3)
        Core.split(lab, channels)
        lab.release()

        val leveled = Mat()
        // The paper is the majority of a document: pinning the 97th percentile
        // to white is what gives the flat, bright sheet of a scan. Without it
        // the page stays grey and every leftover illumination ripple shows.
        val (black, white) = percentileRange(channels[0], 0.004, 0.97)
        if (white - black > 24.0) {
            val alpha = 255.0 / (white - black)
            channels[0].convertTo(leveled, channels[0].type(), alpha, -black * alpha)
        } else {
            channels[0].copyTo(leveled)
        }
        channels[0].release()

        val lifted = liftHighlights(leveled)
        leveled.release()
        val sharpened = edgeMaskedSharpen(lifted)
        lifted.release()

        // 4. Flattening luminance washes colour out: bring chroma back.
        val chromaA = Mat()
        val chromaB = Mat()
        channels[1].convertTo(chromaA, channels[1].type(), 1.05, -6.4)
        channels[2].convertTo(chromaB, channels[2].type(), 1.05, -6.4)
        channels[1].release()
        channels[2].release()

        val merged = ArrayList<Mat>(3)
        merged.add(sharpened)
        merged.add(chromaA)
        merged.add(chromaB)
        val resultLab = Mat()
        Core.merge(merged, resultLab)
        sharpened.release()
        chromaA.release()
        chromaB.release()

        val result = Mat()
        Imgproc.cvtColor(resultLab, result, Imgproc.COLOR_Lab2RGB)
        resultLab.release()
        balanced.release()
        return result
    }

    /**
     * Compresses only the top of the tone curve, so paper reaches a scan white
     * while ink keeps every bit of its contrast. A plain levels stretch either
     * leaves the sheet grey or crushes the thin strokes.
     */
    private fun liftHighlights(gray: Mat): Mat {
        val table = Mat(1, 256, CvType.CV_8UC1)
        val data = ByteArray(256)
        for (value in 0 until 256) {
            val x = value / 255.0
            val weight = ((x - 0.75) / 0.25).coerceIn(0.0, 1.0)
            val y = x + (1.0 - x) * 0.75 * weight
            data[value] = (y * 255.0).toInt().coerceIn(0, 255).toByte()
        }
        table.put(0, 0, data)
        val result = Mat()
        Core.LUT(gray, table, result)
        table.release()
        return result
    }

    /**
     * Unsharp masking weighted by a dilated gradient mask: text and stamps get
     * crisper, uniform paper is left exactly as the levels produced it.
     */
    private fun edgeMaskedSharpen(gray: Mat): Mat {
        val blurred = Mat()
        val sharp = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(0.0, 0.0), 1.1)
        Core.addWeighted(gray, 1.7, blurred, -0.7, 0.0, sharp)
        blurred.release()

        val magnitude = gradientMagnitude(gray)
        val mask8 = Mat()
        magnitude.convertTo(mask8, CvType.CV_8U)
        magnitude.release()
        Imgproc.threshold(mask8, mask8, 32.0, 255.0, Imgproc.THRESH_BINARY)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.dilate(mask8, mask8, kernel, CvPoint(-1.0, -1.0), 2)
        kernel.release()

        val mask = Mat()
        mask8.convertTo(mask, CvType.CV_32F, 1.0 / 255.0)
        mask8.release()
        Imgproc.GaussianBlur(mask, mask, Size(0.0, 0.0), 1.5)

        val grayF = Mat()
        val sharpF = Mat()
        gray.convertTo(grayF, CvType.CV_32F)
        sharp.convertTo(sharpF, CvType.CV_32F)
        sharp.release()

        val inverse = Mat(mask.size(), CvType.CV_32F, Scalar(1.0))
        Core.subtract(inverse, mask, inverse)
        Core.multiply(grayF, inverse, grayF)
        Core.multiply(sharpF, mask, sharpF)
        Core.add(grayF, sharpF, grayF)
        inverse.release()
        sharpF.release()
        mask.release()

        val result = Mat()
        grayF.convertTo(result, CvType.CV_8U)
        grayF.release()
        return result
    }

    /** L channel of an RGB mat, without keeping the whole Lab split alive. */
    private fun luminanceOf(rgb: Mat): Mat {
        val lab = Mat()
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        val split = ArrayList<Mat>(3)
        Core.split(lab, split)
        val luminance = split[0]
        split[1].release()
        split[2].release()
        lab.release()
        return luminance
    }

    /**
     * Paper illumination map at 1/8 size. A smooth Gaussian estimate over a
     * float image follows lamp falloff and soft shadows without quantisation
     * steps, and the cubic upscale keeps it free of block edges.
     *
     * Writing is removed from the map before it is smoothed: ink is darker than
     * the sheet and, left in, it drags the estimate down to the stroke, which
     * shows up later as a grey halo around every letter. Replacing the ink with
     * the coarse estimate first keeps the gain flat across the page, and the
     * edge aware pass then smooths the shadow boundary without rounding off the
     * step that makes it visible.
     */
    private fun paperBackground(luminance: Mat): Mat {
        val width = luminance.cols()
        val height = luminance.rows()
        val smallWidth = max(24, width / 8)
        val smallHeight = max(24, height / 8)
        val smallSide = max(smallWidth, smallHeight)

        val source = Mat()
        luminance.convertTo(source, CvType.CV_32F, 1.0 / 255.0)
        val small = Mat()
        Imgproc.resize(
            source,
            small,
            Size(smallWidth.toDouble(), smallHeight.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_AREA,
        )
        source.release()

        val coarse = Mat()
        Imgproc.GaussianBlur(small, coarse, Size(0.0, 0.0), smallSide / 10.0)

        val inkLimit = Mat()
        Core.multiply(coarse, Scalar(0.80), inkLimit)
        val ink = Mat()
        Core.compare(small, inkLimit, ink, Core.CMP_LT)
        inkLimit.release()
        coarse.copyTo(small, ink)
        ink.release()

        val smooth = Mat()
        Imgproc.bilateralFilter(small, smooth, 9, 0.05, 9.0)
        coarse.release()
        small.release()

        val background = Mat()
        Imgproc.resize(
            smooth,
            background,
            Size(width.toDouble(), height.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_CUBIC,
        )
        smooth.release()
        val floor = Mat(background.size(), background.type(), Scalar(0.05))
        Core.max(background, floor, background)
        floor.release()
        return background
    }

    private const val PAPER_TARGET = 242.0

    private fun distance(a: Point, b: Point): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}