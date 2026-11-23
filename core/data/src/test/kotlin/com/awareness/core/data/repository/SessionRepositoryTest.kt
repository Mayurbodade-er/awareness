package com.awareness.core.data.repository

import app.cash.turbine.test
import com.awareness.core.domain.model.SessionSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    @Test
    fun saveSessionSummaryStoresAndCanBeObserved() = runTest {
        val repository = SessionRepositoryImpl()

        val summary1 = SessionSummary(
            packageName = "com.instagram.android",
            wallClockStart = 1_000_000L,
            wallClockEnd = 1_050_000L,
            durationMillis = 50_000L,
            reelCount = 4,
            isBrowser = false
        )

        val summary2 = SessionSummary(
            packageName = "com.android.chrome",
            wallClockStart = 1_100_000L,
            wallClockEnd = 1_130_000L,
            durationMillis = 30_000L,
            reelCount = 0,
            isBrowser = true
        )

        repository.save(summary1)
        repository.save(summary2)

        val allSessions = repository.getAllSessions()
        assertEquals(2, allSessions.size)
        assertEquals(summary1, allSessions[0])
        assertEquals(summary2, allSessions[1])

        repository.observeDailyAggregates(0L).test {
            val aggregates = awaitItem()
            assertTrue(aggregates.isNotEmpty())
            val totalReels = aggregates.sumOf { it.totalReels }
            val totalMillis = aggregates.sumOf { it.totalMillis }
            val sessionCount = aggregates.sumOf { it.sessionCount }

            assertEquals(4, totalReels)
            assertEquals(80_000L, totalMillis)
            assertEquals(2, sessionCount)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
