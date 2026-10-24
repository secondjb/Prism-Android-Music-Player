package com.secondjb.prism.domain

import com.secondjb.prism.domain.model.AudioFormatInfo
import com.secondjb.prism.domain.model.DeviceNode
import com.secondjb.prism.domain.model.DeviceType
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun testTrackSerializationAndAudioFormat() {
        val track = Track(
            id = "test-track-1",
            uri = "content://media/external/audio/media/42",
            title = "Starfall",
            artist = "Illenium",
            album = "Ascend",
            durationMs = 245000L,
            sampleRate = 96000,
            bitDepth = 24,
            channels = 2,
            bitRateKbps = 2450,
            format = "FLAC"
        )

        // Verify AudioFormatInfo derivation
        val formatInfo = track.audioFormatInfo
        assertTrue(formatInfo.isHiRes)
        assertEquals("96 kHz", formatInfo.formattedSampleRate)
        assertEquals("FLAC • 24-bit / 96 kHz • 2450 kbps", formatInfo.displayBadge)

        // Verify JSON round-trip serialization
        val serialized = json.encodeToString(track)
        val deserialized = json.decodeFromString<Track>(serialized)
        assertEquals(track.id, deserialized.id)
        assertEquals(track.title, deserialized.title)
        assertEquals(track.format, deserialized.format)
    }

    @Test
    fun testRepeatModeCycling() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }

    @Test
    fun testPlaybackStateProgressAndQueueNavigation() {
        val track1 = Track("1", "uri1", "Song 1", "Artist 1", "Album 1", 100000L)
        val track2 = Track("2", "uri2", "Song 2", "Artist 2", "Album 2", 200000L)
        val queue = listOf(track1, track2)

        // At 0ms on the first track: hasNext is true, hasPrevious is false
        val stateAtZero = PlaybackState(
            currentTrack = track1,
            isPlaying = true,
            positionMs = 0L,
            durationMs = 100000L,
            queue = queue,
            queueIndex = 0
        )
        assertEquals(0.0f, stateAtZero.progressFraction, 0.001f)
        assertTrue(stateAtZero.hasNext)
        assertFalse(stateAtZero.hasPrevious)

        // At 25000ms on first track: position > 3000ms, hasPrevious is true (rewind)
        val statePlaying = stateAtZero.copy(positionMs = 25000L)
        assertEquals(0.25f, statePlaying.progressFraction, 0.001f)
        assertTrue(statePlaying.hasNext)
        assertTrue(statePlaying.hasPrevious)

        // On second track: hasNext is false, hasPrevious is true
        val stateAtEnd = stateAtZero.copy(
            currentTrack = track2,
            queueIndex = 1,
            positionMs = 190000L,
            durationMs = 200000L
        )
        assertFalse(stateAtEnd.hasNext)
        assertTrue(stateAtEnd.hasPrevious)
    }

    @Test
    fun testDeviceNodeSerialization() {
        val desktopNode = DeviceNode(
            id = "desktop-win-1",
            name = "Studio PC",
            type = DeviceType.WINDOWS_DESKTOP,
            ipAddress = "192.168.1.100",
            port = 8765,
            isCurrentDevice = false,
            isPaired = true
        )

        val serialized = json.encodeToString(desktopNode)
        val deserialized = json.decodeFromString<DeviceNode>(serialized)
        assertEquals(desktopNode.name, deserialized.name)
        assertEquals(DeviceType.WINDOWS_DESKTOP, deserialized.type)
        assertTrue(deserialized.isPaired)
    }
}
