package com.secondjb.prism.playback

import com.secondjb.prism.core.playback.LocalPlaybackEngine.Companion.toMediaItem
import com.secondjb.prism.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackEngineTest {

    @Test
    fun testTrackToMediaItemMapping() {
        val track = Track(
            id = "track-99",
            uri = "content://media/external/audio/media/99",
            title = "Closer",
            artist = "The Chainsmokers",
            album = "Collage",
            durationMs = 245000L,
            artworkUri = "content://media/external/audio/albumart/12"
        )

        val mediaItem = track.toMediaItem()
        assertEquals(track.id, mediaItem.mediaId)
        assertEquals(track.title, mediaItem.mediaMetadata.title?.toString())
        assertEquals(track.artist, mediaItem.mediaMetadata.artist?.toString())
        assertEquals(track.album, mediaItem.mediaMetadata.albumTitle?.toString())
        assertNotNull(mediaItem.mediaMetadata)
    }

    @Test
    fun testQueueReordering() {
        val tracks = listOf(
            Track("1", "u1", "Song 1", "A1", "Alb1", 1000L),
            Track("2", "u2", "Song 2", "A2", "Alb2", 2000L),
            Track("3", "u3", "Song 3", "A3", "Alb3", 3000L)
        )

        val mutableQueue = tracks.toMutableList()
        // Move track at index 0 to index 2
        val moved = mutableQueue.removeAt(0)
        mutableQueue.add(2, moved)

        assertEquals("2", mutableQueue[0].id)
        assertEquals("3", mutableQueue[1].id)
        assertEquals("1", mutableQueue[2].id)
    }
}
