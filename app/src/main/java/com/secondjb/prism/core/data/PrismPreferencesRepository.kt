package com.secondjb.prism.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.secondjb.prism.domain.model.Track

@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val isFamilyMember: Boolean = false,
    val lastConnectedTimeMs: Long = System.currentTimeMillis()
)

@Serializable
data class PersistedQueueState(
    val userQueue: List<Track> = emptyList(),
    val contextQueue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrackId: String? = null,
    val activePlaylistId: String? = null,
    val positionMs: Long = 0L,
    val isShuffled: Boolean = false,
    val repeatMode: String = "OFF"
)

/**
 * Manages persisted user preferences for Prism Android with feature parity to Prism Windows.
 */
data class PrismPreferences(
    // Library
    val minDurationSeconds: Int = 10,
    val enabledFormats: Set<String> = setOf("FLAC", "MP3", "M4A", "WAV", "OGG", "OPUS"),
    val autoScanOnStartup: Boolean = true,
    val includedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),

    // Audio & Playback
    val isGaplessEnabled: Boolean = true,
    val replayGainMode: String = "track", // "track", "album", "off"
    val crossfadeDurationSeconds: Int = 0, // 0, 2, 4, 6
    val isShuffleEnabled: Boolean = false,
    val repeatMode: String = "OFF", // "OFF", "ALL", "ONE"

    // Navigation & View
    val defaultStartupScreen: String = "Home",
    val librarySortOrder: String = "title_asc",
    val songSortOption: String = "TITLE_ASC",
    val albumSortOption: String = "TITLE_ASC",
    val artistSortOption: String = "NAME_ASC",
    val playlistSortOption: String = "NAME_ASC",
    val libraryFilterChip: String = "ALL_SONGS",
    val downloadArtistImages: Boolean = true,
    val artistImagesWifiOnly: Boolean = true,

    // Lyrics & Visuals
    val lrclibAutoFetch: Boolean = false, // Offline-first default
    val preferOnlineLyrics: Boolean = false,
    val isRomanizationEnabled: Boolean = true,
    val isWavySeekbarEnabled: Boolean = true,
    val lyricsRomanizationMode: String = "OFF",
    val lyricsTranslationMode: String = "OFF",
    val lyricsFontScale: Float = 1.0f,

    // Theme & Appearance
    val isDynamicThemingEnabled: Boolean = true,
    val darkThemeMode: String = "system", // "system", "dark", "light"

    // Listening Stats & Companion Sync
    val autoSyncStats: Boolean = true,
    val preventDoubleCounting: Boolean = true,
    val isStatsCollectionEnabled: Boolean = true,
    val isDeviceFamilyEnabled: Boolean = false,
    val simultaneousConflictMode: String = "DESYNC", // "DESYNC", "REMOTE", "LOCAL"

    // App Updates
    val checkUpdatesOnStartup: Boolean = true
)

class PrismPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<PrismPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): PrismPreferences {
        return PrismPreferences(
            minDurationSeconds = prefs.getInt(KEY_MIN_DURATION, 10),
            enabledFormats = prefs.getStringSet(KEY_FORMATS, setOf("FLAC", "MP3", "M4A", "WAV", "OGG", "OPUS")) ?: setOf("FLAC", "MP3", "M4A", "WAV", "OGG", "OPUS"),
            autoScanOnStartup = prefs.getBoolean(KEY_AUTO_SCAN, true),
            includedFolders = prefs.getStringSet(KEY_INCLUDED_FOLDERS, emptySet()) ?: emptySet(),
            excludedFolders = prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet(),
            isGaplessEnabled = prefs.getBoolean(KEY_GAPLESS, true),
            replayGainMode = prefs.getString(KEY_REPLAY_GAIN, "track") ?: "track",
            crossfadeDurationSeconds = prefs.getInt(KEY_CROSSFADE, 0),
            lrclibAutoFetch = prefs.getBoolean(KEY_LRCLIB, false),
            preferOnlineLyrics = prefs.getBoolean(KEY_PREFER_ONLINE, false),
            isRomanizationEnabled = prefs.getBoolean(KEY_ROMANIZATION, true),
            isWavySeekbarEnabled = prefs.getBoolean(KEY_WAVY_SEEKBAR, true),
            lyricsRomanizationMode = prefs.getString(KEY_LYRICS_ROMANIZATION_MODE, "OFF") ?: "OFF",
            lyricsTranslationMode = prefs.getString(KEY_LYRICS_TRANSLATION_MODE, "OFF") ?: "OFF",
            lyricsFontScale = prefs.getFloat(KEY_LYRICS_FONT_SCALE, 1.0f),
            isDynamicThemingEnabled = prefs.getBoolean(KEY_DYNAMIC_THEME, true),
            darkThemeMode = prefs.getString(KEY_DARK_THEME_MODE, "system") ?: "system",
            autoSyncStats = prefs.getBoolean(KEY_AUTO_SYNC_STATS, true),
            preventDoubleCounting = prefs.getBoolean(KEY_PREVENT_DOUBLE_COUNTING, true),
            isStatsCollectionEnabled = prefs.getBoolean(KEY_STATS_COLLECTION_ENABLED, true),
            isDeviceFamilyEnabled = prefs.getBoolean(KEY_DEVICE_FAMILY_ENABLED, false),
            simultaneousConflictMode = prefs.getString(KEY_SIMULTANEOUS_CONFLICT_MODE, "DESYNC") ?: "DESYNC",
            checkUpdatesOnStartup = prefs.getBoolean(KEY_CHECK_UPDATES_ON_STARTUP, true),
            isShuffleEnabled = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false),
            repeatMode = prefs.getString(KEY_REPEAT_MODE, "OFF") ?: "OFF",
            defaultStartupScreen = prefs.getString(KEY_DEFAULT_STARTUP_SCREEN, "Home") ?: "Home",
            librarySortOrder = prefs.getString(KEY_LIBRARY_SORT_ORDER, "title_asc") ?: "title_asc",
            songSortOption = prefs.getString(KEY_SONG_SORT_OPTION, "TITLE_ASC") ?: "TITLE_ASC",
            albumSortOption = prefs.getString(KEY_ALBUM_SORT_OPTION, "TITLE_ASC") ?: "TITLE_ASC",
            artistSortOption = prefs.getString(KEY_ARTIST_SORT_OPTION, "NAME_ASC") ?: "NAME_ASC",
            playlistSortOption = prefs.getString(KEY_PLAYLIST_SORT_OPTION, "NAME_ASC") ?: "NAME_ASC",
            libraryFilterChip = prefs.getString(KEY_LIBRARY_FILTER_CHIP, "ALL_SONGS") ?: "ALL_SONGS",
            downloadArtistImages = prefs.getBoolean(KEY_DOWNLOAD_ARTIST_IMAGES, true),
            artistImagesWifiOnly = prefs.getBoolean(KEY_ARTIST_IMAGES_WIFI_ONLY, true)
        )
    }

    fun setDownloadArtistImages(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_ARTIST_IMAGES, enabled).apply()
        _preferences.value = _preferences.value.copy(downloadArtistImages = enabled)
    }

    fun setArtistImagesWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTIST_IMAGES_WIFI_ONLY, enabled).apply()
        _preferences.value = _preferences.value.copy(artistImagesWifiOnly = enabled)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isShuffleEnabled = enabled)
    }

    fun setRepeatMode(mode: String) {
        prefs.edit().putString(KEY_REPEAT_MODE, mode).apply()
        _preferences.value = _preferences.value.copy(repeatMode = mode)
    }

    fun setDefaultStartupScreen(screen: String) {
        prefs.edit().putString(KEY_DEFAULT_STARTUP_SCREEN, screen).apply()
        _preferences.value = _preferences.value.copy(defaultStartupScreen = screen)
    }

    fun setLibrarySortOrder(sort: String) {
        prefs.edit().putString(KEY_LIBRARY_SORT_ORDER, sort).apply()
        _preferences.value = _preferences.value.copy(librarySortOrder = sort)
    }

    fun setSongSortOption(sort: String) {
        prefs.edit().putString(KEY_SONG_SORT_OPTION, sort).apply()
        _preferences.value = _preferences.value.copy(songSortOption = sort)
    }

    fun setAlbumSortOption(sort: String) {
        prefs.edit().putString(KEY_ALBUM_SORT_OPTION, sort).apply()
        _preferences.value = _preferences.value.copy(albumSortOption = sort)
    }

    fun setArtistSortOption(sort: String) {
        prefs.edit().putString(KEY_ARTIST_SORT_OPTION, sort).apply()
        _preferences.value = _preferences.value.copy(artistSortOption = sort)
    }

    fun setPlaylistSortOption(sort: String) {
        prefs.edit().putString(KEY_PLAYLIST_SORT_OPTION, sort).apply()
        _preferences.value = _preferences.value.copy(playlistSortOption = sort)
    }

    fun setLibraryFilterChip(chip: String) {
        prefs.edit().putString(KEY_LIBRARY_FILTER_CHIP, chip).apply()
        _preferences.value = _preferences.value.copy(libraryFilterChip = chip)
    }

    fun savePcTrackOrderList(order: List<String>) {
        try {
            val serialized = json.encodeToString(order)
            prefs.edit().putString(KEY_PC_TRACK_ORDER, serialized).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getPcTrackOrderList(): List<String> {
        val raw = prefs.getString(KEY_PC_TRACK_ORDER, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setMinDurationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_MIN_DURATION, seconds).apply()
        _preferences.value = _preferences.value.copy(minDurationSeconds = seconds)
    }

    fun toggleFormat(format: String) {
        val current = _preferences.value.enabledFormats.toMutableSet()
        if (current.contains(format)) {
            if (current.size > 1) current.remove(format)
        } else {
            current.add(format)
        }
        prefs.edit().putStringSet(KEY_FORMATS, current).apply()
        _preferences.value = _preferences.value.copy(enabledFormats = current)
    }

    fun setGaplessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply()
        _preferences.value = _preferences.value.copy(isGaplessEnabled = enabled)
    }

    fun setReplayGainMode(mode: String) {
        prefs.edit().putString(KEY_REPLAY_GAIN, mode).apply()
        _preferences.value = _preferences.value.copy(replayGainMode = mode)
    }

    fun setCrossfadeDuration(seconds: Int) {
        prefs.edit().putInt(KEY_CROSSFADE, seconds).apply()
        _preferences.value = _preferences.value.copy(crossfadeDurationSeconds = seconds)
    }

    fun setLrclibAutoFetch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LRCLIB, enabled).apply()
        _preferences.value = _preferences.value.copy(lrclibAutoFetch = enabled)
    }

    fun setWavySeekbarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAVY_SEEKBAR, enabled).apply()
        _preferences.value = _preferences.value.copy(isWavySeekbarEnabled = enabled)
    }

    fun setDynamicThemingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_THEME, enabled).apply()
        _preferences.value = _preferences.value.copy(isDynamicThemingEnabled = enabled)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _pairedDevices = MutableStateFlow(loadPairedDevices())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private fun loadPairedDevices(): List<PairedDevice> {
        val raw = prefs.getString(KEY_PAIRED_DEVICES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PairedDevice>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePairedDevice(device: PairedDevice) {
        val current = _pairedDevices.value.filter { !(it.host == device.host && it.port == device.port) }.toMutableList()
        current.add(0, device) // most recent at top
        val serialized = json.encodeToString(current)
        prefs.edit().putString(KEY_PAIRED_DEVICES, serialized).apply()
        _pairedDevices.value = current
    }

    fun removePairedDevice(deviceIdOrHost: String) {
        val updated = _pairedDevices.value.filter { it.id != deviceIdOrHost && it.host != deviceIdOrHost }
        val serialized = json.encodeToString(updated)
        prefs.edit().putString(KEY_PAIRED_DEVICES, serialized).apply()
        _pairedDevices.value = updated
    }

    fun addIncludedFolder(folder: String) {
        val clean = folder.trim()
        if (clean.isEmpty()) return
        val current = _preferences.value.includedFolders.toMutableSet().apply { add(clean) }
        prefs.edit().putStringSet(KEY_INCLUDED_FOLDERS, current).apply()
        _preferences.value = _preferences.value.copy(includedFolders = current)
    }

    fun removeIncludedFolder(folder: String) {
        val current = _preferences.value.includedFolders.toMutableSet().apply { remove(folder) }
        prefs.edit().putStringSet(KEY_INCLUDED_FOLDERS, current).apply()
        _preferences.value = _preferences.value.copy(includedFolders = current)
    }

    fun addExcludedFolder(folder: String) {
        val clean = folder.trim()
        if (clean.isEmpty()) return
        val current = _preferences.value.excludedFolders.toMutableSet().apply { add(clean) }
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply()
        _preferences.value = _preferences.value.copy(excludedFolders = current)
    }

    fun removeExcludedFolder(folder: String) {
        val current = _preferences.value.excludedFolders.toMutableSet().apply { remove(folder) }
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply()
        _preferences.value = _preferences.value.copy(excludedFolders = current)
    }

    fun setDarkThemeMode(mode: String) {
        prefs.edit().putString(KEY_DARK_THEME_MODE, mode).apply()
        _preferences.value = _preferences.value.copy(darkThemeMode = mode)
    }

    fun setAutoSyncStats(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_STATS, enabled).apply()
        _preferences.value = _preferences.value.copy(autoSyncStats = enabled)
    }

    fun setPreventDoubleCounting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVENT_DOUBLE_COUNTING, enabled).apply()
        _preferences.value = _preferences.value.copy(preventDoubleCounting = enabled)
    }

    fun setStatsCollectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STATS_COLLECTION_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isStatsCollectionEnabled = enabled)
    }

    fun setLyricsRomanizationMode(mode: String) {
        prefs.edit().putString(KEY_LYRICS_ROMANIZATION_MODE, mode).apply()
        _preferences.value = _preferences.value.copy(lyricsRomanizationMode = mode)
    }

    fun setLyricsTranslationMode(mode: String) {
        prefs.edit().putString(KEY_LYRICS_TRANSLATION_MODE, mode).apply()
        _preferences.value = _preferences.value.copy(lyricsTranslationMode = mode)
    }

    fun setLyricsFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_LYRICS_FONT_SCALE, scale).apply()
        _preferences.value = _preferences.value.copy(lyricsFontScale = scale)
    }

    fun setDeviceFamilyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEVICE_FAMILY_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isDeviceFamilyEnabled = enabled)
    }

    fun setSimultaneousConflictMode(mode: String) {
        prefs.edit().putString(KEY_SIMULTANEOUS_CONFLICT_MODE, mode).apply()
        _preferences.value = _preferences.value.copy(simultaneousConflictMode = mode)
    }

    fun saveQueueState(
        userQueue: List<Track>,
        contextQueue: List<Track>,
        currentIndex: Int,
        currentTrackId: String?,
        activePlaylistId: String?,
        positionMs: Long = 0L,
        isShuffled: Boolean = false,
        repeatMode: String = "OFF"
    ) {
        try {
            val state = PersistedQueueState(
                userQueue = userQueue,
                contextQueue = contextQueue,
                currentIndex = currentIndex,
                currentTrackId = currentTrackId,
                activePlaylistId = activePlaylistId,
                positionMs = positionMs,
                isShuffled = isShuffled,
                repeatMode = repeatMode
            )
            val jsonStr = json.encodeToString(state)
            prefs.edit().putString(KEY_PERSISTED_QUEUE, jsonStr).apply()
        } catch (e: Exception) {
            // Ignore serialization failure
        }
    }

    fun loadPersistedQueueState(): PersistedQueueState? {
        val jsonStr = prefs.getString(KEY_PERSISTED_QUEUE, null) ?: return null
        return try {
            json.decodeFromString<PersistedQueueState>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    fun setCheckUpdatesOnStartup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHECK_UPDATES_ON_STARTUP, enabled).apply()
        _preferences.value = _preferences.value.copy(checkUpdatesOnStartup = enabled)
    }

    companion object {
        private const val PREFS_NAME = "prism_user_preferences"
        private const val KEY_MIN_DURATION = "key_min_duration"
        private const val KEY_FORMATS = "key_formats"
        private const val KEY_AUTO_SCAN = "key_auto_scan"
        private const val KEY_INCLUDED_FOLDERS = "key_included_folders"
        private const val KEY_EXCLUDED_FOLDERS = "key_excluded_folders"
        private const val KEY_GAPLESS = "key_gapless"
        private const val KEY_REPLAY_GAIN = "key_replay_gain"
        private const val KEY_CROSSFADE = "key_crossfade"
        private const val KEY_LRCLIB = "key_lrclib"
        private const val KEY_PREFER_ONLINE = "key_prefer_online"
        private const val KEY_ROMANIZATION = "key_romanization"
        private const val KEY_WAVY_SEEKBAR = "key_wavy_seekbar"
        private const val KEY_DYNAMIC_THEME = "key_dynamic_theme"
        private const val KEY_DARK_THEME_MODE = "key_dark_theme_mode"
        private const val KEY_PAIRED_DEVICES = "key_paired_devices"
        private const val KEY_AUTO_SYNC_STATS = "key_auto_sync_stats"
        private const val KEY_PREVENT_DOUBLE_COUNTING = "key_prevent_double_counting"
        private const val KEY_STATS_COLLECTION_ENABLED = "key_stats_collection_enabled"
        private const val KEY_DEVICE_FAMILY_ENABLED = "key_device_family_enabled"
        private const val KEY_SIMULTANEOUS_CONFLICT_MODE = "key_simultaneous_conflict_mode"
        private const val KEY_PERSISTED_QUEUE = "key_persisted_queue"
        private const val KEY_LYRICS_ROMANIZATION_MODE = "key_lyrics_romanization_mode"
        private const val KEY_LYRICS_TRANSLATION_MODE = "key_lyrics_translation_mode"
        private const val KEY_LYRICS_FONT_SCALE = "key_lyrics_font_scale"
        private const val KEY_CHECK_UPDATES_ON_STARTUP = "key_check_updates_on_startup"
        private const val KEY_SHUFFLE_ENABLED = "key_shuffle_enabled"
        private const val KEY_REPEAT_MODE = "key_repeat_mode"
        private const val KEY_DEFAULT_STARTUP_SCREEN = "key_default_startup_screen"
        private const val KEY_LIBRARY_SORT_ORDER = "key_library_sort_order"
        private const val KEY_SONG_SORT_OPTION = "key_song_sort_option"
        private const val KEY_ALBUM_SORT_OPTION = "key_album_sort_option"
        private const val KEY_ARTIST_SORT_OPTION = "key_artist_sort_option"
        private const val KEY_PLAYLIST_SORT_OPTION = "key_playlist_sort_option"
        private const val KEY_LIBRARY_FILTER_CHIP = "key_library_filter_chip"
        private const val KEY_PC_TRACK_ORDER = "key_pc_track_order"
        private const val KEY_DOWNLOAD_ARTIST_IMAGES = "key_download_artist_images"
        private const val KEY_ARTIST_IMAGES_WIFI_ONLY = "key_artist_images_wifi_only"
    }
}
