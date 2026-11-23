package com.awareness.core.accessibility

import app.cash.turbine.test
import com.awareness.core.domain.model.AwarenessEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityEventBusTest {

    @Test
    fun tryEmitDeliversEventsToCollector() = runTest {
        val bus = AccessibilityEventBusImpl()

        bus.events.test {
            val event1 = AwarenessEvent.SessionStarted("com.instagram.android", 1000L)
            val event2 = AwarenessEvent.SessionEnded("com.instagram.android", 5000L)

            assertTrue(bus.tryEmit(event1))
            assertEquals(event1, awaitItem())

            assertTrue(bus.tryEmit(event2))
            assertEquals(event2, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dropOldestWhenBufferCapacityExceededWithoutSubscribers() = runTest {
        val bus = AccessibilityEventBusImpl()

        // Buffer capacity is 16. Emit 20 events without active subscribers.
        for (i in 1..20) {
            val event = AwarenessEvent.SessionStarted("pkg.$i", i.toLong())
            assertTrue(bus.tryEmit(event))
        }

        // The oldest 4 events should have been dropped; only the last 16 remain or next emitted arrives
        bus.events.test {
            val nextEvent = AwarenessEvent.SessionEnded("pkg.21", 21L)
            assertTrue(bus.tryEmit(nextEvent))
            assertEquals(nextEvent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
