package com.secondjb.prism.ui.screens.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import android.util.Log
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.secondjb.prism.domain.model.Album
import com.secondjb.prism.domain.model.Artist
import com.secondjb.prism.domain.model.Playlist
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.components.FastScrollbar
import com.secondjb.prism.ui.components.getSectionHeader
import com.secondjb.prism.ui.theme.PillShape
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    hasNowPlayingBar: Boolean = false,
    onFilterSelect: (LibraryFilterChip) -> Unit,
    onToggleViewMode: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onTogglePin: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenPlaylist: (Playlist?) -> Unit = {},
    onOpenAlbum: (Album?) -> Unit = {},
    onOpenArtist: (Artist?) -> Unit = {},
    onSetSongSort: (SongSortOption) -> Unit = {},
    onSetAlbumSort: (AlbumSortOption) -> Unit = {},
    onSetArtistSort: (ArtistSortOption) -> Unit = {},
    onSetPlaylistSort: (PlaylistSortOption) -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    onAddTrackToPlaylist: (String, String) -> Unit = { _, _ -> },
    onRemoveTrackFromPlaylist: (String, String) -> Unit = { _, _ -> },
    onToggleLikeTrack: (String) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onGoToQueue: () -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onUpdatePlaylistCover: (String, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var actionTrackIsInPlaylist by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(emptySet<String>()) }
    var showMultiSelectMenu by remember { mutableStateOf(false) }
    var showBatchAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val trackArtMap = remember(uiState.tracks) { uiState.tracks.associate { it.id to it.artworkUri } }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val handleAddTrackToPlaylistWithUndo: (String, String) -> Unit = { playlistId, trackId ->
        onAddTrackToPlaylist(playlistId, trackId)
        val plName = uiState.playlists.find { it.id == playlistId }?.name ?: "Playlist"
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Added to $plName",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRemoveTrackFromPlaylist(playlistId, trackId)
            }
        }
    }

    // Intercept back gesture if in search mode
    BackHandler(enabled = uiState.isSearchActive) {
        keyboardController?.hide()
        onToggleSearch(false)
        onSearchChange("")
    }

    // Intercept back gesture if in multi-select mode
    BackHandler(enabled = selectedTrackIds.isNotEmpty()) {
        selectedTrackIds = emptySet()
    }

    val chips = remember { LibraryFilterChip.entries.toList() }
    val pagerState = rememberPagerState(
        initialPage = chips.indexOf(uiState.selectedChip).coerceAtLeast(0),
        pageCount = { chips.size }
    )
    val currentSelectedChip = chips.getOrElse(pagerState.currentPage) { uiState.selectedChip }

    // Instant chip sync on page transition without waiting for settled state
    LaunchedEffect(pagerState.currentPage) {
        val newChip = chips.getOrElse(pagerState.currentPage) { uiState.selectedChip }
        if (uiState.selectedChip != newChip) {
            onFilterSelect(newChip)
        }
        if (newChip != LibraryFilterChip.ALL_SONGS && selectedTrackIds.isNotEmpty()) {
            selectedTrackIds = emptySet()
        }
    }

    // Sync external filter chip selection to pager only when not already scrolling
    LaunchedEffect(uiState.selectedChip) {
        val targetPage = chips.indexOf(uiState.selectedChip)
        if (!pagerState.isScrollInProgress && targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Options"
                        )
                    }
                    IconButton(onClick = { onToggleSearch(!uiState.isSearchActive) }) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                    }
                }
            }

            // Inline Search Bar
            AnimatedVisibility(
                visible = uiState.isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search songs, artists, albums...") },
                    shape = PillShape,
                    singleLine = true
                )
            }

            // Material 3 Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chips) { chip ->
                    FilterChip(
                        selected = currentSelectedChip == chip,
                        onClick = {
                            coroutineScope.launch {
                                val idx = chips.indexOf(chip)
                                if (idx >= 0 && pagerState.currentPage != idx) {
                                    pagerState.animateScrollToPage(idx)
                                }
                            }
                        },
                        label = { Text(chip.label) },
                        shape = PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Controls bar: normal count & view toggle OR multi-select toolbar
            if (selectedTrackIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedTrackIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                selectedTrackIds = uiState.filteredTracks.map { it.id }.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelMedium)
                        }

                        TextButton(
                            onClick = {
                                selectedTrackIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Deselect", style = MaterialTheme.typography.labelMedium)
                        }

                        Box {
                            IconButton(
                                onClick = { showMultiSelectMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Batch Actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMultiSelectMenu,
                                onDismissRequest = { showMultiSelectMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play Next") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        showMultiSelectMenu = false
                                        val selectedTracks = uiState.filteredTracks.filter { selectedTrackIds.contains(it.id) }
                                        selectedTracks.reversed().forEach { track ->
                                            onPlayNext(track)
                                        }
                                        selectedTrackIds = emptySet()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Add to Queue") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        showMultiSelectMenu = false
                                        val selectedTracks = uiState.filteredTracks.filter { selectedTrackIds.contains(it.id) }
                                        selectedTracks.forEach { track ->
                                            onAddToQueue(track)
                                        }
                                        selectedTrackIds = emptySet()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Add to Playlist") },
                                    leadingIcon = {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        showMultiSelectMenu = false
                                        showBatchAddToPlaylistDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countText = when (currentSelectedChip) {
                        LibraryFilterChip.ALL_SONGS -> "${uiState.filteredTracks.size} songs"
                        LibraryFilterChip.PLAYLISTS -> "${uiState.filteredPlaylists.size} playlists"
                        LibraryFilterChip.ALBUMS -> "${uiState.filteredAlbums.size} albums"
                        LibraryFilterChip.ARTISTS -> "${uiState.filteredArtists.size} artists"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (currentSelectedChip == LibraryFilterChip.ALL_SONGS || currentSelectedChip == LibraryFilterChip.PLAYLISTS) {
                        IconButton(onClick = onToggleViewMode, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (uiState.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/List",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Full screen swipeable content area across all tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (chips[page]) {
                    LibraryFilterChip.ALL_SONGS -> {
                        if (uiState.isGridView) {
                            TrackGridView(
                                tracks = uiState.filteredTracks,
                                pinnedIds = uiState.pinnedIds,
                                selectedTrackIds = selectedTrackIds,
                                searchQuery = uiState.searchQuery,
                                onToggleSelectTrack = { id ->
                                    selectedTrackIds = if (selectedTrackIds.contains(id)) {
                                        selectedTrackIds - id
                                    } else {
                                        selectedTrackIds + id
                                    }
                                },
                                onTogglePin = onTogglePin,
                                onTrackClick = onTrackClick
                            )
                        } else {
                            TrackListView(
                                tracks = uiState.filteredTracks,
                                pinnedIds = uiState.pinnedIds,
                                likedTrackIds = uiState.likedTrackIds,
                                selectedTrackIds = selectedTrackIds,
                                searchQuery = uiState.searchQuery,
                                onToggleSelectTrack = { id ->
                                    selectedTrackIds = if (selectedTrackIds.contains(id)) {
                                        selectedTrackIds - id
                                    } else {
                                        selectedTrackIds + id
                                    }
                                },
                                onTogglePin = onTogglePin,
                                onToggleLike = onToggleLikeTrack,
                                onAddToPlaylist = { trackForPlaylistDialog = it },
                                onMoreClick = {
                                    actionTrack = it
                                    actionTrackIsInPlaylist = false
                                },
                                onTrackClick = onTrackClick
                            )
                        }
                    }
                    LibraryFilterChip.PLAYLISTS -> {
                        PlaylistView(
                            playlists = uiState.filteredPlaylists,
                            allTracks = uiState.tracks,
                            pinnedIds = uiState.pinnedIds,
                            isGrid = uiState.isGridView,
                            onTogglePin = onTogglePin,
                            onSelectPlaylist = onOpenPlaylist,
                            onPlayPlaylist = { pl ->
                                val plTracks = uiState.tracks.filter { pl.trackIds.contains(it.id) }
                                if (plTracks.isNotEmpty()) {
                                    onTrackClick(plTracks.first(), plTracks)
                                }
                            }
                        )
                    }
                    LibraryFilterChip.ALBUMS -> {
                        AlbumGridView(
                            albums = uiState.filteredAlbums,
                            pinnedIds = uiState.pinnedIds,
                            onTogglePin = onTogglePin,
                            onSelectAlbum = onOpenAlbum
                        )
                    }
                    LibraryFilterChip.ARTISTS -> {
                        ArtistListView(
                            artists = uiState.filteredArtists,
                            pinnedIds = uiState.pinnedIds,
                            onTogglePin = onTogglePin,
                            onSelectArtist = onOpenArtist
                        )
                    }
                }
            }
        }

        // Overlay for Playlist Detail (keeps main library composed underneath so scroll position is preserved)
        if (uiState.activePlaylist != null) {
            BackHandler {
                onOpenPlaylist(null)
            }
            Surface(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                color = MaterialTheme.colorScheme.background
            ) {
                PlaylistDetailScreen(
                    playlist = uiState.activePlaylist,
                    tracks = uiState.activePlaylistTracks,
                    onBack = { onOpenPlaylist(null) },
                    onPlayTrack = onTrackClick,
                    onPlayAll = { tracks ->
                        if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks)
                    },
                    onShuffleAll = { tracks ->
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled)
                        }
                    },
                    onRemoveTrack = { trackId ->
                        onRemoveTrackFromPlaylist(uiState.activePlaylist.id, trackId)
                    },
                    onDeletePlaylist = {
                        onDeletePlaylist(uiState.activePlaylist.id)
                    },
                    onUpdateCover = { uri ->
                        onUpdatePlaylistCover(uiState.activePlaylist.id, uri)
                    },
                    onMoreClick = { track ->
                        actionTrack = track
                        actionTrackIsInPlaylist = true
                    }
                )
            }
        }

        // Overlay for Album Detail
        if (uiState.activeAlbum != null) {
            BackHandler {
                onOpenAlbum(null)
            }
            Surface(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                color = MaterialTheme.colorScheme.background
            ) {
                AlbumDetailScreen(
                    album = uiState.activeAlbum,
                    tracks = uiState.activeAlbumTracks,
                    onBack = { onOpenAlbum(null) },
                    onPlayTrack = onTrackClick,
                    onPlayAll = { tracks ->
                        if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks)
                    },
                    onShuffleAll = { tracks ->
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled)
                        }
                    },
                    onMoreClick = { track ->
                        actionTrack = track
                        actionTrackIsInPlaylist = false
                    }
                )
            }
        }

        // Overlay for Artist Detail
        if (uiState.activeArtist != null) {
            BackHandler {
                onOpenArtist(null)
            }
            Surface(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                color = MaterialTheme.colorScheme.background
            ) {
                ArtistDetailScreen(
                    artist = uiState.activeArtist,
                    tracks = uiState.activeArtistTracks,
                    onBack = { onOpenArtist(null) },
                    onPlayTrack = onTrackClick,
                    onPlayAll = { tracks ->
                        if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks)
                    },
                    onShuffleAll = { tracks ->
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled)
                        }
                    },
                    onMoreClick = { track ->
                        actionTrack = track
                        actionTrackIsInPlaylist = false
                    }
                )
            }
        }

        val snackbarBottomPadding by animateDpAsState(
            targetValue = if (hasNowPlayingBar) 84.dp else 16.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "SnackbarPadding"
        )

        // Themed Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomPadding)
                .padding(horizontal = 24.dp)
                .zIndex(4f)
        ) { data ->
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    data.visuals.actionLabel?.let { actionLabel ->
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { data.performAction() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 24.dp)
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        LibrarySortBottomSheet(
            selectedChip = currentSelectedChip,
            songSort = uiState.songSortOption,
            albumSort = uiState.albumSortOption,
            artistSort = uiState.artistSortOption,
            playlistSort = uiState.playlistSortOption,
            onSelectSongSort = {
                onSetSongSort(it)
                showSortSheet = false
            },
            onSelectAlbumSort = {
                onSetAlbumSort(it)
                showSortSheet = false
            },
            onSelectArtistSort = {
                onSetArtistSort(it)
                showSortSheet = false
            },
            onSelectPlaylistSort = {
                onSetPlaylistSort(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    // Add Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Track to Playlist Dialog
    trackForPlaylistDialog?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = uiState.playlists,
            onDismiss = { trackForPlaylistDialog = null },
            onSelectPlaylist = { pl ->
                handleAddTrackToPlaylistWithUndo(pl.id, track.id)
                trackForPlaylistDialog = null
            },
            onCreateNewPlaylist = { name ->
                onCreatePlaylist(name)
            }
        )
    }

    actionTrack?.let { currTrack ->
        TrackActionsBottomSheet(
            track = currTrack,
            isInPlaylist = actionTrackIsInPlaylist,
            onDismiss = { actionTrack = null },
            onPlayNext = { onPlayNext(currTrack) },
            onAddToQueue = { onAddToQueue(currTrack) },
            onAddToPlaylist = { trackForPlaylistDialog = currTrack },
            onRemoveFromPlaylist = if (actionTrackIsInPlaylist && uiState.activePlaylist != null) {
                { onRemoveTrackFromPlaylist(uiState.activePlaylist.id, currTrack.id) }
            } else null,
            onGoToQueue = onGoToQueue,
            onGoToAlbum = onGoToAlbum,
            onGoToArtist = onGoToArtist
        )
    }

    if (showBatchAddToPlaylistDialog) {
        val selectedTracks = uiState.filteredTracks.filter { selectedTrackIds.contains(it.id) }
        BatchAddToPlaylistDialog(
            tracksCount = selectedTracks.size,
            playlists = uiState.playlists,
            onDismiss = { showBatchAddToPlaylistDialog = false },
            onSelectPlaylist = { pl ->
                selectedTracks.forEach { track ->
                    onAddTrackToPlaylist(pl.id, track.id)
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Added ${selectedTracks.size} tracks to ${pl.name}", duration = SnackbarDuration.Short)
                }
                showBatchAddToPlaylistDialog = false
                selectedTrackIds = emptySet()
            },
            onCreateNewPlaylist = { name ->
                onCreatePlaylist(name)
            }
        )
    }
}

@Composable
private fun PlaylistCover(
    playlist: Playlist,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    trackArtMap: Map<String, String?>? = null
) {
    val context = LocalContext.current
    if (!playlist.customCoverUri.isNullOrBlank()) {
        if (playlist.customCoverUri.startsWith("color:")) {
            val hex = playlist.customCoverUri.removePrefix("color:")
            val color = try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primaryContainer
            }
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(playlist.customCoverUri)
                    .size(256)
                    .crossfade(true)
                    .build(),
                contentDescription = playlist.name,
                modifier = modifier.clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        return
    }

    val uniqueArtworks = remember(playlist.id, playlist.trackIds, tracks, trackArtMap) {
        val arts = mutableListOf<String>()
        if (trackArtMap != null) {
            for (id in playlist.trackIds) {
                val art = trackArtMap[id]
                if (!art.isNullOrBlank() && art !in arts) {
                    arts.add(art)
                    if (arts.size == 4) break
                }
            }
        } else {
            val trackMap = tracks.associateBy { it.id }
            for (id in playlist.trackIds) {
                val art = trackMap[id]?.artworkUri
                if (!art.isNullOrBlank() && art !in arts) {
                    arts.add(art)
                    if (arts.size == 4) break
                }
            }
        }
        arts
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            uniqueArtworks.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks[0]).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks[1]).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks[2]).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks[3]).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            uniqueArtworks.size in 2..3 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks[0]).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks[1]).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks.getOrElse(2) { uniqueArtworks[0] }).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uniqueArtworks.getOrElse(3) { uniqueArtworks[1] }).size(160).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            uniqueArtworks.size == 1 -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(uniqueArtworks[0]).size(256).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyPlaylistImageToAppStorage(context: Context, uri: Uri, playlistId: String): String? {
    return try {
        val coversDir = File(context.filesDir, "playlist_covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        val destFile = File(coversDir, "pl_${playlistId}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        "file://${destFile.absolutePath}"
    } catch (e: Exception) {
        Log.e("PlaylistArt", "Failed to copy playlist image to internal storage: ${e.message}")
        null
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onUpdateCover: (String?) -> Unit = {},
    onMoreClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = copyPlaylistImageToAppStorage(context, uri, playlist.id)
            if (localPath != null) {
                onUpdateCover(localPath)
            }
        }
    }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Actions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        if (playlist.id != "favorites") {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Playlist Header with Cover + Info + Play All / Shuffle side-by-side
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(130.dp)) {
                            PlaylistCover(
                                playlist = playlist,
                                tracks = tracks,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clickable {
                                        showCoverOptionsDialog = true
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Cover",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            val totalMs = remember(tracks) { tracks.sumOf { it.durationMs } }
                            Text(
                                text = "${tracks.size} songs • ${formatTotalDuration(totalMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onPlayAll(tracks) },
                                    modifier = Modifier.weight(1f),
                                    shape = PillShape,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    enabled = tracks.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { onShuffleAll(tracks) },
                                    modifier = Modifier.weight(1f),
                                    shape = PillShape,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    enabled = tracks.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Shuffle", maxLines = 1)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (tracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks in this playlist yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(tracks, key = { it.id }) { track ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track, tracks) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onMoreClick(track) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onRemoveTrack(track.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        FastScrollbar(
            state = listState,
            itemCount = tracks.size + 1,
            sectionLabel = { index ->
                if (index == 0) null else getSectionHeader(tracks.getOrNull(index - 1)?.title)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(bottom = 120.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlist.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCoverOptionsDialog) {
        val themeColorHex = String.format("#%06X", (0xFFFFFF and MaterialTheme.colorScheme.primaryContainer.toArgb()))
        AlertDialog(
            onDismissRequest = { showCoverOptionsDialog = false },
            title = { Text("Playlist Artwork") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose how to display artwork for this playlist:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCoverOptionsDialog = false
                                imagePickerLauncher.launch("image/*")
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Upload custom image", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCoverOptionsDialog = false
                                onUpdateCover("color:$themeColorHex")
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Text("Use theme color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (!playlist.customCoverUri.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCoverOptionsDialog = false
                                    onUpdateCover(null)
                                }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                Text("Reset to default collage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCoverOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: (String) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${track.title} • ${track.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))

                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(playlists) { pl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(pl) }
                                    .padding(vertical = 4.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(text = pl.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreateNewPlaylist(newName.trim())
                            isCreatingNew = false
                            newName = ""
                        }
                    }
                ) {
                    Text("Create & Add")
                }
            } else {
                TextButton(onClick = { isCreatingNew = true }) {
                    Text("New Playlist")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListView(
    tracks: List<Track>,
    pinnedIds: Set<String>,
    likedTrackIds: Set<String> = emptySet(),
    selectedTrackIds: Set<String> = emptySet(),
    searchQuery: String = "",
    onToggleSelectTrack: (String) -> Unit = {},
    onTogglePin: (String) -> Unit,
    onToggleLike: (String) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onMoreClick: (Track) -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit
) {
    val sortedTracks = remember(tracks, pinnedIds) {
        tracks.sortedByDescending { pinnedIds.contains(it.id) }
    }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedTracks, key = { it.id }) { track ->
                val isPinned = pinnedIds.contains(track.id)
                val isLiked = likedTrackIds.contains(track.id)
                val isSelected = selectedTrackIds.contains(track.id)
                val isMultiSelectMode = selectedTrackIds.isNotEmpty()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.medium
                                )
                            } else Modifier
                        )
                        .combinedClickable(
                            onClick = {
                                if (isMultiSelectMode) {
                                    onToggleSelectTrack(track.id)
                                } else {
                                    onTrackClick(track, tracks)
                                }
                            },
                            onLongClick = {
                                onToggleSelectTrack(track.id)
                            }
                        ),
                    shape = MaterialTheme.shapes.medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        isPinned -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }

                        // Like Toggle
                        IconButton(
                            onClick = { onToggleLike(track.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Add to Playlist Button
                        IconButton(
                            onClick = { onMoreClick(track) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Add to playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        FastScrollbar(
            state = listState,
            itemCount = sortedTracks.size,
            sectionLabel = { index -> getSectionHeader(sortedTracks.getOrNull(index)?.title) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, bottom = 120.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackGridView(
    tracks: List<Track>,
    pinnedIds: Set<String>,
    selectedTrackIds: Set<String> = emptySet(),
    searchQuery: String = "",
    onToggleSelectTrack: (String) -> Unit = {},
    onTogglePin: (String) -> Unit,
    onMoreClick: (Track) -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit
) {
    val sortedTracks = remember(tracks, pinnedIds) {
        tracks.sortedByDescending { pinnedIds.contains(it.id) }
    }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(sortedTracks, key = { it.id }) { track ->
                val isPinned = pinnedIds.contains(track.id)
                val isSelected = selectedTrackIds.contains(track.id)
                val isMultiSelectMode = selectedTrackIds.isNotEmpty()

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.large
                                )
                            } else Modifier
                        )
                        .combinedClickable(
                            onClick = {
                                if (isMultiSelectMode) {
                                    onToggleSelectTrack(track.id)
                                } else {
                                    onTrackClick(track, tracks)
                                }
                            },
                            onLongClick = {
                                onToggleSelectTrack(track.id)
                            }
                        ),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            isPinned -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { onTogglePin(track.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .zIndex(1f)
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onMoreClick(track) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        FastScrollbar(
            state = gridState,
            itemCount = sortedTracks.size,
            sectionLabel = { index -> getSectionHeader(sortedTracks.getOrNull(index)?.title) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, bottom = 120.dp)
        )
    }
}

@Composable
private fun PlaylistView(
    playlists: List<Playlist>,
    allTracks: List<Track>,
    pinnedIds: Set<String>,
    isGrid: Boolean,
    onTogglePin: (String) -> Unit,
    onSelectPlaylist: (Playlist) -> Unit = {},
    onPlayPlaylist: (Playlist) -> Unit = {}
) {
    if (isGrid) {
        val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                PlaylistCover(
                                    playlist = playlist,
                                    tracks = allTracks,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.trackIds.size} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onPlayPlaylist(playlist) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Playlist",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FastScrollbar(
                state = gridState,
                itemCount = playlists.size,
                sectionLabel = { index -> getSectionHeader(playlists.getOrNull(index)?.name) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, bottom = 120.dp)
            )
        }
    } else {
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(50.dp)) {
                                PlaylistCover(
                                    playlist = playlist,
                                    tracks = allTracks,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.trackIds.size} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onPlayPlaylist(playlist) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Playlist",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            FastScrollbar(
                state = listState,
                itemCount = playlists.size,
                sectionLabel = { index -> getSectionHeader(playlists.getOrNull(index)?.name) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, bottom = 120.dp)
            )
        }
    }
}

@Composable
private fun AlbumGridView(
    albums: List<Album>,
    pinnedIds: Set<String>,
    onTogglePin: (String) -> Unit,
    onSelectAlbum: (Album) -> Unit = {}
) {
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAlbum(album) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = album.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        FastScrollbar(
            state = gridState,
            itemCount = albums.size,
            sectionLabel = { index -> getSectionHeader(albums.getOrNull(index)?.title) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, bottom = 120.dp)
        )
    }
}

@Composable
private fun ArtistListView(
    artists: List<Artist>,
    pinnedIds: Set<String>,
    onTogglePin: (String) -> Unit,
    onSelectArtist: (Artist) -> Unit = {}
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectArtist(artist) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            if (!artist.artworkUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = artist.artworkUri,
                                    contentDescription = artist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = artist.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${artist.trackCount} tracks • ${artist.albumCount} albums",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        FastScrollbar(
            state = listState,
            itemCount = artists.size,
            sectionLabel = { index -> getSectionHeader(artists.getOrNull(index)?.name) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, bottom = 120.dp)
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSecs = durationMs / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatTotalDuration(durationMs: Long): String {
    val totalSecs = durationMs / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    return when {
        hours > 0 -> "${hours} hr ${minutes} min"
        minutes > 0 -> "${minutes} min"
        else -> "${totalSecs} sec"
    }
}

@Composable
private fun BatchAddToPlaylistDialog(
    tracksCount: Int,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: (String) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Add $tracksCount songs to playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(playlists) { pl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(pl) }
                                    .padding(vertical = 4.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(text = pl.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreateNewPlaylist(newName.trim())
                            isCreatingNew = false
                            newName = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            } else {
                TextButton(onClick = { isCreatingNew = true }) {
                    Text("New Playlist")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AlbumDetailScreen(
    album: Album,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Back Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }

                    // Album Info Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = album.artworkUri,
                                contentDescription = album.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val infoText = buildString {
                            val yr = album.year ?: 0
                            if (yr > 0) append("$yr • ")
                            append("${tracks.size} songs")
                        }
                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Play All / Shuffle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayAll(tracks) },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }

                        FilledTonalButton(
                            onClick = { onShuffleAll(tracks) },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (tracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks found in this album.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(tracks, key = { it.id }) { track ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track, tracks) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatDuration(track.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onMoreClick(track) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        FastScrollbar(
            state = listState,
            itemCount = tracks.size + 1,
            sectionLabel = { index ->
                if (index == 0) null else getSectionHeader(tracks.getOrNull(index - 1)?.title)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(bottom = 120.dp)
        )
    }
}

@Composable
private fun ArtistDetailScreen(
    artist: Artist,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onMoreClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Back Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }

                    // Artist Info Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(110.dp)
                        ) {
                            if (!artist.artworkUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = artist.artworkUri,
                                    contentDescription = artist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = artist.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${tracks.size} songs • ${artist.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Play All / Shuffle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayAll(tracks) },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }

                        FilledTonalButton(
                            onClick = { onShuffleAll(tracks) },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (tracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks found for this artist.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(tracks, key = { it.id }) { track ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track, tracks) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.album} • ${formatDuration(track.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onMoreClick(track) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        FastScrollbar(
            state = listState,
            itemCount = tracks.size + 1,
            sectionLabel = { index ->
                if (index == 0) null else getSectionHeader(tracks.getOrNull(index - 1)?.title)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(bottom = 120.dp)
        )
    }
}

@Composable
private fun SortCategoryItem(
    label: String,
    isSelected: Boolean,
    directionLabel: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isSelected && directionLabel != null) {
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = directionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySortBottomSheet(
    selectedChip: LibraryFilterChip,
    songSort: SongSortOption,
    albumSort: AlbumSortOption,
    artistSort: ArtistSortOption,
    playlistSort: PlaylistSortOption,
    onSelectSongSort: (SongSortOption) -> Unit,
    onSelectAlbumSort: (AlbumSortOption) -> Unit,
    onSelectArtistSort: (ArtistSortOption) -> Unit,
    onSelectPlaylistSort: (PlaylistSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when (selectedChip) {
                LibraryFilterChip.ALL_SONGS -> {
                    SortCategoryItem(
                        label = "Title",
                        isSelected = songSort == SongSortOption.TITLE_ASC || songSort == SongSortOption.TITLE_DESC,
                        directionLabel = if (songSort == SongSortOption.TITLE_ASC) "A → Z  ↑" else if (songSort == SongSortOption.TITLE_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectSongSort(
                                if (songSort == SongSortOption.TITLE_ASC) SongSortOption.TITLE_DESC else SongSortOption.TITLE_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Artist",
                        isSelected = songSort == SongSortOption.ARTIST_ASC || songSort == SongSortOption.ARTIST_DESC,
                        directionLabel = if (songSort == SongSortOption.ARTIST_ASC) "A → Z  ↑" else if (songSort == SongSortOption.ARTIST_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectSongSort(
                                if (songSort == SongSortOption.ARTIST_ASC) SongSortOption.ARTIST_DESC else SongSortOption.ARTIST_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Album",
                        isSelected = songSort == SongSortOption.ALBUM_ASC || songSort == SongSortOption.ALBUM_DESC,
                        directionLabel = if (songSort == SongSortOption.ALBUM_ASC) "A → Z  ↑" else if (songSort == SongSortOption.ALBUM_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectSongSort(
                                if (songSort == SongSortOption.ALBUM_ASC) SongSortOption.ALBUM_DESC else SongSortOption.ALBUM_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Date Added",
                        isSelected = songSort == SongSortOption.DATE_ADDED_DESC || songSort == SongSortOption.DATE_ADDED_ASC,
                        directionLabel = if (songSort == SongSortOption.DATE_ADDED_DESC) "Newest  ↓" else if (songSort == SongSortOption.DATE_ADDED_ASC) "Oldest  ↑" else null,
                        onClick = {
                            onSelectSongSort(
                                if (songSort == SongSortOption.DATE_ADDED_DESC) SongSortOption.DATE_ADDED_ASC else SongSortOption.DATE_ADDED_DESC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Duration",
                        isSelected = songSort == SongSortOption.DURATION_DESC || songSort == SongSortOption.DURATION_ASC,
                        directionLabel = if (songSort == SongSortOption.DURATION_DESC) "Longest  ↓" else if (songSort == SongSortOption.DURATION_ASC) "Shortest  ↑" else null,
                        onClick = {
                            onSelectSongSort(
                                if (songSort == SongSortOption.DURATION_DESC) SongSortOption.DURATION_ASC else SongSortOption.DURATION_DESC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Year",
                        isSelected = songSort == SongSortOption.YEAR_DESC || songSort == SongSortOption.YEAR_ASC,
                        directionLabel = if (songSort == SongSortOption.YEAR_DESC) "Newest  ↓" else if (songSort == SongSortOption.YEAR_ASC) "Oldest  ↑" else null,
                        onClick = {
                            onSelectSongSort(
                                if (songSort == SongSortOption.YEAR_DESC) SongSortOption.YEAR_ASC else SongSortOption.YEAR_DESC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Device Family Custom Order",
                        isSelected = songSort == SongSortOption.DEVICE_FAMILY_ORDER,
                        directionLabel = if (songSort == SongSortOption.DEVICE_FAMILY_ORDER) "PC Order (1-1000)" else null,
                        onClick = {
                            onSelectSongSort(SongSortOption.DEVICE_FAMILY_ORDER)
                        }
                    )
                }
                LibraryFilterChip.ALBUMS -> {
                    SortCategoryItem(
                        label = "Album",
                        isSelected = albumSort == AlbumSortOption.TITLE_ASC || albumSort == AlbumSortOption.TITLE_DESC,
                        directionLabel = if (albumSort == AlbumSortOption.TITLE_ASC) "A → Z  ↑" else if (albumSort == AlbumSortOption.TITLE_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectAlbumSort(
                                if (albumSort == AlbumSortOption.TITLE_ASC) AlbumSortOption.TITLE_DESC else AlbumSortOption.TITLE_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Artist",
                        isSelected = albumSort == AlbumSortOption.ARTIST_ASC || albumSort == AlbumSortOption.ARTIST_DESC,
                        directionLabel = if (albumSort == AlbumSortOption.ARTIST_ASC) "A → Z  ↑" else if (albumSort == AlbumSortOption.ARTIST_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectAlbumSort(
                                if (albumSort == AlbumSortOption.ARTIST_ASC) AlbumSortOption.ARTIST_DESC else AlbumSortOption.ARTIST_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Year",
                        isSelected = albumSort == AlbumSortOption.YEAR_DESC || albumSort == AlbumSortOption.YEAR_ASC,
                        directionLabel = if (albumSort == AlbumSortOption.YEAR_DESC) "Newest  ↓" else if (albumSort == AlbumSortOption.YEAR_ASC) "Oldest  ↑" else null,
                        onClick = {
                            onSelectAlbumSort(
                                if (albumSort == AlbumSortOption.YEAR_DESC) AlbumSortOption.YEAR_ASC else AlbumSortOption.YEAR_DESC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Tracks",
                        isSelected = albumSort == AlbumSortOption.TRACK_COUNT_DESC || albumSort == AlbumSortOption.TRACK_COUNT_ASC,
                        directionLabel = if (albumSort == AlbumSortOption.TRACK_COUNT_DESC) "Most  ↓" else if (albumSort == AlbumSortOption.TRACK_COUNT_ASC) "Least  ↑" else null,
                        onClick = {
                            onSelectAlbumSort(
                                if (albumSort == AlbumSortOption.TRACK_COUNT_DESC) AlbumSortOption.TRACK_COUNT_ASC else AlbumSortOption.TRACK_COUNT_DESC
                            )
                        }
                    )
                }
                LibraryFilterChip.ARTISTS -> {
                    SortCategoryItem(
                        label = "Artist",
                        isSelected = artistSort == ArtistSortOption.NAME_ASC || artistSort == ArtistSortOption.NAME_DESC,
                        directionLabel = if (artistSort == ArtistSortOption.NAME_ASC) "A → Z  ↑" else if (artistSort == ArtistSortOption.NAME_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectArtistSort(
                                if (artistSort == ArtistSortOption.NAME_ASC) ArtistSortOption.NAME_DESC else ArtistSortOption.NAME_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Tracks",
                        isSelected = artistSort == ArtistSortOption.TRACK_COUNT_DESC || artistSort == ArtistSortOption.TRACK_COUNT_ASC,
                        directionLabel = if (artistSort == ArtistSortOption.TRACK_COUNT_DESC) "Most  ↓" else if (artistSort == ArtistSortOption.TRACK_COUNT_ASC) "Least  ↑" else null,
                        onClick = {
                            onSelectArtistSort(
                                if (artistSort == ArtistSortOption.TRACK_COUNT_DESC) ArtistSortOption.TRACK_COUNT_ASC else ArtistSortOption.TRACK_COUNT_DESC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Albums",
                        isSelected = artistSort == ArtistSortOption.ALBUM_COUNT_DESC || artistSort == ArtistSortOption.ALBUM_COUNT_ASC,
                        directionLabel = if (artistSort == ArtistSortOption.ALBUM_COUNT_DESC) "Most  ↓" else if (artistSort == ArtistSortOption.ALBUM_COUNT_ASC) "Least  ↑" else null,
                        onClick = {
                            onSelectArtistSort(
                                if (artistSort == ArtistSortOption.ALBUM_COUNT_DESC) ArtistSortOption.ALBUM_COUNT_ASC else ArtistSortOption.ALBUM_COUNT_DESC
                            )
                        }
                    )
                }
                LibraryFilterChip.PLAYLISTS -> {
                    SortCategoryItem(
                        label = "Name",
                        isSelected = playlistSort == PlaylistSortOption.NAME_ASC || playlistSort == PlaylistSortOption.NAME_DESC,
                        directionLabel = if (playlistSort == PlaylistSortOption.NAME_ASC) "A → Z  ↑" else if (playlistSort == PlaylistSortOption.NAME_DESC) "Z → A  ↓" else null,
                        onClick = {
                            onSelectPlaylistSort(
                                if (playlistSort == PlaylistSortOption.NAME_ASC) PlaylistSortOption.NAME_DESC else PlaylistSortOption.NAME_ASC
                            )
                        }
                    )
                    SortCategoryItem(
                        label = "Tracks",
                        isSelected = playlistSort == PlaylistSortOption.TRACK_COUNT_DESC || playlistSort == PlaylistSortOption.TRACK_COUNT_ASC,
                        directionLabel = if (playlistSort == PlaylistSortOption.TRACK_COUNT_DESC) "Most  ↓" else if (playlistSort == PlaylistSortOption.TRACK_COUNT_ASC) "Least  ↑" else null,
                        onClick = {
                            onSelectPlaylistSort(
                                if (playlistSort == PlaylistSortOption.TRACK_COUNT_DESC) PlaylistSortOption.TRACK_COUNT_ASC else PlaylistSortOption.TRACK_COUNT_DESC
                            )
                        }
                    )
                }
            }
        }
    }
}
