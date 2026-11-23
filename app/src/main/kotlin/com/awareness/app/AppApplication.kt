package com.awareness.app

import android.app.Application
import android.os.SystemClock
import com.awareness.core.accessibility.AccessibilityEventBusImpl
import com.awareness.core.data.repository.HudPreferencesRepositoryImpl
import com.awareness.core.data.repository.SessionRepositoryImpl
import com.awareness.core.domain.model.SessionState
import com.awareness.core.domain.orchestrator.SessionOrchestrator
import com.awareness.core.domain.orchestrator.SessionOrchestratorImpl
import com.awareness.core.domain.usecase.ObserveHudStateUseCase
import com.awareness.core.domain.usecase.ObserveHudStateUseCaseImpl
import com.awareness.feature.hud.HudForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var sessionOrchestrator: SessionOrchestrator
    lateinit var observeHudStateUseCase: ObserveHudStateUseCase

    override fun onCreate() {
        super.onCreate()

        val hudPreferencesRepository = HudPreferencesRepositoryImpl.create(applicationContext)
        val sessionRepository = SessionRepositoryImpl()

        sessionOrchestrator = SessionOrchestratorImpl(
            hudPreferencesRepository = hudPreferencesRepository,
            sessionRepository = sessionRepository
        )

        observeHudStateUseCase = ObserveHudStateUseCaseImpl(
            sessionOrchestrator = sessionOrchestrator,
            hudPreferencesRepository = hudPreferencesRepository,
            timeProvider = { SystemClock.elapsedRealtime() }
        )

        val hudStateFlow = observeHudStateUseCase()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = HudForegroundService.defaultHudStateFlow.value
            )
        HudForegroundService.hudStateFlowProvider = { hudStateFlow }

        // Start listening to system accessibility events
        sessionOrchestrator.start(applicationScope, AccessibilityEventBusImpl.instance.events)

        // Automatically start or stop HUD foreground service on session transitions
        applicationScope.launch {
            sessionOrchestrator.sessionState.collect { state ->
                android.util.Log.e("AppApp", "SessionState collection received: $state")
                when (state) {
                    is SessionState.Active -> {
                        android.util.Log.e("AppApp", "Starting HudForegroundService for ${state.packageName}, revealDelay=${state.revealDelayMillisSnapshot}ms")
                        HudForegroundService.start(applicationContext)
                    }
                    is SessionState.Idle -> {
                        android.util.Log.e("AppApp", "Stopping HudForegroundService on Idle")
                        HudForegroundService.stop(applicationContext)
                    }
                }
            }
        }
    }
}
