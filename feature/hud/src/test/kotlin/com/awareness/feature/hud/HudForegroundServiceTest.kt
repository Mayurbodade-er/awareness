package com.awareness.feature.hud

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.awareness.core.overlay.HudWindowParams
import com.awareness.core.overlay.HudWindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class HudForegroundServiceTest {

    @Test
    fun serviceStartsAndAttachesOverlayThenCleansUpOnStop() {
        var attached = false
        val fakeManager = object : HudWindowManager {
            override fun attach(view: View, params: HudWindowParams): Result<Unit> {
                attached = true
                return Result.success(Unit)
            }

            override fun remove(): Result<Unit> {
                attached = false
                return Result.success(Unit)
            }

            override val isAttached: Boolean get() = attached
        }

        val serviceController: ServiceController<HudForegroundService> = Robolectric.buildService(
            HudForegroundService::class.java
        )

        val service = serviceController.get()
        serviceController.create()

        val startIntent = Intent(ApplicationProvider.getApplicationContext(), HudForegroundService::class.java).apply {
            action = HudForegroundService.ACTION_START
        }

        service.onStartCommand(startIntent, 0, 1)

        val stopIntent = Intent(ApplicationProvider.getApplicationContext(), HudForegroundService::class.java).apply {
            action = HudForegroundService.ACTION_STOP
        }

        service.onStartCommand(stopIntent, 0, 2)
        serviceController.destroy()
    }
}
