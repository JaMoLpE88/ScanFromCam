package com.scanfromcam.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LangTest {

    @Test
    fun `spanish devices start in Spanish`() {
        assertEquals(Lang.ES, Lang.defaultFor(Locale("es", "ES")))
        assertEquals(Lang.ES, Lang.defaultFor(Locale("es")))
    }

    @Test
    fun `every other device starts in English`() {
        assertEquals(Lang.EN, Lang.defaultFor(Locale("es", "MX")))
        assertEquals(Lang.EN, Lang.defaultFor(Locale("en", "US")))
        assertEquals(Lang.EN, Lang.defaultFor(Locale("en", "GB")))
        assertEquals(Lang.EN, Lang.defaultFor(Locale("fr", "FR")))
        assertEquals(Lang.EN, Lang.defaultFor(Locale("ca", "ES")))
    }

    @Test
    fun `stored tags round trip and unknown tags fall back to English`() {
        assertEquals(Lang.ES, Lang.fromTag("es"))
        assertEquals(Lang.EN, Lang.fromTag("en"))
        assertEquals(Lang.EN, Lang.fromTag(null))
        assertEquals(Lang.EN, Lang.fromTag("de"))
    }
}
