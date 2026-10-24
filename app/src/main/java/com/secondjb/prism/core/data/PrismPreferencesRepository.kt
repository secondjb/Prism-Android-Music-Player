package com.secondjb.prism.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val lastConnectedTimeMs: Long = System.currentTimeMillis()
)

/**
 * Manages persisted user preferences for Prism Android with feature parity to Prism Windows.
 */
data class PrismPreferences(
    // Library
    val minDurationSeconds: Int = 10,
    val enabledFormats: Set<String> = setOf("FLAC", "MP3", "M4A", "WAV", "OGG", "OPUS"),
    val autoScanOnStartup: Boolean = true,

    // Audio & Playback
    val isGaplessEnabled: Boolean = true,
    val replayGainMode: String = "track", // "track", "album", "off"
    val crossfadeDurationSeconds: Int = 0, // 0, 2, 4, 6

    // Lyrics & Visuals
    val lrclibAutoFetch: Boolean = false, // Offline-first default
    val preferOnlineLyrics: Boolean = false,
    val isRomanizationEnabled: Boolean = true,
    val isWavySeekbarEnabled: Boolean = true,

    // Theme & Appearance
    val isDynamicThemingEnabled: Boolean = true,
    val darkThemeMode: String = "system" // "system", "dark", "light"
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
            isGaplessEnabled = prefs.getBoolean(KEY_GAPLESS, true),
            replayGainMode = prefs.getString(KEY_REPLAY_GAIN, "track") ?: "track",
            crossfadeDurationSeconds = prefs.getInt(KEY_CROSSFADE, 0),
            lrclibAutoFetch = prefs.getBoolean(KEY_LRCLIB, false),
            preferOnlineLyrics = prefs.getBoolean(KEY_PREFER_ONLINE, false),
            isRomanizationEnabled = prefs.getBoolean(KEY_ROMANIZATION, true),
            isWavySeekbarEnabled = prefs.getBoolean(KEY_WAVY_SEEKBAR, true),
            isDynamicThemingEnabled = prefs.getBoolean(KEY_DYNAMIC_THEME, true),
            darkThemeMode = prefs.getString(KEY_DARK_THEME_MODE, "system") ?: "system"
        )
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

    fun setDarkThemeMode(mode: String) {
        prefs.edit().putString(KEY_DARK_THEME_MODE, mode).apply()
        _preferences.value = _preferences.value.copy(darkThemeMode = mode)
    }

    companion object {
        private const val PREFS_NAME = "prism_user_preferences"
        private const val KEY_MIN_DURATION = "key_min_duration"
        private const val KEY_FORMATS = "key_formats"
        private const val KEY_AUTO_SCAN = "key_auto_scan"
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
    }
}
