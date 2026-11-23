package com.awareness.feature.settings

import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudPreferences
import com.awareness.core.domain.repository.HudPreferencesRepository
import com.awareness.feature.settings.hud.HudPersonalizationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HudPersonalizationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeHudPreferencesRepository
    private lateinit var viewModel: HudPersonalizationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeHudPreferencesRepository()
        viewModel = HudPersonalizationViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects repository defaults`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(0, state.initialRevealDelaySeconds)
        assertEquals("0", state.initialRevealDelayInput)
        assertEquals(30, state.messageDisplayDurationSeconds)
        assertEquals("30", state.messageDisplayDurationInput)
        assertEquals(DefaultMindfulnessMessages.defaultSelectedIds, state.selectedMessageIds)
        assertTrue(state.isSaveEnabled)
    }

    @Test
    fun `deselecting all messages disables save and shows error (VS01-TS-08)`() = runTest {
        val currentIds = viewModel.uiState.value.selectedMessageIds
        currentIds.forEach { id ->
            viewModel.onMessageToggle(id)
        }

        val state = viewModel.uiState.value
        assertTrue(state.selectedMessageIds.isEmpty())
        assertFalse(state.isSaveEnabled)
        assertNotNull(state.errorMessage)
        assertEquals("Select at least one message.", state.errorMessage)

        viewModel.save()
        assertFalse(viewModel.uiState.value.saveSuccess)
    }

    @Test
    fun `saving negative or large reveal delay clamps to 0 and 3600 (VS01-TS-09)`() = runTest {
        // Test negative clamp
        viewModel.onRevealDelayChanged("-5")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.initialRevealDelaySeconds)
        assertEquals(0, fakeRepository.lastUpdatedPrefs?.initialRevealDelaySeconds)

        // Test upper clamp
        viewModel.onRevealDelayChanged("99999")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3600, viewModel.uiState.value.initialRevealDelaySeconds)
        assertEquals(3600, fakeRepository.lastUpdatedPrefs?.initialRevealDelaySeconds)
    }

    @Test
    fun `saving out-of-range duration clamps to 5 and 300 (VS01-TS-10)`() = runTest {
        // Test lower clamp
        viewModel.onDurationChanged("1")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.messageDisplayDurationSeconds)
        assertEquals(5, fakeRepository.lastUpdatedPrefs?.messageDisplayDurationSeconds)

        // Test upper clamp
        viewModel.onDurationChanged("999")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(300, viewModel.uiState.value.messageDisplayDurationSeconds)
        assertEquals(300, fakeRepository.lastUpdatedPrefs?.messageDisplayDurationSeconds)
    }

    @Test
    fun `saving periodic nudge hide duration clamps between 0 and 3600`() = runTest {
        viewModel.onHideDurationChanged("-10")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.hideDurationSeconds)
        assertEquals(0, fakeRepository.lastUpdatedPrefs?.hideDurationSeconds)

        viewModel.onHideDurationChanged("5000")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3600, viewModel.uiState.value.hideDurationSeconds)
        assertEquals(3600, fakeRepository.lastUpdatedPrefs?.hideDurationSeconds)
    }

    @Test
    fun `changing opacity and scale updates uiState and persists on save`() = runTest {
        viewModel.onOpacityChanged(50)
        viewModel.onScaleSelected(com.awareness.core.domain.model.HudScale.LARGE)
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.hudOpacityPercent)
        assertEquals(com.awareness.core.domain.model.HudScale.LARGE, viewModel.uiState.value.hudScale)
        assertEquals(50, fakeRepository.lastUpdatedPrefs?.hudOpacityPercent)
        assertEquals(com.awareness.core.domain.model.HudScale.LARGE, fakeRepository.lastUpdatedPrefs?.hudScale)
    }

    @Test
    fun `adding and deleting custom mindfulness message updates state and repository`() = runTest {
        viewModel.onCustomMessageInputChanged("Breathe deeply.")
        viewModel.addCustomMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfterAdd = viewModel.uiState.value
        assertEquals("", stateAfterAdd.customMessageInput)
        assertEquals(1, stateAfterAdd.customMessages.size)
        assertEquals("Breathe deeply.", stateAfterAdd.customMessages[0].text)
        assertTrue(stateAfterAdd.selectedMessageIds.contains(stateAfterAdd.customMessages[0].id))

        // Delete custom message
        val addedId = stateAfterAdd.customMessages[0].id
        viewModel.deleteCustomMessage(addedId)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfterDelete = viewModel.uiState.value
        assertTrue(stateAfterDelete.customMessages.isEmpty())
        assertFalse(stateAfterDelete.selectedMessageIds.contains(addedId))
    }
}

private class FakeHudPreferencesRepository : HudPreferencesRepository {
    private val _prefs = MutableStateFlow(HudPreferences())
    override val preferences: Flow<HudPreferences> = _prefs

    var lastUpdatedPrefs: HudPreferences? = null

    override suspend fun update(prefs: HudPreferences) {
        lastUpdatedPrefs = prefs
        _prefs.value = prefs
    }

    override suspend fun setInitialRevealDelaySeconds(seconds: Int) {
        _prefs.value = _prefs.value.copy(initialRevealDelaySeconds = seconds.coerceIn(0, 3600))
    }

    override suspend fun setMessageDisplayDurationSeconds(seconds: Int) {
        _prefs.value = _prefs.value.copy(messageDisplayDurationSeconds = seconds.coerceIn(5, 300))
    }

    override suspend fun setSelectedMessageIds(ids: Set<String>) {
        _prefs.value = _prefs.value.copy(selectedMessageIds = ids)
    }

    override suspend fun setHideDurationSeconds(seconds: Int) {
        _prefs.value = _prefs.value.copy(hideDurationSeconds = seconds.coerceIn(0, 3600))
    }

    override suspend fun setHudOpacityPercent(percent: Int) {
        _prefs.value = _prefs.value.copy(hudOpacityPercent = percent.coerceIn(20, 100))
    }

    override suspend fun setHudScale(scale: com.awareness.core.domain.model.HudScale) {
        _prefs.value = _prefs.value.copy(hudScale = scale)
    }

    override suspend fun addCustomMessage(text: String): com.awareness.core.domain.model.MindfulnessMessage {
        val msg = com.awareness.core.domain.model.MindfulnessMessage("custom_test_${_prefs.value.customMessages.size + 1}", text)
        _prefs.value = _prefs.value.copy(
            customMessages = _prefs.value.customMessages + msg,
            selectedMessageIds = _prefs.value.selectedMessageIds + msg.id
        )
        return msg
    }

    override suspend fun deleteCustomMessage(id: String) {
        _prefs.value = _prefs.value.copy(
            customMessages = _prefs.value.customMessages.filterNot { it.id == id },
            selectedMessageIds = _prefs.value.selectedMessageIds - id
        )
    }
}
