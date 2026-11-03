package com.scanfromcam.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SharingTest {

    @Test
    fun `mime follows the file kinds`() {
        assertEquals("application/pdf", mimeOf(listOf(File("a/one.pdf"))))
        assertEquals("image/jpeg", mimeOf(listOf(File("a/1.jpg"), File("a/2.jpg"))))
        assertEquals("*/*", mimeOf(listOf(File("a/one.pdf"), File("a/1.jpg"))))
    }
}
