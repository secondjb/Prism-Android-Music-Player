package com.secondjb.prism.playback

import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.lyrics.LrclibClient
import com.secondjb.prism.ui.screens.player.formatDeviceDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPlaybackFeaturesTest {

    @Test
    fun testFormatDeviceDisplayNameSingleDesktop() {
        val formatted = formatDeviceDisplayName("Desktop (AHHHHHHHHHHHHHH)", 0)
        assertEquals("Desktop", formatted)
    }

    @Test
    fun testFormatDeviceDisplayNameMultipleDesktops() {
        assertEquals("Desktop", formatDeviceDisplayName("Desktop", 0))
        assertEquals("Desktop 2", formatDeviceDisplayName("Prism-Windows-DESKTOP-XYZ", 1))
        assertEquals("Desktop 3", formatDeviceDisplayName("Living Room PC", 2))
    }

    @Test
    fun testFormatDeviceDisplayNameAndroidDevices() {
        assertEquals("Phone", formatDeviceDisplayName("Pixel 7", 0))
        assertEquals("Phone 2", formatDeviceDisplayName("Galaxy S24", 1))
    }

    @Test
    fun testLrclibQuerySanitizer() {
        val cleanedTitle = LrclibClient.cleanTitle("Someone You Loved (Official Music Video) [Remastered 2023]")
        assertEquals("Someone You Loved", cleanedTitle)

        val cleanedFeat = LrclibClient.cleanTitle("Starboy (feat. Daft Punk)")
        assertEquals("Starboy", cleanedFeat)

        val cleanedArtist = LrclibClient.cleanArtist("The Weeknd feat. Daft Punk")
        assertEquals("The Weeknd", cleanedArtist)
    }

    @Test
    fun testPairedDeviceMatchingByToken() {
        val original = PairedDevice(
            id = "10.6.0.165:9876",
            name = "Prism Desktop (AHHHHHHHHHHHHHH)",
            host = "10.6.0.165",
            port = 9876,
            token = "crypto-auth-token-12345"
        )

        val updatedAfterDhcpChange = original.copy(
            host = "10.6.0.170",
            id = "10.6.0.170:9876",
            lastConnectedTimeMs = System.currentTimeMillis()
        )

        val currentList = listOf(original)
        val deduplicated = currentList.filterNot {
            (updatedAfterDhcpChange.token.isNotBlank() && it.token == updatedAfterDhcpChange.token) ||
            (updatedAfterDhcpChange.id.isNotBlank() && it.id == updatedAfterDhcpChange.id) ||
            (it.host == updatedAfterDhcpChange.host && it.port == updatedAfterDhcpChange.port)
        }.toMutableList()

        deduplicated.add(0, updatedAfterDhcpChange)

        assertEquals(1, deduplicated.size)
        assertEquals("10.6.0.170", deduplicated.first().host)
        assertEquals("crypto-auth-token-12345", deduplicated.first().token)
    }

    @Test
    fun testLrcStandardLineDoesNotInferSyllables() {
        val rawLrc = "[00:10.00]This is a standard line-by-line lyric\n[00:15.00]Second line here"
        val parsed = com.secondjb.prism.ui.components.lyrics.LrcParser.parse(rawLrc)
        assertEquals(2, parsed.size)
        assertFalse("Standard LRC lines should not infer synthetic syllables", parsed[0].hasExplicitSyllables)
        assertFalse(parsed[0].hasSyllables)
        assertTrue(parsed[0].syllables.isEmpty())
        assertEquals("This is a standard line-by-line lyric", parsed[0].text)
    }

    @Test
    fun testLrcExplicitWordTagsPopulateSyllables() {
        val enhancedLrc = "[00:10.00]<00:10.00>This <00:10.50>is <00:11.00>word <00:11.50>synced"
        val parsed = com.secondjb.prism.ui.components.lyrics.LrcParser.parse(enhancedLrc)
        assertEquals(1, parsed.size)
        assertTrue("Enhanced LRC lines with inline tags must have explicit syllables", parsed[0].hasExplicitSyllables)
        assertTrue(parsed[0].hasSyllables)
        assertEquals(4, parsed[0].syllables.size)
    }
}
