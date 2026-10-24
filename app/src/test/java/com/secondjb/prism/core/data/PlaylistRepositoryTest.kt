package com.secondjb.prism.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testInitialFavoritesPlaylistCreated() = runTest {
        val repo = PlaylistRepository(baseDir = tempFolder.root, scope = this)
        val playlists = repo.playlists.value
        assertEquals(1, playlists.size)
        assertEquals("favorites", playlists[0].id)
        assertEquals("Favorites", playlists[0].name)
        assertTrue(playlists[0].isPinned)
    }

    @Test
    fun testCreateAndAddTrackToPlaylist() = runTest {
        val repo = PlaylistRepository(baseDir = tempFolder.root, scope = this)

        val created = repo.createPlaylist("Cyberpunk Vibes", "Late night synthwave")
        assertEquals("Cyberpunk Vibes", created.name)
        assertEquals(2, repo.playlists.value.size)

        repo.addTrackToPlaylist(created.id, "track_101")
        val updated = repo.playlists.value.find { it.id == created.id }
        assertTrue(updated!!.trackIds.contains("track_101"))

        repo.removeTrackFromPlaylist(created.id, "track_101")
        val afterRemove = repo.playlists.value.find { it.id == created.id }
        assertFalse(afterRemove!!.trackIds.contains("track_101"))
    }

    @Test
    fun testToggleLikeTrack() = runTest {
        val repo = PlaylistRepository(baseDir = tempFolder.root, scope = this)

        val liked = repo.toggleLikeTrack("song_42")
        assertTrue(liked)
        assertTrue(repo.likedTrackIds.value.contains("song_42"))

        val unliked = repo.toggleLikeTrack("song_42")
        assertFalse(unliked)
        assertFalse(repo.likedTrackIds.value.contains("song_42"))
    }
}
