package com.awareness.app

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ManifestSecurityTest {

    @Test
    fun `verify AndroidManifest contains zero INTERNET permission (VS01-TS-05)`() {
        // Read root and module manifests
        val rootDir = File(".").canonicalFile
        val manifestFiles = rootDir.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" }
            .toList()

        assertFalse("At least one AndroidManifest.xml should exist", manifestFiles.isEmpty())

        val forbiddenPermission = "android.permission." + "INTERNET"
        for (manifest in manifestFiles) {
            val content = manifest.readText()
            assertFalse(
                "Forbidden INTERNET permission found in ${manifest.absolutePath}!",
                content.contains(forbiddenPermission)
            )
        }
    }
}
