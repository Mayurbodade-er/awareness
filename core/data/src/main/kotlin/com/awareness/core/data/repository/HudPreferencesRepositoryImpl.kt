package com.awareness.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.awareness.core.data.datastore.PreferencesKeys
import com.awareness.core.data.datastore.settingsDataStore
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.HudScale
import com.awareness.core.domain.model.MindfulnessMessage
import com.awareness.core.domain.repository.HudPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

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
            val hideDurationSeconds = (prefs[PreferencesKeys.HIDE_DURATION_SECONDS] ?: 0).coerceIn(0, 3600)
            val opacityPercent = (prefs[PreferencesKeys.HUD_OPACITY_PERCENT] ?: 85).coerceIn(20, 100)
            val scaleString = prefs[PreferencesKeys.HUD_SCALE] ?: HudScale.STANDARD.name
            val scale = runCatching { HudScale.valueOf(scaleString) }.getOrDefault(HudScale.STANDARD)

            val customMessagesRaw = prefs[PreferencesKeys.CUSTOM_MESSAGES_SET] ?: emptySet()
            val customMessages = customMessagesRaw.mapNotNull { entry ->
                val separatorIndex = entry.indexOf('|')
                if (separatorIndex > 0) {
                    val id = entry.substring(0, separatorIndex)
                    val text = entry.substring(separatorIndex + 1)
                    MindfulnessMessage(id = id, text = text)
                } else null
            }

            val allKnownMessages = DefaultMindfulnessMessages.all + customMessages
            val rawSelectedIds = prefs[PreferencesKeys.SELECTED_MESSAGE_IDS] ?: DefaultMindfulnessMessages.defaultSelectedIds
            
            // Filter out unknown IDs, and fall back to default if empty
            val validIds = rawSelectedIds.filter { id ->
                allKnownMessages.any { it.id == id }
            }.toSet()
            
            val selectedIds = validIds.ifEmpty { DefaultMindfulnessMessages.defaultSelectedIds }

            HudPreferences(
                initialRevealDelaySeconds = delaySeconds,
                messageDisplayDurationSeconds = durationSeconds,
                hideDurationSeconds = hideDurationSeconds,
                selectedMessageIds = selectedIds,
                customMessages = customMessages,
                hudOpacityPercent = opacityPercent,
                hudScale = scale
            )
        }

    override suspend fun update(prefs: HudPreferences) {
        val clampedDelay = prefs.initialRevealDelaySeconds.coerceIn(0, 3600)
        val clampedDuration = prefs.messageDisplayDurationSeconds.coerceIn(5, 300)
        val clampedHideDuration = prefs.hideDurationSeconds.coerceIn(0, 3600)
        val clampedOpacity = prefs.hudOpacityPercent.coerceIn(20, 100)
        val allKnownMessages = DefaultMindfulnessMessages.all + prefs.customMessages
        val validIds = prefs.selectedMessageIds.filter { id ->
            allKnownMessages.any { it.id == id }
        }.toSet().ifEmpty { DefaultMindfulnessMessages.defaultSelectedIds }
        val serializedCustomMessages = prefs.customMessages.map { "${it.id}|${it.text}" }.toSet()

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.INITIAL_REVEAL_DELAY_SECONDS] = clampedDelay
            preferences[PreferencesKeys.MESSAGE_DISPLAY_DURATION_SECONDS] = clampedDuration
            preferences[PreferencesKeys.HIDE_DURATION_SECONDS] = clampedHideDuration
            preferences[PreferencesKeys.HUD_OPACITY_PERCENT] = clampedOpacity
            preferences[PreferencesKeys.HUD_SCALE] = prefs.hudScale.name
            preferences[PreferencesKeys.SELECTED_MESSAGE_IDS] = validIds
            preferences[PreferencesKeys.CUSTOM_MESSAGES_SET] = serializedCustomMessages
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
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MESSAGE_IDS] = ids
        }
    }

    override suspend fun setHideDurationSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_DURATION_SECONDS] = seconds.coerceIn(0, 3600)
        }
    }

    override suspend fun setHudOpacityPercent(percent: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HUD_OPACITY_PERCENT] = percent.coerceIn(20, 100)
        }
    }

    override suspend fun setHudScale(scale: HudScale) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HUD_SCALE] = scale.name
        }
    }

    override suspend fun addCustomMessage(text: String): MindfulnessMessage {
        val trimmed = text.trim()
        val id = "custom_${UUID.randomUUID().toString().take(8)}"
        val message = MindfulnessMessage(id = id, text = trimmed)
        val currentPrefs = preferences.first()
        val updatedCustomMessages = currentPrefs.customMessages + message
        val updatedSelectedIds = currentPrefs.selectedMessageIds + id
        update(currentPrefs.copy(
            customMessages = updatedCustomMessages,
            selectedMessageIds = updatedSelectedIds
        ))
        return message
    }

    override suspend fun deleteCustomMessage(id: String) {
        val currentPrefs = preferences.first()
        val updatedCustomMessages = currentPrefs.customMessages.filterNot { it.id == id }
        val updatedSelectedIds = currentPrefs.selectedMessageIds - id
        update(currentPrefs.copy(
            customMessages = updatedCustomMessages,
            selectedMessageIds = updatedSelectedIds
        ))
    }
}
