package com.awareness.feature.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awareness.core.designsystem.component.StudioBlankButton
import com.awareness.core.designsystem.component.StudioBlankButtonSize
import com.awareness.core.designsystem.component.StudioBlankButtonVariant
import com.awareness.core.designsystem.component.StudioBlankCard
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankSpacing
import com.awareness.core.designsystem.theme.StudioBlankTypography
import com.awareness.feature.settings.hud.HudPersonalizationScreen
import com.awareness.feature.settings.hud.HudPersonalizationViewModel
import com.awareness.feature.settings.permission.AccessibilityPermissionCard
import com.awareness.feature.settings.permission.BatteryOptimizationCard
import com.awareness.feature.settings.permission.NotificationPermissionCard
import com.awareness.feature.settings.permission.OverlayPermissionCard

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    personalizationViewModel: HudPersonalizationViewModel,
    onNavigateToOemGuide: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    if (uiState.showPersonalizationScreen) {
        HudPersonalizationScreen(
            viewModel = personalizationViewModel,
            onBackClicked = viewModel::closePersonalization,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBlankColors.SurfaceBase)
            .padding(StudioBlankSpacing.Space3)
    ) {
        // App Title
        Text(
            text = "Awareness",
            style = StudioBlankTypography.H1,
            color = StudioBlankColors.TextPrimary
        )
        Text(
            text = "Setup Checklist & HUD Configuration",
            style = StudioBlankTypography.BodySm,
            color = StudioBlankColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Permission 1: Accessibility Service
            item {
                AccessibilityPermissionCard(
                    isGranted = uiState.permissionState.accessibilityGranted,
                    onEnableClicked = { activity?.let(viewModel::requestAccessibility) }
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // Permission 2: Overlay Permission
            item {
                OverlayPermissionCard(
                    isGranted = uiState.permissionState.overlayGranted,
                    onGrantClicked = { activity?.let(viewModel::requestOverlay) }
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // Permission 3: Battery Optimization
            item {
                BatteryOptimizationCard(
                    isIgnored = uiState.permissionState.batteryOptimizationIgnored,
                    onExemptClicked = { activity?.let(viewModel::requestBatteryOptimization) }
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // Permission 4: Notifications (Android 13+)
            item {
                NotificationPermissionCard(
                    isGranted = uiState.permissionState.notificationsGranted,
                    onAllowClicked = { activity?.let(viewModel::requestNotifications) }
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // HUD Personalization Summary Card
            item {
                val prefs = uiState.hudPreferences
                val delayDisplay = if (prefs.initialRevealDelaySeconds >= 60) {
                    "${prefs.initialRevealDelaySeconds / 60} min"
                } else {
                    "${prefs.initialRevealDelaySeconds} sec"
                }

                StudioBlankCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = StudioBlankColors.Primary
                ) {
                    Column {
                        Text(
                            text = "HUD Personalization",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Reveal after: $delayDisplay\n" +
                                    "Messages: ${prefs.selectedMessageIds.size} selected\n" +
                                    "Each message: ${prefs.messageDisplayDurationSeconds} sec",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
                        StudioBlankButton(
                            text = "Configure HUD ▸",
                            onClick = viewModel::openPersonalization,
                            variant = StudioBlankButtonVariant.Secondary,
                            size = StudioBlankButtonSize.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))
            }

            // Bottom Navigation Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioBlankButton(
                        text = "OEM Setup Guide",
                        onClick = onNavigateToOemGuide,
                        variant = StudioBlankButtonVariant.Ghost,
                        size = StudioBlankButtonSize.Small
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    StudioBlankButton(
                        text = "View Stats",
                        onClick = onNavigateToStats,
                        variant = StudioBlankButtonVariant.Ghost,
                        size = StudioBlankButtonSize.Small
                    )
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))
            }
        }
    }
}
