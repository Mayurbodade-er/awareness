package com.awareness.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankShape
import com.awareness.core.designsystem.theme.StudioBlankTypography

enum class StudioBlankButtonVariant {
    Primary, Secondary, Ghost, Destructive
}

enum class StudioBlankButtonSize {
    Small, Medium, Large
}

@Composable
fun StudioBlankButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: StudioBlankButtonVariant = StudioBlankButtonVariant.Primary,
    size: StudioBlankButtonSize = StudioBlankButtonSize.Medium,
    enabled: Boolean = true
) {
    val height = when (size) {
        StudioBlankButtonSize.Small -> 32.dp
        StudioBlankButtonSize.Medium -> 40.dp
        StudioBlankButtonSize.Large -> 48.dp
    }
    val horizontalPadding = when (size) {
        StudioBlankButtonSize.Small -> 16.dp
        StudioBlankButtonSize.Medium -> 24.dp
        StudioBlankButtonSize.Large -> 32.dp
    }
    val minWidth = when (size) {
        StudioBlankButtonSize.Small -> 64.dp
        StudioBlankButtonSize.Medium -> 96.dp
        StudioBlankButtonSize.Large -> 128.dp
    }
    val textStyle = when (size) {
        StudioBlankButtonSize.Small -> StudioBlankTypography.Caption
        StudioBlankButtonSize.Medium -> StudioBlankTypography.BodySm
        StudioBlankButtonSize.Large -> StudioBlankTypography.Body
    }

    val (containerColor, contentColor, border) = when (variant) {
        StudioBlankButtonVariant.Primary -> Triple(
            StudioBlankColors.Primary,
            StudioBlankColors.TextInverse,
            null
        )
        StudioBlankButtonVariant.Secondary -> Triple(
            Color.Transparent,
            StudioBlankColors.TextPrimary,
            BorderStroke(1.dp, StudioBlankColors.Primary)
        )
        StudioBlankButtonVariant.Ghost -> Triple(
            Color.Transparent,
            StudioBlankColors.TextPrimary,
            null
        )
        StudioBlankButtonVariant.Destructive -> Triple(
            StudioBlankColors.Error,
            StudioBlankColors.TextInverse,
            null
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = minWidth, minHeight = height),
        enabled = enabled,
        shape = StudioBlankShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.3f),
            disabledContentColor = contentColor.copy(alpha = 0.3f)
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
        elevation = null
    ) {
        Text(text = text, style = textStyle)
    }
}
