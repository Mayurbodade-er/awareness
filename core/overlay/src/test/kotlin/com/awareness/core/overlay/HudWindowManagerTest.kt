package com.awareness.core.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HudWindowManagerTest {

    private lateinit var context: Context
    private lateinit var mockWindowManager: WindowManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @Test
    fun attachFailsWhenOverlayPermissionNotGranted() {
        val manager = HudWindowManagerImpl(
            context = context,
            windowManager = mockWindowManager,
            permissionChecker = { false } // Permission denied
        )

        val view = View(context)
        val result = manager.attach(view)

        assertTrue(result.isFailure)
        assertFalse(manager.isAttached)
    }

    @Test
    fun attachAndRemoveSucceedsWhenPermissionGranted() {
        val manager = HudWindowManagerImpl(
            context = context,
            windowManager = mockWindowManager,
            permissionChecker = { true } // Permission granted
        )

        val view = View(context)
        val attachResult = manager.attach(view)

        assertTrue(attachResult.isSuccess)
        assertTrue(manager.isAttached)

        val removeResult = manager.remove()
        assertTrue(removeResult.isSuccess)
        assertFalse(manager.isAttached)
    }
}
