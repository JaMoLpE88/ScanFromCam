package com.scanfromcam.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class NamesTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val fixedNow = Instant.parse("2026-01-02T03:04:00Z").toEpochMilli()

    @Test
    fun `blank input falls back to a timestamped name`() {
        assertEquals("Escaneo 2026-01-02 03.04", sanitizeName("   ", fixedNow, utc))
    }

    @Test
    fun `illegal file characters are removed`() {
        assertEquals("abcd", sanitizeName("a/b:c*d", fixedNow, utc))
        assertEquals("ficha2026", sanitizeName("ficha<>|2026", fixedNow, utc))
    }

    @Test
    fun `name keeps normal text and collapses spaces`() {
        assertEquals("Factura enero 2026", sanitizeName("  Factura   enero   2026 ", fixedNow, utc))
    }

    @Test
    fun `long names are capped to eighty characters`() {
        val long = "a".repeat(200)
        assertEquals(80, sanitizeName(long, fixedNow, utc).length)
    }
}
