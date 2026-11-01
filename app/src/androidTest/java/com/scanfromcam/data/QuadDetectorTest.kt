package com.scanfromcam.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class QuadDetectorTest {

    @Before
    fun setUp() {
        OpenCVLoader.initLocal()
    }

    @Test
    fun detectsASyntheticPageInsideTheFrame() {
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(55.0))
        Imgproc.rectangle(frame, Point(90.0, 70.0), Point(550.0, 410.0), Scalar(238.0), -1)

        val quad = Cv.detectQuad(frame)

        assertNotNull("No se detectó ningún cuadrilátero", quad)
        val xs = quad!!.map { it.x * 640f }
        val ys = quad.map { it.y * 480f }
        assertTrue("x mínima fuera de rango: ${xs.min()}", xs.min() in 60f..120f)
        assertTrue("x máxima fuera de rango: ${xs.max()}", xs.max() in 520f..580f)
        assertTrue("y mínima fuera de rango: ${ys.min()}", ys.min() in 40f..100f)
        assertTrue("y máxima fuera de rango: ${ys.max()}", ys.max() in 380f..440f)
    }

    @Test
    fun prefersTheSheetOverTheFurnitureAroundIt() {
        // Dark desk with a big bright tabletop filling almost the whole frame and
        // a sheet of paper on top of it: the sheet must win.
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(28.0))
        Imgproc.rectangle(frame, Point(8.0, 8.0), Point(632.0, 472.0), Scalar(150.0), -1)
        Imgproc.rectangle(frame, Point(180.0, 120.0), Point(470.0, 380.0), Scalar(238.0), -1)

        val quad = Cv.detectQuad(frame)

        assertNotNull("No se detectó nada", quad)
        val xs = quad!!.map { it.x * 640f }
        val ys = quad.map { it.y * 480f }
        assertTrue("Eligió la mesa en vez del folio: $xs", xs.min() in 150f..220f)
        assertTrue("Eligió la mesa en vez del folio: $xs", xs.max() in 440f..500f)
        assertTrue("Eligió la mesa en vez del folio: $ys", ys.min() in 90f..150f)
        assertTrue("Eligió la mesa en vez del folio: $ys", ys.max() in 350f..410f)
    }

    @Test
    fun returnsNullForAFlatFrame() {
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(120.0))
        assertTrue(Cv.detectQuad(frame) == null)
    }

    @Test
    fun findsAPageWhoseBorderIsTooSoftForCanny() {
        // Paper barely brighter than the desk: the classic "white sheet on a
        // white table" shot. The bright-blob generator has to catch this one.
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(150.0))
        Imgproc.rectangle(frame, Point(120.0, 90.0), Point(520.0, 400.0), Scalar(196.0), -1)

        val quad = Cv.detectQuad(frame)

        assertNotNull("No se detectó el folio de bajo contraste", quad)
        val xs = quad!!.map { it.x * 640f }
        val ys = quad.map { it.y * 480f }
        assertTrue("x mínima: ${xs.min()}", xs.min() in 90f..160f)
        assertTrue("x máxima: ${xs.max()}", xs.max() in 480f..550f)
        assertTrue("y mínima: ${ys.min()}", ys.min() in 60f..120f)
        assertTrue("y máxima: ${ys.max()}", ys.max() in 370f..430f)
    }

    @Test
    fun refinesASeedQuadOntoTheRealEdges() {
        val frame = Mat(480, 640, CvType.CV_8UC1, Scalar(40.0))
        Imgproc.rectangle(frame, Point(100.0, 80.0), Point(540.0, 400.0), Scalar(235.0), -1)

        // Seed four pixels inside the true border, like a preview quad would be.
        val seed = listOf(
            com.scanfromcam.domain.model.Point(104f / 640f, 84f / 480f),
            com.scanfromcam.domain.model.Point(536f / 640f, 84f / 480f),
            com.scanfromcam.domain.model.Point(536f / 640f, 396f / 480f),
            com.scanfromcam.domain.model.Point(104f / 640f, 396f / 480f),
        )

        val refined = Cv.refineQuad(frame, seed)

        assertNotNull("No se pudo refinar el encuadre", refined)
        val expected = listOf(100f to 80f, 540f to 80f, 540f to 400f, 100f to 400f)
        refined!!.forEachIndexed { index, corner ->
            val x = corner.x * 640f
            val y = corner.y * 480f
            val (ex, ey) = expected[index]
            assertTrue(
                "Esquina $index quedó en ($x, $y), se esperaba ($ex, $ey)",
                kotlin.math.abs(x - ex) < 4f && kotlin.math.abs(y - ey) < 4f,
            )
        }
    }

}
