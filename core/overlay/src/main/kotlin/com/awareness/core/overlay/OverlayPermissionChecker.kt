package com.awareness.core.overlay

import android.content.Context
import android.provider.Settings

object OverlayPermissionChecker {
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
