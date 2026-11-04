package com.scanfromcam.data

/**
 * What one frame has to say about how it will look once stored.
 *
 * [sharpness] is the variance of the Laplacian and [detail] the variance of the
 * Sobel magnitude. Judging the first one alone does not work: a page with a lot
 * of writing scores high even when it is soft, and a sheet with nothing on it
 * scores near zero however crisp the optics are. Their ratio is the part that
 * survives both cases, because it compares the finest detail in the picture
 * with the edge energy the picture actually has.
 */
data class Focus(val sharpness: Double, val detail: Double) {

    val isSharp: Boolean get() = FocusPolicy.isSharp(sharpness, detail)
}

/**
 * The cut between "reads as focused" and "reads as soft", kept apart from the
 * OpenCV code so it can be exercised without a device.
 *
 * Measured on rendered pages photographed at the analysis size: a crisp one
 * scores around 0,65 and a page blurred by two pixels drops under 0,20, while a
 * blank sheet — with or without a shadow across it — has no edge energy at all
 * and must never be rejected for it.
 */
object FocusPolicy {

    /** Below this edge energy the page has nothing written on it to judge. */
    const val MIN_DETAIL = 60.0

    /** Laplacian energy, relative to the edge energy, of a page worth keeping. */
    const val MIN_RATIO = 0.22

    fun isSharp(sharpness: Double, detail: Double): Boolean =
        detail < MIN_DETAIL || sharpness >= detail * MIN_RATIO
}
