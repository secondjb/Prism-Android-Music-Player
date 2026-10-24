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
        val clientVersion: String = "1.0.0"
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
        val repeatMode: RepeatMode? = null
    ) : SyncFrame()

    @Serializable
    @SerialName("swap_device")
    data class SwapDeviceTarget(
        val targetDeviceId: String
    ) : SyncFrame()
}

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
    SET_REPEAT
}
