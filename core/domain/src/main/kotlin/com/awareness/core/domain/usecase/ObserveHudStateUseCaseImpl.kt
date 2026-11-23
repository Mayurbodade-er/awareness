package com.awareness.core.domain.usecase

import com.awareness.core.domain.model.HudState
import com.awareness.core.domain.model.SessionState
import com.awareness.core.domain.orchestrator.SessionOrchestrator
import com.awareness.core.domain.repository.HudPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Pure Kotlin Use Case combining SessionState, HudPreferences, and 1 Hz Monotonic Timer Ticks
 * into a stream of HudState models consumed by the HUD renderer.
 *
 * Invariants:
 * - isVisible is true iff state is Active AND elapsed >= revealDelayMillisSnapshot.
 * - Messages rotate strictly every messageDisplayDurationSeconds.
 * - showReelCount is false for browsers (isBrowser = true).
 * - Only the timer and message rotate on the 1 Hz monotonic tick.
 */
class ObserveHudStateUseCaseImpl(
    private val sessionOrchestrator: SessionOrchestrator,
    private val hudPreferencesRepository: HudPreferencesRepository,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val timerTicks: Flow<Long> = defaultTimerTicksFlow(timeProvider)
) : ObserveHudStateUseCase {

    override fun invoke(): Flow<HudState> {
        return combine(
            sessionOrchestrator.sessionState,
            hudPreferencesRepository.preferences,
            timerTicks
        ) { sessionState, preferences, currentElapsedMillis ->
            when (sessionState) {
                is SessionState.Idle -> HudState(
                    isVisible = false,
                    message = "",
                    elapsedMillis = 0L,
                    reelCount = 0,
                    showReelCount = false,
                    browserDomainLabel = null
                )

                is SessionState.Active -> {
                    val elapsedMillis = maxOf(0L, currentElapsedMillis - sessionState.startElapsed)
                    val isVisible = elapsedMillis >= sessionState.revealDelayMillisSnapshot

                    // Message rotation
                    val messages = preferences.selectedMessages
                    val durationMillis = preferences.messageDisplayDurationSeconds * 1000L
                    val messageText = if (messages.isNotEmpty() && durationMillis > 0L) {
                        val index = ((elapsedMillis / durationMillis) % messages.size).toInt()
                        messages[index].text
                    } else {
                        messages.firstOrNull()?.text ?: ""
                    }

                    // Transient domain label for browsers
                    val domainLabel = if (sessionState.isBrowser && sessionState.inferredDomain != null) {
                        "Browsing: ${sessionState.inferredDomain}"
                    } else null

                    HudState(
                        isVisible = isVisible,
                        message = messageText,
                        elapsedMillis = elapsedMillis,
                        reelCount = sessionState.reelCount,
                        showReelCount = !sessionState.isBrowser,
                        browserDomainLabel = domainLabel
                    )
                }
            }
        }
    }

    companion object {
        fun defaultTimerTicksFlow(
            timeProvider: () -> Long = { System.currentTimeMillis() },
            stepMillis: Long = 1000L
        ): Flow<Long> = flow {
            while (true) {
                emit(timeProvider())
                delay(stepMillis)
            }
        }
    }
}
