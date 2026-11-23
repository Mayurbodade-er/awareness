package com.awareness.core.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserDomainFormatterTest {

    @Test
    fun `label formats known social domains clearly`() {
        assertEquals("You're on instagram.com", BrowserDomainFormatter.label("instagram.com"))
        assertEquals("You're on youtube.com", BrowserDomainFormatter.label("youtube.com"))
    }
}
