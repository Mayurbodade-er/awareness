package com.awareness.feature.hud

import app.cash.turbine.test
import com.awareness.core.domain.model.HudState
import com.awareness.core.domain.usecase.ObserveHudStateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudViewModelTest {

    @Test
    fun hudStateExposesReactiveStreamFromUseCase() = runTest {
        val flow = MutableStateFlow(
            HudState(
                isVisible = false,
                message = "Take a breath",
                elapsedMillis = 0L,
                reelCount = 0,
                showReelCount = false,
                browserDomainLabel = null
            )
        )

        val fakeUseCase = object : ObserveHudStateUseCase {
            override fun invoke(): Flow<HudState> = flow
        }

        val testScope = TestScope(testScheduler)
        val viewModel = HudViewModel(fakeUseCase, testScope)

        viewModel.hudState.test {
            // Initial state from stateIn default
            val initial = awaitItem()
            assertFalse(initial.isVisible)
            assertEquals("", initial.message)

            // Advance scheduler to collect upstream flow emission
            testScope.testScheduler.advanceUntilIdle()
            val upstreamItem = awaitItem()
            assertEquals("Take a breath", upstreamItem.message)

            flow.value = upstreamItem.copy(isVisible = true, elapsedMillis = 15_000L)
            testScope.testScheduler.advanceUntilIdle()
            val updated = awaitItem()
            assertTrue(updated.isVisible)
            assertEquals(15_000L, updated.elapsedMillis)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
