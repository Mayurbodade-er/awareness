package com.awareness.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(value: Boolean)
}
