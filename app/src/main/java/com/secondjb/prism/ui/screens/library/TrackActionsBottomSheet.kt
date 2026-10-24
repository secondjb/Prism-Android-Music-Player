package com.secondjb.prism.ui.screens.library

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsBottomSheet(
    track: Track,
    isInPlaylist: Boolean = false,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onGoToQueue: () -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    onGoToArtist: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showInfoDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            // Track Header with Big Album Art
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.album.isNotBlank()) {
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = track.audioFormatInfo.displayBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = DividerDefaults.color.copy(alpha = 0.5f)
            )

            // Actions List
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionRowItem(
                    icon = Icons.Default.SkipNext,
                    title = "Play Next",
                    subtitle = "Insert track next in current queue",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    }
                )

                ActionRowItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Add to Queue",
                    subtitle = "Append track to custom playback queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    }
                )

                ActionRowItem(
                    icon = Icons.Default.PlaylistAdd,
                    title = "Add to Playlist",
                    subtitle = "Save to favorites or custom playlists",
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )

                if (isInPlaylist && onRemoveFromPlaylist != null) {
                    ActionRowItem(
                        icon = Icons.Default.Delete,
                        title = "Remove from Playlist",
                        subtitle = "Remove this song from the current playlist",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            onDismiss()
                            onRemoveFromPlaylist()
                        }
                    )
                }

                ActionRowItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "Go to Queue",
                    subtitle = "View and manage active playback queue",
                    onClick = {
                        onDismiss()
                        onGoToQueue()
                    }
                )

                if (track.album.isNotBlank()) {
                    ActionRowItem(
                        icon = Icons.Default.Album,
                        title = "Go to Album",
                        subtitle = track.album,
                        onClick = {
                            onDismiss()
                            onGoToAlbum(track.album)
                        }
                    )
                }

                if (track.artist.isNotBlank()) {
                    ActionRowItem(
                        icon = Icons.Default.Person,
                        title = "Go to Artist",
                        subtitle = track.artist,
                        onClick = {
                            onDismiss()
                            onGoToArtist(track.artist)
                        }
                    )
                }

                ActionRowItem(
                    icon = Icons.Default.Info,
                    title = "Song Info / Audio Details",
                    subtitle = "Codec, bit depth, sample rate & tags",
                    onClick = {
                        showInfoDialog = true
                    }
                )
            }
        }
    }

    if (showInfoDialog) {
        SongInfoDialog(
            track = track,
            onDismiss = {
                showInfoDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongInfoDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Song Info", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoField(label = "Title", value = track.title)
                InfoField(label = "Artist", value = track.artist)
                InfoField(label = "Album", value = track.album)
                InfoField(
                    label = "Duration",
                    value = "${track.durationSecs / 60}:${(track.durationSecs % 60).toString().padStart(2, '0')}"
                )
                InfoField(label = "Format / Codec", value = track.format.uppercase())
                InfoField(
                    label = "Sample Rate",
                    value = if (track.sampleRate % 1000 == 0) "${track.sampleRate / 1000} kHz" else "${track.sampleRate} Hz"
                )
                InfoField(label = "Bit Depth", value = if (track.bitDepth > 0) "${track.bitDepth}-bit" else "Standard")
                InfoField(
                    label = "Bitrate",
                    value = if (track.bitRateKbps != null && track.bitRateKbps > 0) "${track.bitRateKbps} kbps" else "Variable / Lossless"
                )
                InfoField(label = "Channels", value = if (track.channels == 2) "Stereo (2 ch)" else "${track.channels} ch")
                if (track.year != null && track.year > 0) {
                    InfoField(label = "Year", value = "${track.year}")
                }
                if (track.genre != null && track.genre.isNotBlank()) {
                    InfoField(label = "Genre", value = track.genre)
                }
                if (track.filePath != null && track.filePath.isNotBlank()) {
                    InfoField(label = "File Path", value = track.filePath)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
