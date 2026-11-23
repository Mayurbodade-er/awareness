package com.awareness.core.domain.repository

import com.awareness.core.domain.model.DailyAggregate
import com.awareness.core.domain.model.SessionSummary
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun save(summary: SessionSummary)
    fun observeDailyAggregates(sinceWallClockMillis: Long): Flow<List<DailyAggregate>>
}
