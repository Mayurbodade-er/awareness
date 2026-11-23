package com.awareness.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.awareness.core.data.repository.HudPreferencesRepositoryImpl
import com.awareness.core.designsystem.theme.AwarenessTheme
import com.awareness.core.presentation.permission.PermissionRequestCoordinator
import com.awareness.feature.settings.hud.HudPersonalizationViewModel

class SettingsActivity : ComponentActivity() {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var personalizationViewModel: HudPersonalizationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionCoordinator = PermissionRequestCoordinator(this)
        val hudPreferencesRepository = HudPreferencesRepositoryImpl.create(this)

        viewModel = SettingsViewModel(permissionCoordinator, hudPreferencesRepository)
        personalizationViewModel = HudPersonalizationViewModel(hudPreferencesRepository)

        setContent {
            AwarenessTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    personalizationViewModel = personalizationViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Card state refresh on resume (VS01-TS-03)
        viewModel.refreshPermissionState()
    }
}
