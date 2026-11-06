package com.secondjb.prism.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.collectAsState
import com.secondjb.prism.core.data.PrismPreferencesRepository
import com.secondjb.prism.ui.components.lyrics.LyricDisplayMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.secondjb.prism.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.lyrics.PrismLyricsEngine
import com.secondjb.prism.domain.model.DeviceNode
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.ui.components.FluidArtworkBackground
import com.secondjb.prism.ui.components.WavySeekBar
import com.secondjb.prism.ui.components.lyrics.ModernLyricsView
import com.secondjb.prism.ui.theme.PillShape

/**
 * Full-Screen Now Playing view modeled after PixelPlayer and LastWave.
 * Supports swipe-down dismissal, fluid blurred artwork backdrop, LastWave wavy seekbar,
 * audio quality badges, device handover modal, queue sheet, and synced lyrics.
 */
import androidx.compose.material.icons.outlined.Timer
import com.secondjb.prism.core.playback.SleepTimerMode
import com.secondjb.prism.core.playback.SleepTimerState
import com.secondjb.prism.ui.screens.player.SleepTimerModal

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onDismiss: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit = {},
    onSelectUserQueueIndex: (Int) -> Unit = {},
    onRemoveQueueIndex: (Int) -> Unit = {},
    onRemoveUserQueueIndex: (Int) -> Unit = {},
    onReorderUserQueue: (Int, Int) -> Unit = { _, _ -> },
    onReorderContextQueue: (Int, Int) -> Unit = { _, _ -> },
    onReshuffleUpcoming: () -> Unit = {},
    onClearQueue: () -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onPairQrClick: () -> Unit = {},
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimerMinutes: (Int, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onStartSleepTimerTracks: (Int, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onCancelSleepTimer: () -> Unit = {},
    isDesktopConnected: Boolean = false,
    isDesktopOutputEnabled: Boolean = false,
    isLocalOutputEnabled: Boolean = true,
    isBluetoothConnected: Boolean = false,
    bluetoothDeviceName: String? = null,
    connectedDeviceName: String? = null,
    connectedEndpointInfo: String? = null,
    availableDiscoveredDevices: List<DeviceNode> = emptyList(),
    pairedDevices: List<PairedDevice> = emptyList(),
    starredDeviceId: String = "desktop",
    onSetStarredDevice: (String) -> Unit = {},
    onToggleDesktopOutput: (Boolean) -> Unit = {},
    onToggleLocalOutput: (Boolean) -> Unit = {},
    onConnectToDevice: (DeviceNode) -> Unit = {},
    onConnectToPairedDevice: (PairedDevice) -> Unit = {},
    onDeletePairing: (PairedDevice) -> Unit = {},
    onDisconnect: () -> Unit = {},
    preferencesRepository: PrismPreferencesRepository? = null,
    modifier: Modifier = Modifier
) {
    var cachedTrack by remember { mutableStateOf(playbackState.currentTrack) }
    LaunchedEffect(playbackState.currentTrack) {
        if (playbackState.currentTrack != null) {
            cachedTrack = playbackState.currentTrack
        }
    }
    val track = playbackState.currentTrack ?: cachedTrack ?: return

    var showQueueSheet by remember { mutableStateOf(false) }
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var isLyricsFullscreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefsRepo = preferencesRepository ?: remember { PrismPreferencesRepository(context) }
    val userPreferences by prefsRepo.preferences.collectAsState()
    val lyricsRomanizationMode = remember(userPreferences.lyricsRomanizationMode) {
        LyricDisplayMode.fromString(userPreferences.lyricsRomanizationMode)
    }
    val lyricsTranslationMode = remember(userPreferences.lyricsTranslationMode) {
        LyricDisplayMode.fromString(userPreferences.lyricsTranslationMode)
    }
    val lyricsFontScale = userPreferences.lyricsFontScale
    val scope = rememberCoroutineScope()
    val lyricsEngine = remember { PrismLyricsEngine(context) }
    var resolvedLyrics by remember(track.id) { mutableStateOf(track.lyrics) }

    LaunchedEffect(track.id, userPreferences.lrclibAutoFetch) {
        if (resolvedLyrics.isNullOrBlank() || userPreferences.preferOnlineLyrics) {
            val fetched = lyricsEngine.resolveLyrics(track, allowOnlineFetch = true)
            if (!fetched.isNullOrBlank()) {
                resolvedLyrics = fetched
            }
        }
    }

    BackHandler(enabled = true) {
        if (isLyricsFullscreen) {
            isLyricsFullscreen = false
        } else {
            onDismiss()
        }
    }

    if (isLyricsFullscreen) {
        FullScreenLyricsPage(
            track = track,
            playbackState = playbackState,
            lyricsText = resolvedLyrics,
            onSeek = onSeek,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onDismiss = { isLyricsFullscreen = false },
            initialRomanizationMode = lyricsRomanizationMode,
            initialTranslationMode = lyricsTranslationMode,
            initialFontScale = lyricsFontScale,
            onUpdateRomanizationMode = { mode ->
                prefsRepo.setLyricsRomanizationMode(mode.name)
            },
            onUpdateTranslationMode = { mode ->
                prefsRepo.setLyricsTranslationMode(mode.name)
            },
            onUpdateFontScale = { scale ->
                prefsRepo.setLyricsFontScale(scale)
            },
            onFetchOnlineLyrics = {
                scope.launch {
                    val fetched = lyricsEngine.resolveLyrics(track, allowOnlineFetch = true)
                    if (!fetched.isNullOrBlank()) {
                        resolvedLyrics = fetched
                    }
                }
            }
        )
        return
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (totalDragY > 150f) {
                            onDismiss()
                        }
                        totalDragY = 0f
                    },
                    onDragCancel = { totalDragY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                    }
                )
            }
    ) {
        // Fluid Ambient Artwork Backdrop
        FluidArtworkBackground(artworkUri = track.artworkUri)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))

            // Top Bar (Swipe Down Chevron + Title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Swipe Down to Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM LIBRARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Placeholder to balance chevron width
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Large Album Art taking as much space as it can with a little bit of padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .aspectRatio(1f)
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(20.dp))

            // Track Title & Artist
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )

            Spacer(Modifier.height(12.dp))

            // Audio Format Badge
            Surface(
                shape = PillShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = track.audioFormatInfo.displayBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // LastWave Animated Sine-Wave WavySeekBar
            WavySeekBar(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                isPlaying = playbackState.isPlaying,
                onSeek = onSeek,
                isTranslucent = true,
                trackKey = track.id,
                primaryColor = MaterialTheme.colorScheme.primary,
                secondaryColor = MaterialTheme.colorScheme.secondary,
                tertiaryColor = MaterialTheme.colorScheme.tertiary
            )

            Spacer(Modifier.height(12.dp))

            // Transport Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }

                // Previous Track
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause Prominent Button
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Track
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode Toggle
                IconButton(onClick = onCycleRepeatMode) {
                    val icon = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val tint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> Color.White.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom Actions (Swap Device & Queue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Swap Playback Device Button
                FilledIconButton(
                    onClick = { showDeviceSheet = true },
                    shape = PillShape,
                    modifier = Modifier.height(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isLocal = playbackState.activeDeviceId == "local"
                        val icon = when {
                            isLocal && isBluetoothConnected -> Icons.Default.BluetoothAudio
                            isLocal -> Icons.Default.PhoneAndroid
                            else -> Icons.Default.Computer
                        }
                        val label = when {
                            isLocal && isBluetoothConnected -> bluetoothDeviceName ?: "Bluetooth"
                            isLocal -> "This Phone"
                            else -> connectedDeviceName?.let { formatDeviceDisplayName(it, 0) } ?: "Desktop"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Swap Device",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Sleep Timer Plain Text in the middle space (e.g. "5 minutes" or "3 tracks (5m)")
                val sleepTimerRemainingMinutes: Int? = if (sleepTimerState.isActive) {
                    when (sleepTimerState.mode) {
                        SleepTimerMode.TIME -> {
                            maxOf(1, ((sleepTimerState.remainingSeconds + 59) / 60).toInt())
                        }
                        SleepTimerMode.TRACKS -> {
                            val currentRemainingMs = (playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)
                            val upcomingTracks = playbackState.userQueue + playbackState.queue.drop(playbackState.queueIndex + 1)
                            val tracksToCount = upcomingTracks.take((sleepTimerState.remainingTracks - 1).coerceAtLeast(0))
                            val upcomingMs = tracksToCount.sumOf { if (it.durationMs > 0L) it.durationMs else 210_000L }
                            val totalMs = currentRemainingMs + upcomingMs
                            maxOf(1, ((totalMs + 59_999L) / 60_000L).toInt())
                        }
                        SleepTimerMode.END_OF_QUEUE -> {
                            val currentRemainingMs = (playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)
                            val upcomingTracks = playbackState.userQueue + playbackState.queue.drop(playbackState.queueIndex + 1)
                            val upcomingMs = upcomingTracks.sumOf { if (it.durationMs > 0L) it.durationMs else 210_000L }
                            val totalMs = currentRemainingMs + upcomingMs
                            maxOf(1, ((totalMs + 59_999L) / 60_000L).toInt())
                        }
                    }
                } else null

                val sleepTimerText: String? = if (sleepTimerState.isActive) {
                    when (sleepTimerState.mode) {
                        SleepTimerMode.TIME -> {
                            val mins = sleepTimerRemainingMinutes ?: maxOf(1, ((sleepTimerState.remainingSeconds + 59) / 60).toInt())
                            "$mins ${if (mins == 1) "minute" else "minutes"}"
                        }
                        SleepTimerMode.TRACKS -> {
                            val tracks = sleepTimerState.remainingTracks
                            val mins = sleepTimerRemainingMinutes ?: 1
                            "$tracks ${if (tracks == 1) "track" else "tracks"} (${mins}m)"
                        }
                        SleepTimerMode.END_OF_QUEUE -> {
                            val mins = sleepTimerRemainingMinutes ?: 1
                            "End of queue (${mins}m)"
                        }
                    }
                } else null

                if (sleepTimerText != null) {
                    Text(
                        text = sleepTimerText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .clickable { showSleepTimer = true }
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep Timer Button (clean, no badge, standard primary coloring)
                    FilledIconButton(
                        onClick = { showSleepTimer = true },
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = "Sleep Timer",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Full Screen Lyrics Button (between Sleep Timer & Queue)
                    FilledIconButton(
                        onClick = { isLyricsFullscreen = true },
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_karaoke_mic),
                            contentDescription = "Full Screen Lyrics",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Queue Modal/Drawer Button
                    FilledIconButton(
                        onClick = { showQueueSheet = true },
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue Drawer",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Scroll down to see Lyrics Card
            ModernLyricsView(
                lyricsText = resolvedLyrics,
                currentPositionMs = playbackState.positionMs,
                onSeek = onSeek,
                isFullScreen = false,
                onToggleFullScreen = { isLyricsFullscreen = true },
                fontScale = lyricsFontScale,
                romanizationMode = lyricsRomanizationMode,
                translationMode = lyricsTranslationMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )

            Spacer(Modifier.height(36.dp))
        }
    }

    // Queue Bottom Sheet
    if (showQueueSheet) {
        QueueBottomSheet(
            currentTrack = playbackState.currentTrack,
            userQueue = playbackState.userQueue,
            contextQueue = playbackState.queue,
            currentIndex = playbackState.queueIndex,
            onSelectIndex = {
                onSelectQueueIndex(it)
                showQueueSheet = false
            },
            onSelectUserQueueIndex = {
                onSelectUserQueueIndex(it)
                showQueueSheet = false
            },
            onRemoveUserQueueIndex = onRemoveUserQueueIndex,
            onRemoveIndex = onRemoveQueueIndex,
            onReorderUserQueue = onReorderUserQueue,
            onReorderContextQueue = onReorderContextQueue,
            onReshuffleUpcoming = onReshuffleUpcoming,
            onClearQueue = onClearQueue,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onDismiss = { showQueueSheet = false }
        )
    }

    // Device Switcher Bottom Sheet
    if (showDeviceSheet) {
        DeviceSwitcherSheet(
            isDesktopConnected = isDesktopConnected,
            isDesktopOutputEnabled = isDesktopOutputEnabled,
            isLocalOutputEnabled = isLocalOutputEnabled,
            isBluetoothConnected = isBluetoothConnected,
            bluetoothDeviceName = bluetoothDeviceName,
            connectedServerName = connectedDeviceName,
            connectedEndpointInfo = connectedEndpointInfo,
            availableDiscoveredDevices = availableDiscoveredDevices,
            pairedDevices = pairedDevices,
            starredDeviceId = starredDeviceId,
            onSetStarredDevice = onSetStarredDevice,
            onToggleDesktopOutput = onToggleDesktopOutput,
            onToggleLocalOutput = onToggleLocalOutput,
            onConnectToDevice = onConnectToDevice,
            onConnectToPairedDevice = onConnectToPairedDevice,
            onDeletePairing = onDeletePairing,
            onDisconnect = onDisconnect,
            onPairQrClick = {
                showDeviceSheet = false
                onPairQrClick()
            },
            onDismiss = { showDeviceSheet = false }
        )
    }

    // Sleep Timer Modal
    if (showSleepTimer) {
        SleepTimerModal(
            timerState = sleepTimerState,
            currentQueue = playbackState.queue,
            currentQueueIndex = playbackState.queueIndex,
            userQueue = playbackState.userQueue,
            currentTrackDurationMs = playbackState.durationMs,
            currentTrackPositionMs = playbackState.positionMs,
            onStartMinutes = onStartSleepTimerMinutes,
            onStartTracks = onStartSleepTimerTracks,
            onCancelTimer = onCancelSleepTimer,
            onDismiss = { showSleepTimer = false }
        )
    }
}
