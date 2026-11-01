package com.scanfromcam.feature.capture.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionStateTest {

    private val state = MotionState(moveLimit = 0.28f, calmLimit = 0.18f, holdMs = 250L)

    @Test
    fun `starts still`() {
        assertTrue(state.stable)
    }

    @Test
    fun `a big movement makes it unstable at once`() {
        assertTrue(state.onLevel(0.9f, now = 0L))
        assertFalse(state.stable)
    }

    @Test
    fun `noise inside the dead band never flips the state`() {
        assertFalse(state.onLevel(0.22f, now = 0L))
        assertTrue(state.stable)
    }

    @Test
    fun `stays moving until the hold time passes`() {
        state.onLevel(0.9f, now = 0L)
        assertFalse(state.onLevel(0.10f, now = 100L))
        assertFalse(state.stable)
        assertFalse(state.onLevel(0.10f, now = 349L))
        assertTrue(state.onLevel(0.10f, now = 350L))
        assertTrue(state.stable)
    }

    @Test
    fun `a jolt alone is enough to report movement`() {
        assertTrue(state.onJolt(0.75f))
        assertFalse(state.stable)
    }
}
