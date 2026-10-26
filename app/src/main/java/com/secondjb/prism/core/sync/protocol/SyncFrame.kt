package com.secondjb.prism.core.sync.protocol

import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bi-directional message frame protocol between Prism Android and Prism Windows.
 */
@Serializable
sealed class SyncFrame {

    @Serializable
    @SerialName("handshake_req")
    data class HandshakeRequest(
        val clientToken: String,
        val deviceName: String,
        val clientVersion: String = "0.1.0"
    ) : SyncFrame()

    @Serializable
    @SerialName("handshake_resp")
    data class HandshakeResponse(
        val isApproved: Boolean,
        val serverDeviceName: String,
        val sessionId: String
    ) : SyncFrame()

    @Serializable
    @SerialName("state_sync")
    data class StateSync(
        val playbackState: PlaybackState
    ) : SyncFrame()

    @Serializable
    @SerialName("queue_sync")
    data class QueueSync(
        val queue: List<Track>,
        val currentIndex: Int
    ) : SyncFrame()

    @Serializable
    @SerialName("command")
    data class PlaybackCommand(
        val action: CommandAction,
        val seekPositionMs: Long? = null,
        val volume: Float? = null,
        val repeatMode: RepeatMode? = null,
        val trackTitle: String? = null,
        val trackArtist: String? = null,
        val trackFilename: String? = null,
        val trackUri: String? = null,
        val queueFilenames: List<String>? = null,
        val sleepTimerMode: String? = null,
        val sleepTimerValue: Long? = null
    ) : SyncFrame()

    @Serializable
    @SerialName("swap_device")
    data class SwapDeviceTarget(
        val targetDeviceId: String
    ) : SyncFrame()

    @Serializable
    @SerialName("track_not_found")
    data class TrackNotFound(
        val trackTitle: String,
        val trackArtist: String,
        val trackFilename: String
    ) : SyncFrame()

    @Serializable
    @SerialName("manifest_req")
    data class ManifestRequest(
        val clientType: String = "android"
    ) : SyncFrame()

    @Serializable
    @SerialName("manifest_resp")
    data class ManifestResponse(
        val tracks: List<ManifestTrackEntry>
    ) : SyncFrame()

    @Serializable
    @SerialName("file_chunk")
    data class FileChunk(
        val fileId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val dataBase64: String,
        val sizeBytes: Long,
        val isLast: Boolean
    ) : SyncFrame()

    @Serializable
    @SerialName("routing_sync")
    data class OutputRoutingSync(
        val isDesktopEnabled: Boolean,
        val isCompanionEnabled: Boolean,
        val starredDeviceId: String? = null
    ) : SyncFrame()

    @Serializable
    @SerialName("stats_sync")
    data class StatsSync(
        val events: List<SyncListeningEventItem>
    ) : SyncFrame()

    @Serializable
    @SerialName("playlist_sync")
    data class PlaylistSync(
        val playlists: List<SyncPlaylistDto>
    ) : SyncFrame()

    @Serializable
    @SerialName("enc")
    data class Encrypted(
        val n: String,
        val d: String
    ) : SyncFrame()
}

@Serializable
data class SyncPlaylistDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val isPinned: Boolean = false,
    val tracks: List<SyncTrackKeyDto> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class SyncTrackKeyDto(
    val title: String,
    val artist: String,
    val filename: String,
    val durationMs: Long = 0L
)

@Serializable
data class SyncListeningEventItem(
    @SerialName("song_title") val songTitle: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("played_at") val playedAt: String
)

@Serializable
data class ManifestTrackEntry(
    val filename: String,
    val title: String,
    val artist: String,
    val sizeBytes: Long = 0L,
    val durationSecs: Double = 0.0
)

@Serializable
enum class CommandAction {
    PLAY,
    PAUSE,
    TOGGLE_PLAY_PAUSE,
    SEEK,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    SET_VOLUME,
    TOGGLE_SHUFFLE,
    SET_REPEAT,
    PLAY_TRACK,
    PLAY_NEXT,
    ADD_TO_QUEUE,
    REQUEST_FILE_DOWNLOAD,
    SET_SLEEP_TIMER,
    CANCEL_SLEEP_TIMER
}
