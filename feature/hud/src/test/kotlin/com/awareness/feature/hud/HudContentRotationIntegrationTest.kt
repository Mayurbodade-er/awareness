package com.awareness.feature.hud

import app.cash.turbine.test
import com.awareness.core.domain.model.AwarenessEvent
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.MindfulnessMessage
import com.awareness.core.domain.model.SessionSummary
import com.awareness.core.domain.orchestrator.SessionOrchestratorImpl
import com.awareness.core.domain.repository.HudPreferencesRepository
import com.awareness.core.domain.repository.SessionRepository
import com.awareness.core.domain.usecase.ObserveHudStateUseCaseImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HudContentRotationIntegrationTest {

    private lateinit var prefsRepo: FakePrefsRepo
    private lateinit var sessionRepo: FakeSessRepo
    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var timerTicks: MutableStateFlow<Long>
    private lateinit var useCase: ObserveHudStateUseCaseImpl

    @Before
    fun setUp() {
        prefsRepo = FakePrefsRepo()
        sessionRepo = FakeSessRepo()
        orchestrator = SessionOrchestratorImpl(
            hudPreferencesRepository = prefsRepo,
            sessionRepository = sessionRepo
        )
        timerTicks = MutableStateFlow(0L)
        useCase = ObserveHudStateUseCaseImpl(
            sessionOrchestrator = orchestrator,
            hudPreferencesRepository = prefsRepo,
            timerTicks = timerTicks
        )
    }

    @Test
    fun hudRemainsHiddenBeforeRevealDelayThenAppearsWithTimerAndReels() = runTest {
        // Configure 3 minutes (180s) reveal delay
        prefsRepo.update(
            HudPreferences(
                initialRevealDelaySeconds = 180,
                messageDisplayDurationSeconds = 30
            )
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        // Scroll 2 reels before reveal delay
        orchestrator.onEvent(AwarenessEvent.ReelScrolled(15_000L))
        orchestrator.onEvent(AwarenessEvent.ReelScrolled(20_000L))

        // Set initial timer tick before collecting flow
        timerTicks.value = 110_000L

        useCase().test {
            // At elapsed 100s < 180s -> invisible
            val hiddenState = awaitItem()
            assertFalse(hiddenState.isVisible)
            assertEquals(100_000L, hiddenState.elapsedMillis)
            assertEquals(2, hiddenState.reelCount)

            // At elapsed 180s >= 180s -> visible
            timerTicks.value = 190_000L
            val visibleState = awaitItem()
            assertTrue(visibleState.isVisible)
            assertEquals(180_000L, visibleState.elapsedMillis)
            assertEquals(2, visibleState.reelCount)
            assertTrue(visibleState.showReelCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageRotatesEveryDisplayDurationBoundary() = runTest {
        prefsRepo.update(
            HudPreferences(
                initialRevealDelaySeconds = 0,
                messageDisplayDurationSeconds = 30,
                selectedMessageIds = setOf("serving", "chose", "five_more")
            )
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))

        // Set initial timer tick before collecting flow
        timerTicks.value = 10_000L

        useCase().test {
            // 0s to 29s -> first message
            val item1 = awaitItem()
            assertTrue(item1.isVisible)
            assertEquals("Is this serving you?", item1.message)

            // 30s to 59s -> second message
            timerTicks.value = 40_000L
            val item2 = awaitItem()
            assertEquals("You chose this.", item2.message)

            // 60s to 89s -> third message
            timerTicks.value = 70_000L
            val item3 = awaitItem()
            assertEquals("Just five more?", item3.message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun singleSelectedMessageRemainsStatic() = runTest {
        prefsRepo.update(
            HudPreferences(
                initialRevealDelaySeconds = 0,
                messageDisplayDurationSeconds = 30,
                selectedMessageIds = setOf("serving")
            )
        )

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        timerTicks.value = 10_000L

        useCase().test {
            assertEquals("Is this serving you?", awaitItem().message)

            timerTicks.value = 50_000L
            assertEquals("Is this serving you?", awaitItem().message)

            timerTicks.value = 120_000L
            assertEquals("Is this serving you?", awaitItem().message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun browserSessionHidesReelLineCompletely() = runTest {
        prefsRepo.update(HudPreferences(initialRevealDelaySeconds = 0))

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.android.chrome", 10_000L))
        timerTicks.value = 15_000L

        useCase().test {
            val state = awaitItem()
            assertTrue(state.isVisible)
            assertFalse("Browser sessions must hide Reel count line", state.showReelCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakePrefsRepo : HudPreferencesRepository {
    private val _prefs = MutableStateFlow(HudPreferences())
    override val preferences: Flow<HudPreferences> = _prefs

    override suspend fun update(prefs: HudPreferences) { _prefs.value = prefs }
    override suspend fun setInitialRevealDelaySeconds(seconds: Int) {}
    override suspend fun setMessageDisplayDurationSeconds(seconds: Int) {}
    override suspend fun setSelectedMessageIds(ids: Set<String>) {}
    override suspend fun setHideDurationSeconds(seconds: Int) {}
    override suspend fun setHudOpacityPercent(percent: Int) {}
    override suspend fun setHudScale(scale: com.awareness.core.domain.model.HudScale) {}
    override suspend fun addCustomMessage(text: String): com.awareness.core.domain.model.MindfulnessMessage =
        com.awareness.core.domain.model.MindfulnessMessage("id", text)
    override suspend fun deleteCustomMessage(id: String) {}
}

private class FakeSessRepo : SessionRepository {
    override suspend fun save(summary: SessionSummary) {}
    override fun observeDailyAggregates(sinceWallClockMillis: Long): Flow<List<com.awareness.core.domain.model.DailyAggregate>> =
        flowOf(emptyList())
}
