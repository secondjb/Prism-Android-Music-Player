package com.secondjb.prism.ui.screens.sync

import android.os.SystemClock
import android.util.Base64
import android.util.Log
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
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.data.PrismPreferencesRepository

class SyncViewModel(
    val transport: SyncTransport = LanWebSocketSyncTransport(),
    val discoveryManager: NsdDiscoveryManager? = null,
    private val localTracksProvider: (suspend () -> List<Track>)? = null,
    private val preferencesRepository: PrismPreferencesRepository? = null,
    private val cacheDir: File? = null
) : ViewModel() {

    val connectionState: StateFlow<TransportConnectionState> = transport.connectionState
    val discoveredDevices: StateFlow<List<DeviceNode>> = discoveryManager?.discoveredDevices ?: MutableStateFlow(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = preferencesRepository?.pairedDevices ?: MutableStateFlow(emptyList())

    private val _isDesktopOutputEnabled = MutableStateFlow(false)
    val isDesktopOutputEnabled: StateFlow<Boolean> = _isDesktopOutputEnabled.asStateFlow()

    private val _isLocalOutputEnabled = MutableStateFlow(true)
    val isLocalOutputEnabled: StateFlow<Boolean> = _isLocalOutputEnabled.asStateFlow()

    private val _connectedServerName = MutableStateFlow<String?>("Windows PC")
    val connectedServerName: StateFlow<String?> = _connectedServerName.asStateFlow()

    private val _connectedEndpoint = MutableStateFlow<ConnectionEndpoint?>(null)
    val connectedEndpoint: StateFlow<ConnectionEndpoint?> = _connectedEndpoint.asStateFlow()

    private val _activeDeviceId = MutableStateFlow("local")
    val activeDeviceId: StateFlow<String> = _activeDeviceId.asStateFlow()

    private val _remotePlaybackState = MutableStateFlow<PlaybackState?>(null)
    val remotePlaybackState: StateFlow<PlaybackState?> = _remotePlaybackState.asStateFlow()

    private val _lastPairingError = MutableStateFlow<String?>(null)
    val lastPairingError: StateFlow<String?> = _lastPairingError.asStateFlow()

    private var cachedLocalTracks: List<Track> = emptyList()
    private var lastSyncRealtime: Long = 0L
    private var basePositionMs: Long = 0L
    private var lastPairingToken: String = ""

    init {
        discoveryManager?.startDiscovery()
        loadLocalTracks()
        observeIncomingFrames()
        observeConnectionState()
        startContinuousProgressTicker()
        autoConnectToLastPairedDevice()
    }

    private fun autoConnectToLastPairedDevice() {
        val last = preferencesRepository?.pairedDevices?.value?.firstOrNull() ?: return
        viewModelScope.launch {
            Log.i("PrismSync", "Auto-connecting to previously paired desktop: ${last.name} (${last.host}:${last.port})")
            val endpoint = ConnectionEndpoint(host = last.host, port = last.port, authToken = last.token)
            _connectedEndpoint.value = endpoint
            _connectedServerName.value = last.name
            lastPairingToken = last.token
            transport.connect(endpoint).onFailure {
                Log.d("PrismSync", "Auto-connect deferred: ${it.message}")
            }
        }
    }

    private fun loadLocalTracks() {
        localTracksProvider?.let { provider ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    cachedLocalTracks = provider()
                    Log.i("PrismSync", "Loaded ${cachedLocalTracks.size} local tracks for library sync reconciliation")
                } catch (e: Exception) {
                    Log.w("PrismSync", "Failed to load local tracks: ${e.message}")
                }
            }
        }
    }

    private fun startContinuousProgressTicker() {
        viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                delay(30) // ~33 fps for fluid seekbar movement & word-by-word synced lyrics
                val current = _remotePlaybackState.value
                if (_activeDeviceId.value != "local" && current != null && current.isPlaying) {
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - lastSyncRealtime
                    val interpolatedPos = (basePositionMs + elapsed).coerceAtMost(current.durationMs)
                    if (interpolatedPos != current.positionMs) {
                        _remotePlaybackState.value = current.copy(positionMs = interpolatedPos)
                    }
                }
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            transport.connectionState.collect { state ->
                when (state) {
                    is TransportConnectionState.Connected -> {
                        _lastPairingError.value = null
                        _isDesktopOutputEnabled.value = true
                    }
                    is TransportConnectionState.Error -> {
                        _lastPairingError.value = state.message
                        _isDesktopOutputEnabled.value = false
                        _isLocalOutputEnabled.value = true
                    }
                    is TransportConnectionState.Disconnected -> {
                        _isDesktopOutputEnabled.value = false
                        _isLocalOutputEnabled.value = true
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeIncomingFrames() {
        viewModelScope.launch(Dispatchers.IO) {
            transport.incomingFrames.collect { frame ->
                when (frame) {
                    is SyncFrame.StateSync -> {
                        val incoming = frame.playbackState
                        val reconciled = reconcilePlaybackState(incoming)

                        basePositionMs = incoming.positionMs
                        lastSyncRealtime = SystemClock.elapsedRealtime()

                        _remotePlaybackState.value = reconciled
                        _activeDeviceId.value = "remote"
                    }
                    is SyncFrame.SwapDeviceTarget -> {
                        _activeDeviceId.value = frame.targetDeviceId
                    }
                    is SyncFrame.HandshakeResponse -> {
                        Log.i("PrismSync", "HandshakeResponse: isApproved=${frame.isApproved}")
                        if (frame.isApproved) {
                            _activeDeviceId.value = "remote"
                            _lastPairingError.value = null
                            if (frame.serverDeviceName.isNotBlank()) {
                                _connectedServerName.value = frame.serverDeviceName
                            }
                            _isDesktopOutputEnabled.value = true

                            // Persist paired device for seamless automatic reconnection
                            preferencesRepository?.let { repo ->
                                val endpoint = _connectedEndpoint.value
                                if (endpoint != null && lastPairingToken.isNotBlank()) {
                                    repo.savePairedDevice(
                                        PairedDevice(
                                            id = "${endpoint.host}:${endpoint.port}",
                                            name = frame.serverDeviceName.ifBlank { "Prism Desktop" },
                                            host = endpoint.host,
                                            port = endpoint.port,
                                            token = lastPairingToken,
                                            lastConnectedTimeMs = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        } else {
                            _lastPairingError.value = "Pairing rejected by desktop: invalid token"
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun reconcilePlaybackState(state: PlaybackState): PlaybackState {
        val reconciledTrack = state.currentTrack?.let { reconcileTrack(it) }
        val reconciledQueue = state.queue.map { reconcileTrack(it) }
        return state.copy(
            currentTrack = reconciledTrack,
            queue = reconciledQueue
        )
    }

    private fun reconcileTrack(remoteTrack: Track): Track {
        // 1. Check if the exact audio file exists in phone's local library
        val match = cachedLocalTracks.firstOrNull { local ->
            val sameTitle = local.title.equals(remoteTrack.title, ignoreCase = true)
            val sameArtist = local.artist.equals(remoteTrack.artist, ignoreCase = true)
            if (sameTitle && sameArtist) return@firstOrNull true

            // Match by audio filename
            val remoteFilename = remoteTrack.uri.substringAfterLast('/').substringAfterLast('\\')
            val localFilename = (local.filePath ?: local.uri).substringAfterLast('/').substringAfterLast('\\')
            if (remoteFilename.isNotBlank() && remoteFilename.equals(localFilename, ignoreCase = true)) return@firstOrNull true

            // Match by same title and duration within 2 seconds
            if (sameTitle && Math.abs(local.durationMs - remoteTrack.durationMs) <= 2000L) return@firstOrNull true

            false
        }

        if (match != null) {
            Log.d("PrismSync", "Matched remote track '${remoteTrack.title}' with local library track")
            return remoteTrack.copy(
                artworkUri = match.artworkUri ?: remoteTrack.artworkUri,
                lyrics = if (!match.lyrics.isNullOrBlank()) match.lyrics else remoteTrack.lyrics,
                filePath = match.filePath ?: remoteTrack.filePath
            )
        }

        // 2. If no local match, check if remote track contains Base64 embedded art (data:image/...)
        val rawArt = remoteTrack.artworkUri
        if (rawArt != null && rawArt.startsWith("data:image")) {
            val fileArtUri = cacheBase64ArtworkToFile(remoteTrack.id, rawArt)
            if (fileArtUri != null) {
                return remoteTrack.copy(artworkUri = fileArtUri)
            }
        }

        return remoteTrack
    }

    private fun cacheBase64ArtworkToFile(trackId: String, dataUri: String): String? {
        val baseDir = cacheDir ?: return null
        return try {
            val artDir = File(baseDir, "synced_art").apply { if (!exists()) mkdirs() }
            val cleanId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val targetFile = File(artDir, "art_${cleanId}.jpg")
            if (!targetFile.exists() || targetFile.length() == 0L) {
                val base64Part = dataUri.substringAfter("base64,")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                FileOutputStream(targetFile).use { it.write(bytes) }
            }
            "file://${targetFile.absolutePath}"
        } catch (e: Exception) {
            Log.w("PrismSync", "Failed to cache base64 artwork to file: ${e.message}")
            null
        }
    }

    fun toggleDesktopOutput(enabled: Boolean) {
        if (!enabled && !_isLocalOutputEnabled.value) {
            _isLocalOutputEnabled.value = true
        }
        _isDesktopOutputEnabled.value = enabled
        if (!enabled) {
            sendRemoteCommand(CommandAction.PAUSE)
        } else {
            sendRemoteCommand(CommandAction.PLAY)
        }
    }

    fun toggleLocalOutput(enabled: Boolean) {
        if (!enabled && !_isDesktopOutputEnabled.value) {
            return
        }
        _isLocalOutputEnabled.value = enabled
    }

    fun pairWithQrCode(rawCode: String, onConnected: () -> Unit = {}) {
        val result = QrPairingParser.parse(rawCode)
        result.onSuccess { pairing ->
            _lastPairingError.value = null
            _connectedEndpoint.value = pairing.endpoint
            lastPairingToken = pairing.endpoint.authToken ?: ""
            viewModelScope.launch {
                transport.connect(pairing.endpoint)
                    .onFailure { err ->
                        _lastPairingError.value = "Connection setup failed: ${err.message}"
                    }
            }
        }.onFailure { err ->
            _lastPairingError.value = "Invalid pairing code: ${err.message}"
        }
    }

    fun connectToPairedDevice(device: PairedDevice) {
        viewModelScope.launch {
            _lastPairingError.value = null
            val endpoint = ConnectionEndpoint(host = device.host, port = device.port, authToken = device.token)
            _connectedEndpoint.value = endpoint
            _connectedServerName.value = device.name
            lastPairingToken = device.token
            transport.connect(endpoint)
                .onFailure { err ->
                    _lastPairingError.value = "Failed to connect to ${device.name}: ${err.message}"
                }
        }
    }

    fun deletePairing(device: PairedDevice) {
        preferencesRepository?.removePairedDevice(device.id)
        if (_connectedEndpoint.value?.host == device.host && _connectedEndpoint.value?.port == device.port) {
            viewModelScope.launch {
                transport.disconnect()
            }
            _isDesktopOutputEnabled.value = false
            _isLocalOutputEnabled.value = true
        }
    }

    fun connectToDevice(device: DeviceNode) {
        val endpoint = ConnectionEndpoint(host = device.ipAddress, port = device.port)
        _connectedEndpoint.value = endpoint
        _connectedServerName.value = device.name
        viewModelScope.launch {
            transport.connect(endpoint)
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

    fun sendPlayTrackCommand(trackTitle: String, trackArtist: String, trackFilename: String, trackUri: String) {
        lastSyncRealtime = SystemClock.elapsedRealtime()
        basePositionMs = 0L
        _remotePlaybackState.update { it?.copy(isPlaying = true, positionMs = 0L) }

        viewModelScope.launch {
            val command = SyncFrame.PlaybackCommand(
                action = CommandAction.PLAY_TRACK,
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                trackFilename = trackFilename,
                trackUri = trackUri
            )
            transport.sendFrame(command)
        }
    }

    fun findLocalMatch(remoteTrack: Track): Track? {
        return cachedLocalTracks.firstOrNull { local ->
            val sameTitle = local.title.equals(remoteTrack.title, ignoreCase = true)
            val sameArtist = local.artist.equals(remoteTrack.artist, ignoreCase = true)
            if (sameTitle && sameArtist) return@firstOrNull true

            val remoteFilename = remoteTrack.uri.substringAfterLast('/').substringAfterLast('\\')
            val localFilename = (local.filePath ?: local.uri).substringAfterLast('/').substringAfterLast('\\')
            if (remoteFilename.isNotBlank() && remoteFilename.equals(localFilename, ignoreCase = true)) return@firstOrNull true

            if (sameTitle && Math.abs(local.durationMs - remoteTrack.durationMs) <= 3000L) return@firstOrNull true

            false
        }
    }

    fun getAllLocalTracks(): List<Track> = cachedLocalTracks

    fun sendRemoteCommand(action: CommandAction, seekMs: Long? = null, volume: Float? = null) {
        // Optimistic UI updates
        when (action) {
            CommandAction.PAUSE -> {
                _remotePlaybackState.update { it?.copy(isPlaying = false) }
            }
            CommandAction.PLAY -> {
                lastSyncRealtime = SystemClock.elapsedRealtime()
                _remotePlaybackState.update { it?.copy(isPlaying = true) }
            }
            CommandAction.TOGGLE_PLAY_PAUSE -> {
                val currentPlaying = _remotePlaybackState.value?.isPlaying == true
                lastSyncRealtime = SystemClock.elapsedRealtime()
                _remotePlaybackState.update { it?.copy(isPlaying = !currentPlaying) }
            }
            CommandAction.SEEK -> {
                if (seekMs != null) {
                    basePositionMs = seekMs
                    lastSyncRealtime = SystemClock.elapsedRealtime()
                    _remotePlaybackState.update { it?.copy(positionMs = seekMs) }
                }
            }
            else -> {}
        }

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
