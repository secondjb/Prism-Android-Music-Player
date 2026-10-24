package com.secondjb.prism.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondjb.prism.core.data.MediaStoreAudioRepository
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

data class LibraryUiState(
    val selectedChip: LibraryFilterChip = LibraryFilterChip.ALL_SONGS,
    val isGridView: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
) {
    val filteredTracks: List<Track>
        get() = if (searchQuery.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }

    val filteredAlbums: List<Album>
        get() = if (searchQuery.isBlank()) {
            albums
        } else {
            albums.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }

    val filteredArtists: List<Artist>
        get() = if (searchQuery.isBlank()) {
            artists
        } else {
            artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

    val filteredPlaylists: List<Playlist>
        get() = if (searchQuery.isBlank()) {
            playlists
        } else {
            playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
}

class LibraryViewModel(
    private val repository: MediaStoreAudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibrary()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = repository.loadAllTracks()
            val albums = repository.loadAlbums()
            val artists = repository.loadArtists()

            // Built-in initial playlists
            val defaultPlaylists = listOf(
                Playlist(
                    id = "favorites",
                    name = "Favorites",
                    description = "Your favorite tracks",
                    isPinned = true
                ),
                Playlist(
                    id = "hi_res",
                    name = "Hi-Res Collection",
                    description = "Lossless FLAC and 24-bit audio",
                    isPinned = true
                )
            )

            _uiState.update {
                it.copy(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    playlists = defaultPlaylists,
                    isLoading = false
                )
            }
        }
    }

    fun setFilterChip(chip: LibraryFilterChip) {
        _uiState.update { it.copy(selectedChip = chip) }
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

    fun togglePin(id: String) {
        _uiState.update {
            val updated = if (it.pinnedIds.contains(id)) {
                it.pinnedIds - id
            } else {
                it.pinnedIds + id
            }
            it.copy(pinnedIds = updated)
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        val newPlaylist = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name,
            description = description
        )
        _uiState.update { it.copy(playlists = it.playlists + newPlaylist) }
    }
}
