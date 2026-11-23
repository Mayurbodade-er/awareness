package com.awareness.core.domain.model

sealed interface AwarenessEvent {
    data class SessionStarted(val packageName: String, val timestamp: Long) : AwarenessEvent
    data class SessionEnded(val packageName: String, val timestamp: Long) : AwarenessEvent
    data class ReelScrolled(val timestamp: Long) : AwarenessEvent
    data class BrowserDomainInferred(
        val packageName: String,
        val domain: String,
        val timestamp: Long
    ) : AwarenessEvent
}
