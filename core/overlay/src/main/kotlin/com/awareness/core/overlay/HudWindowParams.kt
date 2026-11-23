package com.awareness.core.overlay

import android.view.Gravity

/**
 * Window positioning parameters for the floating HUD overlay.
 */
data class HudWindowParams(
    val gravity: Int = Gravity.TOP or Gravity.END,
    val xOffsetDp: Int = 16,
    val yOffsetDp: Int = 16
)
