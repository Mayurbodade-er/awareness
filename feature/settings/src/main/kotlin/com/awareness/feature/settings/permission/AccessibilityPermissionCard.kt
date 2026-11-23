package com.awareness.feature.settings.permission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.awareness.core.designsystem.component.StudioBlankButton
import com.awareness.core.designsystem.component.StudioBlankButtonSize
import com.awareness.core.designsystem.component.StudioBlankButtonVariant
import com.awareness.core.designsystem.component.StudioBlankCard
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankSpacing
import com.awareness.core.designsystem.theme.StudioBlankTypography

@Composable
fun AccessibilityPermissionCard(
    isGranted: Boolean,
    onEnableClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isGranted) StudioBlankColors.Success else StudioBlankColors.Warning
    val statusIcon = if (isGranted) "✓" else "⚠"

    StudioBlankCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = borderColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$statusIcon Accessibility Service",
                    style = StudioBlankTypography.H3,
                    color = StudioBlankColors.TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isGranted) {
                    Text(
                        text = "ACTIVE",
                        style = StudioBlankTypography.Caption,
                        color = StudioBlankColors.Success
                    )
                }
            }
            Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
            Text(
                text = "Required for target social app foreground detection & mindful awareness.",
                style = StudioBlankTypography.BodySm,
                color = StudioBlankColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            StudioBlankButton(
                text = if (isGranted) "Enabled" else "Enable in Settings ▸",
                onClick = onEnableClicked,
                enabled = !isGranted,
                variant = if (isGranted) StudioBlankButtonVariant.Ghost else StudioBlankButtonVariant.Primary,
                size = StudioBlankButtonSize.Medium
            )
        }
    }
}
