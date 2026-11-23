package com.awareness.core.domain.orchestrator

import com.awareness.core.domain.model.AwarenessEvent
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.SessionState
import com.awareness.core.domain.model.SessionSummary
import com.awareness.core.domain.model.TargetApp
import com.awareness.core.domain.repository.HudPreferencesRepository
import com.awareness.core.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pure Kotlin business orchestrator managing the Awareness session lifecycle.
 *
 * Invariants:
 * - Pure Kotlin; zero Android framework dependencies.
 * - Snapshots revealDelayMillisSnapshot at the exact instant of Idle -> Active.
 * - Increments reelCount on ReelScrolled regardless of HUD visibility.
 * - Inferred browser domain is transient and never persisted.
 * - On session end, persists SessionSummary to SessionRepository.
 */
interface SessionOrchestrator {
    val sessionState: StateFlow<SessionState>
    fun start(scope: CoroutineScope, events: Flow<AwarenessEvent>): Job
    suspend fun onEvent(event: AwarenessEvent)
}

class SessionOrchestratorImpl(
    private val hudPreferencesRepository: HudPreferencesRepository,
    private val sessionRepository: SessionRepository,
    private val wallClockProvider: () -> Long = { System.currentTimeMillis() }
) : SessionOrchestrator {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var lastEventTimestamp: Long = 0L

    override fun start(scope: CoroutineScope, events: Flow<AwarenessEvent>): Job {
        return scope.launch {
            events.collect { event ->
                onEvent(event)
            }
        }
    }

    override suspend fun onEvent(event: AwarenessEvent) {
        when (event) {
            is AwarenessEvent.SessionStarted -> handleSessionStarted(event)
            is AwarenessEvent.SessionEnded -> handleSessionEnded(event)
            is AwarenessEvent.ReelScrolled -> handleReelScrolled(event)
            is AwarenessEvent.BrowserDomainInferred -> handleDomainInferred(event)
        }
    }

    private suspend fun handleSessionStarted(event: AwarenessEvent.SessionStarted) {
        val currentState = _sessionState.value

        // Coalescing de-dupe: Ignore duplicate started events for the same package within 150ms
        if (currentState is SessionState.Active) {
            if (currentState.packageName == event.packageName) {
                if (event.timestamp - lastEventTimestamp < DE_DUPE_THRESHOLD_MILLIS) {
                    return
                }
                // Same package re-trigger without exit is ignored
                return
            } else {
                // Switched directly to another target app without intermediate exit
                closeActiveSession(currentState, event.timestamp)
            }
        }

        lastEventTimestamp = event.timestamp

        // Snapshot reveal delay from preferences at session start (default to 0 on read failure)
        val revealDelayMillis = try {
            val prefs = hudPreferencesRepository.preferences.first()
            prefs.revealDelayMillis
        } catch (_: Exception) {
            0L
        }

        val isBrowser = TargetApp.fromPackageName(event.packageName)?.isBrowser == true
        val wallClockStart = wallClockProvider()

        _sessionState.value = SessionState.Active(
            packageName = event.packageName,
            startElapsed = event.timestamp,
            wallClockStart = wallClockStart,
            isBrowser = isBrowser,
            revealDelayMillisSnapshot = revealDelayMillis,
            reelCount = 0,
            inferredDomain = null
        )
    }

    private suspend fun handleSessionEnded(event: AwarenessEvent.SessionEnded) {
        val currentState = _sessionState.value as? SessionState.Active ?: return
        if (currentState.packageName == event.packageName) {
            closeActiveSession(currentState, event.timestamp)
        }
    }

    private suspend fun closeActiveSession(activeState: SessionState.Active, endElapsed: Long) {
        val wallClockEnd = wallClockProvider()
        val durationMillis = maxOf(0L, endElapsed - activeState.startElapsed)

        val summary = SessionSummary(
            packageName = activeState.packageName,
            wallClockStart = activeState.wallClockStart,
            wallClockEnd = wallClockEnd,
            durationMillis = durationMillis,
            reelCount = activeState.reelCount,
            isBrowser = activeState.isBrowser
        )

        // Persist session summary asynchronously to local repository
        try {
            sessionRepository.save(summary)
        } catch (_: Exception) {
            // Local persistence failure logged non-fatally
        }

        _sessionState.value = SessionState.Idle
    }

    private fun handleReelScrolled(event: AwarenessEvent.ReelScrolled) {
        val currentState = _sessionState.value as? SessionState.Active ?: return
        // Increment reelCount independent of HUD visibility
        _sessionState.value = currentState.copy(
            reelCount = currentState.reelCount + 1
        )
    }

    private fun handleDomainInferred(event: AwarenessEvent.BrowserDomainInferred) {
        val currentState = _sessionState.value as? SessionState.Active ?: return
        if (currentState.isBrowser && currentState.packageName == event.packageName) {
            // Domain is transient in memory; never saved to SessionSummary
            _sessionState.value = currentState.copy(
                inferredDomain = event.domain
            )
        }
    }

    companion object {
        const val DE_DUPE_THRESHOLD_MILLIS = 150L
    }
}
