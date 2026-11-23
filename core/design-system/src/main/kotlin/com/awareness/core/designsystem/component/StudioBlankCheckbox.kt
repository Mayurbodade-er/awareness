package com.awareness.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankShape
import com.awareness.core.designsystem.theme.StudioBlankSpacing
import com.awareness.core.designsystem.theme.StudioBlankTypography

@Composable
fun StudioBlankCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    val (fillColor, borderColor, checkColor) = when {
        !enabled -> Triple(
            StudioBlankColors.SurfaceSubtle,
            StudioBlankColors.BorderSubtle,
            StudioBlankColors.TextDisabled
        )
        checked -> Triple(
            StudioBlankColors.Primary,
            StudioBlankColors.Primary,
            StudioBlankColors.Secondary
        )
        else -> Triple(
            StudioBlankColors.SurfaceCard,
            StudioBlankColors.BorderDefault,
            Color.Transparent
        )
    }

    Row(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(fillColor, StudioBlankShape)
                .border(BorderStroke(1.dp, borderColor), StudioBlankShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = checkColor,
                    style = StudioBlankTypography.Caption
                )
            }
        }

        if (label != null) {
            Spacer(modifier = Modifier.width(StudioBlankSpacing.Space2))
            Text(
                text = label,
                style = StudioBlankTypography.BodySm,
                color = if (enabled) StudioBlankColors.TextPrimary else StudioBlankColors.TextDisabled
            )
        }
    }
}
