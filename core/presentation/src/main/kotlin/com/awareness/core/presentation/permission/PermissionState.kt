package com.awareness.core.presentation.permission

data class PermissionState(
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val notificationsGranted: Boolean // true on < API 33
)
