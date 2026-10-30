package com.secondjb.prism.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.secondjb.prism.domain.model.Track

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

    var draggingTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 60.dp.toPx() }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
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
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 4.dp)
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
                if (userQueue.isNotEmpty()) {
                    item(key = "header_user_queue") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp, bottom = 2.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT UP (${userQueue.size})",
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

                    itemsIndexed(userQueue, key = { idx, t -> "uq_${t.id}_$idx" }) { idx, track ->
                        val isItemDragging = draggingTrackId == track.id
                        val itemTranslation = if (isItemDragging) dragOffsetY else 0f
                        val itemZ = if (isItemDragging) 10f else 1f

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                when (dismissValue) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        onRemoveUserQueueIndex(idx)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        onPlayNext(track)
                                        false
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .zIndex(itemZ)
                                .graphicsLayer { translationY = itemTranslation }
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    DismissBackground(dismissState.targetValue)
                                }
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isItemDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Drag handle
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .pointerInput(track.id) {
                                                    detectVerticalDragGestures(
                                                        onDragStart = {
                                                            draggingTrackId = track.id
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggingTrackId = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingTrackId = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onVerticalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY += dragAmount
                                                            val currIdx = userQueue.indexOfFirst { it.id == track.id }
                                                            if (currIdx != -1) {
                                                                if (dragOffsetY > itemHeightPx && currIdx < userQueue.lastIndex) {
                                                                    onReorderUserQueue(currIdx, currIdx + 1)
                                                                    dragOffsetY -= itemHeightPx
                                                                } else if (dragOffsetY < -itemHeightPx && currIdx > 0) {
                                                                    onReorderUserQueue(currIdx, currIdx - 1)
                                                                    dragOffsetY += itemHeightPx
                                                                }
                                                            }
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = if (isItemDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable { onSelectUserQueueIndex(idx) }
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
                                                    fontWeight = FontWeight.Medium,
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
                                            canMoveUp = idx > 0,
                                            canMoveDown = idx < userQueue.lastIndex,
                                            onPlayNext = { onPlayNext(track) },
                                            onAddToQueue = { onAddToQueue(track) },
                                            onMoveUp = { onReorderUserQueue(idx, idx - 1) },
                                            onMoveDown = { onReorderUserQueue(idx, idx + 1) },
                                            onMoveToTop = { onReorderUserQueue(idx, 0) },
                                            onMoveToBottom = { onReorderUserQueue(idx, userQueue.lastIndex) },
                                            onRemove = { onRemoveUserQueueIndex(idx) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Upcoming Context Queue Section
                if (upcomingContextTracks.isNotEmpty()) {
                    item(key = "header_context_queue") {
                        Text(
                            text = "NEXT FROM PLAYLIST / ALBUM",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }

                    itemsIndexed(upcomingContextTracks, key = { offset, t -> "cq_${t.id}_$offset" }) { offset, track ->
                        val targetContextIndex = currentIndex + 1 + offset
                        val isItemDragging = draggingTrackId == track.id
                        val itemTranslation = if (isItemDragging) dragOffsetY else 0f
                        val itemZ = if (isItemDragging) 10f else 1f

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                when (dismissValue) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        onRemoveIndex(targetContextIndex)
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        onAddToQueue(track)
                                        false
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .zIndex(itemZ)
                                .graphicsLayer { translationY = itemTranslation }
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    DismissBackground(dismissState.targetValue)
                                }
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isItemDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Drag handle
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .pointerInput(track.id) {
                                                    detectVerticalDragGestures(
                                                        onDragStart = {
                                                            draggingTrackId = track.id
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggingTrackId = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingTrackId = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onVerticalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY += dragAmount
                                                            val currOffset = upcomingContextTracks.indexOfFirst { it.id == track.id }
                                                            if (currOffset != -1) {
                                                                if (dragOffsetY > itemHeightPx && currOffset < upcomingContextTracks.lastIndex) {
                                                                    onReorderContextQueue(currOffset, currOffset + 1)
                                                                    dragOffsetY -= itemHeightPx
                                                                } else if (dragOffsetY < -itemHeightPx && currOffset > 0) {
                                                                    onReorderContextQueue(currOffset, currOffset - 1)
                                                                    dragOffsetY += itemHeightPx
                                                                }
                                                            }
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = if (isItemDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable { onSelectIndex(targetContextIndex) }
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
                                                    fontWeight = FontWeight.Normal,
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
                                            canMoveUp = offset > 0,
                                            canMoveDown = offset < upcomingContextTracks.lastIndex,
                                            onPlayNext = { onPlayNext(track) },
                                            onAddToQueue = { onAddToQueue(track) },
                                            onMoveUp = { onReorderContextQueue(offset, offset - 1) },
                                            onMoveDown = { onReorderContextQueue(offset, offset + 1) },
                                            onMoveToTop = { onReorderContextQueue(offset, 0) },
                                            onMoveToBottom = { onReorderContextQueue(offset, upcomingContextTracks.lastIndex) },
                                            onRemove = { onRemoveIndex(targetContextIndex) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(targetValue: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        when (targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "dismissBackground"
    )
    val alignment = when (targetValue) {
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }
    val icon = when (targetValue) {
        SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.DeleteOutline
        SwipeToDismissBoxValue.StartToEnd -> Icons.AutoMirrored.Filled.QueueMusic
        SwipeToDismissBoxValue.Settled -> null
    }
    val iconTint = when (targetValue) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
        SwipeToDismissBoxValue.Settled -> Color.Transparent
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
