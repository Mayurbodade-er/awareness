package com.awareness.core.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterBoundaryTest {

    @Test
    fun mmssFormatsCorrectlyAtAllBoundaries() {
        assertEquals("00:00", TimeFormatter.mmss(0L))
        assertEquals("00:00", TimeFormatter.mmss(999L))
        assertEquals("00:01", TimeFormatter.mmss(1000L))
        assertEquals("00:59", TimeFormatter.mmss(59_999L))
        assertEquals("01:00", TimeFormatter.mmss(60_000L))
        assertEquals("59:59", TimeFormatter.mmss(3_599_000L))
    }

    @Test
    fun hmsFormatsCorrectlyBeyondHourBoundary() {
        assertEquals("00:00", TimeFormatter.hms(0L))
        assertEquals("12:34", TimeFormatter.hms(754_000L))
        assertEquals("1:00:00", TimeFormatter.hms(3_600_000L))
        assertEquals("1:01:05", TimeFormatter.hms(3_665_000L))
        assertEquals("2:30:15", TimeFormatter.hms(9_015_000L))
    }
}
