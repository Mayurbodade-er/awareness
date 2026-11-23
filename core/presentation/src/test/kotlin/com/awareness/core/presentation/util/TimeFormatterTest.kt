package com.awareness.core.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun `mmss formats zero correctly`() {
        assertEquals("00:00", TimeFormatter.mmss(0L))
    }

    @Test
    fun `mmss formats minutes and seconds correctly`() {
        assertEquals("03:45", TimeFormatter.mmss(225_000L))
        assertEquals("59:59", TimeFormatter.mmss(3599_000L))
    }

    @Test
    fun `hms formats hours, minutes, and seconds correctly`() {
        assertEquals("00:00", TimeFormatter.hms(0L))
        assertEquals("03:45", TimeFormatter.hms(225_000L))
        assertEquals("1:00:00", TimeFormatter.hms(3600_000L))
        assertEquals("2:15:30", TimeFormatter.hms(8130_000L))
    }

    @Test
    fun `negative milliseconds clamp to zero`() {
        assertEquals("00:00", TimeFormatter.mmss(-5000L))
        assertEquals("00:00", TimeFormatter.hms(-5000L))
    }
}
