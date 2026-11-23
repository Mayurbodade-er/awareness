package com.awareness.feature.hud

import com.awareness.core.domain.model.HudState
import com.awareness.core.domain.usecase.ObserveHudStateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel exposing reactive HudState stream to the HUD overlay composable.
 */
class HudViewModel(
    observeHudStateUseCase: ObserveHudStateUseCase,
    scope: CoroutineScope
) {
    val hudState: StateFlow<HudState> = observeHudStateUseCase()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = HudState(
                isVisible = false,
                message = "",
                elapsedMillis = 0L,
                reelCount = 0,
                showReelCount = false,
                browserDomainLabel = null
            )
        )
}
