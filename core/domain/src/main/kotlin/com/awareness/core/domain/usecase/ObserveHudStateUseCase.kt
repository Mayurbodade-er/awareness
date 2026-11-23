package com.awareness.core.domain.usecase

import com.awareness.core.domain.model.HudState
import kotlinx.coroutines.flow.Flow

interface ObserveHudStateUseCase {
    operator fun invoke(): Flow<HudState>
}
