package com.awareness.core.overlay

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class HudLayoutParamsFactoryTest {

    @Test
    fun createContainsTouchPassThroughFlagsAndOverlayType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val params = HudWindowParams(
            gravity = Gravity.TOP or Gravity.END,
            xOffsetDp = 20,
            yOffsetDp = 30
        )

        val layoutParams = HudLayoutParamsFactory.create(context, params)

        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, layoutParams.type)
        val expectedFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        assertTrue(
            "FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE must both be enabled",
            (layoutParams.flags and expectedFlags) == expectedFlags
        )
        assertEquals(Gravity.TOP or Gravity.END, layoutParams.gravity)
    }
}
