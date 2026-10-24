package com.secondjb.prism.ui.screens.sync

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondjb.prism.core.data.PairedDevice
import com.secondjb.prism.core.data.PrismPreferencesRepository
import com.secondjb.prism.domain.model.DeviceNode
import com.secondjb.prism.core.sync.discovery.NsdDiscoveryManager
import com.secondjb.prism.core.sync.protocol.CommandAction
import com.secondjb.prism.core.sync.protocol.ManifestTrackEntry
import com.secondjb.prism.core.sync.protocol.SyncFrame
import com.secondjb.prism.core.sync.qr.QrPairingParser
import com.secondjb.prism.core.sync.transport.ConnectionEndpoint
import com.secondjb.prism.core.sync.transport.LanWebSocketSyncTransport
import com.secondjb.prism.core.sync.transport.SyncTransport
import com.secondjb.prism.core.sync.transport.TransportConnectionState
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class TrackNotFoundEvent(
    val title: String,
    val artist: String,
    val filename: String
)

enum class ConnectionSpeedTier(val label: String, val approxSpeedMBps: Double) {
    USB_REVERSE("USB Cable (High-Speed)", 35.0),
    WIFI_HOTSPOT("Wi-Fi Hotspot / Tether", 16.0),
    WIFI_LAN("Wi-Fi Local Network", 9.0)
}

data class FileSyncManifestDiff(
    val missingOnPhone: List<ManifestTrackEntry> = emptyList(),
    val missingOnDesktop: List<ManifestTrackEntry> = emptyList(),
    val syncedCount: Int = 0,
    val totalMissingBytesPhone: Long = 0L,
    val totalMissingBytesDesktop: Long = 0L,
    val connectionTier: ConnectionSpeedTier = ConnectionSpeedTier.WIFI_LAN,
    val estimatedTimeSecondsPhone: Long = 0L,
    val estimatedTimeSecondsDesktop: Long = 0L,
    val isAnalyzing: Boolean = false,
    val isSyncing: Boolean = false,
    val currentSyncTrack: String? = null,
    val syncProgressPercent: Float = 0f,
    val syncStatusMessage: String? = null
)

class SyncViewModel(
    val transport: SyncTransport = LanWebSocketSyncTransport(),
    val discoveryManager: NsdDiscoveryManager? = null,
    private val localTracksProvider: (suspend () -> List<Track>)? = null,
    private val preferencesRepository: PrismPreferencesRepository? = null,
    private val cacheDir: File? = null,
    private val context: Context? = null
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

    private val _trackNotFoundPrompt = MutableStateFlow<TrackNotFoundEvent?>(null)
    val trackNotFoundPrompt: StateFlow<TrackNotFoundEvent?> = _trackNotFoundPrompt.asStateFlow()

    private val _fileSyncState = MutableStateFlow(FileSyncManifestDiff())
    val fileSyncState: StateFlow<FileSyncManifestDiff> = _fileSyncState.asStateFlow()

    private var cachedLocalTracks: List<Track> = emptyList()
    private var lastSyncRealtime: Long = 0L
    private var basePositionMs: Long = 0L
    private var lastPairingToken: String = ""

    init {
        discoveryManager?.startDiscovery()
        observeIncomingFrames()
        observeConnectionState()
        preloadLocalTracks()
        autoConnectRecentPairedDevice()
    }

    private fun autoConnectRecentPairedDevice() {
        viewModelScope.launch {
            val devices = preferencesRepository?.pairedDevices?.value ?: return@launch
            if (devices.isNotEmpty()) {
                val mostRecent = devices.maxByOrNull { it.lastConnectedTimeMs } ?: devices.first()
                Log.i("PrismSync", "Auto-connecting to previously paired desktop: ${mostRecent.name} (${mostRecent.host}:${mostRecent.port})")
                connectToPairedDevice(mostRecent)
            }
        }
    }

    private fun preloadLocalTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            localTracksProvider?.let {
                try {
                    cachedLocalTracks = it()
                    Log.d("PrismSync", "Preloaded ${cachedLocalTracks.size} local tracks for companion pairing")
                } catch (e: Exception) {
                    Log.w("PrismSync", "Failed to preload local tracks: ${e.message}")
                }
            }
        }
    }

    fun refreshLocalTracks() {
        preloadLocalTracks()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionState.collect { state ->
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
                    is SyncFrame.TrackNotFound -> {
                        Log.w("PrismSync", "Remote track not found on desktop: ${frame.trackTitle}")
                        _trackNotFoundPrompt.value = TrackNotFoundEvent(
                            title = frame.trackTitle,
                            artist = frame.trackArtist,
                            filename = frame.trackFilename
                        )
                    }
                    is SyncFrame.ManifestResponse -> {
                        handleManifestResponse(frame.tracks)
                    }
                    is SyncFrame.FileChunk -> {
                        handleIncomingFileChunk(frame)
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
        val match = cachedLocalTracks.firstOrNull { local ->
            val sameTitle = local.title.equals(remoteTrack.title, ignoreCase = true)
            val sameArtist = local.artist.equals(remoteTrack.artist, ignoreCase = true)
            if (sameTitle && sameArtist) return@firstOrNull true

            val remoteFilename = remoteTrack.uri.substringAfterLast('/').substringAfterLast('\\')
            val localFilename = (local.filePath ?: local.uri).substringAfterLast('/').substringAfterLast('\\')
            if (remoteFilename.isNotBlank() && remoteFilename.equals(localFilename, ignoreCase = true)) return@firstOrNull true

            if (sameTitle && Math.abs(local.durationMs - remoteTrack.durationMs) <= 3000L) return@firstOrNull true

            false
        }

        if (match != null) {
            return remoteTrack.copy(
                artworkUri = match.artworkUri ?: remoteTrack.artworkUri,
                lyrics = if (!match.lyrics.isNullOrBlank()) match.lyrics else remoteTrack.lyrics,
                filePath = match.filePath ?: remoteTrack.filePath
            )
        }

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

    fun sendPlayTrackCommand(
        trackTitle: String,
        trackArtist: String,
        trackFilename: String,
        trackUri: String,
        queueFilenames: List<String>? = null
    ) {
        lastSyncRealtime = SystemClock.elapsedRealtime()
        basePositionMs = 0L
        _remotePlaybackState.update { it?.copy(isPlaying = true, positionMs = 0L) }

        viewModelScope.launch {
            val command = SyncFrame.PlaybackCommand(
                action = CommandAction.PLAY_TRACK,
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                trackFilename = trackFilename,
                trackUri = trackUri,
                queueFilenames = queueFilenames
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

    fun clearTrackNotFoundPrompt() {
        _trackNotFoundPrompt.value = null
    }

    fun requestManifestDiff() {
        val host = _connectedEndpoint.value?.host ?: ""
        val tier = when {
            host == "127.0.0.1" || host == "localhost" -> ConnectionSpeedTier.USB_REVERSE
            host.startsWith("192.168.43.") -> ConnectionSpeedTier.WIFI_HOTSPOT
            else -> ConnectionSpeedTier.WIFI_LAN
        }
        _fileSyncState.update {
            it.copy(
                isAnalyzing = true,
                connectionTier = tier,
                syncStatusMessage = "Analyzing remote desktop library manifest..."
            )
        }

        viewModelScope.launch {
            transport.sendFrame(SyncFrame.ManifestRequest())
        }
    }

    private fun handleManifestResponse(remoteTracks: List<ManifestTrackEntry>) {
        val normalize = { s: String? -> (s ?: "").trim().lowercase() }
        val localList = cachedLocalTracks

        val missingOnPhone = remoteTracks.filter { remote ->
            val remoteFn = normalize(remote.filename)
            val remoteTitle = normalize(remote.title)
            val remoteArtist = normalize(remote.artist)

            localList.none { local ->
                val localFn = normalize((local.filePath ?: local.uri).substringAfterLast('/').substringAfterLast('\\'))
                if (remoteFn.isNotBlank() && localFn == remoteFn) return@none true
                if (remoteTitle.isNotBlank() && normalize(local.title) == remoteTitle) {
                    if (remoteArtist.isBlank() || normalize(local.artist).contains(remoteArtist) || remoteArtist.contains(normalize(local.artist))) {
                        return@none true
                    }
                }
                false
            }
        }

        val missingOnDesktop = localList.filter { local ->
            val localFn = normalize((local.filePath ?: local.uri).substringAfterLast('/').substringAfterLast('\\'))
            val localTitle = normalize(local.title)
            val localArtist = normalize(local.artist)

            remoteTracks.none { remote ->
                val remoteFn = normalize(remote.filename)
                if (localFn.isNotBlank() && remoteFn == localFn) return@none true
                if (localTitle.isNotBlank() && normalize(remote.title) == localTitle) {
                    if (localArtist.isBlank() || normalize(remote.artist).contains(localArtist) || localArtist.contains(normalize(remote.artist))) {
                        return@none true
                    }
                }
                false
            }
        }.map { local ->
            ManifestTrackEntry(
                filename = (local.filePath ?: local.uri).substringAfterLast('/').substringAfterLast('\\'),
                title = local.title,
                artist = local.artist,
                sizeBytes = 25_000_000L,
                durationSecs = (local.durationMs / 1000.0)
            )
        }

        val totalBytesPhone = missingOnPhone.sumOf { it.sizeBytes.takeIf { s -> s > 0 } ?: 25_000_000L }
        val totalBytesDesktop = missingOnDesktop.sumOf { it.sizeBytes }

        val tier = _fileSyncState.value.connectionTier
        val speedBytesPerSec = tier.approxSpeedMBps * 1024 * 1024
        val estSecondsPhone = Math.max(1L, (totalBytesPhone / speedBytesPerSec).toLong())
        val estSecondsDesktop = Math.max(1L, (totalBytesDesktop / speedBytesPerSec).toLong())

        _fileSyncState.update {
            it.copy(
                missingOnPhone = missingOnPhone,
                missingOnDesktop = missingOnDesktop,
                syncedCount = remoteTracks.size - missingOnPhone.size,
                totalMissingBytesPhone = totalBytesPhone,
                totalMissingBytesDesktop = totalBytesDesktop,
                estimatedTimeSecondsPhone = estSecondsPhone,
                estimatedTimeSecondsDesktop = estSecondsDesktop,
                isAnalyzing = false,
                syncStatusMessage = null
            )
        }
    }

    fun startSyncDownload() {
        val missing = _fileSyncState.value.missingOnPhone
        if (missing.isEmpty()) return

        _fileSyncState.update { it.copy(isSyncing = true, syncProgressPercent = 0f, syncStatusMessage = "Starting safe download...") }

        viewModelScope.launch {
            for ((index, track) in missing.withIndex()) {
                val progress = (index.toFloat() / missing.size.toFloat())
                _fileSyncState.update {
                    it.copy(
                        currentSyncTrack = "${track.title} - ${track.artist}",
                        syncProgressPercent = progress,
                        syncStatusMessage = "Syncing ${index + 1} of ${missing.size}: ${track.title}"
                    )
                }

                transport.sendFrame(
                    SyncFrame.PlaybackCommand(
                        action = CommandAction.REQUEST_FILE_DOWNLOAD,
                        trackFilename = track.filename,
                        trackTitle = track.title
                    )
                )

                delay(600)
            }

            _fileSyncState.update {
                it.copy(
                    isSyncing = false,
                    syncProgressPercent = 1f,
                    syncStatusMessage = "Sync completed! All missing tracks saved safely."
                )
            }
        }
    }

    private fun handleIncomingFileChunk(chunk: SyncFrame.FileChunk) {
        try {
            val baseDir = cacheDir ?: return
            val downloadDir = File(baseDir, "sync_incoming").apply { if (!exists()) mkdirs() }
            val tmpFile = File(downloadDir, "${chunk.fileId}.tmp")

            val bytes = Base64.decode(chunk.dataBase64, Base64.DEFAULT)
            FileOutputStream(tmpFile, true).use { it.write(bytes) }

            if (chunk.isLast) {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "Prism").apply { if (!exists()) mkdirs() }
                val targetFile = File(targetDir, chunk.fileId)

                // Never overwrite existing files!
                if (!targetFile.exists()) {
                    tmpFile.renameTo(targetFile)
                    context?.let { ctx ->
                        MediaScannerConnection.scanFile(ctx, arrayOf(targetFile.absolutePath), null, null)
                    }
                } else {
                    tmpFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("PrismSync", "Error handling file chunk for ${chunk.fileId}: ${e.message}")
        }
    }

    fun sendSingleTrackToDesktop(track: Track, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = (track.filePath ?: track.uri).substringAfterLast('/').substringAfterLast('\\')
                val path = track.filePath
                val file = if (path != null) File(path) else null

                if (file != null && file.exists()) {
                    val bytes = file.readBytes()
                    val totalSize = bytes.size.toLong()
                    val chunkSize = 512 * 1024
                    val totalChunks = Math.ceil(bytes.size.toDouble() / chunkSize).toInt()

                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = Math.min(bytes.size, start + chunkSize)
                        val chunkBytes = bytes.copyOfRange(start, end)
                        val b64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)

                        transport.sendFrame(
                            SyncFrame.FileChunk(
                                fileId = filename,
                                chunkIndex = i,
                                totalChunks = totalChunks,
                                dataBase64 = b64,
                                sizeBytes = totalSize,
                                isLast = i == totalChunks - 1
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("PrismSync", "Failed to send track to desktop: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager?.stopDiscovery()
        viewModelScope.launch {
            transport.disconnect()
        }
    }
}
