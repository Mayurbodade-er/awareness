package com.awareness.core.presentation.util

object MessageRotation {
    fun indexFor(
        elapsedMillis: Long,
        revealDelayMillis: Long,
        messageDisplayDurationSeconds: Int,
        messageCount: Int
    ): Int {
        if (messageCount <= 1) return 0
        val durationMillis = (messageDisplayDurationSeconds.coerceIn(5, 300)) * 1000L
        val effectiveElapsed = (elapsedMillis - revealDelayMillis).coerceAtLeast(0L)
        val cycle = effectiveElapsed / durationMillis
        return (cycle % messageCount).toInt()
    }
}
