package com.awareness.feature.settings.hud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.model.HudScale
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
    val hideDurationSeconds: Int = 0,
    val hideDurationInput: String = "0",
    val hudOpacityPercent: Int = 85,
    val hudScale: HudScale = HudScale.STANDARD,
    val selectedMessageIds: Set<String> = DefaultMindfulnessMessages.defaultSelectedIds,
    val customMessages: List<MindfulnessMessage> = emptyList(),
    val customMessageInput: String = "",
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
) {
    val allMessages: List<MindfulnessMessage>
        get() = DefaultMindfulnessMessages.all + customMessages

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
                    hideDurationSeconds = prefs.hideDurationSeconds,
                    hideDurationInput = prefs.hideDurationSeconds.toString(),
                    hudOpacityPercent = prefs.hudOpacityPercent,
                    hudScale = prefs.hudScale,
                    selectedMessageIds = prefs.selectedMessageIds,
                    customMessages = prefs.customMessages
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

    fun onHideDurationChanged(input: String) {
        _uiState.update { it.copy(hideDurationInput = input, errorMessage = null) }
    }

    fun onOpacityChanged(opacity: Int) {
        _uiState.update { it.copy(hudOpacityPercent = opacity.coerceIn(20, 100)) }
    }

    fun onScaleSelected(scale: HudScale) {
        _uiState.update { it.copy(hudScale = scale) }
    }

    fun onCustomMessageInputChanged(input: String) {
        _uiState.update { it.copy(customMessageInput = input) }
    }

    fun addCustomMessage() {
        val text = _uiState.value.customMessageInput.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            val newMessage = repository.addCustomMessage(text)
            _uiState.update { current ->
                current.copy(
                    customMessages = current.customMessages + newMessage,
                    selectedMessageIds = current.selectedMessageIds + newMessage.id,
                    customMessageInput = "",
                    errorMessage = null
                )
            }
        }
    }

    fun deleteCustomMessage(id: String) {
        viewModelScope.launch {
            repository.deleteCustomMessage(id)
            _uiState.update { current ->
                val remainingCustom = current.customMessages.filterNot { it.id == id }
                val updatedSelected = current.selectedMessageIds - id
                current.copy(
                    customMessages = remainingCustom,
                    selectedMessageIds = updatedSelected,
                    errorMessage = if (updatedSelected.isEmpty()) "Select at least one message." else null
                )
            }
        }
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
        val parsedHideDuration = (state.hideDurationInput.toIntOrNull() ?: 0).coerceIn(0, 3600)

        viewModelScope.launch {
            repository.update(
                HudPreferences(
                    initialRevealDelaySeconds = parsedDelay,
                    messageDisplayDurationSeconds = parsedDuration,
                    hideDurationSeconds = parsedHideDuration,
                    hudOpacityPercent = state.hudOpacityPercent,
                    hudScale = state.hudScale,
                    selectedMessageIds = state.selectedMessageIds,
                    customMessages = state.customMessages
                )
            )
            _uiState.update {
                it.copy(
                    initialRevealDelaySeconds = parsedDelay,
                    initialRevealDelayInput = parsedDelay.toString(),
                    messageDisplayDurationSeconds = parsedDuration,
                    messageDisplayDurationInput = parsedDuration.toString(),
                    hideDurationSeconds = parsedHideDuration,
                    hideDurationInput = parsedHideDuration.toString(),
                    saveSuccess = true
                )
            }
        }
    }

    fun resetSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
