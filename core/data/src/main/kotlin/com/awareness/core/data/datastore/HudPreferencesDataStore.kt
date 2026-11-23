package com.awareness.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object PreferencesKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val INITIAL_REVEAL_DELAY_SECONDS = intPreferencesKey("hud_initial_reveal_delay_seconds")
    val MESSAGE_DISPLAY_DURATION_SECONDS = intPreferencesKey("hud_message_display_duration_seconds")
    val SELECTED_MESSAGE_IDS = stringSetPreferencesKey("hud_selected_message_ids")
    val HIDE_DURATION_SECONDS = intPreferencesKey("hud_hide_duration_seconds")
    val HUD_OPACITY_PERCENT = intPreferencesKey("hud_opacity_percent")
    val HUD_SCALE = stringPreferencesKey("hud_scale")
    val CUSTOM_MESSAGES_SET = stringSetPreferencesKey("hud_custom_messages_set")
}
