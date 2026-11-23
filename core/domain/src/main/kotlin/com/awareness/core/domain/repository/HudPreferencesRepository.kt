package com.awareness.core.domain.repository

import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.HudScale
import com.awareness.core.domain.model.MindfulnessMessage
import kotlinx.coroutines.flow.Flow

interface HudPreferencesRepository {
    val preferences: Flow<HudPreferences>
    suspend fun update(prefs: HudPreferences)
    suspend fun setInitialRevealDelaySeconds(seconds: Int)
    suspend fun setMessageDisplayDurationSeconds(seconds: Int)
    suspend fun setSelectedMessageIds(ids: Set<String>)
    suspend fun setHideDurationSeconds(seconds: Int)
    suspend fun setHudOpacityPercent(percent: Int)
    suspend fun setHudScale(scale: HudScale)
    suspend fun addCustomMessage(text: String): MindfulnessMessage
    suspend fun deleteCustomMessage(id: String)
}
