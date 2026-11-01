package com.scanfromcam.feature.capture.detection

/**
 * Decides when an automatic capture is allowed: the document must stay framed
 * for [holdMs] before the shutter fires, so the user has time to line it up.
 *
 * Two extra facts gate the same decision: the page must cover enough of the
 * frame (otherwise the crop would be tiny and upscaled), and the phone must be
 * still (otherwise the photo is blurred and the quad the preview showed is no
 * longer where the page is).
 */
class AutoCaptureGate(private val holdMs: Long = 3_000L) {

    data class State(
        val framed: Boolean,
        val remainingSeconds: Int,
        val shouldCapture: Boolean,
    )

    private var framedSince: Long? = null

    fun reset() {
        framedSince = null
    }

    fun update(
        now: Long,
        framed: Boolean,
        autoEnabled: Boolean,
        covered: Boolean = true,
        deviceStable: Boolean = true,
    ): State {
        val ready = framed && covered && deviceStable
        if (!ready) {
            framedSince = null
            return State(framed = framed && covered, remainingSeconds = 0, shouldCapture = false)
        }

        val started = framedSince ?: now.also { framedSince = it }
        val held = (now - started).coerceAtLeast(0L)
        val remaining = ((holdMs - held + 999) / 1000).toInt().coerceAtLeast(0)
        val shouldCapture = autoEnabled && held >= holdMs

        if (shouldCapture) framedSince = null
        return State(framed = true, remainingSeconds = remaining, shouldCapture = shouldCapture)
    }
}
