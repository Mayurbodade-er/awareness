package com.awareness.feature.settings.permission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.awareness.core.designsystem.component.StudioBlankButton
import com.awareness.core.designsystem.component.StudioBlankButtonSize
import com.awareness.core.designsystem.component.StudioBlankButtonVariant
import com.awareness.core.designsystem.component.StudioBlankCard
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankSpacing
import com.awareness.core.designsystem.theme.StudioBlankTypography

@Composable
fun BatteryOptimizationCard(
    isIgnored: Boolean,
    onExemptClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isIgnored) StudioBlankColors.Success else StudioBlankColors.Tertiary
    val statusIcon = if (isIgnored) "✓" else "○"

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
                    text = "$statusIcon Battery Optimization",
                    style = StudioBlankTypography.H3,
                    color = StudioBlankColors.TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isIgnored) {
                    Text(
                        text = "EXEMPTED",
                        style = StudioBlankTypography.Caption,
                        color = StudioBlankColors.Success
                    )
                }
            }
            Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
            Text(
                text = "Recommended for reliable session tracking without OEM background termination.",
                style = StudioBlankTypography.BodySm,
                color = StudioBlankColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            StudioBlankButton(
                text = if (isIgnored) "Exempted" else "Exempt from Doze ▸",
                onClick = onExemptClicked,
                enabled = !isIgnored,
                variant = if (isIgnored) StudioBlankButtonVariant.Ghost else StudioBlankButtonVariant.Secondary,
                size = StudioBlankButtonSize.Medium
            )
        }
    }
}
