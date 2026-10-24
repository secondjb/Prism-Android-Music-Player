package com.secondjb.prism

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondjb.prism.core.data.MediaStoreAudioRepository
import com.secondjb.prism.core.data.PlaylistRepository
import com.secondjb.prism.core.data.PrismPreferencesRepository
import com.secondjb.prism.core.playback.LocalPlaybackEngine
import com.secondjb.prism.core.sync.discovery.NsdDiscoveryManager
import com.secondjb.prism.core.sync.protocol.CommandAction
import com.secondjb.prism.core.sync.transport.TransportConnectionState
import com.secondjb.prism.core.util.AudioPermissionManager
import com.secondjb.prism.domain.model.DemoDataProvider
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.domain.playback.PlaybackController
import com.secondjb.prism.ui.components.FloatingNowPlayingBar
import com.secondjb.prism.ui.navigation.Screen
import com.secondjb.prism.ui.screens.library.LibraryScreen
import com.secondjb.prism.ui.screens.library.LibraryViewModel
import com.secondjb.prism.ui.screens.main.MainScreen
import com.secondjb.prism.ui.screens.main.MainViewModel
import com.secondjb.prism.ui.screens.stats.StatsScreen
import com.secondjb.prism.ui.screens.player.NowPlayingScreen
import com.secondjb.prism.ui.screens.settings.SettingsScreen
import com.secondjb.prism.ui.screens.sync.QrScannerDialog
import com.secondjb.prism.ui.screens.sync.SmartFileSyncSheet
import com.secondjb.prism.ui.screens.sync.SyncViewModel
import com.secondjb.prism.ui.screens.sync.TrackNotFoundSheet
import com.secondjb.prism.ui.theme.PrismDynamicThemeProvider
import com.secondjb.prism.ui.theme.PrismTheme

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		setContent {
			val context = LocalContext.current
			val preferencesRepository = remember { PrismPreferencesRepository(context) }
			val repository = remember { MediaStoreAudioRepository(context, preferencesRepository) }
			val playlistRepository = remember { PlaylistRepository(context) }
			val statsRepository = remember { com.secondjb.prism.core.stats.ListeningStatsRepository(context) }
			val playbackEngine = remember { LocalPlaybackEngine(context, statsRepository) }
			val sleepTimerManager = remember { com.secondjb.prism.core.playback.SleepTimerManager(playbackEngine) }
			val sleepTimerState by sleepTimerManager.timerState.collectAsState()
			val discoveryManager = remember { NsdDiscoveryManager(context) }
			val syncViewModel = remember {
				SyncViewModel(
					discoveryManager = discoveryManager,
					localTracksProvider = { repository.loadAllTracks() },
					preferencesRepository = preferencesRepository,
					statsRepository = statsRepository,
					cacheDir = context.cacheDir,
					context = context
				)
			}

			val mainViewModel = remember { MainViewModel(repository, statsRepository) }
			val libraryViewModel = remember { LibraryViewModel(repository, playlistRepository) }

			val userPreferences by preferencesRepository.preferences.collectAsState()

			val localPlaybackState by playbackEngine.playbackState.collectAsState()
			val remotePlaybackState by syncViewModel.remotePlaybackState.collectAsState()
			val activeDeviceId by syncViewModel.activeDeviceId.collectAsState()

			val connectionState by syncViewModel.connectionState.collectAsState()
			val isDesktopConnected = connectionState is TransportConnectionState.Connected
			val isDesktopOutputEnabled by syncViewModel.isDesktopOutputEnabled.collectAsState()
			val isLocalOutputEnabled by syncViewModel.isLocalOutputEnabled.collectAsState()
			val connectedServerName by syncViewModel.connectedServerName.collectAsState()
			val connectedEndpoint by syncViewModel.connectedEndpoint.collectAsState()
			val pairingError by syncViewModel.lastPairingError.collectAsState()
			val discoveredDevices by syncViewModel.discoveredDevices.collectAsState()
			val pairedDevices by syncViewModel.pairedDevices.collectAsState()
			val starredDeviceId by syncViewModel.starredDeviceId.collectAsState()
			val scope = rememberCoroutineScope()

			// Effective playback state prefers remote companion state when desktop output is active
			val effectivePlaybackState = if (isDesktopConnected && isDesktopOutputEnabled && remotePlaybackState != null) {
				remotePlaybackState!!
			} else {
				localPlaybackState
			}

			val isRemoteOnlyActive = isDesktopConnected && isDesktopOutputEnabled && !isLocalOutputEnabled

			LaunchedEffect(isRemoteOnlyActive, userPreferences.preventDoubleCounting, userPreferences.isStatsCollectionEnabled) {
				playbackEngine.isRemoteOnlyActive = isRemoteOnlyActive
				playbackEngine.preventDoubleCounting = userPreferences.preventDoubleCounting
				playbackEngine.isStatsCollectionEnabled = userPreferences.isStatsCollectionEnabled
			}

			// Immediately pause local audio when local output is toggled off; restore and resync when toggled on
			LaunchedEffect(isLocalOutputEnabled) {
				if (!isLocalOutputEnabled) {
					if (playbackEngine.playbackState.value.isPlaying) {
						playbackEngine.pause()
					}
				} else {
					playbackEngine.restoreLocalOutput()
					if (isDesktopConnected && isDesktopOutputEnabled) {
						val remote = remotePlaybackState
						val remoteTrack = remote?.currentTrack
						if (remote != null && remoteTrack != null && remote.isPlaying) {
							val match = syncViewModel.findLocalMatch(remoteTrack)
							if (match != null) {
								playbackEngine.playTrack(match, syncViewModel.getAllLocalTracks())
								playbackEngine.seekTo(remote.positionMs)
							}
						}
					}
				}
			}

			// Wire up SleepTimerManager callbacks for local pause & remote desktop sync
			LaunchedEffect(sleepTimerManager, isDesktopConnected, isDesktopOutputEnabled) {
				sleepTimerManager.onStopPlaybackCallback = {
					if (isDesktopConnected && isDesktopOutputEnabled) {
						syncViewModel.sendRemoteCommand(CommandAction.PAUSE)
					}
					if (isLocalOutputEnabled) {
						playbackEngine.pause()
					}
				}
				sleepTimerManager.onStartSleepTimerCallback = { mode, value ->
					if (isDesktopConnected) {
						syncViewModel.sendSleepTimerCommand(if (mode == com.secondjb.prism.core.playback.SleepTimerMode.TRACKS) "tracks" else "time", value)
					}
				}
				sleepTimerManager.onCancelSleepTimerCallback = {
					if (isDesktopConnected) {
						syncViewModel.sendCancelSleepTimerCommand()
					}
				}
			}

			// Configure MediaSessionBridge so hardware buttons and Android system notifications command desktop (Spotify Connect style)
			LaunchedEffect(isRemoteOnlyActive, connectedServerName) {
				com.secondjb.prism.core.service.MediaSessionBridge.isRemoteActive = isRemoteOnlyActive
				com.secondjb.prism.core.service.MediaSessionBridge.remoteServerName = connectedServerName?.ifBlank { "Prism Desktop" } ?: "Prism Desktop"
				com.secondjb.prism.core.service.MediaSessionBridge.onRemotePlay = {
					syncViewModel.sendRemoteCommand(CommandAction.PLAY)
				}
				com.secondjb.prism.core.service.MediaSessionBridge.onRemotePause = {
					syncViewModel.sendRemoteCommand(CommandAction.PAUSE)
				}
				com.secondjb.prism.core.service.MediaSessionBridge.onRemoteSkipNext = {
					syncViewModel.sendRemoteCommand(CommandAction.SKIP_NEXT)
				}
				com.secondjb.prism.core.service.MediaSessionBridge.onRemoteSkipPrevious = {
					syncViewModel.sendRemoteCommand(CommandAction.SKIP_PREVIOUS)
				}
				com.secondjb.prism.core.service.MediaSessionBridge.onRemoteSeek = { pos ->
					syncViewModel.sendRemoteCommand(CommandAction.SEEK, seekMs = pos)
				}
			}

			// Keep Android Quick Settings & Lockscreen media notification in sync with PC track
			LaunchedEffect(
				remotePlaybackState?.currentTrack?.title,
				remotePlaybackState?.isPlaying,
				isRemoteOnlyActive,
				isDesktopConnected
			) {
				if (isRemoteOnlyActive) {
					val remote = remotePlaybackState ?: return@LaunchedEffect
					val remoteTrack = remote.currentTrack ?: return@LaunchedEffect
					val localMatch = syncViewModel.findLocalMatch(remoteTrack)
					val playableUri = localMatch?.uri ?: syncViewModel.getAllLocalTracks().firstOrNull()?.uri ?: remoteTrack.uri

					com.secondjb.prism.core.service.MediaSessionBridge.isRemotePlaying = remote.isPlaying
					com.secondjb.prism.core.service.MediaSessionBridge.remotePositionMs = remote.positionMs
					com.secondjb.prism.core.service.MediaSessionBridge.remoteDurationMs = remote.durationMs
					com.secondjb.prism.core.service.MediaSessionBridge.onStateChanged?.invoke()

					playbackEngine.syncRemoteMetadata(
						remoteTrack = remoteTrack,
						playableUri = playableUri,
						isPlaying = remote.isPlaying,
						positionMs = remote.positionMs,
						serverName = connectedServerName?.ifBlank { "Prism Desktop" } ?: "Prism Desktop"
					)
				} else if (isLocalOutputEnabled) {
					com.secondjb.prism.core.service.MediaSessionBridge.isRemoteActive = false
					com.secondjb.prism.core.service.MediaSessionBridge.onStateChanged?.invoke()
					playbackEngine.restoreLocalOutput()
				}
			}

			// Multi-room synchronization: when both Windows and Phone outputs are enabled, keep them in sync!
			LaunchedEffect(remotePlaybackState?.currentTrack?.title, remotePlaybackState?.isPlaying, isDesktopOutputEnabled, isLocalOutputEnabled, isDesktopConnected) {
				if (!isDesktopConnected || !isDesktopOutputEnabled || !isLocalOutputEnabled) return@LaunchedEffect
				val remote = remotePlaybackState ?: return@LaunchedEffect
				val remoteTrack = remote.currentTrack ?: return@LaunchedEffect
				val localState = playbackEngine.playbackState.value

				val isSameTrack = localState.currentTrack != null && (
					localState.currentTrack?.title.equals(remoteTrack.title, ignoreCase = true) ||
					(localState.currentTrack?.filePath ?: localState.currentTrack?.uri)?.substringAfterLast('/')?.equals(
						remoteTrack.uri.substringAfterLast('/'), ignoreCase = true
					) == true
				)

				if (!isSameTrack) {
					val match = syncViewModel.findLocalMatch(remoteTrack)
					if (match != null) {
						playbackEngine.playTrack(match, syncViewModel.getAllLocalTracks())
						playbackEngine.seekTo(remote.positionMs)
						if (!remote.isPlaying) {
							playbackEngine.pause()
						}
					}
				} else {
					if (remote.isPlaying && !localState.isPlaying) {
						playbackEngine.play()
					} else if (!remote.isPlaying && localState.isPlaying) {
						playbackEngine.pause()
					}

					val drift = Math.abs(remote.positionMs - localState.positionMs)
					if (drift > 1000L) {
						playbackEngine.seekTo(remote.positionMs)
					}
				}
			}

			// Unified command handlers for single or simultaneous dual output
			val handlePlayTrack: (Track, List<Track>) -> Unit = { track, queue ->
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled
				val filename = (track.filePath ?: track.uri).substringAfterLast('/').substringAfterLast('\\')

				if (isDesktopConnected) {
					val queueFns = queue.take(500).map {
						(it.filePath ?: it.uri).substringAfterLast('/').substringAfterLast('\\')
					}
					syncViewModel.sendPlayTrackCommand(
						trackTitle = track.title,
						trackArtist = track.artist,
						trackFilename = filename,
						trackUri = track.uri,
						queueFilenames = queueFns
					)
					if (!desktopActive) {
						// Keep PC in sync with track/queue, but paused so PC audio stays quiet
						syncViewModel.sendRemoteCommand(CommandAction.PAUSE)
					}
				}

				if (localActive) {
					playbackEngine.playTrack(track, queue)
				} else {
					playbackEngine.pause()
				}
			}

			val handleTogglePlayPause: () -> Unit = {
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled

				if (desktopActive) {
					syncViewModel.sendRemoteCommand(CommandAction.TOGGLE_PLAY_PAUSE)
				}
				if (localActive) {
					playbackEngine.togglePlayPause()
				}
			}

			val handleSeek: (Long) -> Unit = { pos ->
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled

				if (desktopActive) {
					syncViewModel.sendRemoteCommand(CommandAction.SEEK, seekMs = pos)
				}
				if (localActive) {
					playbackEngine.seekTo(pos)
				}
			}

			val handleSkipNext: () -> Unit = {
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled

				if (desktopActive) {
					syncViewModel.sendRemoteCommand(CommandAction.SKIP_NEXT)
				}
				if (localActive) {
					playbackEngine.skipNext()
				}
			}

			val handleSkipPrevious: () -> Unit = {
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled

				if (desktopActive) {
					syncViewModel.sendRemoteCommand(CommandAction.SKIP_PREVIOUS)
				}
				if (localActive) {
					playbackEngine.skipPrevious()
				}
			}

			val handleToggleShuffle: () -> Unit = {
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled

				if (desktopActive) {
					syncViewModel.sendRemoteCommand(CommandAction.TOGGLE_SHUFFLE)
				}
				if (localActive) {
					playbackEngine.toggleShuffle()
				}
			}

			val handleCycleRepeatMode: () -> Unit = {
				val desktopActive = isDesktopConnected && isDesktopOutputEnabled
				val localActive = isLocalOutputEnabled

				if (desktopActive) {
					syncViewModel.sendRemoteCommand(CommandAction.SET_REPEAT)
				}
				if (localActive) {
					playbackEngine.setRepeatMode(effectivePlaybackState.repeatMode.next())
				}
			}

			val handlePlayNext: (Track) -> Unit = { track ->
				playbackEngine.playNext(track)
				if (isDesktopConnected) {
					syncViewModel.sendPlayNextCommand(track)
				}
			}

			val handleAddToQueue: (Track) -> Unit = { track ->
				playbackEngine.addToQueue(track)
				if (isDesktopConnected) {
					syncViewModel.sendAddToQueueCommand(track)
				}
			}

			var isNowPlayingExpanded by remember { mutableStateOf(false) }
			var showQrPairingDialog by remember { mutableStateOf(false) }
			var showSmartFileSyncSheet by remember { mutableStateOf(false) }
			val trackNotFoundEvent by syncViewModel.trackNotFoundPrompt.collectAsState()
			val fileSyncState by syncViewModel.fileSyncState.collectAsState()

			// Fallback demo tracks if device library is empty initially - ALWAYS start in paused state
			LaunchedEffect(Unit) {
				val tracks = repository.loadAllTracks()
				if (tracks.isEmpty()) {
					val demoTrack = DemoDataProvider.demoTracks.first()
					playbackEngine.prepareTrack(demoTrack, DemoDataProvider.demoTracks)
				}
			}

			// When Desktop Output is toggled on, only sync track if PC has a different track loaded
			LaunchedEffect(isDesktopOutputEnabled) {
				if (isDesktopConnected && isDesktopOutputEnabled) {
					val localTrack = playbackEngine.playbackState.value.currentTrack
					val remoteTrack = remotePlaybackState?.currentTrack
					if (localTrack != null) {
						val isAlreadySameTrack = remoteTrack != null && (
							remoteTrack.title.equals(localTrack.title, ignoreCase = true) ||
							remoteTrack.uri.substringAfterLast('/').substringAfterLast('\\').equals(
								(localTrack.filePath ?: localTrack.uri).substringAfterLast('/').substringAfterLast('\\'),
								ignoreCase = true
							)
						)

						if (!isAlreadySameTrack) {
							val filename = (localTrack.filePath ?: localTrack.uri).substringAfterLast('/').substringAfterLast('\\')
							val queueFns = playbackEngine.playbackState.value.queue.take(150).map {
								(it.filePath ?: it.uri).substringAfterLast('/').substringAfterLast('\\')
							}
							syncViewModel.sendPlayTrackCommand(
								trackTitle = localTrack.title,
								trackArtist = localTrack.artist,
								trackFilename = filename,
								trackUri = localTrack.uri,
								queueFilenames = queueFns
							)
							val currentPos = playbackEngine.playbackState.value.positionMs
							if (currentPos > 0) {
								syncViewModel.sendRemoteCommand(CommandAction.SEEK, seekMs = currentPos)
							}
						}
						if (playbackEngine.playbackState.value.isPlaying) {
							syncViewModel.sendRemoteCommand(CommandAction.PLAY)
						} else {
							syncViewModel.sendRemoteCommand(CommandAction.PAUSE)
						}
					}
				}
			}

			// Request audio permission on first launch
			val permissionLauncher = rememberLauncherForActivityResult(
				contract = ActivityResultContracts.RequestPermission()
			) { isGranted ->
				if (isGranted) {
					mainViewModel.loadDashboard()
					libraryViewModel.loadLibrary()
				}
			}

			LaunchedEffect(Unit) {
				if (!AudioPermissionManager.hasAudioPermission(context)) {
					permissionLauncher.launch(AudioPermissionManager.getRequiredAudioPermission())
				}
			}

			DisposableEffect(playbackEngine) {
				onDispose {
					playbackEngine.release()
				}
			}

			// Feed effective playback state to sleepTimerManager so both local and remote tracks count down and pause properly
			LaunchedEffect(effectivePlaybackState.currentTrack?.id, effectivePlaybackState.positionMs, effectivePlaybackState.isPlaying, effectivePlaybackState.durationMs) {
				sleepTimerManager.handlePlaybackStateUpdate(effectivePlaybackState)
			}

			// Retain last valid artwork URI so dynamic theme colors never flash or revert to default
			var lastValidArtworkUri by remember { mutableStateOf<String?>(null) }
			LaunchedEffect(effectivePlaybackState.currentTrack?.artworkUri) {
				val uri = effectivePlaybackState.currentTrack?.artworkUri
				if (!uri.isNullOrBlank()) {
					lastValidArtworkUri = uri
				}
			}
			val activeArtworkData = if (userPreferences.isDynamicThemingEnabled) {
				effectivePlaybackState.currentTrack?.artworkUri ?: lastValidArtworkUri
			} else null

			// Dynamic Palette Provider adapts all screens to active album art!
			PrismDynamicThemeProvider(
				artworkData = activeArtworkData
			) {
				Box(modifier = Modifier.fillMaxSize()) {
					PrismAppRoot(
						mainViewModel = mainViewModel,
						libraryViewModel = libraryViewModel,
						preferencesRepository = preferencesRepository,
						playbackState = effectivePlaybackState,
						playbackEngine = playbackEngine,
						onPlayTrack = handlePlayTrack,
						onPlayNext = handlePlayNext,
						onAddToQueue = handleAddToQueue,
						onTogglePlayPause = handleTogglePlayPause,
						onSkipNext = handleSkipNext,
						onSkipPrevious = handleSkipPrevious,
						onShuffleAll = {
							val tracks = libraryViewModel.uiState.value.tracks.ifEmpty { DemoDataProvider.demoTracks }
							val shuffled = tracks.shuffled()
							handlePlayTrack(shuffled.first(), shuffled)
						},
						onExpandNowPlaying = {
							isNowPlayingExpanded = true
						},
						onOpenQrPairing = {
							showQrPairingDialog = true
						},
						onRescanLibrary = {
							libraryViewModel.loadLibrary()
							syncViewModel.refreshLocalTracks()
						},
						isRemoteActive = isRemoteOnlyActive,
						remoteDeviceName = connectedServerName?.ifBlank { "Prism Desktop" } ?: "Prism Desktop",
						connectedDeviceName = connectedServerName,
						isDesktopConnected = isDesktopConnected,
						pairedDevices = pairedDevices,
						onConnectToPairedDevice = { syncViewModel.connectToPairedDevice(it) },
						onDeletePairedDevice = { syncViewModel.deletePairing(it) },
						onOpenSmartFileSync = {
							syncViewModel.requestManifestDiff()
							showSmartFileSyncSheet = true
						},
						onSyncStatsNow = {
							syncViewModel.syncStatsNow()
						},
						onClearStatsHistory = {
							scope.launch { statsRepository.clearHistory() }
						}
					)

					// BackHandler collapses now playing before exiting app
					BackHandler(enabled = isNowPlayingExpanded) {
						isNowPlayingExpanded = false
					}

					var lastKnownTrack by remember { mutableStateOf<Track?>(null) }
					LaunchedEffect(effectivePlaybackState.currentTrack) {
						if (effectivePlaybackState.currentTrack != null) {
							lastKnownTrack = effectivePlaybackState.currentTrack
						}
					}
					val activeTrackForNowPlaying = effectivePlaybackState.currentTrack ?: lastKnownTrack

					// Full-Screen Now Playing View (PixelPlayer & LastWave inspired)
					AnimatedVisibility(
						visible = isNowPlayingExpanded && activeTrackForNowPlaying != null,
						enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
						exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
					) {
						val stateForNowPlaying = if (effectivePlaybackState.currentTrack == null && activeTrackForNowPlaying != null) {
							effectivePlaybackState.copy(currentTrack = activeTrackForNowPlaying)
						} else {
							effectivePlaybackState
						}
						NowPlayingScreen(
							playbackState = stateForNowPlaying,
							onTogglePlayPause = handleTogglePlayPause,
							onSeek = handleSeek,
							onSkipNext = handleSkipNext,
							onSkipPrevious = handleSkipPrevious,
							onToggleShuffle = handleToggleShuffle,
							onCycleRepeatMode = handleCycleRepeatMode,
							onDismiss = { isNowPlayingExpanded = false },
							onSelectQueueIndex = { index ->
								if (index in effectivePlaybackState.queue.indices) {
									handlePlayTrack(effectivePlaybackState.queue[index], effectivePlaybackState.queue)
								}
							},
							onRemoveQueueIndex = { playbackEngine.removeFromQueue(it) },
							onClearQueue = { playbackEngine.clearQueue() },
							onPairQrClick = {
								showQrPairingDialog = true
							},
							isDesktopConnected = isDesktopConnected,
							isDesktopOutputEnabled = isDesktopOutputEnabled,
							isLocalOutputEnabled = isLocalOutputEnabled,
							connectedDeviceName = connectedServerName,
							connectedEndpointInfo = connectedEndpoint?.let { "ws://${it.host}:${it.port}" },
							availableDiscoveredDevices = discoveredDevices,
							pairedDevices = pairedDevices,
							starredDeviceId = starredDeviceId,
							onSetStarredDevice = { syncViewModel.setStarredDevice(it) },
							onToggleDesktopOutput = { syncViewModel.toggleDesktopOutput(it) },
							onToggleLocalOutput = { syncViewModel.toggleLocalOutput(it) },
							onConnectToDevice = { syncViewModel.connectToDevice(it) },
							onConnectToPairedDevice = { syncViewModel.connectToPairedDevice(it) },
							onDeletePairing = { syncViewModel.deletePairing(it) },
							sleepTimerState = sleepTimerState,
							onStartSleepTimerMinutes = { mins, finishLast, endOfQ ->
								sleepTimerManager.startTimerByMinutes(mins, finishLast, endOfQ)
							},
							onStartSleepTimerTracks = { count, finishLast, endOfQ ->
								sleepTimerManager.startTimerByTracks(count, finishLast, endOfQ)
							},
							onCancelSleepTimer = { sleepTimerManager.cancelTimer() }
						)
					}

					// Offline QR Code Scanner & Pairing Dialog
					if (showQrPairingDialog) {
						QrScannerDialog(
							connectionState = connectionState,
							errorMessage = pairingError,
							onPair = { rawQr ->
								syncViewModel.pairWithQrCode(rawQr)
							},
							onDismiss = {
								syncViewModel.clearPairingError()
								showQrPairingDialog = false
							}
						)
					}

					// Smart Library File Sync Dialog
					if (showSmartFileSyncSheet) {
						SmartFileSyncSheet(
							syncState = fileSyncState,
							onStartSync = { syncViewModel.startSyncDownload() },
							onDismiss = { showSmartFileSyncSheet = false }
						)
					}

					// Track Not Found on PC Sheet
					trackNotFoundEvent?.let { event ->
						TrackNotFoundSheet(
							event = event,
							onPlayLocally = {
								syncViewModel.toggleLocalOutput(true)
								scope.launch {
									val tracks = repository.loadAllTracks()
									val match = tracks.firstOrNull {
										it.title.equals(event.title, ignoreCase = true) ||
										(it.filePath ?: it.uri).endsWith(event.filename, ignoreCase = true)
									} ?: syncViewModel.findLocalMatch(
										Track(
											id = "local_direct",
											uri = "",
											title = event.title,
											artist = event.artist,
											album = "",
											durationMs = 0L,
											format = "FLAC"
										)
									)
									if (match != null) {
										playbackEngine.playTrack(match, tracks.ifEmpty { syncViewModel.getAllLocalTracks() })
									}
								}
								syncViewModel.clearTrackNotFoundPrompt()
							},
							onSendToDesktop = {
								scope.launch {
									val tracks = repository.loadAllTracks()
									val match = tracks.firstOrNull {
										it.title.equals(event.title, ignoreCase = true) ||
										(it.filePath ?: it.uri).endsWith(event.filename, ignoreCase = true)
									}
									if (match != null) {
										android.widget.Toast.makeText(context, "Sending ${match.title} to PC...", android.widget.Toast.LENGTH_SHORT).show()
										syncViewModel.sendSingleTrackToDesktop(match) {
											android.widget.Toast.makeText(context, "Transferred ${match.title} to PC", android.widget.Toast.LENGTH_SHORT).show()
											syncViewModel.sendPlayTrackCommand(
												trackTitle = event.title,
												trackArtist = event.artist,
												trackFilename = event.filename,
												trackUri = match.uri
											)
										}
									}
								}
								syncViewModel.clearTrackNotFoundPrompt()
							},
							onStreamToDesktop = {
								scope.launch {
									val tracks = repository.loadAllTracks()
									val match = tracks.firstOrNull {
										it.title.equals(event.title, ignoreCase = true) ||
										(it.filePath ?: it.uri).endsWith(event.filename, ignoreCase = true)
									}
									if (match != null) {
										android.widget.Toast.makeText(context, "Streaming ${match.title} to PC...", android.widget.Toast.LENGTH_SHORT).show()
										syncViewModel.sendSingleTrackToDesktop(match) {
											syncViewModel.sendPlayTrackCommand(
												trackTitle = match.title,
												trackArtist = match.artist,
												trackFilename = event.filename,
												trackUri = match.uri
											)
										}
									}
								}
								syncViewModel.clearTrackNotFoundPrompt()
							},
							onDismiss = {
								syncViewModel.clearTrackNotFoundPrompt()
							}
						)
					}
				}
			}
		}
	}
}

@Composable
fun PrismAppRoot(
	mainViewModel: MainViewModel,
	libraryViewModel: LibraryViewModel,
	preferencesRepository: PrismPreferencesRepository,
	playbackState: PlaybackState,
	playbackEngine: PlaybackController,
	onPlayTrack: (Track, List<Track>) -> Unit,
	onPlayNext: (Track) -> Unit = { playbackEngine.playNext(it) },
	onAddToQueue: (Track) -> Unit = { playbackEngine.addToQueue(it) },
	onTogglePlayPause: () -> Unit = { playbackEngine.togglePlayPause() },
	onSkipNext: () -> Unit = { playbackEngine.skipNext() },
	onSkipPrevious: () -> Unit = { playbackEngine.skipPrevious() },
	onShuffleAll: () -> Unit,
	onExpandNowPlaying: () -> Unit,
	onOpenQrPairing: () -> Unit,
	onRescanLibrary: () -> Unit = {},
	isRemoteActive: Boolean = false,
	remoteDeviceName: String = "Prism Desktop",
	connectedDeviceName: String? = null,
	isDesktopConnected: Boolean = false,
	pairedDevices: List<com.secondjb.prism.core.data.PairedDevice> = emptyList(),
	onConnectToPairedDevice: (com.secondjb.prism.core.data.PairedDevice) -> Unit = {},
	onDeletePairedDevice: (com.secondjb.prism.core.data.PairedDevice) -> Unit = {},
	onOpenSmartFileSync: () -> Unit = {},
	onSyncStatsNow: () -> Unit = {},
	onClearStatsHistory: () -> Unit = {}
) {
	val navController = rememberNavController()
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val currentDestination = navBackStackEntry?.destination

	val mainState by mainViewModel.uiState.collectAsState()
	val libraryState by libraryViewModel.uiState.collectAsState()

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		bottomBar = {
			NavigationBar {
				Screen.bottomNavItems.forEach { screen ->
					val isSelected = currentDestination?.route == screen.route
					NavigationBarItem(
						selected = isSelected,
						onClick = {
							navController.navigate(screen.route) {
								popUpTo(navController.graph.findStartDestination().id) {
									saveState = true
								}
								launchSingleTop = true
								restoreState = true
							}
						},
						icon = {
							Icon(
								imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
								contentDescription = screen.title
							)
						},
						label = {
							Text(
								text = screen.title,
								fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
							)
						},
						colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
							selectedIconColor = MaterialTheme.colorScheme.primary,
							selectedTextColor = MaterialTheme.colorScheme.primary,
							indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
							unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
							unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
						)
					)
				}
			}
		}
	) { innerPadding ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.statusBarsPadding()
				.padding(
					top = 4.dp,
					bottom = innerPadding.calculateBottomPadding()
				)
		) {
			NavHost(
				navController = navController,
				startDestination = Screen.Main.route,
				modifier = Modifier.fillMaxSize()
			) {
				composable(Screen.Main.route) {
					MainScreen(
						uiState = mainState,
						onTrackClick = onPlayTrack,
						onShuffleAllClick = onShuffleAll,
						onSelectStatsRange = { mainViewModel.setStatsRange(it) },
						onOpenStatsClick = { navController.navigate(Screen.Stats.route) }
					)
				}

				composable(Screen.Stats.route) {
					StatsScreen(
						uiState = mainState,
						allTracks = libraryState.tracks,
						onBackClick = { navController.popBackStack() },
						onTrackClick = onPlayTrack,
						onSelectStatsRange = { mainViewModel.setStatsRange(it) },
						onMetricChange = { mainViewModel.setLeaderboardMetric(it) },
						onGoToArtist = { artistName ->
							navController.popBackStack()
							libraryViewModel.setFilterChip(com.secondjb.prism.ui.screens.library.LibraryFilterChip.ARTISTS)
							libraryViewModel.setSearchQuery(artistName)
							navController.navigate(Screen.Library.route) {
								popUpTo(navController.graph.findStartDestination().id) { saveState = true }
								launchSingleTop = true
								restoreState = true
							}
						},
						onGoToAlbum = { albumName ->
							navController.popBackStack()
							libraryViewModel.setFilterChip(com.secondjb.prism.ui.screens.library.LibraryFilterChip.ALBUMS)
							libraryViewModel.setSearchQuery(albumName)
							navController.navigate(Screen.Library.route) {
								popUpTo(navController.graph.findStartDestination().id) { saveState = true }
								launchSingleTop = true
								restoreState = true
							}
						}
					)
				}

				composable(Screen.Library.route) {
					LibraryScreen(
						uiState = libraryState,
						onFilterSelect = libraryViewModel::setFilterChip,
						onToggleViewMode = libraryViewModel::toggleViewMode,
						onSearchChange = libraryViewModel::setSearchQuery,
						onToggleSearch = libraryViewModel::toggleSearchActive,
						onTogglePin = libraryViewModel::togglePin,
						onCreatePlaylist = libraryViewModel::createPlaylist,
						onTrackClick = onPlayTrack,
						onOpenPlaylist = libraryViewModel::openPlaylist,
						onDeletePlaylist = libraryViewModel::deletePlaylist,
						onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
						onRemoveTrackFromPlaylist = libraryViewModel::removeTrackFromPlaylist,
						onToggleLikeTrack = libraryViewModel::toggleLikeTrack,
						onPlayNext = onPlayNext,
						onAddToQueue = onAddToQueue,
						onGoToQueue = onExpandNowPlaying,
						onGoToAlbum = { albumName ->
							libraryViewModel.setFilterChip(com.secondjb.prism.ui.screens.library.LibraryFilterChip.ALBUMS)
							libraryViewModel.setSearchQuery(albumName)
						},
						onGoToArtist = { artistName ->
							libraryViewModel.setFilterChip(com.secondjb.prism.ui.screens.library.LibraryFilterChip.ARTISTS)
							libraryViewModel.setSearchQuery(artistName)
						}
					)
				}

				composable(Screen.Settings.route) {
					SettingsScreen(
						preferencesRepository = preferencesRepository,
						totalTrackCount = libraryState.tracks.size,
						onRescanLibrary = onRescanLibrary,
						onScanQrClick = onOpenQrPairing,
						connectedDeviceName = connectedDeviceName,
						isDesktopConnected = isDesktopConnected,
						pairedDevices = pairedDevices,
						onConnectToPairedDevice = onConnectToPairedDevice,
						onDeletePairedDevice = onDeletePairedDevice,
						onStartFileSync = onOpenSmartFileSync,
						onSyncStatsNow = onSyncStatsNow,
						onClearStatsHistory = onClearStatsHistory
					)
				}
			}

			// Spotify-style Floating Now Playing Bar floating seamlessly above the navigation bar
			FloatingNowPlayingBar(
				playbackState = playbackState,
				onTogglePlayPause = onTogglePlayPause,
				onSkipNext = onSkipNext,
				onSkipPrevious = onSkipPrevious,
				onBarClick = onExpandNowPlaying,
				isRemoteActive = isRemoteActive,
				remoteDeviceName = remoteDeviceName,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 6.dp)
			)
		}
	}
}

/**
 * Pixel device preview demonstrating the Full-Screen Player with sample track & synced lyrics.
 */
@Preview(showBackground = true, device = Devices.PIXEL_7)
@Composable
fun NowPlayingScreenPixelPreview() {
	val sampleTrack = DemoDataProvider.demoTracks.first()
	val sampleState = PlaybackState(
		currentTrack = sampleTrack,
		isPlaying = true,
		positionMs = 12000L,
		durationMs = sampleTrack.durationMs,
		queue = DemoDataProvider.demoTracks,
		queueIndex = 0
	)
	PrismTheme(artworkData = sampleTrack.artworkUri) {
		NowPlayingScreen(
			playbackState = sampleState,
			onTogglePlayPause = {},
			onSeek = {},
			onSkipNext = {},
			onSkipPrevious = {},
			onToggleShuffle = {},
			onCycleRepeatMode = {},
			onDismiss = {}
		)
	}
}
