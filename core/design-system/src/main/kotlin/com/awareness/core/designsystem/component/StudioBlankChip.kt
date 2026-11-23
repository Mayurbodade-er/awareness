package com.awareness.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankShape
import com.awareness.core.designsystem.theme.StudioBlankTypography

@Composable
fun StudioBlankChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, borderColor) = if (selected) {
        Triple(StudioBlankColors.Primary, StudioBlankColors.Secondary, StudioBlankColors.Primary)
    } else {
        Triple(Color.Transparent, StudioBlankColors.TextSecondary, StudioBlankColors.BorderDefault)
    }

    Surface(
        modifier = modifier
            .height(28.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = StudioBlankShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = StudioBlankTypography.Caption,
                color = textColor
            )
        }
    }
}
