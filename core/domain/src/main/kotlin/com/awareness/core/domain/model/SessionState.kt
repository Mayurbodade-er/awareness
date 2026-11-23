package com.awareness.core.domain.model

sealed interface SessionState {
    data object Idle : SessionState

    data class Active(
        val packageName: String,
        val startElapsed: Long,
        val wallClockStart: Long,
        val isBrowser: Boolean,
        val revealDelayMillisSnapshot: Long,
        val reelCount: Int,
        val inferredDomain: String? = null
    ) : SessionState
}
