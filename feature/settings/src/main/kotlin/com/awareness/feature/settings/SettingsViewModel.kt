package com.awareness.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.repository.HudPreferencesRepository
import com.awareness.core.presentation.permission.PermissionRequestCoordinator
import com.awareness.core.presentation.permission.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val permissionState: PermissionState = PermissionState(
        accessibilityGranted = false,
        overlayGranted = false,
        batteryOptimizationIgnored = false,
        notificationsGranted = true
    ),
    val hudPreferences: HudPreferences = HudPreferences(),
    val showPersonalizationScreen: Boolean = false
)

class SettingsViewModel(
    private val permissionCoordinator: PermissionRequestCoordinator,
    private val hudPreferencesRepository: HudPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionState()
        observePreferences()
    }

    fun refreshPermissionState() {
        val state = permissionCoordinator.getPermissionState()
        _uiState.update { it.copy(permissionState = state) }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            hudPreferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(hudPreferences = prefs) }
            }
        }
    }

    fun requestAccessibility(activity: Activity) {
        permissionCoordinator.requestAccessibility(activity)
    }

    fun requestOverlay(activity: Activity) {
        permissionCoordinator.requestOverlay(activity)
    }

    fun requestBatteryOptimization(activity: Activity) {
        permissionCoordinator.requestBatteryOptimization(activity)
    }

    fun requestNotifications(activity: Activity) {
        permissionCoordinator.requestNotifications(activity)
    }

    fun openPersonalization() {
        _uiState.update { it.copy(showPersonalizationScreen = true) }
    }

    fun closePersonalization() {
        _uiState.update { it.copy(showPersonalizationScreen = false) }
    }
}
