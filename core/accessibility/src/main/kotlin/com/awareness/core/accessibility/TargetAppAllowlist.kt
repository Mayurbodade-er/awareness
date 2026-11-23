package com.awareness.core.accessibility

/**
 * Single source of truth for target applications monitored by the Awareness App.
 *
 * Invariant: All packages declared here must match the XML packageNames filter
 * in accessibility_service_config.xml.
 */
object TargetAppAllowlist {
    val socialApps: Set<String> = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.lite",
        "com.snapchat.android",
        "com.google.android.youtube"
    )

    val browsers: Set<String> = setOf(
        "com.android.chrome",
        "org.mozilla.firefox"
    )

    val all: Set<String> = socialApps + browsers

    fun isTargetApp(packageName: String?): Boolean = packageName != null && packageName in all

    fun isBrowser(packageName: String?): Boolean = packageName != null && packageName in browsers

    fun isSocialApp(packageName: String?): Boolean = packageName != null && packageName in socialApps
}
