package com.awareness.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager

object HudLayoutParamsFactory {

    fun create(
        context: Context,
        params: HudWindowParams = HudWindowParams()
    ): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val xOffsetPx = (params.xOffsetDp * density).toInt()
        val yOffsetPx = (params.yOffsetDp * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = params.gravity
            x = xOffsetPx
            y = yOffsetPx
        }
    }
}
