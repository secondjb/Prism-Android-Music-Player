package com.secondjb.prism.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

                    itemsIndexed(userQueue, key = { idx, t -> "uq_${t.id}_${userQueue.take(idx).count { it.id == t.id }}" }) { idx, track ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QueueDragHandle(
                                    trackId = track.id,
                                    currentIndex = idx,
                                    maxIndex = userQueue.lastIndex,
                                    onMoveUp = { onReorderUserQueue(idx, idx - 1) },
                                    onMoveDown = { onReorderUserQueue(idx, idx + 1) },
                                    onMoveToTop = { onReorderUserQueue(idx, 0) },
                                    onMoveToBottom = { onReorderUserQueue(idx, userQueue.lastIndex) },
                                    onReorder = onReorderUserQueue,
                                    listProvider = { userQueue }
                                )

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

                                IconButton(onClick = { onRemoveUserQueueIndex(idx) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from Queue",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
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

                    itemsIndexed(upcomingContextTracks, key = { offset, t -> "cq_${t.id}_${upcomingContextTracks.take(offset).count { it.id == t.id }}" }) { offset, track ->
                        val targetContextIndex = currentIndex + 1 + offset
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QueueDragHandle(
                                    trackId = track.id,
                                    currentIndex = offset,
                                    maxIndex = upcomingContextTracks.lastIndex,
                                    onMoveUp = { onReorderContextQueue(offset, offset - 1) },
                                    onMoveDown = { onReorderContextQueue(offset, offset + 1) },
                                    onMoveToTop = { onReorderContextQueue(offset, 0) },
                                    onMoveToBottom = { onReorderContextQueue(offset, upcomingContextTracks.lastIndex) },
                                    onReorder = onReorderContextQueue,
                                    listProvider = { upcomingContextTracks }
                                )

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

                                IconButton(onClick = { onRemoveIndex(targetContextIndex) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
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

@Composable
private fun QueueDragHandle(
    trackId: String,
    currentIndex: Int,
    maxIndex: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: (() -> Unit)? = null,
    onMoveToBottom: (() -> Unit)? = null,
    onReorder: (Int, Int) -> Unit,
    listProvider: () -> List<Track>,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .size(44.dp)
            .onGloballyPositioned { layoutCoordinates = it }
            .pointerInput(trackId) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val initialCoords = layoutCoordinates ?: continue
                        val startRootY = initialCoords.positionInRoot().y + down.position.y
                        var lastTriggerRootY = startRootY
                        var hasMoved = false
                        val thresholdPx = 48.dp.toPx()
                        down.consume()
                        isDragging = true

                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    if (!hasMoved) {
                                        showMenu = true
                                    }
                                    break
                                }
                                val currentCoords = layoutCoordinates ?: continue
                                val currentRootY = currentCoords.positionInRoot().y + change.position.y
                                val delta = currentRootY - lastTriggerRootY
                                change.consume()

                                if (Math.abs(currentRootY - startRootY) > 8.dp.toPx()) {
                                    hasMoved = true
                                }

                                if (delta > thresholdPx) {
                                    val list = listProvider()
                                    val currIdx = list.indexOfFirst { it.id == trackId }
                                    if (currIdx != -1 && currIdx < list.lastIndex) {
                                        onReorder(currIdx, currIdx + 1)
                                        lastTriggerRootY += thresholdPx
                                    }
                                } else if (delta < -thresholdPx) {
                                    val list = listProvider()
                                    val currIdx = list.indexOfFirst { it.id == trackId }
                                    if (currIdx != -1 && currIdx > 0) {
                                        onReorder(currIdx, currIdx - 1)
                                        lastTriggerRootY -= thresholdPx
                                    }
                                }
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to reorder (or tap for move options)",
            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (currentIndex > 0) {
                onMoveToTop?.let { moveToTop ->
                    DropdownMenuItem(
                        text = { Text("Move to Top") },
                        onClick = {
                            moveToTop()
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Move Up") },
                    onClick = {
                        onMoveUp()
                        showMenu = false
                    }
                )
            }
            if (currentIndex < maxIndex) {
                DropdownMenuItem(
                    text = { Text("Move Down") },
                    onClick = {
                        onMoveDown()
                        showMenu = false
                    }
                )
                onMoveToBottom?.let { moveToBottom ->
                    DropdownMenuItem(
                        text = { Text("Move to Bottom") },
                        onClick = {
                            moveToBottom()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}
