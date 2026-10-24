package com.secondjb.prism.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondjb.prism.core.sync.discovery.NsdDiscoveryManager
import com.secondjb.prism.core.sync.protocol.CommandAction
import com.secondjb.prism.core.sync.protocol.SyncFrame
import com.secondjb.prism.core.sync.qr.QrPairingParser
import com.secondjb.prism.core.sync.transport.ConnectionEndpoint
import com.secondjb.prism.core.sync.transport.LanWebSocketSyncTransport
import com.secondjb.prism.core.sync.transport.SyncTransport
import com.secondjb.prism.core.sync.transport.TransportConnectionState
import com.secondjb.prism.domain.model.DeviceNode
import com.secondjb.prism.domain.model.DeviceType
import com.secondjb.prism.domain.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncViewModel(
    val transport: SyncTransport = LanWebSocketSyncTransport(),
    val discoveryManager: NsdDiscoveryManager? = null
) : ViewModel() {

    val connectionState: StateFlow<TransportConnectionState> = transport.connectionState
    val discoveredDevices: StateFlow<List<DeviceNode>> = discoveryManager?.discoveredDevices ?: MutableStateFlow(emptyList())

    private val _activeDeviceId = MutableStateFlow("local")
    val activeDeviceId: StateFlow<String> = _activeDeviceId.asStateFlow()

    private val _remotePlaybackState = MutableStateFlow<PlaybackState?>(null)
    val remotePlaybackState: StateFlow<PlaybackState?> = _remotePlaybackState.asStateFlow()

    private val _lastPairingError = MutableStateFlow<String?>(null)
    val lastPairingError: StateFlow<String?> = _lastPairingError.asStateFlow()

    init {
        discoveryManager?.startDiscovery()
        observeIncomingFrames()
    }

    private fun observeIncomingFrames() {
        viewModelScope.launch {
            transport.incomingFrames.collect { frame ->
                when (frame) {
                    is SyncFrame.StateSync -> {
                        _remotePlaybackState.value = frame.playbackState
                    }
                    is SyncFrame.SwapDeviceTarget -> {
                        _activeDeviceId.value = frame.targetDeviceId
                    }
                    is SyncFrame.HandshakeResponse -> {
                        if (frame.isApproved) {
                            _activeDeviceId.value = "remote"
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun pairWithQrCode(rawCode: String, onConnected: () -> Unit = {}) {
        val result = QrPairingParser.parse(rawCode)
        result.onSuccess { pairing ->
            _lastPairingError.value = null
            viewModelScope.launch {
                transport.connect(pairing.endpoint)
                    .onSuccess {
                        _activeDeviceId.value = "remote"
                        onConnected()
                    }
                    .onFailure { err ->
                        _lastPairingError.value = "Connection failed: ${err.message}"
                    }
            }
        }.onFailure { err ->
            _lastPairingError.value = "Invalid QR code format: ${err.message}"
        }
    }

    fun connectToDevice(device: DeviceNode) {
        viewModelScope.launch {
            val endpoint = ConnectionEndpoint(host = device.ipAddress, port = device.port)
            transport.connect(endpoint)
                .onSuccess {
                    _activeDeviceId.value = device.id
                }
                .onFailure { err ->
                    _lastPairingError.value = "Failed to connect to ${device.name}: ${err.message}"
                }
        }
    }

    fun swapToLocalDevice() {
        _activeDeviceId.value = "local"
        viewModelScope.launch {
            transport.sendFrame(SyncFrame.SwapDeviceTarget(targetDeviceId = "local"))
        }
    }

    fun sendRemoteCommand(action: CommandAction, seekMs: Long? = null, volume: Float? = null) {
        viewModelScope.launch {
            val command = SyncFrame.PlaybackCommand(
                action = action,
                seekPositionMs = seekMs,
                volume = volume
            )
            transport.sendFrame(command)
        }
    }

    fun clearPairingError() {
        _lastPairingError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager?.stopDiscovery()
        viewModelScope.launch {
            transport.disconnect()
        }
    }
}
