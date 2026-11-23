package com.awareness.core.accessibility

import com.awareness.core.domain.model.AwarenessEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AwarenessAccessibilityServiceTest {

    private lateinit var recordedEvents: MutableList<AwarenessEvent>
    private lateinit var fakeBus: AccessibilityEventBus
    private var simulatedTime: Long = 10_000L
    private lateinit var service: AwarenessAccessibilityService

    @Before
    fun setUp() {
        recordedEvents = mutableListOf()
        fakeBus = object : AccessibilityEventBus {
            override val events = AccessibilityEventBusImpl().events
            override fun tryEmit(event: AwarenessEvent): Boolean {
                recordedEvents.add(event)
                return true
            }
        }
        service = AwarenessAccessibilityService(
            eventBus = fakeBus,
            timeProvider = { simulatedTime }
        )
    }

    @Test
    fun targetAppForegroundEmitsSessionStarted() {
        simulatedTime = 10_500L
        service.handleWindowStateChanged("com.instagram.android")

        assertEquals(1, recordedEvents.size)
        val event = recordedEvents[0] as AwarenessEvent.SessionStarted
        assertEquals("com.instagram.android", event.packageName)
        assertEquals(10_500L, event.timestamp)
    }

    @Test
    fun consecutiveEventsForSameTargetAppDoNotEmitDuplicateSessionStarted() {
        service.handleWindowStateChanged("com.instagram.android")
        simulatedTime += 200L
        service.handleWindowStateChanged("com.instagram.android")

        assertEquals(1, recordedEvents.size)
        assertTrue(recordedEvents[0] is AwarenessEvent.SessionStarted)
    }

    @Test
    fun switchingFromTargetToNonTargetEmitsSessionEnded() {
        service.handleWindowStateChanged("com.instagram.android")
        assertEquals(1, recordedEvents.size)

        simulatedTime += 5_000L
        service.handleWindowStateChanged("com.android.settings")

        assertEquals(2, recordedEvents.size)
        val ended = recordedEvents[1] as AwarenessEvent.SessionEnded
        assertEquals("com.instagram.android", ended.packageName)
        assertEquals(simulatedTime, ended.timestamp)
    }

    @Test
    fun switchingBetweenTwoTargetAppsEmitsSessionEndedThenSessionStarted() {
        service.handleWindowStateChanged("com.instagram.android")
        assertEquals(1, recordedEvents.size)

        simulatedTime += 3_000L
        service.handleWindowStateChanged("com.android.chrome")

        assertEquals(3, recordedEvents.size)
        val ended = recordedEvents[1] as AwarenessEvent.SessionEnded
        assertEquals("com.instagram.android", ended.packageName)
        assertEquals(simulatedTime, ended.timestamp)

        val started = recordedEvents[2] as AwarenessEvent.SessionStarted
        assertEquals("com.android.chrome", started.packageName)
        assertEquals(simulatedTime, started.timestamp)
    }

    @Test
    fun nonTargetAppEventsDoNotEmitAnyEvents() {
        service.handleWindowStateChanged("com.android.settings")
        service.handleWindowStateChanged("com.google.android.calculator")

        assertTrue(recordedEvents.isEmpty())
    }

    @Test
    fun serviceInterruptionWhileTargetActiveEmitsSessionEnded() {
        service.handleWindowStateChanged("com.snapchat.android")
        assertEquals(1, recordedEvents.size)

        simulatedTime += 1_000L
        service.onInterrupt()

        assertEquals(2, recordedEvents.size)
        val ended = recordedEvents[1] as AwarenessEvent.SessionEnded
        assertEquals("com.snapchat.android", ended.packageName)
        assertEquals(simulatedTime, ended.timestamp)
    }

    @Test
    fun serviceDestructionWhileTargetActiveEmitsSessionEnded() {
        service.handleWindowStateChanged("com.google.android.youtube")
        assertEquals(1, recordedEvents.size)

        simulatedTime += 2_000L
        service.onDestroy()

        assertEquals(2, recordedEvents.size)
        val ended = recordedEvents[1] as AwarenessEvent.SessionEnded
        assertEquals("com.google.android.youtube", ended.packageName)
        assertEquals(simulatedTime, ended.timestamp)
    }
}
