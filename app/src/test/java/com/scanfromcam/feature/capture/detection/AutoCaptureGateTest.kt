package com.scanfromcam.feature.capture.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureGateTest {

    @Test
    fun `does not fire before the hold time`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        val start = gate.update(now = 1_000, framed = true, autoEnabled = true)
        assertFalse(start.shouldCapture)
        assertEquals(3, start.remainingSeconds)

        val almost = gate.update(now = 3_900, framed = true, autoEnabled = true)
        assertFalse(almost.shouldCapture)
        assertEquals(1, almost.remainingSeconds)
    }

    @Test
    fun `fires after three seconds framed`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = true)
        gate.update(now = 1_500, framed = true, autoEnabled = true)
        val ready = gate.update(now = 3_000, framed = true, autoEnabled = true)
        assertTrue(ready.shouldCapture)
        assertEquals(0, ready.remainingSeconds)
    }

    @Test
    fun `losing the frame restarts the countdown`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = true)
        gate.update(now = 2_500, framed = true, autoEnabled = true)

        val lost = gate.update(now = 2_600, framed = false, autoEnabled = true)
        assertFalse(lost.framed)
        assertEquals(0, lost.remainingSeconds)

        val restarted = gate.update(now = 2_700, framed = true, autoEnabled = true)
        assertEquals(3, restarted.remainingSeconds)
        assertFalse(restarted.shouldCapture)
    }

    @Test
    fun `manual mode never fires by itself`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = false)
        val held = gate.update(now = 10_000, framed = true, autoEnabled = false)
        assertFalse(held.shouldCapture)
        assertTrue(held.framed)
    }

    @Test
    fun `fires again only after another full hold`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = true)
        assertTrue(gate.update(now = 3_000, framed = true, autoEnabled = true).shouldCapture)
        assertFalse(gate.update(now = 3_100, framed = true, autoEnabled = true).shouldCapture)
    }
    @Test
    fun `a zero hold fires on the first framed frame`() {
        val gate = AutoCaptureGate(holdMs = 0)
        val first = gate.update(now = 1_000, framed = true, autoEnabled = true)
        assertTrue(first.shouldCapture)
        assertEquals(0, first.remainingSeconds)
    }

    @Test
    fun `does not fire while the page is too far away`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = true, covered = false)
        val held = gate.update(now = 5_000, framed = true, autoEnabled = true, covered = false)
        assertFalse(held.shouldCapture)
        assertEquals(0, held.remainingSeconds)
    }

    @Test
    fun `does not fire while the phone is moving`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = true, deviceStable = false)
        val held = gate.update(now = 5_000, framed = true, autoEnabled = true, deviceStable = false)
        assertFalse(held.shouldCapture)
    }

    @Test
    fun `motion restarts the hold instead of pausing it`() {
        val gate = AutoCaptureGate(holdMs = 3_000)
        gate.update(now = 0, framed = true, autoEnabled = true)
        gate.update(now = 2_000, framed = true, autoEnabled = true, deviceStable = false)
        val restarted = gate.update(now = 2_100, framed = true, autoEnabled = true)
        assertEquals(3, restarted.remainingSeconds)
        assertFalse(restarted.shouldCapture)
    }

}