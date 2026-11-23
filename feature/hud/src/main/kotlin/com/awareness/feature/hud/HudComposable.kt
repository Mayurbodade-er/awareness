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
 * - Hidden State: Renders empty Box with zero size when isVisible = false (touch pass-through).
 * - Visible State: Renders sharp 0dp card with 1dp border and monochromatic typography.
 * - Recomposition Scoping: Sub-composables isolate 1-second timer text updates.
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
                MessageLine(message = state.message)
                TimerLine(elapsedMillis = state.elapsedMillis)
                ReelsLine(reelCount = state.reelCount, showReelCount = state.showReelCount)
                BrowserDomainLine(domainLabel = state.browserDomainLabel)
            }
        }
    }
}

@Composable
private fun MessageLine(message: String) {
    if (message.isNotEmpty()) {
        Text(
            text = message,
            color = StudioBlankColors.TextInverse,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun TimerLine(elapsedMillis: Long) {
    val formatted = if (elapsedMillis >= 3600_000L) {
        TimeFormatter.hms(elapsedMillis)
    } else {
        TimeFormatter.mmss(elapsedMillis)
    }
    Text(
        text = formatted,
        color = StudioBlankColors.TextInverse,
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ReelsLine(reelCount: Int, showReelCount: Boolean) {
    if (showReelCount) {
        Text(
            text = "Reels: $reelCount",
            color = StudioBlankColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun BrowserDomainLine(domainLabel: String?) {
    if (domainLabel != null) {
        Text(
            text = domainLabel,
            color = StudioBlankColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
