package com.awareness.core.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetAppAllowlistTest {

    @Test
    fun socialAppsContainsExpectedPackages() {
        val expected = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.instagram.android",
            "com.instagram.lite",
            "com.snapchat.android",
            "com.google.android.youtube"
        )
        assertEquals(expected, TargetAppAllowlist.socialApps)
    }

    @Test
    fun browsersContainsExpectedPackages() {
        val expected = setOf(
            "com.android.chrome",
            "org.mozilla.firefox"
        )
        assertEquals(expected, TargetAppAllowlist.browsers)
    }

    @Test
    fun socialAndBrowsersAreDisjoint() {
        val intersection = TargetAppAllowlist.socialApps.intersect(TargetAppAllowlist.browsers)
        assertTrue("Social apps and browsers must have no overlapping packages", intersection.isEmpty())
    }

    @Test
    fun allContainsUnionOfSocialAndBrowsers() {
        val expected = TargetAppAllowlist.socialApps + TargetAppAllowlist.browsers
        assertEquals(expected, TargetAppAllowlist.all)
        assertEquals(8, TargetAppAllowlist.all.size)
    }

    @Test
    fun isTargetAppReturnsTrueForSocialAndBrowsers() {
        assertTrue(TargetAppAllowlist.isTargetApp("com.instagram.android"))
        assertTrue(TargetAppAllowlist.isTargetApp("com.android.chrome"))
        assertTrue(TargetAppAllowlist.isTargetApp("com.google.android.youtube"))
    }

    @Test
    fun isTargetAppReturnsFalseForUnknownOrNull() {
        assertFalse(TargetAppAllowlist.isTargetApp(null))
        assertFalse(TargetAppAllowlist.isTargetApp("com.android.settings"))
        assertFalse(TargetAppAllowlist.isTargetApp("com.whatsapp"))
    }

    @Test
    fun isBrowserReturnsTrueForBrowsersOnly() {
        assertTrue(TargetAppAllowlist.isBrowser("com.android.chrome"))
        assertTrue(TargetAppAllowlist.isBrowser("org.mozilla.firefox"))
        assertFalse(TargetAppAllowlist.isBrowser("com.instagram.android"))
        assertFalse(TargetAppAllowlist.isBrowser(null))
    }

    @Test
    fun isSocialAppReturnsTrueForSocialOnly() {
        assertTrue(TargetAppAllowlist.isSocialApp("com.instagram.android"))
        assertTrue(TargetAppAllowlist.isSocialApp("com.google.android.youtube"))
        assertFalse(TargetAppAllowlist.isSocialApp("com.android.chrome"))
        assertFalse(TargetAppAllowlist.isSocialApp(null))
    }
}
