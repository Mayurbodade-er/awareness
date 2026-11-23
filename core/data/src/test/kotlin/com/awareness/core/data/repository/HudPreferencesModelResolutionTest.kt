package com.awareness.core.data.repository

import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.HudScale
import com.awareness.core.domain.model.MindfulnessMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudPreferencesModelResolutionTest {

    @Test
    fun `first run defaults match expected delay and duration (VS01-TS-12)`() {
        val prefs = HudPreferences()
        assertEquals(0, prefs.initialRevealDelaySeconds)
        assertEquals(30, prefs.messageDisplayDurationSeconds)
        assertEquals(0, prefs.hideDurationSeconds)
        assertEquals(85, prefs.hudOpacityPercent)
        assertEquals(HudScale.STANDARD, prefs.hudScale)
        assertEquals(DefaultMindfulnessMessages.defaultSelectedIds, prefs.selectedMessageIds)
    }

    @Test
    fun `corrupt or unknown message IDs fall back to default selected messages (VS01-TS-11)`() {
        val prefs = HudPreferences(selectedMessageIds = setOf("invalid_id_1", "invalid_id_2"))
        val messages = prefs.selectedMessages
        // Unknown IDs filtered out -> empty -> falls back to default fallback
        assertEquals(DefaultMindfulnessMessages.fallback, messages)
    }

    @Test
    fun `reveal delay converts accurately to milliseconds (VS01-TS-06)`() {
        val prefs = HudPreferences(initialRevealDelaySeconds = 180) // 3 min
        assertEquals(180_000L, prefs.revealDelayMillis)
    }

    @Test
    fun `custom messages allow valid selection and do not fall back`() {
        val custom = MindfulnessMessage("custom_abc", "Be present in this moment.")
        val prefs = HudPreferences(
            customMessages = listOf(custom),
            selectedMessageIds = setOf("custom_abc")
        )
        assertEquals(1, prefs.selectedMessages.size)
        assertEquals("Be present in this moment.", prefs.selectedMessages.first().text)
    }
}
