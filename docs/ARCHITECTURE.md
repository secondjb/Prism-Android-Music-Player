# Prism Android Music Player — Architectural Specification & System Guide

> **Audience**: AI Agents and Core Contributors.
> **Purpose**: Authoritative reference for subsystem design, data flow, playback lifecycle, P2P sync protocol, and persistence across the Android app.

---

## 1. System Overview

Prism Android is a modern, high-fidelity music player and companion controller built on **Jetpack Compose**, **Material Design 3**, and **AndroidX Media3 (ExoPlayer)**.

```
+-----------------------------------------------------------------------------------+
|                        Jetpack Compose Presentation Layer                         |
|                                                                                   |
|  [MainActivity.kt] App Scaffolding, Navigation Coordinator, Audio Engine Binding  |
|     ├── [DashboardScreen.kt] Overview & Quick Actions                             |
|     ├── [LibraryScreen.kt] Swipeable Library Tabs (Playlists, Artists, Songs, ...) |
|     ├── [NowPlayingScreen.kt] Fullscreen Player (Fluid Art, WavySeekBar, Lyrics)   |
|     │     ├── [SleepTimerModal.kt] M3 Timer (Icons.Outlined.Timer)                |
|     │     ├── [DeviceSwitcherSheet.kt] Local vs Desktop Output Routing            |
|     │     └── [QueueBottomSheet.kt] Queue Inspector & Reordering                  |
|     ├── [FullScreenLyricsPage.kt] Syllable/Line Synced Lyrics + Romanization      |
|     ├── [StatsScreen.kt] Listening Analytics (Time vs Plays Leaderboards)         |
|     └── [SettingsScreen.kt] Companion Pairing, Double-Counting, Storage Clears    |
|                                                                                   |
|  Theming: [Theme.kt] (Material 3 Dynamic Palette from Album Art + Dark Glass)     |
|  State Holders: [LibraryViewModel], [SyncViewModel], [StatsViewModel]             |
+-----------------------------------------▲-----------------------------------------+
                                          │ StateFlow / Coroutines
+-----------------------------------------▼-----------------------------------------+
|                               Core Subsystems & Services                          |
|                                                                                   |
|  [PlaybackEngine.kt] (AndroidX Media3 / ExoPlayer Engine)                         |
|     ├── Audio Focus, Gapless Transition, Local Output Muting                      |
|     └── [SleepTimerManager.kt] Countdown Timers & Pause Triggers                 |
|                                                                                   |
|  [P2pSyncClient.kt] (OkHttp WebSocket Client + NsdManager mDNS)                   |
|     ├── Bi-directional SyncFrame Protocol (`command`, `state_sync`, `stats_sync`) |
|     └── Remote Output Routing & Handover                                          |
|                                                                                   |
|  Storage & Persistence:                                                           |
|     ├── [MediaStoreAudioRepository.kt] Local Device Audio & Tag Extraction        |
|     ├── [AudioTagMetadataExtractor.kt] Embedded Quality, Bitrate, Format Badges    |
|     ├── [PrismPreferencesRepository.kt] Jetpack DataStore (Settings & Pairing)    |
|     ├── [PlaylistRepository.kt] JSON Custom Playlists                             |
|     └── [ListeningStatsEngine.kt] SQLite Database (`listening_stats.db`)          |
+-----------------------------------------------------------------------------------+
```

---

## 2. Audio Playback & Engine Architecture (`core/playback/`)

### 2.1 Playback Engine (`PlaybackEngine.kt`)
Playback is powered by **AndroidX Media3 (ExoPlayer)**:
1. **Audio Routing**:
   - Supports seamless switching between `local` and `desktop` output modes.
   - When Desktop Output is active in remote-only mode (`isRemoteOnlyActive = true`), local ExoPlayer audio output is muted or paused while maintaining UI state synchronization.
2. **Audio Focus & Headset Lifecycle**:
   - Manages audio focus requests with ducking / pausing on incoming notifications or phone calls.
   - Pauses playback gracefully on headset disconnect / Bluetooth unplug (`ACTION_AUDIO_BECOMING_NOISY`).
3. **Gapless Transitions**:
   - Pre-buffers next track items in the ExoPlayer playlist to ensure zero latency between tracks.

### 2.2 Sleep Timer (`SleepTimerManager.kt`)
- Supports 3 modes:
  1. **Time-Based**: Countdown by minutes.
  2. **Track-Based**: Countdown by remaining track completions.
  3. **End-of-Queue**: Stops playback once the active queue runs out.
- Options:
  - `finishLastSong`: Waits for the currently playing track to finish before executing the pause.
- Coordinates pause events locally and dispatches remote pause commands to the paired desktop over WebSocket.

---

## 3. P2P Companion Sync Protocol (`core/sync/`)

### 3.1 Connection & Discovery
- **Discovery**: LAN discovery uses mDNS (`_prism-sync._tcp`) via Android's `NsdManager`.
- **Alternative USB Mode**: Supports `1-Click Reverse Proxy` via ADB forwarding `127.0.0.1:9876`.
- **Authentication**: WebSocket client sends `handshake_req` containing the persistent pairing token (`clientToken`).

### 3.2 Frame Schema (`SyncProtocol.kt`)
The client exchange frames serialized via `kotlinx.serialization.json`:
- `handshake_req` / `handshake_resp`: Mutual client authentication.
- `state_sync`: Real-time playback synchronization (track title, artist, position, isPlaying, duration).
- `queue_sync`: Queue contents and current index.
- `command`: Remote control actions (`play`, `pause`, `skip_next`, `skip_previous`, `seek`, `play_track`, `play_next`, `add_to_queue`).
- `output_routing`: Dynamic toggling of local vs desktop speakers.
- `stats_sync`: Bi-directional listening history sync across devices.

---

## 4. Storage & Persistence (`data/` & `core/data/`)

1. **Jetpack DataStore (`PrismPreferencesRepository.kt`)**:
   - Stores user preferences:
     - `pairedDevices`: Array of paired hosts, ports, tokens, and device names.
     - `preventDoubleCounting`: Prevents duplicate play count entries when both devices are linked.
     - `isStatsCollectionEnabled`: Enables/disables listening analytics logging.
     - `lyricsRomanizationMode`, `lyricsTranslationMode`, `lyricsFontScale`: Fullscreen & preview lyrics display preferences.
2. **Local SQLite (`ListeningStatsEngine.kt`)**:
   - Stores listening history in `listening_stats.db`.
   - Records track title, artist, album, duration, timestamp, and device origin (`phone` or `desktop`).
   - Supports aggregation by total listening time or play counts with custom time filters (Today, 7 Days, 30 Days, All Time).
3. **Playlists (`PlaylistRepository.kt`)**:
   - Stores custom playlists as JSON files in app-internal private storage.

---

## 5. UI Guidelines & Component Conventions

1. **Icons**:
   - Always use standard **Material Design 3 icons** (`Icons.Outlined.*`, `Icons.Default.*`).
   - Outlined Timer: Use `Icons.Outlined.Timer` directly (`androidx.compose.material.icons.outlined.Timer`).
   - Custom Vector Drawables are reserved exclusively for brand-specific or non-standard icons (e.g. Lucide's tilted handheld karaoke mic `ic_karaoke_mic.xml`).
2. **Text Contrast over Album Art**:
   - Now Playing screens and lyric preview cards sit on top of dynamic fluid artwork backdrops.
   - Text elements must specify high-contrast colors (e.g. `Color.White`, `Color.White.copy(alpha = 0.8f)`) to remain fully legible regardless of artwork palette extraction.
3. **Regression Testing**:
   - All code changes must pass the 26 unit and integration tests via `.\Run-Regression-Tests.bat`.
