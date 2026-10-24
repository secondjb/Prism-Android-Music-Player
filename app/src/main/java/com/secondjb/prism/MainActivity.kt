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
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondjb.prism.core.data.MediaStoreAudioRepository
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
import com.secondjb.prism.ui.screens.player.NowPlayingScreen
import com.secondjb.prism.ui.screens.settings.SettingsScreen
import com.secondjb.prism.ui.screens.sync.QrScannerDialog
import com.secondjb.prism.ui.screens.sync.SyncViewModel
import com.secondjb.prism.ui.theme.PrismDynamicThemeProvider
import com.secondjb.prism.ui.theme.PrismTheme

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		setContent {
			val context = LocalContext.current
			val repository = remember { MediaStoreAudioRepository(context) }
			val playbackEngine = remember { LocalPlaybackEngine(context) }
			val discoveryManager = remember { NsdDiscoveryManager(context) }
			val syncViewModel = remember { SyncViewModel(discoveryManager = discoveryManager) }

			val mainViewModel = remember { MainViewModel(repository) }
			val libraryViewModel = remember { LibraryViewModel(repository) }

			val localPlaybackState by playbackEngine.playbackState.collectAsState()
			val remotePlaybackState by syncViewModel.remotePlaybackState.collectAsState()
			val activeDeviceId by syncViewModel.activeDeviceId.collectAsState()

			// Effective playback state switches between local engine and remote companion state!
			val effectivePlaybackState = if (activeDeviceId != "local" && remotePlaybackState != null) {
				remotePlaybackState!!
			} else {
				localPlaybackState
			}

			var isNowPlayingExpanded by remember { mutableStateOf(false) }
			var showQrPairingDialog by remember { mutableStateOf(false) }

			val connectionState by syncViewModel.connectionState.collectAsState()
			val pairingError by syncViewModel.lastPairingError.collectAsState()
			val discoveredDevices by syncViewModel.discoveredDevices.collectAsState()

			// Fallback demo tracks if device library is empty initially
			LaunchedEffect(Unit) {
				val tracks = repository.loadAllTracks()
				if (tracks.isEmpty()) {
					val demoTrack = DemoDataProvider.demoTracks.first()
					playbackEngine.playTrack(demoTrack, DemoDataProvider.demoTracks)
					playbackEngine.pause()
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

			// Dynamic Palette Provider adapts all screens to active album art!
			PrismDynamicThemeProvider(
				artworkData = effectivePlaybackState.currentTrack?.artworkUri
			) {
				Box(modifier = Modifier.fillMaxSize()) {
					PrismAppRoot(
						mainViewModel = mainViewModel,
						libraryViewModel = libraryViewModel,
						playbackState = effectivePlaybackState,
						playbackEngine = playbackEngine,
						onPlayTrack = { track, queue ->
							if (activeDeviceId == "local") {
								playbackEngine.playTrack(track, queue)
							} else {
								syncViewModel.sendRemoteCommand(CommandAction.PLAY)
							}
						},
						onShuffleAll = {
							val tracks = libraryViewModel.uiState.value.tracks.ifEmpty { DemoDataProvider.demoTracks }
							val shuffled = tracks.shuffled()
							playbackEngine.playTrack(shuffled.first(), shuffled)
						},
						onExpandNowPlaying = {
							isNowPlayingExpanded = true
						},
						onOpenQrPairing = {
							showQrPairingDialog = true
						}
					)

					// BackHandler collapses now playing before exiting app
					BackHandler(enabled = isNowPlayingExpanded) {
						isNowPlayingExpanded = false
					}

					// Full-Screen Now Playing View (PixelPlayer & LastWave inspired)
					AnimatedVisibility(
						visible = isNowPlayingExpanded && effectivePlaybackState.currentTrack != null,
						enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
						exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
					) {
						NowPlayingScreen(
							playbackState = effectivePlaybackState,
							onTogglePlayPause = {
								if (activeDeviceId == "local") {
									playbackEngine.togglePlayPause()
								} else {
									syncViewModel.sendRemoteCommand(CommandAction.TOGGLE_PLAY_PAUSE)
								}
							},
							onSeek = { pos ->
								if (activeDeviceId == "local") {
									playbackEngine.seekTo(pos)
								} else {
									syncViewModel.sendRemoteCommand(CommandAction.SEEK, seekMs = pos)
								}
							},
							onSkipNext = {
								if (activeDeviceId == "local") {
									playbackEngine.skipNext()
								} else {
									syncViewModel.sendRemoteCommand(CommandAction.SKIP_NEXT)
								}
							},
							onSkipPrevious = {
								if (activeDeviceId == "local") {
									playbackEngine.skipPrevious()
								} else {
									syncViewModel.sendRemoteCommand(CommandAction.SKIP_PREVIOUS)
								}
							},
							onToggleShuffle = {
								if (activeDeviceId == "local") {
									playbackEngine.toggleShuffle()
								} else {
									syncViewModel.sendRemoteCommand(CommandAction.TOGGLE_SHUFFLE)
								}
							},
							onCycleRepeatMode = {
								if (activeDeviceId == "local") {
									playbackEngine.setRepeatMode(effectivePlaybackState.repeatMode.next())
								} else {
									syncViewModel.sendRemoteCommand(CommandAction.SET_REPEAT)
								}
							},
							onDismiss = { isNowPlayingExpanded = false },
							onSelectQueueIndex = { index ->
								if (index in effectivePlaybackState.queue.indices) {
									playbackEngine.playTrack(effectivePlaybackState.queue[index], effectivePlaybackState.queue, index)
								}
							},
							onRemoveQueueIndex = { playbackEngine.removeFromQueue(it) },
							onClearQueue = { playbackEngine.clearQueue() },
							onPairQrClick = {
								showQrPairingDialog = true
							}
						)
					}

					// Offline QR Code Scanner & Pairing Dialog
					if (showQrPairingDialog) {
						QrScannerDialog(
							connectionState = connectionState,
							errorMessage = pairingError,
							onPair = { rawQr ->
								syncViewModel.pairWithQrCode(rawQr) {
									showQrPairingDialog = false
								}
							},
							onDismiss = {
								syncViewModel.clearPairingError()
								showQrPairingDialog = false
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
	playbackState: PlaybackState,
	playbackEngine: PlaybackController,
	onPlayTrack: (Track, List<Track>) -> Unit,
	onShuffleAll: () -> Unit,
	onExpandNowPlaying: () -> Unit,
	onOpenQrPairing: () -> Unit
) {
	val navController = rememberNavController()
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val currentDestination = navBackStackEntry?.destination

	val mainState by mainViewModel.uiState.collectAsState()
	val libraryState by libraryViewModel.uiState.collectAsState()

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		bottomBar = {
			Column {
				// Spotify-style Floating Now Playing Bar
				FloatingNowPlayingBar(
					playbackState = playbackState,
					onTogglePlayPause = { playbackEngine.togglePlayPause() },
					onSkipNext = { playbackEngine.skipNext() },
					onSkipPrevious = { playbackEngine.skipPrevious() },
					onBarClick = onExpandNowPlaying
				)

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
							label = { Text(screen.title) }
						)
					}
				}
			}
		}
	) { innerPadding ->
		NavHost(
			navController = navController,
			startDestination = Screen.Main.route,
			modifier = Modifier.padding(innerPadding)
		) {
			composable(Screen.Main.route) {
				MainScreen(
					uiState = mainState,
					onTrackClick = onPlayTrack,
					onShuffleAllClick = onShuffleAll
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
					onTrackClick = onPlayTrack
				)
			}

			composable(Screen.Settings.route) {
				SettingsScreen(
					onScanQrClick = onOpenQrPairing
				)
			}
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
