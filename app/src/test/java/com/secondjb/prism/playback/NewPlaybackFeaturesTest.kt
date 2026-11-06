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
        val formatted = formatDeviceDisplayName("Prism Desktop (AHHHHHHHHHHHHHH)", 0)
        assertEquals("Prism Desktop", formatted)
    }

    @Test
    fun testFormatDeviceDisplayNameMultipleDesktops() {
        assertEquals("Prism Desktop", formatDeviceDisplayName("Prism Desktop", 0))
        assertEquals("Prism Desktop 2", formatDeviceDisplayName("Prism-Windows-DESKTOP-XYZ", 1))
        assertEquals("Prism Desktop 3", formatDeviceDisplayName("Living Room PC", 2))
    }

    @Test
    fun testFormatDeviceDisplayNameAndroidDevices() {
        assertEquals("Prism Android", formatDeviceDisplayName("Pixel 7", 0))
        assertEquals("Prism Android 2", formatDeviceDisplayName("Galaxy S24", 1))
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
}
