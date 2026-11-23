package com.awareness.core.domain.usecase

import app.cash.turbine.test
import com.awareness.core.domain.model.AwarenessEvent
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.orchestrator.FakeHudPreferencesRepository
import com.awareness.core.domain.orchestrator.FakeSessionRepository
import com.awareness.core.domain.orchestrator.SessionOrchestratorImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeriodicNudgeTimingTest {

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
    fun `hideDurationSeconds zero keeps HUD visible continuously after reveal delay`() = runTest {
        fakePreferencesRepository.emitPreferences(
            HudPreferences(
                initialRevealDelaySeconds = 60,
                messageDisplayDurationSeconds = 30,
                hideDurationSeconds = 0
            )
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))

        // Before reveal delay (elapsed 50s < 60s)
        timerTicksFlow.value = 60_000L
        useCase().test {
            assertFalse(awaitItem().isVisible)

            // After reveal delay (elapsed 60s)
            timerTicksFlow.value = 70_000L
            assertTrue(awaitItem().isVisible)

            // Elapsed 120s (still visible, continuous mode)
            timerTicksFlow.value = 130_000L
            assertTrue(awaitItem().isVisible)

            // Elapsed 300s (still visible)
            timerTicksFlow.value = 310_000L
            assertTrue(awaitItem().isVisible)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideDurationSeconds greater than zero alternates between visible and hidden periods`() = runTest {
        // Reveal after 60s, show for 30s, hide for 30s, cycle period = 60s
        fakePreferencesRepository.emitPreferences(
            HudPreferences(
                initialRevealDelaySeconds = 60,
                messageDisplayDurationSeconds = 30,
                hideDurationSeconds = 30
            )
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))

        timerTicksFlow.value = 10_000L

        useCase().test {
            // Elapsed 0s (< 60s reveal): hidden
            assertFalse(awaitItem().isVisible)

            // Elapsed 59s (< 60s reveal): hidden
            timerTicksFlow.value = 69_000L
            assertFalse(awaitItem().isVisible)

            // Elapsed 60s (start of visible window, cycleElapsed = 0s): visible
            timerTicksFlow.value = 70_000L
            assertTrue(awaitItem().isVisible)

            // Elapsed 89s (end of visible window, cycleElapsed = 29s): visible
            timerTicksFlow.value = 99_000L
            assertTrue(awaitItem().isVisible)

            // Elapsed 90s (start of hidden window, cycleElapsed = 30s): hidden
            timerTicksFlow.value = 100_000L
            assertFalse(awaitItem().isVisible)

            // Elapsed 119s (end of hidden window, cycleElapsed = 59s): hidden
            timerTicksFlow.value = 129_000L
            assertFalse(awaitItem().isVisible)

            // Elapsed 120s (start of next cycle visible window, cycleElapsed = 60s % 60s = 0s): visible
            timerTicksFlow.value = 130_000L
            assertTrue(awaitItem().isVisible)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
