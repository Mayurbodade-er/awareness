package com.awareness.core.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRotationTest {

    @Test
    fun `single message always returns index 0`() {
        val index = MessageRotation.indexFor(
            elapsedMillis = 50_000L,
            revealDelayMillis = 0L,
            messageDisplayDurationSeconds = 30,
            messageCount = 1
        )
        assertEquals(0, index)
    }

    @Test
    fun `message index cycles sequentially across duration boundaries`() {
        // Duration: 30s = 30,000ms. Message count: 3
        assertEquals(0, MessageRotation.indexFor(0L, 0L, 30, 3))
        assertEquals(0, MessageRotation.indexFor(29_000L, 0L, 30, 3))
        assertEquals(1, MessageRotation.indexFor(30_000L, 0L, 30, 3))
        assertEquals(1, MessageRotation.indexFor(59_999L, 0L, 30, 3))
        assertEquals(2, MessageRotation.indexFor(60_000L, 0L, 30, 3))
        assertEquals(0, MessageRotation.indexFor(90_000L, 0L, 30, 3)) // Wraps around
    }

    @Test
    fun `rotation accounts for reveal delay offset`() {
        // Reveal delay: 60s. Duration: 30s. Elapsed: 90s -> effective 30s -> index 1
        val index = MessageRotation.indexFor(
            elapsedMillis = 90_000L,
            revealDelayMillis = 60_000L,
            messageDisplayDurationSeconds = 30,
            messageCount = 3
        )
        assertEquals(1, index)
    }

    @Test
    fun `duration is clamped to valid 5 to 300 range`() {
        // Given duration 1s (clamped to 5s)
        val index = MessageRotation.indexFor(
            elapsedMillis = 10_000L,
            revealDelayMillis = 0L,
            messageDisplayDurationSeconds = 1,
            messageCount = 3
        )
        // 10s / 5s = 2
        assertEquals(2, index)
    }
}
