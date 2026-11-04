package com.scanfromcam.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capture gate refuses to photograph a page it reads as soft. The numbers
 * below are the ones measured on rendered pages at the analysis size, so the
 * decision keeps working when either figure moves.
 */
class FocusPolicyTest {

    @Test
    fun crispPagePasses() {
        assertTrue(Focus(sharpness = 43_080.0, detail = 65_237.0).isSharp)
    }

    @Test
    fun faintPrintStillPassesWhenCrisp() {
        assertTrue(Focus(sharpness = 8_291.0, detail = 12_651.0).isSharp)
    }

    @Test
    fun softPageIsRejected() {
        assertFalse(Focus(sharpness = 318.0, detail = 2_568.0).isSharp)
    }

    @Test
    fun blankSheetIsNeverRejected() {
        assertTrue(Focus(sharpness = 0.0, detail = 0.0).isSharp)
        // Even with a shadow across it: a smooth ramp is not edge energy.
        assertTrue(Focus(sharpness = 9.0, detail = 3.9).isSharp)
    }

    @Test
    fun theCutIsInclusive() {
        assertTrue(FocusPolicy.isSharp(FocusPolicy.MIN_DETAIL * 2 * FocusPolicy.MIN_RATIO, FocusPolicy.MIN_DETAIL * 2))
        assertFalse(FocusPolicy.isSharp(FocusPolicy.MIN_DETAIL * 2 * FocusPolicy.MIN_RATIO - 1.0, FocusPolicy.MIN_DETAIL * 2))
    }
}
