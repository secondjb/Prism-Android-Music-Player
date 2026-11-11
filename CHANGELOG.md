# Changelog

All notable changes to Prism Android Music Player are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-11

### Added
- Library sorting modes (title, artist, album, duration, date added, and desktop device-family order), dedicated Album and Artist views, and configurable startup screen setting.
- Custom playlist cover art, 4-tile playlist cover collages, and bidirectional playlist/cover sync with Prism Desktop.
- Two-tier queue (custom priority queue vs context queue) with drag-to-reorder handles, damped swipe gestures (swipe right to add next, swipe left to remove), and batch multi-selection queue actions.
- Seamless atomic device handoff preserving queue, playback position, and state, plus multi-room synchronized playback.
- Word-by-word and syllable-synced romanization (`Replace` and `Below` modes) in `ModernLyricsView` and `FullScreenLyricsPage`.
- Animated instrumental interlude indicator with 3-dot bubble-to-note canvas transitions matching Prism Desktop timing.
- Redesigned sleep timer modal with minute, track-count, and end-of-queue countdowns plus a live bottom-bar countdown label.

### Fixed
- Fixed repeat mode transitions on manual skip, position resumption across restarts, and persistent Media3 notification skip controls.
- Eliminated screen-wake volume ducking, rogue cold-start remote commands, multi-room play/pause phase inversion, and queue sync clobbering.
- Improved LAN/subnet auto-reconnection, mDNS endpoint auto-healing on DHCP changes, and LRCLIB query sanitization.
- Mapped Material 3 error color tokens to the dynamic album primary color for consistent artwork-derived theming.

## [0.1.0] - 2026-10-24

### Added
- Initial release of Prism Android Music Player.
- Local playback powered by AndroidX Media3 (ExoPlayer) with audio focus and noisy-audio handling.
- MediaStore library indexing, embedded tag/format badge extraction, and dynamic album-art Palette theming.
- Synchronized LRC/TTML/LRCLIB lyrics viewer, local SQLite listening statistics, and P2P companion sync with Prism Desktop.
