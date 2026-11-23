package com.awareness.core.domain.model

enum class TargetApp(val packageName: String, val appName: String, val isBrowser: Boolean) {
    INSTAGRAM("com.instagram.android", "Instagram", false),
    INSTAGRAM_LITE("com.instagram.lite", "Instagram Lite", false),
    FACEBOOK("com.facebook.katana", "Facebook", false),
    FACEBOOK_LITE("com.facebook.lite", "Facebook Lite", false),
    SNAPCHAT("com.snapchat.android", "Snapchat", false),
    YOUTUBE("com.google.android.youtube", "YouTube", false),
    CHROME("com.android.chrome", "Google Chrome", true),
    FIREFOX("org.mozilla.firefox", "Firefox", true);

    companion object {
        fun fromPackageName(packageName: String): TargetApp? =
            entries.find { it.packageName == packageName }

        val allowlist: Set<String> = entries.map { it.packageName }.toSet()
        val socialApps: Set<String> = entries.filter { !it.isBrowser }.map { it.packageName }.toSet()
        val browsers: Set<String> = entries.filter { it.isBrowser }.map { it.packageName }.toSet()
    }
}
