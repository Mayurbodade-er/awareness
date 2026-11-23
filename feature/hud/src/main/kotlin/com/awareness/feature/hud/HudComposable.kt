package com.awareness.feature.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awareness.core.designsystem.theme.AwarenessTheme
import com.awareness.core.designsystem.theme.StudioBlankColors
import com.awareness.core.domain.model.HudState
import com.awareness.core.presentation.util.TimeFormatter
import kotlinx.coroutines.flow.StateFlow

/**
 * Visibility-aware HUD Composable adhering strictly to StudioBlank Design System:
 * - When isVisible is false: renders completely empty Box (zero size, zero pixels, touch pass-through preserved).
 * - When isVisible is true: renders sharp 0dp card with 1dp border and monochromatic typography.
 */
@Composable
fun HudOverlayView(
    hudStateFlow: StateFlow<HudState>,
    modifier: Modifier = Modifier
) {
    val state by hudStateFlow.collectAsState()

    if (!state.isVisible) {
        // Render nothing visible when isVisible is false
        Box(modifier = Modifier.wrapContentSize())
        return
    }

    AwarenessTheme(darkTheme = true) {
        Box(
            modifier = modifier
                .wrapContentSize()
                .widthIn(max = 220.dp)
                .background(
                    color = StudioBlankColors.SurfaceInverse.copy(alpha = 0.85f),
                    shape = RectangleShape
                )
                .border(
                    width = 1.dp,
                    color = StudioBlankColors.BorderDefault,
                    shape = RectangleShape
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (state.message.isNotEmpty()) {
                    Text(
                        text = state.message,
                        color = StudioBlankColors.TextInverse,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 16.sp
                    )
                }

                Text(
                    text = TimeFormatter.mmss(state.elapsedMillis),
                    color = StudioBlankColors.TextInverse,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (state.showReelCount) {
                    Text(
                        text = "Reels: ${state.reelCount}",
                        color = StudioBlankColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (state.browserDomainLabel != null) {
                    Text(
                        text = state.browserDomainLabel!!,
                        color = StudioBlankColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
