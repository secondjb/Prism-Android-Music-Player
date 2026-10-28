package com.secondjb.prism.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondjb.prism.core.data.MediaStoreAudioRepository
import com.secondjb.prism.core.data.PlaylistRepository
import com.secondjb.prism.domain.model.Album
import com.secondjb.prism.domain.model.Artist
import com.secondjb.prism.domain.model.Playlist
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilterChip(val label: String) {
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    ALL_SONGS("All Songs"),
    ALBUMS("Albums")
}

enum class SongSortOption(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    ARTIST_DESC("Artist (Z-A)"),
    ALBUM_ASC("Album (A-Z)"),
    ALBUM_DESC("Album (Z-A)"),
    DATE_ADDED_DESC("Date Added (Newest)"),
    DATE_ADDED_ASC("Date Added (Oldest)"),
    DURATION_DESC("Duration (Longest)"),
    DURATION_ASC("Duration (Shortest)"),
    YEAR_DESC("Year (Newest)"),
    YEAR_ASC("Year (Oldest)")
}

enum class AlbumSortOption(val label: String) {
    TITLE_ASC("Album (A-Z)"),
    TITLE_DESC("Album (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    ARTIST_DESC("Artist (Z-A)"),
    YEAR_DESC("Year (Newest)"),
    YEAR_ASC("Year (Oldest)"),
    TRACK_COUNT_DESC("Tracks (Most)"),
    TRACK_COUNT_ASC("Tracks (Least)")
}

enum class ArtistSortOption(val label: String) {
    NAME_ASC("Artist (A-Z)"),
    NAME_DESC("Artist (Z-A)"),
    TRACK_COUNT_DESC("Tracks (Most)"),
    TRACK_COUNT_ASC("Tracks (Least)"),
    ALBUM_COUNT_DESC("Albums (Most)"),
    ALBUM_COUNT_ASC("Albums (Least)")
}

enum class PlaylistSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    TRACK_COUNT_DESC("Tracks (Most)"),
    TRACK_COUNT_ASC("Tracks (Least)")
}

data class LibraryUiState(
    val selectedChip: LibraryFilterChip = LibraryFilterChip.ALL_SONGS,
    val isGridView: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val likedTrackIds: Set<String> = emptySet(),
    val activePlaylist: Playlist? = null,
    val activeAlbum: Album? = null,
    val activeArtist: Artist? = null,
    val songSortOption: SongSortOption = SongSortOption.TITLE_ASC,
    val albumSortOption: AlbumSortOption = AlbumSortOption.TITLE_ASC,
    val artistSortOption: ArtistSortOption = ArtistSortOption.NAME_ASC,
    val playlistSortOption: PlaylistSortOption = PlaylistSortOption.NAME_ASC,
    val pinnedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
) {
    val filteredTracks: List<Track>
        get() {
            val base = if (searchQuery.isBlank()) {
                tracks
            } else {
                tracks.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
                }
            }
            return when (songSortOption) {
                SongSortOption.TITLE_ASC -> base.sortedBy { it.title.lowercase() }
                SongSortOption.TITLE_DESC -> base.sortedByDescending { it.title.lowercase() }
                SongSortOption.ARTIST_ASC -> base.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
                SongSortOption.ARTIST_DESC -> base.sortedWith(compareByDescending<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
                SongSortOption.ALBUM_ASC -> base.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber ?: 0 }, { it.title.lowercase() }))
                SongSortOption.ALBUM_DESC -> base.sortedWith(compareByDescending<Track> { it.album.lowercase() }.thenBy { it.trackNumber ?: 0 }.thenBy { it.title.lowercase() })
                SongSortOption.DATE_ADDED_DESC -> base.sortedByDescending { it.dateAdded }
                SongSortOption.DATE_ADDED_ASC -> base.sortedBy { it.dateAdded }
                SongSortOption.DURATION_DESC -> base.sortedByDescending { it.durationMs }
                SongSortOption.DURATION_ASC -> base.sortedBy { it.durationMs }
                SongSortOption.YEAR_DESC -> base.sortedWith(compareByDescending<Track> { it.year ?: 0 }.thenBy { it.title.lowercase() })
                SongSortOption.YEAR_ASC -> base.sortedWith(compareBy<Track> { it.year ?: 9999 }.thenBy { it.title.lowercase() })
            }
        }

    val filteredAlbums: List<Album>
        get() {
            val base = if (searchQuery.isBlank()) {
                albums
            } else {
                albums.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
                }
            }
            return when (albumSortOption) {
                AlbumSortOption.TITLE_ASC -> base.sortedBy { it.title.lowercase() }
                AlbumSortOption.TITLE_DESC -> base.sortedByDescending { it.title.lowercase() }
                AlbumSortOption.ARTIST_ASC -> base.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
                AlbumSortOption.ARTIST_DESC -> base.sortedWith(compareByDescending<Album> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
                AlbumSortOption.YEAR_DESC -> base.sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy { it.title.lowercase() })
                AlbumSortOption.YEAR_ASC -> base.sortedWith(compareBy<Album> { it.year ?: 9999 }.thenBy { it.title.lowercase() })
                AlbumSortOption.TRACK_COUNT_DESC -> base.sortedByDescending { it.trackCount }
                AlbumSortOption.TRACK_COUNT_ASC -> base.sortedBy { it.trackCount }
            }
        }

    val filteredArtists: List<Artist>
        get() {
            val base = if (searchQuery.isBlank()) {
                artists
            } else {
                artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            return when (artistSortOption) {
                ArtistSortOption.NAME_ASC -> base.sortedBy { it.name.lowercase() }
                ArtistSortOption.NAME_DESC -> base.sortedByDescending { it.name.lowercase() }
                ArtistSortOption.TRACK_COUNT_DESC -> base.sortedByDescending { it.trackCount }
                ArtistSortOption.TRACK_COUNT_ASC -> base.sortedBy { it.trackCount }
                ArtistSortOption.ALBUM_COUNT_DESC -> base.sortedByDescending { it.albumCount }
                ArtistSortOption.ALBUM_COUNT_ASC -> base.sortedBy { it.albumCount }
            }
        }

    val filteredPlaylists: List<Playlist>
        get() {
            val base = if (searchQuery.isBlank()) {
                playlists
            } else {
                playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            return when (playlistSortOption) {
                PlaylistSortOption.NAME_ASC -> base.sortedBy { it.name.lowercase() }
                PlaylistSortOption.NAME_DESC -> base.sortedByDescending { it.name.lowercase() }
                PlaylistSortOption.TRACK_COUNT_DESC -> base.sortedByDescending { it.trackIds.size }
                PlaylistSortOption.TRACK_COUNT_ASC -> base.sortedBy { it.trackIds.size }
            }
        }

    val activePlaylistTracks: List<Track>
        get() {
            val pl = activePlaylist ?: return emptyList()
            val idSet = pl.trackIds.toSet()
            return tracks.filter { idSet.contains(it.id) }
        }

    val activeAlbumTracks: List<Track>
        get() {
            val alb = activeAlbum ?: return emptyList()
            return tracks.filter { it.album.equals(alb.title, ignoreCase = true) && it.artist.equals(alb.artist, ignoreCase = true) }
                .sortedWith(compareBy({ it.trackNumber ?: 0 }, { it.title.lowercase() }))
        }

    val activeArtistTracks: List<Track>
        get() {
            val art = activeArtist ?: return emptyList()
            return tracks.filter { it.artist.equals(art.name, ignoreCase = true) }
                .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber ?: 0 }, { it.title.lowercase() }))
        }
}

class LibraryViewModel(
    private val repository: MediaStoreAudioRepository,
    private val playlistRepository: PlaylistRepository? = null
) : ViewModel() {

    var onTrackAddedToPlaylist: ((Track, String) -> Unit)? = null
    var onPlaylistsChanged: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibrary()
        playlistRepository?.let { plRepo ->
            viewModelScope.launch {
                plRepo.playlists.collect { plList ->
                    _uiState.update { it.copy(playlists = plList) }
                }
            }
            viewModelScope.launch {
                plRepo.likedTrackIds.collect { likedSet ->
                    _uiState.update { it.copy(likedTrackIds = likedSet) }
                }
            }
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = repository.loadAllTracks()
            val albums = repository.loadAlbums()
            val artists = repository.loadArtists()

            _uiState.update {
                it.copy(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    isLoading = false
                )
            }
        }
    }

    fun setFilterChip(chip: LibraryFilterChip) {
        _uiState.update { it.copy(selectedChip = chip, activePlaylist = null) }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery) }
    }

    fun openPlaylist(playlist: Playlist?) {
        _uiState.update { it.copy(activePlaylist = playlist, activeAlbum = null, activeArtist = null) }
    }

    fun openAlbum(album: Album?) {
        _uiState.update { it.copy(activeAlbum = album, activePlaylist = null, activeArtist = null) }
    }

    fun openArtist(artist: Artist?) {
        _uiState.update { it.copy(activeArtist = artist, activeAlbum = null, activePlaylist = null) }
    }

    fun setSongSort(sort: SongSortOption) {
        _uiState.update { it.copy(songSortOption = sort) }
    }

    fun setAlbumSort(sort: AlbumSortOption) {
        _uiState.update { it.copy(albumSortOption = sort) }
    }

    fun setArtistSort(sort: ArtistSortOption) {
        _uiState.update { it.copy(artistSortOption = sort) }
    }

    fun setPlaylistSort(sort: PlaylistSortOption) {
        _uiState.update { it.copy(playlistSortOption = sort) }
    }

    fun togglePin(id: String) {
        playlistRepository?.let {
            viewModelScope.launch { it.togglePinPlaylist(id) }
        } ?: run {
            _uiState.update {
                val updated = if (it.pinnedIds.contains(id)) it.pinnedIds - id else it.pinnedIds + id
                it.copy(pinnedIds = updated)
            }
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        playlistRepository?.let {
            viewModelScope.launch {
                it.createPlaylist(name, description)
                onPlaylistsChanged?.invoke()
            }
        } ?: run {
            val newPlaylist = Playlist(
                id = "pl_${System.currentTimeMillis()}",
                name = name,
                description = description
            )
            _uiState.update { it.copy(playlists = it.playlists + newPlaylist) }
            onPlaylistsChanged?.invoke()
        }
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepository?.let {
            viewModelScope.launch {
                it.deletePlaylist(playlistId)
                if (_uiState.value.activePlaylist?.id == playlistId) {
                    _uiState.update { s -> s.copy(activePlaylist = null) }
                }
                onPlaylistsChanged?.invoke()
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        playlistRepository?.let {
            viewModelScope.launch {
                it.addTrackToPlaylist(playlistId, trackId)
                val track = _uiState.value.tracks.find { t -> t.id == trackId }
                if (track != null) {
                    onTrackAddedToPlaylist?.invoke(track, playlistId)
                }
                onPlaylistsChanged?.invoke()
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistRepository?.let {
            viewModelScope.launch {
                it.removeTrackFromPlaylist(playlistId, trackId)
                onPlaylistsChanged?.invoke()
            }
        }
    }

    fun updatePlaylistCover(playlistId: String, coverUri: String?) {
        playlistRepository?.let {
            viewModelScope.launch {
                it.updatePlaylistCover(playlistId, coverUri)
                _uiState.update { curr ->
                    val updatedActive = if (curr.activePlaylist?.id == playlistId) {
                        curr.activePlaylist.copy(customCoverUri = coverUri)
                    } else curr.activePlaylist
                    curr.copy(activePlaylist = updatedActive)
                }
                onPlaylistsChanged?.invoke()
            }
        }
    }

    fun toggleLikeTrack(trackId: String) {
        playlistRepository?.let {
            viewModelScope.launch { it.toggleLikeTrack(trackId) }
        }
    }
}
