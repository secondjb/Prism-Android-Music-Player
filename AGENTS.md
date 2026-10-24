# Prism Android Music Player — Agent Quick Guide & Guidelines

> **Notice for AI Agents**: Always consult this guide before locating files or modifying code.
> When you introduce, remove, or modify core pipelines, components, or architecture, you **must update** `docs/ARCHITECTURE.md` (and this file if paths changed) before concluding your task.

---

## 🗺️ Codebase Map & Key Touchpoints

### 1. Presentation & UI Layer (`app/src/main/java/com/secondjb/prism/ui/`)
* **Main Entry & Coordinator**:
  * [`MainActivity.kt`](app/src/main/java/com/secondjb/prism/MainActivity.kt): Root Activity, permissions flow, dependency coordination, swipeable library navigation, and global playback state wiring.
* **Now Playing & Player Screens**:
  * [`ui/screens/player/NowPlayingScreen.kt`](app/src/main/java/com/secondjb/prism/ui/screens/player/NowPlayingScreen.kt): Full-screen player modeled after PixelPlayer and LastWave. Includes swipe-down dismissal, fluid blurred artwork backdrop, animated sine-wave seekbar, transport controls, device switcher, queue drawer, and full-screen lyrics trigger.
  * [`ui/screens/player/SleepTimerModal.kt`](app/src/main/java/com/secondjb/prism/ui/screens/player/SleepTimerModal.kt): Material 3 BottomSheet for time-based, track-based, and end-of-queue sleep timers. Uses `Icons.Outlined.Timer`.
  * [`ui/screens/player/DeviceSwitcherSheet.kt`](app/src/main/java/com/secondjb/prism/ui/screens/player/DeviceSwitcherSheet.kt): Local vs Desktop audio routing switcher.
  * [`ui/screens/player/QueueBottomSheet.kt`](app/src/main/java/com/secondjb/prism/ui/screens/player/QueueBottomSheet.kt): Queue inspection, drag reordering, and track removal.
* **Library & Multi-Selection**:
  * [`ui/screens/library/LibraryScreen.kt`](app/src/main/java/com/secondjb/prism/ui/screens/library/LibraryScreen.kt): Multi-tab library view (Playlists, Artists, Songs, Albums) supporting full-screen horizontal swiping across tabs. Includes long-press multi-selection with outline badge, select all, deselect, and 3-dot batch queue actions.
  * [`ui/screens/library/LibraryViewModel.kt`](app/src/main/java/com/secondjb/prism/ui/screens/library/LibraryViewModel.kt): Library state, playlist management, and search filtering.
* **Lyrics System**:
  * [`ui/components/lyrics/ModernLyricsView.kt`](app/src/main/java/com/secondjb/prism/ui/components/lyrics/ModernLyricsView.kt): Preview lyrics card anchored beneath the player controls on the Now Playing screen. Ensures title text is white and legible over dynamic artwork gradients.
  * [`ui/components/lyrics/FullScreenLyricsPage.kt`](app/src/main/java/com/secondjb/prism/ui/components/lyrics/FullScreenLyricsPage.kt): Dedicated fullscreen lyrics page with romanization modes (Hiragana, Katakana, Romaji, Cyrillic, Pinyin), translations, and adjustable font scale.
  * [`ui/components/lyrics/LyricDisplayMode.kt`](app/src/main/java/com/secondjb/prism/ui/components/lyrics/LyricDisplayMode.kt): Shared lyric settings enums persisted across preview and fullscreen views.
* **Companion Pairing & Settings**:
  * [`ui/screens/sync/SyncViewModel.kt`](app/src/main/java/com/secondjb/prism/ui/screens/sync/SyncViewModel.kt): Handles mDNS companion discovery, CameraX QR scanner, WebSocket client connection, and command dispatching.
  * [`ui/screens/settings/SettingsScreen.kt`](app/src/main/java/com/secondjb/prism/ui/screens/settings/SettingsScreen.kt): App settings, paired desktop companion list, connection clear buttons, listening stats collection toggles, and prevent-double-counting options.
* **Stats & Leaderboards**:
  * [`ui/screens/stats/StatsScreen.kt`](app/src/main/java/com/secondjb/prism/ui/screens/stats/StatsScreen.kt): Mobile listening analytics, time vs plays selector, and top tracks/artists leaderboards.

---

### 2. Core Audio & Playback Engine (`app/src/main/java/com/secondjb/prism/core/playback/`)
* **Audio Engine**:
  * [`PlaybackEngine.kt`](app/src/main/java/com/secondjb/prism/core/playback/PlaybackEngine.kt): Built on AndroidX Media3 / ExoPlayer. Handles gapless preloading, dynamic volume adjustment, local audio muting when desktop output is active (`isRemoteOnlyActive`), audio focus, and listening event triggers.
* **Sleep Timer Manager**:
  * [`SleepTimerManager.kt`](app/src/main/java/com/secondjb/prism/core/playback/SleepTimerManager.kt): Manages countdown timers (minutes, track count, or end of queue), waiting for last track to finish, and firing local/remote pause callbacks.

---

### 3. Networking, P2P Sync & Protocol (`app/src/main/java/com/secondjb/prism/core/sync/`)
* **Client & Protocol**:
  * [`P2pSyncClient.kt`](app/src/main/java/com/secondjb/prism/core/sync/P2pSyncClient.kt): OkHttp WebSocket transport client. Handles auto-reconnect, ping/pong heartbeats, and LAN discovery via Android `NsdManager`.
  * [`SyncProtocol.kt`](app/src/main/java/com/secondjb/prism/core/sync/SyncProtocol.kt): Kotlinx Serialization data classes matching the Windows desktop schema (`handshake_req`, `handshake_resp`, `state_sync`, `queue_sync`, `command`, `output_routing`, `swap_active_device`, `stats_sync`).

---

### 4. Storage & Persistence (`app/src/main/java/com/secondjb/prism/data/` & `core/data/`)
* **Repositories**:
  * [`data/MediaStoreAudioRepository.kt`](app/src/main/java/com/secondjb/prism/data/MediaStoreAudioRepository.kt): Scans device storage via `MediaStore`, extracting metadata, album art URIs, duration, and audio formats.
  * [`core/data/PrismPreferencesRepository.kt`](app/src/main/java/com/secondjb/prism/core/data/PrismPreferencesRepository.kt): Jetpack DataStore preferences storing paired desktop hosts/tokens, lyrics typography preferences, and stats options.
  * [`core/data/PlaylistRepository.kt`](app/src/main/java/com/secondjb/prism/core/data/PlaylistRepository.kt): JSON-based playlist storage in app internal storage.
  * [`core/stats/ListeningStatsEngine.kt`](app/src/main/java/com/secondjb/prism/core/stats/ListeningStatsEngine.kt): Local SQLite database (`listening_stats.db`) recording plays, time listened, and synchronizing listening events bidirectionally with desktop.

---

## ⚠️ Critical Architectural Gotchas & Pitfalls

1. **Icons in Jetpack Compose**:
   * Always prefer standard **Material Design 3 icons** (`Icons.Outlined.*`, `Icons.Default.*`) from `androidx.compose.material.icons.*`.
   * For the outlined timer, use `Icons.Outlined.Timer` directly (`androidx.compose.material.icons.outlined.Timer`). `material-icons-extended` is already included in `app/build.gradle.kts`.
   * Only use custom vector XML drawables when an icon does not exist in Material Icons (such as Lucide's tilted handheld karaoke vocal mic `R.drawable.ic_karaoke_mic`).
2. **PC Playback Lag & Rogue Cold-Start Commands**:
   * **Never** trigger automated `sendPlayTrackCommand` or `sendRemoteCommand(CommandAction.PAUSE)` inside background connection listeners or launched effects on start. Playback commands must be driven by explicit user intent (e.g. `handlePlayTrack`, transport buttons).
3. **Double Counting Prevention**:
   * When both desktop and phone are active, verify `preventDoubleCounting` and `isRemoteOnlyActive` in `PlaybackEngine` so plays are logged exactly once across the ecosystem.
4. **Lyrics Preview Legibility**:
   * The Now Playing screen sits on top of dynamic fluid artwork backdrops. Always ensure header text, titles, and subheadings explicitly define high-contrast white text (`color = Color.White`) rather than relying on default theme content colors that might resolve to dark/black.
5. **Regression Test Suite**:
   * Always run `.\Run-Regression-Tests.bat` after making changes. All 26 regression tests must pass before committing.

---

## 🚀 Rule for Agents: Commit, Push & Install
👉 **Always build the debug APK, verify regression tests, install to the connected phone (`41ab1f9a`), and push changes to `origin/master` before ending your turn.**
