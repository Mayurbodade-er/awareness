package com.awareness.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.designsystem.theme.StudioBlankShape
import com.awareness.core.designsystem.theme.StudioBlankTypography

@Composable
fun StudioBlankTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    val borderColor = when {
        !enabled -> StudioBlankColors.BorderSubtle
        isError -> StudioBlankColors.Error
        else -> StudioBlankColors.BorderDefault
    }
    val backgroundColor = if (enabled) StudioBlankColors.SurfaceCard else StudioBlankColors.SurfaceSubtle

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(backgroundColor, StudioBlankShape)
            .border(BorderStroke(1.dp, borderColor), StudioBlankShape),
        textStyle = StudioBlankTypography.BodySm.copy(color = StudioBlankColors.TextPrimary),
        cursorBrush = SolidColor(StudioBlankColors.Primary),
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .defaultMinSize(minHeight = 40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = StudioBlankTypography.BodySm,
                        color = StudioBlankColors.TextDisabled
                    )
                }
                innerTextField()
            }
        }
    )
}
