package com.awareness.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMindfulnessMessagesTest {

    @Test
    fun `all messages list is non-empty and IDs are unique`() {
        val all = DefaultMindfulnessMessages.all
        assertTrue(all.isNotEmpty())
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `defaultSelectedIds contains valid known messages`() {
        val defaultIds = DefaultMindfulnessMessages.defaultSelectedIds
        assertTrue(defaultIds.isNotEmpty())
        val allIds = DefaultMindfulnessMessages.all.map { it.id }.toSet()
        assertTrue(allIds.containsAll(defaultIds))
    }

    @Test
    fun `fallback returns messages matching defaultSelectedIds`() {
        val fallback = DefaultMindfulnessMessages.fallback
        assertEquals(DefaultMindfulnessMessages.defaultSelectedIds.size, fallback.size)
        val fallbackIds = fallback.map { it.id }.toSet()
        assertEquals(DefaultMindfulnessMessages.defaultSelectedIds, fallbackIds)
    }
}
