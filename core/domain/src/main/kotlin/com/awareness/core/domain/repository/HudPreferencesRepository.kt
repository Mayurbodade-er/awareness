package com.awareness.core.domain.repository

import com.awareness.core.domain.model.HudPreferences
import kotlinx.coroutines.flow.Flow

interface HudPreferencesRepository {
    val preferences: Flow<HudPreferences>
    suspend fun update(prefs: HudPreferences)
    suspend fun setInitialRevealDelaySeconds(seconds: Int)
    suspend fun setMessageDisplayDurationSeconds(seconds: Int)
    suspend fun setSelectedMessageIds(ids: Set<String>)
}
