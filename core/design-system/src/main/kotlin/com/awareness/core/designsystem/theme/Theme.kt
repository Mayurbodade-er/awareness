package com.awareness.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StudioBlankLightColorScheme = lightColorScheme(
    primary = StudioBlankColors.Primary,
    onPrimary = StudioBlankColors.TextInverse,
    background = StudioBlankColors.SurfaceBase,
    onBackground = StudioBlankColors.TextPrimary,
    surface = StudioBlankColors.SurfaceCard,
    onSurface = StudioBlankColors.TextPrimary,
    outline = StudioBlankColors.BorderDefault,
    error = StudioBlankColors.Error,
    onError = StudioBlankColors.TextInverse
)

private val StudioBlankDarkColorScheme = darkColorScheme(
    primary = StudioBlankColors.Secondary,
    onPrimary = StudioBlankColors.TextPrimary,
    background = StudioBlankColors.SurfaceInverse,
    onBackground = StudioBlankColors.TextInverse,
    surface = StudioBlankColors.SurfaceInverse,
    onSurface = StudioBlankColors.TextInverse,
    outline = StudioBlankColors.BorderDefault,
    error = StudioBlankColors.Error,
    onError = StudioBlankColors.TextInverse
)

@Composable
fun AwarenessTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioBlankDarkColorScheme else StudioBlankLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
