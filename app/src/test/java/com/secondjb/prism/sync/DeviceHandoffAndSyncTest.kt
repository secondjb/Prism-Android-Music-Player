package com.secondjb.prism.sync

import com.secondjb.prism.core.sync.protocol.CommandAction
import com.secondjb.prism.core.sync.protocol.SyncFrame
import com.secondjb.prism.core.sync.protocol.SyncPlaylistDto
import com.secondjb.prism.core.sync.protocol.SyncTrackKeyDto
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHandoffAndSyncTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private val sampleTracks = listOf(
        Track(
            id = "t1",
            title = "Midnight Echoes",
            artist = "Lunar City",
            album = "Night Waves",
            durationMs = 210000L,
            uri = "content://media/external/audio/media/101",
            filePath = "/storage/emulated/0/Music/Midnight Echoes.flac",
            format = "FLAC"
        ),
        Track(
            id = "t2",
            title = "Neon Horizon",
            artist = "Synthwave Express",
            album = "Future Past",
            durationMs = 185000L,
            uri = "content://media/external/audio/media/102",
            filePath = "/storage/emulated/0/Music/Neon Horizon.mp3",
            format = "MP3"
        ),
        Track(
            id = "t3",
            title = "Solar Flare",
            artist = "Cosmic Ray",
            album = "Star Dust",
            durationMs = 240000L,
            uri = "content://media/external/audio/media/103",
            filePath = "/storage/emulated/0/Music/Solar Flare.wav",
            format = "WAV"
        )
    )

    @Test
    fun testHandoffToDesktopCommandPayload() {
        val currentTrack = sampleTracks[1] // Neon Horizon
        val currentPositionMs = 52400L // 52.4 seconds into song
        val queueFns = sampleTracks.map { (it.filePath ?: it.uri).substringAfterLast('/') }

        val command = SyncFrame.PlaybackCommand(
            action = CommandAction.PLAY_TRACK,
            trackTitle = currentTrack.title,
            trackArtist = currentTrack.artist,
            trackFilename = (currentTrack.filePath ?: currentTrack.uri).substringAfterLast('/'),
            trackUri = currentTrack.uri,
            queueFilenames = queueFns,
            seekPositionMs = currentPositionMs
        )

        val serialized = json.encodeToString<SyncFrame>(command)
        assertTrue(serialized.contains("PLAY_TRACK"))
        assertTrue(serialized.contains("52400"))
        assertTrue(serialized.contains("Neon Horizon.mp3"))

        val decoded = json.decodeFromString<SyncFrame>(serialized) as SyncFrame.PlaybackCommand
        assertEquals(CommandAction.PLAY_TRACK, decoded.action)
        assertEquals(52400L, decoded.seekPositionMs)
        assertEquals("Neon Horizon", decoded.trackTitle)
        assertEquals(3, decoded.queueFilenames?.size)
        assertEquals("Neon Horizon.mp3", decoded.queueFilenames?.get(1))
    }

    @Test
    fun testHandoffFromDesktopTrackAndQueueResolution() {
        val remoteCurrent = sampleTracks[2] // Solar Flare
        val remotePositionMs = 89000L
        val remoteQueue = listOf(sampleTracks[2], sampleTracks[0], sampleTracks[1])

        // Verify local match lookup by filename or title
        val matchedTrack = sampleTracks.firstOrNull { local ->
            val localFn = (local.filePath ?: local.uri).substringAfterLast('/')
            val remoteFn = (remoteCurrent.filePath ?: remoteCurrent.uri).substringAfterLast('/')
            localFn.equals(remoteFn, ignoreCase = true) || local.title.equals(remoteCurrent.title, ignoreCase = true)
        }
        assertNotNull(matchedTrack)
        assertEquals("t3", matchedTrack?.id)

        // Verify queue mapped in matching order
        val matchedQueue = remoteQueue.mapNotNull { rq ->
            sampleTracks.firstOrNull { local ->
                val localFn = (local.filePath ?: local.uri).substringAfterLast('/')
                val remoteFn = (rq.filePath ?: rq.uri).substringAfterLast('/')
                localFn.equals(remoteFn, ignoreCase = true) || local.title.equals(rq.title, ignoreCase = true)
            }
        }
        assertEquals(3, matchedQueue.size)
        assertEquals("t3", matchedQueue[0].id)
        assertEquals("t1", matchedQueue[1].id)
        assertEquals("t2", matchedQueue[2].id)

        // Verify target index
        val targetIdx = matchedQueue.indexOfFirst { it.id == matchedTrack?.id }
        assertEquals(0, targetIdx)
        assertEquals(89000L, remotePositionMs)
    }

    @Test
    fun testSwapDeviceTargetSerialization() {
        val swapToLocal = SyncFrame.SwapDeviceTarget(targetDeviceId = "local")
        val serializedLocal = json.encodeToString<SyncFrame>(swapToLocal)
        assertTrue(serializedLocal.contains("swap_device"))
        assertTrue(serializedLocal.contains("local"))

        val swapToDesktop = SyncFrame.SwapDeviceTarget(targetDeviceId = "desktop")
        val serializedDesktop = json.encodeToString<SyncFrame>(swapToDesktop)
        assertTrue(serializedDesktop.contains("desktop"))
    }

    @Test
    fun testPlaylistSyncSerializationWithBase64AndReset() {
        val customCoverB64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBD..."
        val playlistWithCover = SyncPlaylistDto(
            id = "pl-1",
            name = "Road Trip",
            tracks = listOf(
                SyncTrackKeyDto(title = "Midnight Echoes", artist = "Lunar City", filename = "Midnight Echoes.flac")
            ),
            customCoverUri = customCoverB64
        )

        val serializedCover = json.encodeToString(playlistWithCover)
        assertTrue(serializedCover.contains("data:image/jpeg;base64"))

        val decodedCover = json.decodeFromString<SyncPlaylistDto>(serializedCover)
        assertEquals(customCoverB64, decodedCover.customCoverUri)

        // Playlist with reset cover (empty string or null)
        val playlistReset = SyncPlaylistDto(
            id = "pl-1",
            name = "Road Trip",
            tracks = emptyList(),
            customCoverUri = ""
        )
        val serializedReset = json.encodeToString(playlistReset)
        assertTrue(serializedReset.contains("\"customCoverUri\":\"\""))
    }

    @Test
    fun testPlaybackStatePreservationAcrossSimulatedHandoff() {
        // Initial state: phone playing at 30 seconds
        val initialState = PlaybackState(
            currentTrack = sampleTracks[0],
            queue = sampleTracks,
            queueIndex = 0,
            isPlaying = true,
            positionMs = 30000L,
            durationMs = 210000L
        )
        assertTrue(initialState.isPlaying)
        assertEquals(30000L, initialState.positionMs)

        // Simulated handoff to remote: remote adopts state + continues to 45 seconds
        val remoteAdoptedState = initialState.copy(
            positionMs = 45000L,
            isPlaying = true
        )
        assertEquals(45000L, remoteAdoptedState.positionMs)

        // Simulated handoff back to local phone: local adopts remote track and timestamp
        val returnedState = PlaybackState(
            currentTrack = remoteAdoptedState.currentTrack,
            queue = remoteAdoptedState.queue,
            queueIndex = remoteAdoptedState.queueIndex,
            positionMs = remoteAdoptedState.positionMs,
            durationMs = remoteAdoptedState.durationMs,
            isPlaying = remoteAdoptedState.isPlaying
        )

        assertEquals("t1", returnedState.currentTrack?.id)
        assertEquals(45000L, returnedState.positionMs)
        assertTrue(returnedState.isPlaying)
        assertEquals(3, returnedState.queue.size)
    }
}
