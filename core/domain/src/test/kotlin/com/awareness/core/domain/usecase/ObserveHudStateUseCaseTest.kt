package com.awareness.core.domain.usecase

import app.cash.turbine.test
import com.awareness.core.domain.model.AwarenessEvent
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.SessionState
import com.awareness.core.domain.orchestrator.FakeHudPreferencesRepository
import com.awareness.core.domain.orchestrator.FakeSessionRepository
import com.awareness.core.domain.orchestrator.SessionOrchestratorImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObserveHudStateUseCaseTest {

    private lateinit var fakePreferencesRepository: FakeHudPreferencesRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var timerTicksFlow: MutableStateFlow<Long>
    private lateinit var useCase: ObserveHudStateUseCaseImpl

    @Before
    fun setUp() {
        fakePreferencesRepository = FakeHudPreferencesRepository()
        fakeSessionRepository = FakeSessionRepository()
        orchestrator = SessionOrchestratorImpl(
            hudPreferencesRepository = fakePreferencesRepository,
            sessionRepository = fakeSessionRepository
        )
        timerTicksFlow = MutableStateFlow(0L)
        useCase = ObserveHudStateUseCaseImpl(
            sessionOrchestrator = orchestrator,
            hudPreferencesRepository = fakePreferencesRepository,
            timerTicks = timerTicksFlow
        )
    }

    @Test
    fun idleStateProducesInvisibleHudState() = runTest {
        useCase().test {
            val state = awaitItem()
            assertFalse(state.isVisible)
            assertEquals("", state.message)
            assertEquals(0L, state.elapsedMillis)
            assertEquals(0, state.reelCount)
            assertFalse(state.showReelCount)
            assertNull(state.browserDomainLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activeStateBeforeRevealDelayProducesInvisibleHudState() = runTest {
        fakePreferencesRepository.emitPreferences(
            HudPreferences(initialRevealDelaySeconds = 60, messageDisplayDurationSeconds = 30)
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        timerTicksFlow.value = 40_000L // 30s elapsed < 60s delay

        useCase().test {
            val state = awaitItem()
            assertFalse("HUD must remain invisible before reveal delay", state.isVisible)
            assertEquals(30_000L, state.elapsedMillis)
            assertTrue(state.showReelCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activeStateAfterRevealDelayProducesVisibleHudStateWithRotatingMessage() = runTest {
        fakePreferencesRepository.emitPreferences(
            HudPreferences(initialRevealDelaySeconds = 60, messageDisplayDurationSeconds = 30)
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        // Advance clock past reveal delay (70s elapsed >= 60s delay)
        timerTicksFlow.value = 80_000L

        useCase().test {
            val state = awaitItem()
            assertTrue("HUD must be visible after reveal delay", state.isVisible)
            assertEquals(70_000L, state.elapsedMillis)
            // 70s / 30s duration = index 2 (third message)
            val expectedMessage = DefaultMindfulnessMessages.fallback[2 % DefaultMindfulnessMessages.fallback.size].text
            assertEquals(expectedMessage, state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun browserSessionHidesReelCountAndFormatsDomain() = runTest {
        fakePreferencesRepository.emitPreferences(
            HudPreferences(initialRevealDelaySeconds = 0)
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.android.chrome", 10_000L))
        orchestrator.onEvent(AwarenessEvent.BrowserDomainInferred("com.android.chrome", "github.com", 11_000L))
        timerTicksFlow.value = 15_000L

        useCase().test {
            val state = awaitItem()
            assertTrue(state.isVisible)
            assertFalse("Reels must not be shown for browser sessions", state.showReelCount)
            assertEquals("Browsing: github.com", state.browserDomainLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
