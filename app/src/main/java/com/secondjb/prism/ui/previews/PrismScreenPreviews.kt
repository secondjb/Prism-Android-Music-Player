package com.secondjb.prism.ui.previews

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.data.PrismPreferencesRepository
import com.secondjb.prism.core.stats.AggregatedStats
import com.secondjb.prism.core.stats.StatTopItem
import com.secondjb.prism.core.stats.StatsFilterRange
import com.secondjb.prism.core.stats.StatsMetric
import com.secondjb.prism.domain.model.Album
import com.secondjb.prism.domain.model.Artist
import com.secondjb.prism.domain.model.DemoDataProvider
import com.secondjb.prism.domain.model.ListeningStats
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.Playlist
import com.secondjb.prism.ui.components.FloatingNowPlayingBar
import com.secondjb.prism.ui.screens.library.LibraryFilterChip
import com.secondjb.prism.ui.screens.library.LibraryScreen
import com.secondjb.prism.ui.screens.library.LibraryUiState
import com.secondjb.prism.ui.screens.main.MainScreen
import com.secondjb.prism.ui.screens.main.MainUiState
import com.secondjb.prism.ui.screens.player.FullScreenLyricsPage
import com.secondjb.prism.ui.screens.settings.SettingsScreen
import com.secondjb.prism.ui.screens.stats.StatsScreen
import com.secondjb.prism.ui.theme.PrismTheme

private fun previewMainUiState(): MainUiState {
    val tracks = DemoDataProvider.demoTracks
    val topSongs = tracks.mapIndexed { idx, track ->
        StatTopItem(
            name = track.title,
            count = 42 - idx * 7,
            listenedMs = (42 - idx * 7) * track.durationMs,
            subtitle = track.artist,
            album = track.album
        )
    }
    val topArtists = tracks.mapIndexed { idx, track ->
        StatTopItem(
            name = track.artist,
            count = 55 - idx * 9,
            listenedMs = (55 - idx * 9) * 180_000L
        )
    }
    val topAlbums = tracks.mapIndexed { idx, track ->
        StatTopItem(
            name = track.album,
            count = 35 - idx * 6,
            listenedMs = (35 - idx * 6) * 195_000L,
            subtitle = track.artist
        )
    }
    val aggregated = AggregatedStats(
        totalTracksPlayed = 184,
        totalListeningSeconds = 42_300L,
        topArtist = tracks.first().artist,
        topAlbum = tracks.first().album,
        topSong = tracks.first().title,
        topArtistsList = topArtists,
        topSongsList = topSongs,
        topAlbumsList = topAlbums,
        listeningHabitsHours = listOf(
            1, 0, 0, 0, 0, 0, 2, 5, 9, 12, 8, 6,
            10, 14, 11, 9, 15, 18, 22, 19, 16, 12, 7, 3
        )
    )
    return MainUiState(
        stats = ListeningStats(
            totalTracksPlayed = aggregated.totalTracksPlayed,
            totalListeningSeconds = aggregated.totalListeningSeconds,
            topArtist = aggregated.topArtist,
            topAlbum = aggregated.topAlbum,
            flacTracksCount = 3,
            hiResTracksCount = 2
        ),
        aggregatedStats = aggregated,
        selectedRange = StatsFilterRange.ALL_TIME,
        leaderboardMetric = StatsMetric.TIME,
        recentTracks = tracks,
        quickRecommendations = tracks.reversed(),
        isLoading = false
    )
}

@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun MainScreenPreview() {
    PrismTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainScreen(
                uiState = previewMainUiState(),
                onTrackClick = { _, _ -> },
                onShuffleAllClick = {}
            )
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun LibraryScreenSongsPreview() {
    val tracks = DemoDataProvider.demoTracks
    val libraryState = LibraryUiState(
        selectedChip = LibraryFilterChip.ALL_SONGS,
        tracks = tracks,
        albums = tracks.mapIndexed { i, t ->
            Album(
                id = "alb_$i",
                title = t.album,
                artist = t.artist,
                artworkUri = t.artworkUri,
                trackCount = 1,
                year = t.year
            )
        },
        artists = tracks.mapIndexed { i, t ->
            Artist(
                id = "art_$i",
                name = t.artist,
                trackCount = 1,
                albumCount = 1
            )
        },
        playlists = listOf(
            Playlist(
                id = "pl_1",
                name = "Hi-Res Favorites",
                trackIds = tracks.take(2).map { it.id },
                isPinned = true
            )
        ),
        likedTrackIds = setOf(tracks.first().id)
    )
    PrismTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LibraryScreen(
                uiState = libraryState,
                hasNowPlayingBar = true,
                onFilterSelect = {},
                onToggleViewMode = {},
                onSearchChange = {},
                onToggleSearch = {},
                onTogglePin = {},
                onCreatePlaylist = {},
                onTrackClick = { _, _ -> }
            )
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun StatsScreenPreview() {
    PrismTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            StatsScreen(
                uiState = previewMainUiState(),
                allTracks = DemoDataProvider.demoTracks,
                onBackClick = {},
                onTrackClick = { _, _ -> },
                onSelectStatsRange = {},
                onMetricChange = {},
                onGoToArtist = {},
                onGoToAlbum = {}
            )
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun SettingsScreenPreview() {
    val context = LocalContext.current
    val prefsRepo = remember(context) { PrismPreferencesRepository(context) }
    PrismTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                preferencesRepository = prefsRepo,
                totalTrackCount = DemoDataProvider.demoTracks.size,
                connectedDeviceName = "Prism Desktop",
                isDesktopConnected = true,
                pairedDevices = listOf(
                    PairedDevice(
                        id = "desktop-preview-1",
                        name = "Prism Desktop",
                        host = "192.168.1.42",
                        port = 9876,
                        token = "preview-token"
                    )
                )
            )
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun FullScreenLyricsPagePreview() {
    val sampleTrack = DemoDataProvider.demoTracks.first()
    val playbackState = PlaybackState(
        currentTrack = sampleTrack,
        isPlaying = true,
        positionMs = 15_000L,
        durationMs = sampleTrack.durationMs,
        queue = DemoDataProvider.demoTracks,
        queueIndex = 0
    )
    PrismTheme(darkTheme = true, dynamicColor = false) {
        FullScreenLyricsPage(
            track = sampleTrack,
            playbackState = playbackState,
            lyricsText = sampleTrack.lyrics,
            onSeek = {},
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FloatingNowPlayingBarPreview() {
    val sampleTrack = DemoDataProvider.demoTracks.first()
    val playbackState = PlaybackState(
        currentTrack = sampleTrack,
        isPlaying = true,
        positionMs = 64_000L,
        durationMs = sampleTrack.durationMs,
        queue = DemoDataProvider.demoTracks,
        queueIndex = 0
    )
    PrismTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FloatingNowPlayingBar(
                playbackState = playbackState,
                onTogglePlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onBarClick = {}
            )
        }
    }
}
