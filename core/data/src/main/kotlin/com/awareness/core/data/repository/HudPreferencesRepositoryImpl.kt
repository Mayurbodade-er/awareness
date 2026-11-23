package com.awareness.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.awareness.core.data.datastore.PreferencesKeys
import com.awareness.core.data.datastore.settingsDataStore
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.repository.HudPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class HudPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : HudPreferencesRepository {

    companion object {
        fun create(context: android.content.Context): HudPreferencesRepository =
            HudPreferencesRepositoryImpl(context.settingsDataStore)
    }

    override val preferences: Flow<HudPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val delaySeconds = (prefs[PreferencesKeys.INITIAL_REVEAL_DELAY_SECONDS] ?: 0).coerceIn(0, 3600)
            val durationSeconds = (prefs[PreferencesKeys.MESSAGE_DISPLAY_DURATION_SECONDS] ?: 30).coerceIn(5, 300)
            val rawSelectedIds = prefs[PreferencesKeys.SELECTED_MESSAGE_IDS] ?: DefaultMindfulnessMessages.defaultSelectedIds
            
            // Filter out unknown IDs, and fall back to default if empty
            val validIds = rawSelectedIds.filter { id ->
                DefaultMindfulnessMessages.all.any { it.id == id }
            }.toSet()
            
            val selectedIds = validIds.ifEmpty { DefaultMindfulnessMessages.defaultSelectedIds }

            HudPreferences(
                initialRevealDelaySeconds = delaySeconds,
                messageDisplayDurationSeconds = durationSeconds,
                selectedMessageIds = selectedIds
            )
        }

    override suspend fun update(prefs: HudPreferences) {
        val clampedDelay = prefs.initialRevealDelaySeconds.coerceIn(0, 3600)
        val clampedDuration = prefs.messageDisplayDurationSeconds.coerceIn(5, 300)
        val validIds = prefs.selectedMessageIds.filter { id ->
            DefaultMindfulnessMessages.all.any { it.id == id }
        }.toSet().ifEmpty { DefaultMindfulnessMessages.defaultSelectedIds }

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.INITIAL_REVEAL_DELAY_SECONDS] = clampedDelay
            preferences[PreferencesKeys.MESSAGE_DISPLAY_DURATION_SECONDS] = clampedDuration
            preferences[PreferencesKeys.SELECTED_MESSAGE_IDS] = validIds
        }
    }

    override suspend fun setInitialRevealDelaySeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.INITIAL_REVEAL_DELAY_SECONDS] = seconds.coerceIn(0, 3600)
        }
    }

    override suspend fun setMessageDisplayDurationSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MESSAGE_DISPLAY_DURATION_SECONDS] = seconds.coerceIn(5, 300)
        }
    }

    override suspend fun setSelectedMessageIds(ids: Set<String>) {
        val validIds = ids.filter { id ->
            DefaultMindfulnessMessages.all.any { it.id == id }
        }.toSet().ifEmpty { DefaultMindfulnessMessages.defaultSelectedIds }

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MESSAGE_IDS] = validIds
        }
    }
}
