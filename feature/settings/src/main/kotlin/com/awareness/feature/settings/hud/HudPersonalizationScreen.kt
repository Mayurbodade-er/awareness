package com.awareness.feature.settings.hud

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awareness.core.designsystem.component.StudioBlankButton
import com.awareness.core.designsystem.component.StudioBlankButtonSize
import com.awareness.core.designsystem.component.StudioBlankButtonVariant
import com.awareness.core.designsystem.component.StudioBlankCard
import com.awareness.core.designsystem.component.StudioBlankCheckbox
import com.awareness.core.designsystem.component.StudioBlankTextField
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankSpacing
import com.awareness.core.designsystem.theme.StudioBlankTypography

@Composable
fun HudPersonalizationScreen(
    viewModel: HudPersonalizationViewModel,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            // Reveal Delay Configuration
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Reveal HUD after",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Wait duration in seconds before overlay appears (0–3600 sec).",
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

            // Message Duration Configuration
            item {
                StudioBlankCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Each message appears for",
                            style = StudioBlankTypography.H3,
                            color = StudioBlankColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
                        Text(
                            text = "Duration each rotating mindfulness message stays visible (5–300 sec).",
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

            // Messages Selection Checklist
            item {
                Text(
                    text = "MESSAGES (at least 1 must be active)",
                    style = StudioBlankTypography.Caption,
                    color = StudioBlankColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(StudioBlankSpacing.Space2))
            }

            items(uiState.allMessages) { message ->
                val isChecked = uiState.selectedMessageIds.contains(message.id)
                StudioBlankCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = 12.dp
                ) {
                    StudioBlankCheckbox(
                        checked = isChecked,
                        onCheckedChange = { viewModel.onMessageToggle(message.id) },
                        label = message.text
                    )
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
