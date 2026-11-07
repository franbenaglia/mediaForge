package com.fabapps.mediaforge.ui.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    private val us = Locale.US
    private val ar = Locale.forLanguageTag("es-AR")

    @Test
    fun bytesBelowOneKilobyte() {
        assertEquals("0 B", Formatters.bytes(0, us))
        assertEquals("999 B", Formatters.bytes(999, us))
    }

    @Test
    fun bytesUseDecimalUnits() {
        assertEquals("1.0 kB", Formatters.bytes(1_000, us))
        assertEquals("1.5 MB", Formatters.bytes(1_500_000, us))
        assertEquals("4.7 GB", Formatters.bytes(4_700_000_000, us))
    }

    @Test
    fun bytesRoundUpToNextUnitInsteadOfShowing1000() {
        assertEquals("1.0 MB", Formatters.bytes(999_999, us))
    }

    @Test
    fun bytesRespectLocaleDecimalSeparator() {
        assertEquals("1,5 MB", Formatters.bytes(1_500_000, ar))
    }

    @Test(expected = IllegalArgumentException::class)
    fun bytesRejectNegative() {
        Formatters.bytes(-1, us)
    }

    @Test
    fun durationUnderAnHour() {
        assertEquals("0:00", Formatters.duration(0))
        assertEquals("0:59", Formatters.duration(59))
        assertEquals("10:35", Formatters.duration(635))
    }

    @Test
    fun durationWithHours() {
        assertEquals("1:00:00", Formatters.duration(3600))
        assertEquals("2:03:04", Formatters.duration(7384))
    }
}
