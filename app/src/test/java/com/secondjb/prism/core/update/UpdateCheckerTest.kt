package com.secondjb.prism.core.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testParseVersionParts() {
        assertEquals(listOf(0, 1, 0), UpdateChecker.parseVersionParts("0.1.0"))
        assertEquals(listOf(0, 2, 0), UpdateChecker.parseVersionParts("v0.2.0"))
        assertEquals(listOf(1, 0, 0), UpdateChecker.parseVersionParts("V1.0.0"))
        assertEquals(listOf(0, 1, 5), UpdateChecker.parseVersionParts("v0.1.5-beta.1"))
        assertEquals(listOf(2, 3), UpdateChecker.parseVersionParts("2.3+build99"))
        assertEquals(emptyList<Int>(), UpdateChecker.parseVersionParts(""))
        assertEquals(emptyList<Int>(), UpdateChecker.parseVersionParts("not_a_version"))
    }

    @Test
    fun testIsNewerVersion() {
        // Newer versions
        assertTrue(UpdateChecker.isNewerVersion("0.2.0", "0.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("v0.2.0", "0.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.1", "0.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0", "0.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.0.1", "0.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("v0.2.0-rc1", "0.1.0"))

        // Same or older versions
        assertFalse(UpdateChecker.isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.0.9", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.0.1", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.0-alpha", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.1", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.0", "0.1.0.1"))
        assertFalse(UpdateChecker.isNewerVersion("", "0.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("invalid", "0.1.0"))
    }

    @Test
    fun testGitHubReleaseJsonParsing() {
        val jsonPayload = """
            {
                "tag_name": "v0.2.0",
                "name": "Prism Companion v0.2.0",
                "html_url": "https://github.com/secondjb/Prism-Android-Music-Player/releases/tag/v0.2.0",
                "body": "Initial release with updated audio engine",
                "draft": false,
                "prerelease": false,
                "extra_unknown_field": 12345
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val release = json.decodeFromString<GitHubRelease>(jsonPayload)

        assertEquals("v0.2.0", release.tagName)
        assertEquals("Prism Companion v0.2.0", release.name)
        assertEquals("https://github.com/secondjb/Prism-Android-Music-Player/releases/tag/v0.2.0", release.htmlUrl)
        assertEquals("Initial release with updated audio engine", release.body)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
    }
}
