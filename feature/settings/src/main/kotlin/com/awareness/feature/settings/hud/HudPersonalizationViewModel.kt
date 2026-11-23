package com.awareness.feature.settings.hud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.MindfulnessMessage
import com.awareness.core.domain.repository.HudPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HudPersonalizationUiState(
    val initialRevealDelaySeconds: Int = 0,
    val initialRevealDelayInput: String = "0",
    val messageDisplayDurationSeconds: Int = 30,
    val messageDisplayDurationInput: String = "30",
    val selectedMessageIds: Set<String> = DefaultMindfulnessMessages.defaultSelectedIds,
    val allMessages: List<MindfulnessMessage> = DefaultMindfulnessMessages.all,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
) {
    val isSaveEnabled: Boolean
        get() = selectedMessageIds.isNotEmpty()
}

class HudPersonalizationViewModel(
    private val repository: HudPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HudPersonalizationUiState())
    val uiState: StateFlow<HudPersonalizationUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val prefs = repository.preferences.first()
            _uiState.update { current ->
                current.copy(
                    initialRevealDelaySeconds = prefs.initialRevealDelaySeconds,
                    initialRevealDelayInput = prefs.initialRevealDelaySeconds.toString(),
                    messageDisplayDurationSeconds = prefs.messageDisplayDurationSeconds,
                    messageDisplayDurationInput = prefs.messageDisplayDurationSeconds.toString(),
                    selectedMessageIds = prefs.selectedMessageIds
                )
            }
        }
    }

    fun onRevealDelayChanged(input: String) {
        _uiState.update { it.copy(initialRevealDelayInput = input, errorMessage = null) }
    }

    fun onDurationChanged(input: String) {
        _uiState.update { it.copy(messageDisplayDurationInput = input, errorMessage = null) }
    }

    fun onMessageToggle(messageId: String) {
        _uiState.update { current ->
            val updated = if (current.selectedMessageIds.contains(messageId)) {
                current.selectedMessageIds - messageId
            } else {
                current.selectedMessageIds + messageId
            }
            current.copy(
                selectedMessageIds = updated,
                errorMessage = if (updated.isEmpty()) "Select at least one message." else null
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.selectedMessageIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one message.") }
            return
        }

        val parsedDelay = (state.initialRevealDelayInput.toIntOrNull() ?: 0).coerceIn(0, 3600)
        val parsedDuration = (state.messageDisplayDurationInput.toIntOrNull() ?: 30).coerceIn(5, 300)

        viewModelScope.launch {
            repository.update(
                HudPreferences(
                    initialRevealDelaySeconds = parsedDelay,
                    messageDisplayDurationSeconds = parsedDuration,
                    selectedMessageIds = state.selectedMessageIds
                )
            )
            _uiState.update {
                it.copy(
                    initialRevealDelaySeconds = parsedDelay,
                    initialRevealDelayInput = parsedDelay.toString(),
                    messageDisplayDurationSeconds = parsedDuration,
                    messageDisplayDurationInput = parsedDuration.toString(),
                    saveSuccess = true
                )
            }
        }
    }

    fun resetSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
