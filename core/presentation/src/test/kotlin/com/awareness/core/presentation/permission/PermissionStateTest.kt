package com.awareness.core.presentation.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {

    @Test
    fun `permission state holds accurate status combinations`() {
        val stateAllGranted = PermissionState(
            accessibilityGranted = true,
            overlayGranted = true,
            batteryOptimizationIgnored = true,
            notificationsGranted = true
        )
        assertTrue(stateAllGranted.accessibilityGranted)
        assertTrue(stateAllGranted.overlayGranted)
        assertTrue(stateAllGranted.batteryOptimizationIgnored)
        assertTrue(stateAllGranted.notificationsGranted)

        val stateNoneGranted = PermissionState(
            accessibilityGranted = false,
            overlayGranted = false,
            batteryOptimizationIgnored = false,
            notificationsGranted = false
        )
        assertFalse(stateNoneGranted.accessibilityGranted)
        assertFalse(stateNoneGranted.overlayGranted)
        assertFalse(stateNoneGranted.batteryOptimizationIgnored)
        assertFalse(stateNoneGranted.notificationsGranted)
    }
}
