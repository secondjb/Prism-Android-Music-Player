package com.secondjb.prism

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondjb.prism.core.data.MediaStoreAudioRepository
import com.secondjb.prism.core.playback.LocalPlaybackEngine
import com.secondjb.prism.core.util.AudioPermissionManager
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.playback.PlaybackController
import com.secondjb.prism.ui.components.FloatingNowPlayingBar
import com.secondjb.prism.ui.navigation.Screen
import com.secondjb.prism.ui.screens.library.LibraryScreen
import com.secondjb.prism.ui.screens.library.LibraryViewModel
import com.secondjb.prism.ui.screens.main.MainScreen
import com.secondjb.prism.ui.screens.main.MainViewModel
import com.secondjb.prism.ui.screens.settings.SettingsScreen
import com.secondjb.prism.ui.theme.PrismDynamicThemeProvider

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		setContent {
			val context = LocalContext.current
			val repository = remember { MediaStoreAudioRepository(context) }
			val playbackEngine = remember { LocalPlaybackEngine(context) }

			val mainViewModel = remember { MainViewModel(repository) }
			val libraryViewModel = remember { LibraryViewModel(repository) }

			val playbackState by playbackEngine.playbackState.collectAsState()

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
				artworkData = playbackState.currentTrack?.artworkUri
			) {
				PrismAppRoot(
					mainViewModel = mainViewModel,
					libraryViewModel = libraryViewModel,
					playbackState = playbackState,
					playbackEngine = playbackEngine,
					onPlayTrack = { track, queue ->
						playbackEngine.playTrack(track, queue)
					},
					onShuffleAll = {
						val tracks = libraryViewModel.uiState.value.tracks
						if (tracks.isNotEmpty()) {
							val shuffled = tracks.shuffled()
							playbackEngine.playTrack(shuffled.first(), shuffled)
						}
					}
				)
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
	onPlayTrack: (com.secondjb.prism.domain.model.Track, List<com.secondjb.prism.domain.model.Track>) -> Unit,
	onShuffleAll: () -> Unit
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
					onBarClick = {
						// FullScreen Player trigger (Phase 5)
					}
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
				SettingsScreen()
			}
		}
	}
}
