package com.awareness.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetAppTest {

    @Test
    fun `allowlist contains social apps and browsers`() {
        val allowlist = TargetApp.allowlist
        assertTrue(allowlist.contains("com.instagram.android"))
        assertTrue(allowlist.contains("com.facebook.katana"))
        assertTrue(allowlist.contains("com.snapchat.android"))
        assertTrue(allowlist.contains("com.google.android.youtube"))
        assertTrue(allowlist.contains("com.android.chrome"))
        assertTrue(allowlist.contains("org.mozilla.firefox"))
    }

    @Test
    fun `browsers set contains only browser packages`() {
        val browsers = TargetApp.browsers
        assertTrue(browsers.contains("com.android.chrome"))
        assertTrue(browsers.contains("org.mozilla.firefox"))
        assertFalse(browsers.contains("com.instagram.android"))
    }

    @Test
    fun `fromPackageName returns correct target app`() {
        val app = TargetApp.fromPackageName("com.instagram.android")
        assertNotNull(app)
        assertEquals("Instagram", app?.appName)
        assertFalse(app?.isBrowser ?: true)
    }
}
