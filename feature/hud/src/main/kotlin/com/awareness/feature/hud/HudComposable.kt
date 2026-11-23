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
import com.awareness.core.domain.model.HudScale
import com.awareness.core.domain.model.HudState
import com.awareness.core.presentation.util.TimeFormatter
import kotlinx.coroutines.flow.StateFlow

/**
 * Visibility-aware HUD Composable adhering strictly to StudioBlank Design System:
 * - Hidden State: Renders empty Box with zero size when isVisible = false (touch pass-through).
 * - Visible State: Renders sharp 0dp card with 1dp border and monochromatic typography.
 * - Sizing & Opacity: Customizable via HudState.opacityPercent and HudState.scale.
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

    val (maxWidth, hPad, vPad) = when (state.scale) {
        HudScale.COMPACT -> Triple(180.dp, 8.dp, 6.dp)
        HudScale.STANDARD -> Triple(220.dp, 12.dp, 8.dp)
        HudScale.LARGE -> Triple(260.dp, 16.dp, 10.dp)
    }

    val alpha = (state.opacityPercent.coerceIn(20, 100) / 100f)

    AwarenessTheme(darkTheme = true) {
        Box(
            modifier = modifier
                .wrapContentSize()
                .widthIn(max = maxWidth)
                .background(
                    color = StudioBlankColors.SurfaceInverse.copy(alpha = alpha),
                    shape = RectangleShape
                )
                .border(
                    width = 1.dp,
                    color = StudioBlankColors.BorderDefault,
                    shape = RectangleShape
                )
                .padding(horizontal = hPad, vertical = vPad)
        ) {
            Column {
                MessageLine(message = state.message, scale = state.scale)
                TimerLine(elapsedMillis = state.elapsedMillis, scale = state.scale)
                ReelsLine(reelCount = state.reelCount, showReelCount = state.showReelCount, scale = state.scale)
                BrowserDomainLine(domainLabel = state.browserDomainLabel, scale = state.scale)
            }
        }
    }
}

@Composable
private fun MessageLine(message: String, scale: HudScale) {
    if (message.isNotEmpty()) {
        val (fontSize, lineHeight) = when (scale) {
            HudScale.COMPACT -> Pair(11.sp, 14.sp)
            HudScale.STANDARD -> Pair(12.sp, 16.sp)
            HudScale.LARGE -> Pair(14.sp, 18.sp)
        }
        Text(
            text = message,
            color = StudioBlankColors.TextInverse,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            lineHeight = lineHeight
        )
    }
}

@Composable
private fun TimerLine(elapsedMillis: Long, scale: HudScale) {
    val formatted = if (elapsedMillis >= 3600_000L) {
        TimeFormatter.hms(elapsedMillis)
    } else {
        TimeFormatter.mmss(elapsedMillis)
    }
    val fontSize = when (scale) {
        HudScale.COMPACT -> 12.sp
        HudScale.STANDARD -> 14.sp
        HudScale.LARGE -> 16.sp
    }
    Text(
        text = formatted,
        color = StudioBlankColors.TextInverse,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ReelsLine(reelCount: Int, showReelCount: Boolean, scale: HudScale) {
    if (showReelCount) {
        val fontSize = when (scale) {
            HudScale.COMPACT -> 10.sp
            HudScale.STANDARD -> 11.sp
            HudScale.LARGE -> 13.sp
        }
        Text(
            text = "Reels: $reelCount",
            color = StudioBlankColors.TextSecondary,
            fontSize = fontSize,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun BrowserDomainLine(domainLabel: String?, scale: HudScale) {
    if (domainLabel != null) {
        val fontSize = when (scale) {
            HudScale.COMPACT -> 10.sp
            HudScale.STANDARD -> 11.sp
            HudScale.LARGE -> 13.sp
        }
        Text(
            text = domainLabel,
            color = StudioBlankColors.TextSecondary,
            fontSize = fontSize,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
