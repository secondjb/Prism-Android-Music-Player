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

    val activePlaylistTracks: List<Track>
        get() {
            val pl = activePlaylist ?: return emptyList()
            val idSet = pl.trackIds.toSet()
            return tracks.filter { idSet.contains(it.id) }
        }
}

class LibraryViewModel(
    private val repository: MediaStoreAudioRepository,
    private val playlistRepository: PlaylistRepository? = null
) : ViewModel() {

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
        _uiState.update { it.copy(activePlaylist = playlist) }
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
            viewModelScope.launch { it.createPlaylist(name, description) }
        } ?: run {
            val newPlaylist = Playlist(
                id = "pl_${System.currentTimeMillis()}",
                name = name,
                description = description
            )
            _uiState.update { it.copy(playlists = it.playlists + newPlaylist) }
        }
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepository?.let {
            viewModelScope.launch {
                it.deletePlaylist(playlistId)
                if (_uiState.value.activePlaylist?.id == playlistId) {
                    _uiState.update { s -> s.copy(activePlaylist = null) }
                }
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        playlistRepository?.let {
            viewModelScope.launch { it.addTrackToPlaylist(playlistId, trackId) }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistRepository?.let {
            viewModelScope.launch { it.removeTrackFromPlaylist(playlistId, trackId) }
        }
    }

    fun toggleLikeTrack(trackId: String) {
        playlistRepository?.let {
            viewModelScope.launch { it.toggleLikeTrack(trackId) }
        }
    }
}
