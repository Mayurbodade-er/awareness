package com.awareness.core.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityConfigVerificationTest {

    @Test
    fun xmlPackageNamesMatchesTargetAppAllowlist() {
        val configXml = File("src/main/res/xml/accessibility_service_config.xml")
        assertTrue("accessibility_service_config.xml must exist", configXml.exists())

        val xmlContent = configXml.readText()
        val packageNamesMatch = Regex("""android:packageNames="([^"]+)"""").find(xmlContent)
        assertTrue("android:packageNames attribute must be present in config XML", packageNamesMatch != null)

        val packageListInXml = packageNamesMatch!!.groupValues[1]
            .split(",")
            .map { it.trim() }
            .toSet()

        assertEquals(
            "XML packageNames must exactly match TargetAppAllowlist.all",
            TargetAppAllowlist.all,
            packageListInXml
        )
    }

    @Test
    fun noForbiddenPollingApiInCodebase() {
        val forbiddenApi = "UsageStats" + "Manager"
        val projectRoot = File("../../")
        val kotlinFiles = projectRoot.walkTopDown()
            .filter { it.extension == "kt" && it.path.contains("/src/main/") }
            .toList()

        for (file in kotlinFiles) {
            val content = file.readText()
            assertFalse(
                "Polling API is forbidden in battery-disciplined architecture: ${file.path}",
                content.contains(forbiddenApi)
            )
        }
    }
}
