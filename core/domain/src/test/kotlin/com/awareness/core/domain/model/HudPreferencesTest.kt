package com.awareness.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudPreferencesTest {

    @Test
    fun `default values match requirements`() {
        val prefs = HudPreferences()
        assertEquals(0, prefs.initialRevealDelaySeconds)
        assertEquals(30, prefs.messageDisplayDurationSeconds)
        assertEquals(0L, prefs.revealDelayMillis)
        assertEquals(DefaultMindfulnessMessages.defaultSelectedIds, prefs.selectedMessageIds)
        assertEquals(3, prefs.selectedMessages.size)
    }

    @Test
    fun `revealDelayMillis computes correct monotonic milliseconds`() {
        val prefs = HudPreferences(initialRevealDelaySeconds = 180)
        assertEquals(180_000L, prefs.revealDelayMillis)
    }

    @Test
    fun `selectedMessages falls back to default fallback when selected ids are empty`() {
        val prefs = HudPreferences(selectedMessageIds = emptySet())
        val messages = prefs.selectedMessages
        assertTrue(messages.isNotEmpty())
        assertEquals(DefaultMindfulnessMessages.fallback, messages)
    }

    @Test
    fun `selectedMessages filters to only matching known messages`() {
        val prefs = HudPreferences(selectedMessageIds = setOf("serving", "unknown_id_xyz"))
        val messages = prefs.selectedMessages
        assertEquals(1, messages.size)
        assertEquals("serving", messages[0].id)
    }
}
