package com.scanfromcam.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun `only a strictly newer version is offered`() {
        assertTrue(isNewerVersion("1.2.0", "1.1.0"))
        assertTrue(isNewerVersion("v1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("1.2", "1.1.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
        assertFalse(isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(isNewerVersion("1.1.0", "1.2.0"))
        assertFalse(isNewerVersion("1.2.0", "1.2"))
        assertFalse(isNewerVersion("1.2.0-rc1", "1.2.0"))
        assertFalse(isNewerVersion("nightly", "1.0.0"))
    }

    @Test
    fun `the apk of this device is picked`() {
        val assets = listOf("ScanFromCam-1.2.0-arm64-v8a.apk", "ScanFromCam-1.2.0-x86_64.apk")
        assertEquals(
            "ScanFromCam-1.2.0-arm64-v8a.apk",
            pickAssetName("1.2.0", listOf("arm64-v8a", "armeabi-v7a"), assets),
        )
        assertEquals(
            "ScanFromCam-1.2.0-x86_64.apk",
            pickAssetName("1.2.0", listOf("x86_64"), assets),
        )
        assertNull("no arm64 build published", pickAssetName("1.3.0", listOf("arm64-v8a"), assets))
        assertNull("device abi not published", pickAssetName("1.2.0", listOf("armeabi-v7a"), assets))
    }

    @Test
    fun `notes come from the section of the current language`() {
        val body = RELEASE_BODY
        assertEquals("• Recorte más rápido", extractNotes(body, spanish = true))
        assertEquals("• Faster crop\n• PDF fix", extractNotes(body, spanish = false))
    }

    @Test
    fun `notes fall back to the other language`() {
        val body = """
            # ScanFromCam 1.3.0

            ## What's new in 1.3.0
            - One fix
        """.trimIndent()
        assertEquals("• One fix", extractNotes(body, spanish = true))
    }

    @Test
    fun `no notes is an empty string, never a table`() {
        val body = """
            # ScanFromCam 1.3.0

            | APK | For |
            |---|---|
            | a.apk | phones |
        """.trimIndent()
        val notes = extractNotes(body, spanish = false)
        assertFalse(notes.contains("|"))
        assertEquals("ScanFromCam 1.3.0", notes)
    }

    @Test
    fun `digest is hex`() {
        assertEquals("00ff10", hex(byteArrayOf(0x00, 0xFF.toByte(), 0x10)))
    }

    private companion object {
        val RELEASE_BODY = """
            # ScanFromCam 1.2.0

            A scanner.

            ## What's new in 1.2.0
            - **Faster** crop
            - `PDF` fix

            ## Novedades de 1.2.0
            - Recorte más rápido

            ## Download
            | APK | SHA-256 |
            |---|---|
            | a.apk | abc |
        """.trimIndent()
    }
}
