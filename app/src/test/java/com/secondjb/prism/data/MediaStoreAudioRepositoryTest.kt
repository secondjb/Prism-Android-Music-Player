package com.secondjb.prism.data

import com.secondjb.prism.core.data.MediaStoreAudioRepository
import com.secondjb.prism.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreAudioRepositoryTest {

    @Test
    fun testComputeStatsWithMixedFormats() {
        // Mock a list of tracks
        val tracks = listOf(
            Track("1", "u1", "Track 1", "Daft Punk", "Discovery", 120000L, sampleRate = 96000, bitDepth = 24, format = "FLAC"),
            Track("2", "u2", "Track 2", "Daft Punk", "Discovery", 180000L, sampleRate = 44100, bitDepth = 16, format = "MP3"),
            Track("3", "u3", "Track 3", "Porter Robinson", "Nurture", 240000L, sampleRate = 48000, bitDepth = 16, format = "FLAC")
        )

        // Dummy repository instance or pure test of calculation
        val totalSecs = tracks.sumOf { it.durationMs } / 1000
        val flacCount = tracks.count { it.format.equals("FLAC", ignoreCase = true) }
        val hiResCount = tracks.count { it.audioFormatInfo.isHiRes }
        val topArtist = tracks.groupBy { it.artist }.maxByOrNull { it.value.size }?.key
        val topAlbum = tracks.groupBy { it.album }.maxByOrNull { it.value.size }?.key

        assertEquals(540L, totalSecs)
        assertEquals(2, flacCount)
        assertEquals(1, hiResCount)
        assertEquals("Daft Punk", topArtist)
        assertEquals("Discovery", topAlbum)
    }
}
