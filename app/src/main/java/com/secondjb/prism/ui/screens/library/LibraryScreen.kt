package com.secondjb.prism.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import com.secondjb.prism.ui.components.FastScrollbar
import com.secondjb.prism.ui.components.getSectionHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.secondjb.prism.domain.model.Album
import com.secondjb.prism.domain.model.Artist
import com.secondjb.prism.domain.model.Playlist
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.theme.PillShape

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onFilterSelect: (LibraryFilterChip) -> Unit,
    onToggleViewMode: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onTogglePin: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenPlaylist: (Playlist?) -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    onAddTrackToPlaylist: (String, String) -> Unit = { _, _ -> },
    onRemoveTrackFromPlaylist: (String, String) -> Unit = { _, _ -> },
    onToggleLikeTrack: (String) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onGoToQueue: () -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
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

    // Intercept back gesture if in multi-select mode
    BackHandler(enabled = selectedTrackIds.isNotEmpty()) {
        selectedTrackIds = emptySet()
    }

    // If viewing a single playlist detail
    if (uiState.activePlaylist != null) {
        BackHandler {
            onOpenPlaylist(null)
        }
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
            onMoreClick = { track ->
                actionTrack = track
                actionTrackIsInPlaylist = true
            },
            modifier = modifier
        )
        if (actionTrack != null) {
            val currTrack = actionTrack!!
            TrackActionsBottomSheet(
                track = currTrack,
                isInPlaylist = actionTrackIsInPlaylist,
                onDismiss = { actionTrack = null },
                onPlayNext = { onPlayNext(currTrack) },
                onAddToQueue = { onAddToQueue(currTrack) },
                onAddToPlaylist = { trackForPlaylistDialog = currTrack },
                onRemoveFromPlaylist = { onRemoveTrackFromPlaylist(uiState.activePlaylist.id, currTrack.id) },
                onGoToQueue = onGoToQueue,
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist
            )
        }
        return
    }

    Column(
        modifier = modifier
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                placeholder = { Text("Search songs, artists, albums...") },
                shape = PillShape,
                singleLine = true
            )
        }

        val chips = remember { LibraryFilterChip.entries.toList() }
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(
            initialPage = chips.indexOf(uiState.selectedChip).coerceAtLeast(0),
            pageCount = { chips.size }
        )

        // Sync pager swipes with uiState filter chip when page settles
        androidx.compose.runtime.LaunchedEffect(pagerState.settledPage) {
            val newChip = chips[pagerState.settledPage]
            if (uiState.selectedChip != newChip) {
                onFilterSelect(newChip)
            }
            if (newChip != LibraryFilterChip.ALL_SONGS && selectedTrackIds.isNotEmpty()) {
                selectedTrackIds = emptySet()
            }
        }

        // Sync external filter chip selection to pager only when not already scrolling
        androidx.compose.runtime.LaunchedEffect(uiState.selectedChip) {
            val targetPage = chips.indexOf(uiState.selectedChip)
            if (!pagerState.isScrollInProgress && targetPage >= 0 && pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(targetPage)
            }
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
                    selected = uiState.selectedChip == chip,
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
                val countText = when (uiState.selectedChip) {
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

                if (uiState.selectedChip == LibraryFilterChip.ALL_SONGS || uiState.selectedChip == LibraryFilterChip.PLAYLISTS) {
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
                        pinnedIds = uiState.pinnedIds,
                        isGrid = uiState.isGridView,
                        onTogglePin = onTogglePin,
                        onSelectPlaylist = onOpenPlaylist
                    )
                }
                LibraryFilterChip.ALBUMS -> {
                    AlbumGridView(
                        albums = uiState.filteredAlbums,
                        pinnedIds = uiState.pinnedIds,
                        onTogglePin = onTogglePin
                    )
                }
                LibraryFilterChip.ARTISTS -> {
                    ArtistListView(
                        artists = uiState.filteredArtists,
                        pinnedIds = uiState.pinnedIds,
                        onTogglePin = onTogglePin
                    )
                }
            }
        }
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
                onAddTrackToPlaylist(pl.id, track.id)
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
            onRemoveFromPlaylist = null,
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
private fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onMoreClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 4.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tracks.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (playlist.id != "favorites") {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Play All / Shuffle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tracks in this playlist yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    itemCount = tracks.size,
                    sectionLabel = { index -> getSectionHeader(tracks.getOrNull(index)?.title) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(bottom = 120.dp)
                )
            }
        }
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
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
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
    val listState = rememberLazyListState()

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
    onToggleSelectTrack: (String) -> Unit = {},
    onTogglePin: (String) -> Unit,
    onMoreClick: (Track) -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit
) {
    val sortedTracks = remember(tracks, pinnedIds) {
        tracks.sortedByDescending { pinnedIds.contains(it.id) }
    }
    val gridState = rememberLazyGridState()

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
    pinnedIds: Set<String>,
    isGrid: Boolean,
    onTogglePin: (String) -> Unit,
    onSelectPlaylist: (Playlist) -> Unit = {}
) {
    if (isGrid) {
        val gridState = rememberLazyGridState()
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${playlist.trackIds.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
        val listState = rememberLazyListState()
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
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${playlist.trackIds.size} songs",
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
    onTogglePin: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
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
                    modifier = Modifier.fillMaxWidth()
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
    onTogglePin: (String) -> Unit
) {
    val listState = rememberLazyListState()
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
                    modifier = Modifier.fillMaxWidth()
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
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = artist.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
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
