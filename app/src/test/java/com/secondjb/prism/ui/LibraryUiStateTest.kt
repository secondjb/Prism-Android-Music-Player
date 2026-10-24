package com.secondjb.prism.ui

import com.secondjb.prism.domain.model.Album
import com.secondjb.prism.domain.model.Artist
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.navigation.Screen
import com.secondjb.prism.ui.screens.library.LibraryFilterChip
import com.secondjb.prism.ui.screens.library.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUiStateTest {

    @Test
    fun testTrackSearchFiltering() {
        val tracks = listOf(
            Track("1", "u1", "Midnight City", "M83", "Hurry Up", 240000L),
            Track("2", "u2", "Wait", "M83", "Hurry Up", 200000L),
            Track("3", "u3", "Strobe", "deadmau5", "For Lack of a Better Name", 600000L)
        )

        val state = LibraryUiState(
            tracks = tracks,
            searchQuery = "m83"
        )

        val filtered = state.filteredTracks
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.artist == "M83" })
    }

    @Test
    fun testAlbumAndArtistSearchFiltering() {
        val albums = listOf(
            Album("1", "Discovery", "Daft Punk"),
            Album("2", "Random Access Memories", "Daft Punk"),
            Album("3", "Cross", "Justice")
        )
        val artists = listOf(
            Artist("1", "Daft Punk"),
            Artist("2", "Justice")
        )

        val state = LibraryUiState(
            albums = albums,
            artists = artists,
            searchQuery = "justice"
        )

        assertEquals(1, state.filteredAlbums.size)
        assertEquals("Cross", state.filteredAlbums[0].title)

        assertEquals(1, state.filteredArtists.size)
        assertEquals("Justice", state.filteredArtists[0].name)
    }

    @Test
    fun testNavigationScreenDefinitions() {
        assertEquals("main", Screen.Main.route)
        assertEquals("Home", Screen.Main.title)
        assertEquals("library", Screen.Library.route)
        assertEquals("settings", Screen.Settings.route)
        assertEquals(3, Screen.bottomNavItems.size)
    }

    @Test
    fun testFilterChipLabels() {
        assertEquals("Playlists", LibraryFilterChip.PLAYLISTS.label)
        assertEquals("Artists", LibraryFilterChip.ARTISTS.label)
        assertEquals("All Songs", LibraryFilterChip.ALL_SONGS.label)
        assertEquals("Albums", LibraryFilterChip.ALBUMS.label)
    }
}
