package com.awareness.core.domain.model

data class DailyAggregate(
    val day: String, // "YYYY-MM-DD"
    val totalMillis: Long,
    val totalReels: Int,
    val sessionCount: Int
)
