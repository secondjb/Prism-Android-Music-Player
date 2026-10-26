package com.secondjb.prism.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a musical track within the Prism ecosystem, interoperable with Prism Windows.
 */
@Serializable
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 2,
    val bitRateKbps: Int? = null,
    val format: String = "MP3",
    val artworkUri: String? = null,
    val lyrics: String? = null,
    val filePath: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val durationSecs: Long
        get() = durationMs / 1000

    val audioFormatInfo: AudioFormatInfo
        get() = AudioFormatInfo(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            bitRateKbps = bitRateKbps,
            format = format
        )
}

/**
 * Rich audio metadata badge details (FLAC, Hi-Res, Bitrate, Sample Rate).
 */
@Serializable
data class AudioFormatInfo(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val bitRateKbps: Int?,
    val format: String
) {
    val isHiRes: Boolean
        get() = sampleRate >= 96000 || bitDepth >= 24

    val formattedSampleRate: String
        get() = if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz" else "${sampleRate / 1000.0} kHz"

    val displayBadge: String
        get() = buildString {
            append(format.uppercase())
            if (bitDepth > 0 && sampleRate > 0) {
                append(" • ")
                append("${bitDepth}-bit / $formattedSampleRate")
            }
            if (bitRateKbps != null && bitRateKbps > 0) {
                append(" • ")
                append("${bitRateKbps} kbps")
            }
        }
}

@Serializable
enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

/**
 * Unified playback state supporting local ExoPlayer and remote Windows companion playback.
 */
@Serializable
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val activeDeviceId: String = "local",
    val userQueue: List<Track> = emptyList(),
    val activePlaylistId: String? = null,
    val lastActionTimestampMs: Long = 0L
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasNext: Boolean
        get() = userQueue.isNotEmpty() || (queue.isNotEmpty() && (queueIndex < queue.lastIndex || repeatMode == RepeatMode.ALL))

    val hasPrevious: Boolean
        get() = queue.isNotEmpty() && (queueIndex > 0 || positionMs > 3000L)
}

/**
 * Playlist model with pinning capability.
 */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val trackIds: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Album collection model.
 */
@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val year: Int? = null,
    val isPinned: Boolean = false
)

/**
 * Artist collection model.
 */
@Serializable
data class Artist(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artworkUri: String? = null,
    val isPinned: Boolean = false
)

@Serializable
enum class DeviceType {
    ANDROID,
    WINDOWS_DESKTOP,
    UNKNOWN
}

/**
 * Peer device in the Prism LAN ecosystem.
 */
@Serializable
data class DeviceNode(
    val id: String,
    val name: String,
    val type: DeviceType,
    val ipAddress: String,
    val port: Int,
    val isCurrentDevice: Boolean = false,
    val isPaired: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Aggregate stats displayed on the Main Screen.
 */
@Serializable
data class ListeningStats(
    val totalTracksPlayed: Int = 0,
    val totalListeningSeconds: Long = 0L,
    val topArtist: String? = null,
    val topAlbum: String? = null,
    val flacTracksCount: Int = 0,
    val hiResTracksCount: Int = 0
)
