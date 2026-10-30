package com.secondjb.prism.core.sync.transport

import android.util.Log
import com.secondjb.prism.core.sync.protocol.SyncFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Local Area Network WebSocket transport implementation of SyncTransport.
 * Establishes real-time bi-directional synchronization with low latency (<50ms).
 */
class LanWebSocketSyncTransport(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SyncTransport {

    override val transportId: String = "LAN_WEBSOCKET"

    private val _connectionState = MutableStateFlow<TransportConnectionState>(TransportConnectionState.Disconnected)
    override val connectionState: StateFlow<TransportConnectionState> = _connectionState.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<SyncFrame>(extraBufferCapacity = 64)
    override val incomingFrames: SharedFlow<SyncFrame> = _incomingFrames.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .build()

    private var activeWebSocket: WebSocket? = null

    override suspend fun connect(endpoint: ConnectionEndpoint): Result<Unit> = runCatching {
        disconnect()

        val url = "ws://${endpoint.host}:${endpoint.port}/prism-sync"
        Log.i("PrismSync", "Initiating WebSocket connection to $url with token: ${endpoint.authToken?.take(6)}...")
        _connectionState.value = TransportConnectionState.Connecting(url)

        val request = Request.Builder()
            .url(url)
            .apply {
                if (!endpoint.authToken.isNullOrBlank()) {
                    addHeader("X-Prism-Auth-Token", endpoint.authToken)
                }
            }
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("PrismSync", "WebSocket opened successfully to $url (HTTP ${response.code})")
                _connectionState.value = TransportConnectionState.Connected(
                    peerName = "Prism Desktop (${endpoint.host})",
                    endpoint = url
                )
                // Automatically send handshake frame
                scope.launch {
                    val handshake = SyncFrame.HandshakeRequest(
                        clientToken = endpoint.authToken ?: "",
                        deviceName = "Prism Android Companion"
                    )
                    sendFrame(handshake)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val frame = json.decodeFromString<SyncFrame>(text)
                    Log.d("PrismSync", "Received sync frame: ${frame::class.simpleName}")
                    scope.launch {
                        _incomingFrames.emit(frame)
                    }
                } catch (e: Exception) {
                    Log.e("PrismSync", "Failed to deserialize frame: ${e.message}", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("PrismSync", "WebSocket closed ($code: $reason)")
                _connectionState.value = TransportConnectionState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "Connection failed"
                Log.e("PrismSync", "WebSocket failure connecting to $url: $msg (HTTP ${response?.code})", t)
                _connectionState.value = TransportConnectionState.Error(msg)
            }
        })
    }

    override suspend fun sendFrame(frame: SyncFrame): Result<Unit> = runCatching {
        val serialized = json.encodeToString(frame)
        val sent = activeWebSocket?.send(serialized) ?: false
        if (!sent) {
            throw IllegalStateException("Failed to send frame over WebSocket: transport disconnected")
        }
    }

    override suspend fun disconnect() {
        try {
            activeWebSocket?.send("""{"type":"disconnect","reason":"user_disconnect"}""")
        } catch (_: Exception) {}
        try {
            activeWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        try {
            activeWebSocket?.cancel()
        } catch (_: Exception) {}
        activeWebSocket = null
        _connectionState.value = TransportConnectionState.Disconnected
    }
}
