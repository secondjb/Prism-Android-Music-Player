package com.secondjb.prism.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.secondjb.prism.domain.model.Track
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class QueueEntry(
    val stableId: String,
    val track: Track
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    currentTrack: Track? = null,
    userQueue: List<Track> = emptyList(),
    contextQueue: List<Track> = emptyList(),
    currentIndex: Int = -1,
    onSelectIndex: (Int) -> Unit = {},
    onSelectUserQueueIndex: (Int) -> Unit = {},
    onRemoveUserQueueIndex: (Int) -> Unit = {},
    onRemoveIndex: (Int) -> Unit = {},
    onReorderUserQueue: (Int, Int) -> Unit = { _, _ -> },
    onReorderContextQueue: (Int, Int) -> Unit = { _, _ -> },
    onReshuffleUpcoming: () -> Unit = {},
    onClearQueue: () -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val upcomingContextTracks = if (currentIndex in contextQueue.indices) {
        contextQueue.drop(currentIndex + 1)
    } else {
        contextQueue
    }
    val totalUpcoming = userQueue.size + upcomingContextTracks.size
    val userQueueTotalSecs = userQueue.sumOf { it.durationMs } / 1000L

    var localUserQueue by remember {
        val countMap = mutableMapOf<String, Int>()
        mutableStateOf(userQueue.map { track ->
            val c = countMap.getOrDefault(track.id, 0)
            countMap[track.id] = c + 1
            QueueEntry("uq_${track.id}_$c", track)
        })
    }
    var localContextTracks by remember {
        val countMap = mutableMapOf<String, Int>()
        mutableStateOf(upcomingContextTracks.map { track ->
            val c = countMap.getOrDefault(track.id, 0)
            countMap[track.id] = c + 1
            QueueEntry("cq_${track.id}_$c", track)
        })
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState

        if (fromKey.startsWith("uq_") && toKey.startsWith("uq_")) {
            val fromIdx = localUserQueue.indexOfFirst { it.stableId == fromKey }
            val toIdx = localUserQueue.indexOfFirst { it.stableId == toKey }
            if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                localUserQueue = localUserQueue.toMutableList().apply {
                    add(toIdx, removeAt(fromIdx))
                }
                onReorderUserQueue(fromIdx, toIdx)
            }
        } else if (fromKey.startsWith("cq_") && toKey.startsWith("cq_")) {
            val fromOffset = localContextTracks.indexOfFirst { it.stableId == fromKey }
            val toOffset = localContextTracks.indexOfFirst { it.stableId == toKey }
            if (fromOffset != -1 && toOffset != -1 && fromOffset != toOffset) {
                localContextTracks = localContextTracks.toMutableList().apply {
                    add(toOffset, removeAt(fromOffset))
                }
                onReorderContextQueue(fromOffset, toOffset)
            }
        }
    }

    // Only sync from upstream props when user is NOT actively dragging items!
    LaunchedEffect(userQueue) {
        if (!reorderableState.isAnyItemDragging) {
            if (localUserQueue.map { it.track.id } != userQueue.map { it.id }) {
                val countMap = mutableMapOf<String, Int>()
                localUserQueue = userQueue.map { track ->
                    val c = countMap.getOrDefault(track.id, 0)
                    countMap[track.id] = c + 1
                    QueueEntry("uq_${track.id}_$c", track)
                }
            }
        }
    }

    LaunchedEffect(upcomingContextTracks) {
        if (!reorderableState.isAnyItemDragging) {
            if (localContextTracks.map { it.track.id } != upcomingContextTracks.map { it.id }) {
                val countMap = mutableMapOf<String, Int>()
                localContextTracks = upcomingContextTracks.map { track ->
                    val c = countMap.getOrDefault(track.id, 0)
                    countMap[track.id] = c + 1
                    QueueEntry("cq_${track.id}_$c", track)
                }
            }
        }
    }

    fun formatDurationSecs(totalSecs: Long): String {
        if (totalSecs <= 0) return "0s"
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins == 0L) "${secs}s" else if (secs == 0L) "${mins}m" else "${mins}m ${secs}s"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Play Queue",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalUpcoming track${if (totalUpcoming != 1) "s" else ""} next",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (upcomingContextTracks.size > 1) {
                        IconButton(onClick = onReshuffleUpcoming) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Reshuffle Play Queue",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (userQueue.isNotEmpty() || contextQueue.isNotEmpty()) {
                        IconButton(onClick = onClearQueue) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear Queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Queue List
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Now Playing Section
                if (currentTrack != null) {
                    item(key = "header_now_playing") {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                    item(key = "track_${currentTrack.id}_current") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .padding(end = 8.dp)
                                )

                                AsyncImage(
                                    model = currentTrack.artworkUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Next Up Section (Priority User Queue)
                if (localUserQueue.isNotEmpty()) {
                    item(key = "header_user_queue") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp, bottom = 2.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT UP (${localUserQueue.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = formatDurationSecs(userQueueTotalSecs),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(localUserQueue, key = { it.stableId }) { entry ->
                        val idx = localUserQueue.indexOf(entry)
                        QueueTrackRow(
                            entry = entry,
                            isUserQueue = true,
                            reorderableState = reorderableState,
                            canMoveUp = idx > 0,
                            canMoveDown = idx < localUserQueue.lastIndex,
                            onClick = {
                                if (idx in userQueue.indices) onSelectUserQueueIndex(idx)
                            },
                            onDismissEndToStart = {
                                localUserQueue = localUserQueue.filter { it.stableId != entry.stableId }
                                if (idx in userQueue.indices) onRemoveUserQueueIndex(idx)
                            },
                            onDismissStartToEnd = {
                                onPlayNext(entry.track)
                            },
                            onPlayNext = { onPlayNext(entry.track) },
                            onAddToQueue = { onAddToQueue(entry.track) },
                            onMoveUp = { onReorderUserQueue(idx, idx - 1) },
                            onMoveDown = { onReorderUserQueue(idx, idx + 1) },
                            onMoveToTop = { onReorderUserQueue(idx, 0) },
                            onMoveToBottom = { onReorderUserQueue(idx, localUserQueue.lastIndex) },
                            onRemove = { onRemoveUserQueueIndex(idx) }
                        )
                    }
                }

                // 3. Upcoming Context Queue Section
                if (localContextTracks.isNotEmpty()) {
                    item(key = "header_context_queue") {
                        Text(
                            text = "NEXT FROM PLAYLIST / ALBUM",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }

                    items(localContextTracks, key = { it.stableId }) { entry ->
                        val offset = localContextTracks.indexOf(entry)
                        val targetContextIndex = currentIndex + 1 + offset
                        QueueTrackRow(
                            entry = entry,
                            isUserQueue = false,
                            reorderableState = reorderableState,
                            canMoveUp = offset > 0,
                            canMoveDown = offset < localContextTracks.lastIndex,
                            onClick = {
                                onSelectIndex(targetContextIndex)
                            },
                            onDismissEndToStart = {
                                localContextTracks = localContextTracks.filter { it.stableId != entry.stableId }
                                onRemoveIndex(targetContextIndex)
                            },
                            onDismissStartToEnd = {
                                onAddToQueue(entry.track)
                            },
                            onPlayNext = { onPlayNext(entry.track) },
                            onAddToQueue = { onAddToQueue(entry.track) },
                            onMoveUp = { onReorderContextQueue(offset, offset - 1) },
                            onMoveDown = { onReorderContextQueue(offset, offset + 1) },
                            onMoveToTop = { onReorderContextQueue(offset, 0) },
                            onMoveToBottom = { onReorderContextQueue(offset, localContextTracks.lastIndex) },
                            onRemove = { onRemoveIndex(targetContextIndex) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.QueueTrackRow(
    entry: QueueEntry,
    isUserQueue: Boolean,
    reorderableState: ReorderableLazyListState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onDismissEndToStart: () -> Unit,
    onDismissStartToEnd: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit
) {
    val track = entry.track
    var hasHandledRightSwipe by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDismissEndToStart()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!hasHandledRightSwipe) {
                        hasHandledRightSwipe = true
                        onDismissStartToEnd()
                    }
                    false // Smoothly snap back without dismissing the row
                }
                SwipeToDismissBoxValue.Settled -> {
                    hasHandledRightSwipe = false
                    false
                }
            }
        }
    )

    // Reset guard when settled
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
            hasHandledRightSwipe = false
        }
    }

    ReorderableItem(reorderableState, key = entry.stableId) { isDragging ->
        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                DismissBackground(dismissState)
            }
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isDragging) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else if (isUserQueue) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shadowElevation = elevation,
                tonalElevation = elevation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Far left: Drag Handle using draggableHandle modifier
                    IconButton(
                        modifier = Modifier.draggableHandle(),
                        onClick = {}
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Artwork & Track info (clickable to play)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onClick)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isUserQueue) FontWeight.Medium else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
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
                    }

                    // 3-dot overflow menu
                    QueueItemMenu(
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onMoveToTop = onMoveToTop,
                        onMoveToBottom = onMoveToBottom,
                        onRemove = onRemove
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val direction = dismissState.dismissDirection
    val isSettled = dismissState.currentValue == SwipeToDismissBoxValue.Settled && dismissState.targetValue == SwipeToDismissBoxValue.Settled
    val color by animateColorAsState(
        when {
            isSettled -> Color.Transparent
            direction == SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            direction == SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        label = "dismissBackground"
    )
    val alignment = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }
    val icon = when {
        isSettled -> null
        direction == SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.DeleteOutline
        direction == SwipeToDismissBoxValue.StartToEnd -> Icons.AutoMirrored.Filled.QueueMusic
        else -> null
    }
    val iconTint = when {
        isSettled -> Color.Transparent
        direction == SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
        direction == SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .padding(horizontal = 16.dp),
        contentAlignment = alignment
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QueueItemMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play Next") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onPlayNext()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Add to Queue") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onAddToQueue()
                    showMenu = false
                }
            )
            HorizontalDivider()
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text("Move Up") },
                    onClick = {
                        onMoveUp()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to Top") },
                    onClick = {
                        onMoveToTop()
                        showMenu = false
                    }
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text("Move Down") },
                    onClick = {
                        onMoveDown()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to Bottom") },
                    onClick = {
                        onMoveToBottom()
                        showMenu = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove from Queue", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onRemove()
                    showMenu = false
                }
            )
        }
    }
}
