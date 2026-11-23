package com.awareness.core.data.repository

import com.awareness.core.domain.model.DailyAggregate
import com.awareness.core.domain.model.SessionSummary
import com.awareness.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory session repository implementation for VS-03.
 *
 * Persists session summaries in memory until Room SQLite persistence is wired in VS-08.
 */
class SessionRepositoryImpl : SessionRepository {

    private val mutex = Mutex()
    private val sessions = mutableListOf<SessionSummary>()
    private val _aggregates = MutableStateFlow<List<DailyAggregate>>(emptyList())

    override suspend fun save(summary: SessionSummary) {
        mutex.withLock {
            sessions.add(summary)
            recalculateAggregates()
        }
    }

    override fun observeDailyAggregates(sinceWallClockMillis: Long): Flow<List<DailyAggregate>> =
        _aggregates.asStateFlow()

    suspend fun getAllSessions(): List<SessionSummary> {
        return mutex.withLock { sessions.toList() }
    }

    private fun recalculateAggregates() {
        val grouped = sessions.groupBy { summary ->
            // Simple epoch day formatting for aggregate key
            val epochDay = summary.wallClockStart / (1000 * 60 * 60 * 24)
            "Day-$epochDay"
        }

        val result = grouped.map { (day, list) ->
            DailyAggregate(
                day = day,
                totalMillis = list.sumOf { it.durationMillis },
                totalReels = list.sumOf { it.reelCount },
                sessionCount = list.size
            )
        }
        _aggregates.value = result
    }
}
