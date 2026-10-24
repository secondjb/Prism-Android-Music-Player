package com.secondjb.prism.core.sync.transport

import com.secondjb.prism.core.sync.protocol.SyncFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed class TransportConnectionState {
    data object Disconnected : TransportConnectionState()
    data class Connecting(val endpoint: String) : TransportConnectionState()
    data class Connected(val peerName: String, val endpoint: String) : TransportConnectionState()
    data class Error(val message: String) : TransportConnectionState()
}

data class ConnectionEndpoint(
    val host: String,
    val port: Int,
    val authToken: String? = null,
    val fingerprint: String? = null
)

/**
 * Modular transport contract for P2P synchronization.
 * Decouples synchronization logic from physical network transport
 * (LAN WebSocket today, Bluetooth/Relay tomorrow).
 */
interface SyncTransport {
    val transportId: String
    val connectionState: StateFlow<TransportConnectionState>
    val incomingFrames: SharedFlow<SyncFrame>

    suspend fun connect(endpoint: ConnectionEndpoint): Result<Unit>
    suspend fun sendFrame(frame: SyncFrame): Result<Unit>
    suspend fun disconnect()
}
