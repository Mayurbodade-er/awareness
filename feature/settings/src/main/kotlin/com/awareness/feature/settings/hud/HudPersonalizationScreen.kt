package com.awareness.feature.settings.hud

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awareness.core.designsystem.component.StudioBlankButton
import com.awareness.core.designsystem.component.StudioBlankButtonSize
import com.awareness.core.designsystem.component.StudioBlankButtonVariant
import com.awareness.core.designsystem.component.StudioBlankCard
import com.awareness.core.designsystem.component.StudioBlankCheckbox
import com.awareness.core.designsystem.component.StudioBlankChip
import com.awareness.core.designsystem.component.StudioBlankTextField
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankSpacing
import com.awareness.core.designsystem.theme.StudioBlankTypography
import com.awareness.core.domain.model.DefaultMindfulnessMessages
import com.awareness.core.domain.model.HudScale

@Composable
fun HudPersonalizationScreen(
    viewModel: HudPersonalizationViewModel,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "Configuration Saved", Toast.LENGTH_SHORT).show()
            viewModel.resetSaveSuccess()
            onBackClicked()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBlankColors.SurfaceBase)
            .padding(StudioBlankSpacing.Space3)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioBlankButton(
                text = "← Back",
                onClick = onBackClicked,
                variant = StudioBlankButtonVariant.Ghost,
                size = StudioBlankButtonSize.Small
            )
            Spacer(modifier = Modifier.width(StudioBlankSpacing.Space2))
            Text(
                text = "HUD Personalization",
                style = StudioBlankTypography.H2,
                color = StudioBlankColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Section 1: Timing & Nudge Intervals
            item {
                Text(
                    text = "TIMING & NUDGE CYCLES",
                    style = StudioBlankTypography.Caption,
                    color = StudioBlankColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
            }

            // Initial Reveal Delay
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Initial Reveal Delay",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Seconds before the HUD first appears after opening a target app (0–3600 sec).",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        StudioBlankTextField(
                            value = uiState.initialRevealDelayInput,
                            onValueChange = viewModel::onRevealDelayChanged,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = "0"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // Display Duration
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Visible Duration",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "How long the HUD stays visible once revealed (5–300 sec).",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        StudioBlankTextField(
                            value = uiState.messageDisplayDurationInput,
                            onValueChange = viewModel::onDurationChanged,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = "30"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // Periodic Nudge Hide Duration
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Periodic Nudge Interval (Hidden)",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "How long HUD hides before nudging you again. Set 0 to keep HUD visible continuously (0–3600 sec).",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        StudioBlankTextField(
                            value = uiState.hideDurationInput,
                            onValueChange = viewModel::onHideDurationChanged,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = "0"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))
            }

            // Section 2: HUD Appearance
            item {
                Text(
                    text = "APPEARANCE & STYLING",
                    style = StudioBlankTypography.Caption,
                    color = StudioBlankColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
            }

            // Size / Scale Selection
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "HUD Size",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Choose the display scale for the floating overlay.",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(StudioBlankSpacing.Space2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StudioBlankChip(
                                text = "Compact",
                                selected = uiState.hudScale == HudScale.COMPACT,
                                onClick = { viewModel.onScaleSelected(HudScale.COMPACT) }
                            )
                            StudioBlankChip(
                                text = "Standard",
                                selected = uiState.hudScale == HudScale.STANDARD,
                                onClick = { viewModel.onScaleSelected(HudScale.STANDARD) }
                            )
                            StudioBlankChip(
                                text = "Large",
                                selected = uiState.hudScale == HudScale.LARGE,
                                onClick = { viewModel.onScaleSelected(HudScale.LARGE) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            }

            // Opacity Selection
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "HUD Background Opacity",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Background transparency level: ${uiState.hudOpacityPercent}%.",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(StudioBlankSpacing.Space2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(40, 60, 85, 100).forEach { opacity ->
                                StudioBlankChip(
                                    text = if (opacity == 85) "85% (Def)" else "$opacity%",
                                    selected = uiState.hudOpacityPercent == opacity,
                                    onClick = { viewModel.onOpacityChanged(opacity) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))
            }

            // Section 3: Mindfulness Messages & Custom Prompts
            item {
                Text(
                    text = "CUSTOM PROMPT CREATION",
                    style = StudioBlankTypography.Caption,
                    color = StudioBlankColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
            }

            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Add Custom Prompt",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Write your personal mindfulness question or prompt.",
                            style = StudioBlankTypography.BodySm,
                            color = StudioBlankColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        StudioBlankTextField(
                            value = uiState.customMessageInput,
                            onValueChange = viewModel::onCustomMessageInputChanged,
                            placeholder = "e.g., Are you scrolling purposefully?"
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                        StudioBlankButton(
                            text = "+ Add Message",
                            onClick = viewModel::addCustomMessage,
                            enabled = uiState.customMessageInput.isNotBlank(),
                            variant = StudioBlankButtonVariant.Secondary,
                            size = StudioBlankButtonSize.Small,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))
            }

            item {
                Text(
                    text = "ACTIVE MESSAGES (at least 1 must be active)",
                    style = StudioBlankTypography.Caption,
                    color = StudioBlankColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
            }

            items(uiState.allMessages) { message ->
                val isChecked = uiState.selectedMessageIds.contains(message.id)
                val isCustom = message.id.startsWith("custom_")
                StudioBlankCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioBlankCheckbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.onMessageToggle(message.id) },
                            label = message.text,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCustom) {
                            StudioBlankButton(
                                text = "✕",
                                onClick = { viewModel.deleteCustomMessage(message.id) },
                                variant = StudioBlankButtonVariant.Ghost,
                                size = StudioBlankButtonSize.Small
                            )
                        }
                    }
                }
            }

            // Error message if present
            if (uiState.errorMessage != null) {
                item {
                    Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = StudioBlankTypography.BodySm,
                        color = StudioBlankColors.Error
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space5))
                StudioBlankButton(
                    text = "Save Configuration",
                    onClick = viewModel::save,
                    enabled = uiState.isSaveEnabled,
                    variant = StudioBlankButtonVariant.Primary,
                    size = StudioBlankButtonSize.Large,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space4))
            }
        }
    }
}
