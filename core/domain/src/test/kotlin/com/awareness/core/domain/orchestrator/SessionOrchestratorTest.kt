package com.awareness.core.domain.orchestrator

import com.awareness.core.domain.model.AwarenessEvent
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.SessionState
import com.awareness.core.domain.model.SessionSummary
import com.awareness.core.domain.repository.HudPreferencesRepository
import com.awareness.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionOrchestratorTest {

    private lateinit var fakePreferencesRepository: FakeHudPreferencesRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private var simulatedWallClock = 1_000_000L
    private lateinit var orchestrator: SessionOrchestratorImpl

    @Before
    fun setUp() {
        fakePreferencesRepository = FakeHudPreferencesRepository()
        fakeSessionRepository = FakeSessionRepository()
        orchestrator = SessionOrchestratorImpl(
            hudPreferencesRepository = fakePreferencesRepository,
            sessionRepository = fakeSessionRepository,
            wallClockProvider = { simulatedWallClock }
        )
    }

    @Test
    fun startSessionTransitionsIdleToActiveWithSnapshot() = runTest {
        assertEquals(SessionState.Idle, orchestrator.sessionState.value)

        fakePreferencesRepository.emitPreferences(
            HudPreferences(initialRevealDelaySeconds = 120, messageDisplayDurationSeconds = 30)
        )

        val t0 = 10_000L
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", t0))

        val state = orchestrator.sessionState.value as SessionState.Active
        assertEquals("com.instagram.android", state.packageName)
        assertEquals(t0, state.startElapsed)
        assertEquals(simulatedWallClock, state.wallClockStart)
        assertFalse(state.isBrowser)
        assertEquals(120_000L, state.revealDelayMillisSnapshot)
        assertEquals(0, state.reelCount)
        assertNull(state.inferredDomain)
    }

    @Test
    fun duplicateSessionStartedWithin150msIsIgnored() = runTest {
        fakePreferencesRepository.emitPreferences(HudPreferences(initialRevealDelaySeconds = 60))

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        val firstState = orchestrator.sessionState.value

        // Duplicate event 50ms later for the same package
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_050L))
        val secondState = orchestrator.sessionState.value

        assertEquals(firstState, secondState)
    }

    @Test
    fun endSessionTransitionsActiveToIdleAndSavesSummary() = runTest {
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        assertTrue(orchestrator.sessionState.value is SessionState.Active)

        simulatedWallClock += 5_000L
        orchestrator.onEvent(AwarenessEvent.SessionEnded("com.instagram.android", 15_000L))

        assertEquals(SessionState.Idle, orchestrator.sessionState.value)
        assertEquals(1, fakeSessionRepository.savedSummaries.size)

        val summary = fakeSessionRepository.savedSummaries[0]
        assertEquals("com.instagram.android", summary.packageName)
        assertEquals(5_000L, summary.durationMillis)
        assertEquals(0, summary.reelCount)
        assertFalse(summary.isBrowser)
    }

    @Test
    fun switchingBetweenTargetAppsEndsPreviousAndStartsNew() = runTest {
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        assertEquals("com.instagram.android", (orchestrator.sessionState.value as SessionState.Active).packageName)

        simulatedWallClock += 12_000L
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.android.chrome", 22_000L))

        // Previous session saved
        assertEquals(1, fakeSessionRepository.savedSummaries.size)
        assertEquals("com.instagram.android", fakeSessionRepository.savedSummaries[0].packageName)
        assertEquals(12_000L, fakeSessionRepository.savedSummaries[0].durationMillis)

        // New session is active and browser is true
        val newState = orchestrator.sessionState.value as SessionState.Active
        assertEquals("com.android.chrome", newState.packageName)
        assertTrue(newState.isBrowser)
    }

    @Test
    fun revealDelaySnapshotIsImmutableMidSession() = runTest {
        fakePreferencesRepository.emitPreferences(HudPreferences(initialRevealDelaySeconds = 180))

        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))
        val active = orchestrator.sessionState.value as SessionState.Active
        assertEquals(180_000L, active.revealDelayMillisSnapshot)

        // Change preferences mid-session to 30s
        fakePreferencesRepository.emitPreferences(HudPreferences(initialRevealDelaySeconds = 30))

        // Current session must retain the original 180s delay snapshot!
        val current = orchestrator.sessionState.value as SessionState.Active
        assertEquals(180_000L, current.revealDelayMillisSnapshot)

        // End session and start a new one -> new session receives updated 30s delay
        orchestrator.onEvent(AwarenessEvent.SessionEnded("com.instagram.android", 15_000L))
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 20_000L))

        val nextActive = orchestrator.sessionState.value as SessionState.Active
        assertEquals(30_000L, nextActive.revealDelayMillisSnapshot)
    }

    @Test
    fun reelScrolledIncrementsReelCountBeforeReveal() = runTest {
        fakePreferencesRepository.emitPreferences(HudPreferences(initialRevealDelaySeconds = 180))
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.instagram.android", 10_000L))

        orchestrator.onEvent(AwarenessEvent.ReelScrolled(11_000L))
        orchestrator.onEvent(AwarenessEvent.ReelScrolled(12_000L))
        orchestrator.onEvent(AwarenessEvent.ReelScrolled(13_000L))

        val active = orchestrator.sessionState.value as SessionState.Active
        assertEquals(3, active.reelCount)

        orchestrator.onEvent(AwarenessEvent.SessionEnded("com.instagram.android", 15_000L))
        val summary = fakeSessionRepository.savedSummaries[0]
        assertEquals(3, summary.reelCount)
    }

    @Test
    fun preferencesReadFailureDefaultsToZeroDelayWithoutCrashing() = runTest {
        val failingRepo = object : HudPreferencesRepository {
            override val preferences: Flow<HudPreferences>
                get() = throw RuntimeException("Simulated DataStore read failure")
            override suspend fun update(prefs: HudPreferences) {}
            override suspend fun setInitialRevealDelaySeconds(seconds: Int) {}
            override suspend fun setMessageDisplayDurationSeconds(seconds: Int) {}
            override suspend fun setSelectedMessageIds(ids: Set<String>) {}
        }

        val safeOrchestrator = SessionOrchestratorImpl(
            hudPreferencesRepository = failingRepo,
            sessionRepository = fakeSessionRepository,
            wallClockProvider = { simulatedWallClock }
        )

        safeOrchestrator.onEvent(AwarenessEvent.SessionStarted("com.snapchat.android", 10_000L))
        val active = safeOrchestrator.sessionState.value as SessionState.Active
        assertEquals(0L, active.revealDelayMillisSnapshot)
        assertEquals("com.snapchat.android", active.packageName)
    }

    @Test
    fun browserFlagAndInferredDomainAreHandledProperly() = runTest {
        orchestrator.onEvent(AwarenessEvent.SessionStarted("com.android.chrome", 10_000L))
        val active = orchestrator.sessionState.value as SessionState.Active
        assertTrue(active.isBrowser)
        assertNull(active.inferredDomain)

        orchestrator.onEvent(AwarenessEvent.BrowserDomainInferred("com.android.chrome", "reddit.com", 11_000L))
        val updated = orchestrator.sessionState.value as SessionState.Active
        assertEquals("reddit.com", updated.inferredDomain)

        orchestrator.onEvent(AwarenessEvent.SessionEnded("com.android.chrome", 15_000L))
        val summary = fakeSessionRepository.savedSummaries[0]
        assertTrue(summary.isBrowser)
    }
}

class FakeHudPreferencesRepository : HudPreferencesRepository {
    private val _prefs = MutableStateFlow(HudPreferences())
    override val preferences: Flow<HudPreferences> = _prefs

    fun emitPreferences(prefs: HudPreferences) {
        _prefs.value = prefs
    }

    override suspend fun update(prefs: HudPreferences) { _prefs.value = prefs }
    override suspend fun setInitialRevealDelaySeconds(seconds: Int) {}
    override suspend fun setMessageDisplayDurationSeconds(seconds: Int) {}
    override suspend fun setSelectedMessageIds(ids: Set<String>) {}
}

class FakeSessionRepository : SessionRepository {
    val savedSummaries = mutableListOf<SessionSummary>()

    override suspend fun save(summary: SessionSummary) {
        savedSummaries.add(summary)
    }

    override fun observeDailyAggregates(sinceWallClockMillis: Long): Flow<List<com.awareness.core.domain.model.DailyAggregate>> =
        flowOf(emptyList())
}
