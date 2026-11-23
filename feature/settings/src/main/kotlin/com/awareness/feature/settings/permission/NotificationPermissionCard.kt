package com.awareness.feature.settings.permission

import android.os.Build
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
fun NotificationPermissionCard(
    isGranted: Boolean,
    onAllowClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }

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
                    text = "$statusIcon Notifications (Android 13+)",
                    style = StudioBlankTypography.H3,
                    color = StudioBlankColors.TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isGranted) {
                    Text(
                        text = "ALLOWED",
                        style = StudioBlankTypography.Caption,
                        color = StudioBlankColors.Success
                    )
                }
            }
            Spacer(modifier = Modifier.height(StudioBlankSpacing.Space1))
            Text(
                text = "Required for Foreground Service notification display while HUD is running.",
                style = StudioBlankTypography.BodySm,
                color = StudioBlankColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(StudioBlankSpacing.Space3))
            StudioBlankButton(
                text = if (isGranted) "Allowed" else "Allow Notifications ▸",
                onClick = onAllowClicked,
                enabled = !isGranted,
                variant = if (isGranted) StudioBlankButtonVariant.Ghost else StudioBlankButtonVariant.Primary,
                size = StudioBlankButtonSize.Medium
            )
        }
    }
}
