package com.awareness.core.domain.model

data class SessionSummary(
    val packageName: String,
    val wallClockStart: Long,
    val wallClockEnd: Long,
    val durationMillis: Long,
    val reelCount: Int,
    val isBrowser: Boolean
)
