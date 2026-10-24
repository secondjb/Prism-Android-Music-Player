package com.secondjb.prism.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.secondjb.prism.domain.model.PlaybackState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Spotify-style floating Now Playing bar positioned above the bottom navigation bar.
 * Supports horizontal swipe gestures to skip next (swipe left) or previous (swipe right),
 * with tactile spring return physics.
 */
@Composable
fun FloatingNowPlayingBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRemoteActive: Boolean = false,
    remoteDeviceName: String = "Prism Desktop"
) {
    val track = playbackState.currentTrack
    val isVisible = track != null

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (track == null) return@AnimatedVisibility

        val coroutineScope = rememberCoroutineScope()
        val offsetX = remember { Animatable(0f) }
        val swipeThreshold = 140f

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), MaterialTheme.shapes.large),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tap / Swipe Area: Artwork + Info
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .pointerInput(track.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            when {
                                                offsetX.value < -swipeThreshold -> {
                                                    onSkipNext()
                                                }
                                                offsetX.value > swipeThreshold -> {
                                                    onSkipPrevious()
                                                }
                                            }
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        coroutineScope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount)
                                        }
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBarClick
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = "Track Artwork",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        // Title & Artist & Spotify-style Remote Indicator
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isRemoteActive) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Devices,
                                        contentDescription = "Playing on $remoteDeviceName",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Playing on $remoteDeviceName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Rewind / Previous Button
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    // Play/Pause Button
                    FilledTonalIconButton(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    // Next Button
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Mini bottom progress indicator
                LinearProgressIndicator(
                    progress = { playbackState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
