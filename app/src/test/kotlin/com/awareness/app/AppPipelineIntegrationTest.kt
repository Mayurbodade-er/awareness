package com.awareness.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.awareness.core.accessibility.AccessibilityEventBusImpl
import com.awareness.core.domain.model.AwarenessEvent
import com.awareness.core.domain.model.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AppApplication::class)
class AppPipelineIntegrationTest {

    @Test
    fun applicationWiresOrchestratorToAccessibilityEvents() {
        val app = ApplicationProvider.getApplicationContext<Context>() as AppApplication

        assertNotNull("sessionOrchestrator must be initialized", app.sessionOrchestrator)
        assertNotNull("observeHudStateUseCase must be initialized", app.observeHudStateUseCase)

        // Ensure appScope coroutines in onCreate() start collecting
        ShadowLooper.idleMainLooper()

        // Verify initial state is Idle
        assertTrue(app.sessionOrchestrator.sessionState.value is SessionState.Idle)

        // Emit SessionStarted event via AccessibilityEventBus
        val emitted = AccessibilityEventBusImpl.instance.tryEmit(
            AwarenessEvent.SessionStarted("com.instagram.android", 10_000L)
        )
        assertTrue("Event should be emitted", emitted)

        // Allow background DataStore read and main looper coroutine to complete
        val startWait = System.currentTimeMillis()
        while (app.sessionOrchestrator.sessionState.value !is SessionState.Active &&
            System.currentTimeMillis() - startWait < 5000
        ) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
        ShadowLooper.idleMainLooper()

        // Verify state transitioned to Active
        val activeState = app.sessionOrchestrator.sessionState.value as SessionState.Active
        assertEquals("com.instagram.android", activeState.packageName)

        // Emit SessionEnded
        AccessibilityEventBusImpl.instance.tryEmit(
            AwarenessEvent.SessionEnded("com.instagram.android", 15_000L)
        )

        val endWait = System.currentTimeMillis()
        while (app.sessionOrchestrator.sessionState.value !is SessionState.Idle &&
            System.currentTimeMillis() - endWait < 5000
        ) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
        ShadowLooper.idleMainLooper()

        assertTrue(app.sessionOrchestrator.sessionState.value is SessionState.Idle)
    }
}
