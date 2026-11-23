package com.awareness.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudStylingAndCustomMessageTest {

    @Test
    fun `custom messages are included in allAvailableMessages and selectable`() {
        val customMsg = MindfulnessMessage(id = "custom_1", text = "Is this helping your goals?")
        val prefs = HudPreferences(
            customMessages = listOf(customMsg),
            selectedMessageIds = setOf("custom_1")
        )

        assertTrue(prefs.allAvailableMessages.any { it.id == "custom_1" })
        assertEquals(1, prefs.selectedMessages.size)
        assertEquals("Is this helping your goals?", prefs.selectedMessages[0].text)
    }

    @Test
    fun `hud scale and opacity default to standard and 85 percent`() {
        val prefs = HudPreferences()
        assertEquals(HudScale.STANDARD, prefs.hudScale)
        assertEquals(85, prefs.hudOpacityPercent)
        assertEquals(0, prefs.hideDurationSeconds)
    }

    @Test
    fun `hudState preserves opacity and scale`() {
        val state = HudState(
            isVisible = true,
            message = "Notice your breath",
            elapsedMillis = 5000L,
            reelCount = 0,
            showReelCount = true,
            opacityPercent = 70,
            scale = HudScale.LARGE
        )

        assertEquals(70, state.opacityPercent)
        assertEquals(HudScale.LARGE, state.scale)
    }
}
