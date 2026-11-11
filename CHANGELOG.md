# Changelog

All notable changes to Prism Android Music Player are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-11

### Added
- Peer-to-peer companion sync with Prism Desktop over LAN (mDNS discovery, QR code scanner, manual `host:port:token` pairing) and ADB USB reverse proxy, secured with 32-character token authentication and SHA-256 CTR stream encryption.
- Bidirectional synchronization for playback state, two-tier queue, playlists, custom covers, sleep timer, and SQLite listening stats, plus atomic device handoff (preserving queue and position), multi-room playback, and smart file transfer from desktop.
- Library sorting modes (title, artist, album, duration, date added, and desktop order), dedicated Album and Artist views, custom playlist cover art/collages, fast-scroller sidebar, and configurable startup screen.
- Two-tier queue with drag-to-reorder handles, swipe-right to queue next, swipe-left to remove, and multi-select batch actions.
- Word-by-word and syllable-synced lyrics with romanization (`Replace` and `Below` modes), translations, and animated bubble-to-note instrumental interlude indicators.
- Sleep timer modal supporting minute, track-count, and end-of-queue countdowns with a bottom-bar countdown label.
- GitHub release update checker on launch and in Settings.

### Fixed
- Repeat mode transitions on manual skip, playback position resumption across restarts, and persistent Media3 notification skip controls.
- Screen-wake volume ducking, audio focus resumption, and LRCLIB query sanitization.
- Dynamic palette contrast and Material 3 color mapping over album artwork backdrops.

## [0.1.0] - 2026-10-24

### Added
- Initial baseline of the native Kotlin/Compose Prism Android Music Player with AndroidX Media3 (ExoPlayer) playback, MediaStore indexing, embedded tag/format badges, dynamic Palette theming, LRC/TTML lyrics, and local SQLite listening stats.
