package com.secondjb.prism.core.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.secondjb.prism.core.data.PrismPreferencesRepository
import com.secondjb.prism.core.service.PrismPlaybackService
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.domain.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Local implementation of PlaybackController backed by AndroidX Media3 MediaController.
 * Supports distinct Priority User Queue ("Next Up") and Context Queue ("Next from Playlist / Album"),
 * reliable shuffling, queue persistence across restarts, and listening analytics.
 */
class LocalPlaybackEngine(
    private val context: Context,
    private val statsRepository: com.secondjb.prism.core.stats.ListeningStatsRepository? = null,
    private val preferencesRepository: PrismPreferencesRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) : PlaybackController {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Two-tier queue matching Prism Windows desktop logic
    private var userQueue: MutableList<Track> = mutableListOf()
    private var contextQueue: MutableList<Track> = mutableListOf()
    private var originalQueue: MutableList<Track> = mutableListOf()
    private var currentQueueIndex: Int = -1
    private var activePlaylistId: String? = null

    val customQueueIds = mutableSetOf<String>()
    private var positionTickerJob: Job? = null

    var isRemoteOnlyActive: Boolean = false
    var preventDoubleCounting: Boolean = true
    var isStatsCollectionEnabled: Boolean = true

    // Listening stats accumulation
    private var lastPlayTickRealtime = 0L
    private var activeListeningMs = 0L
    private var currentScrobbleTrack: Track? = null
    private var hasLoggedCurrentTrack = false

    init {
        initializeController()
        hydratePersistedQueue()
    }

    private fun hydratePersistedQueue() {
        val persisted = preferencesRepository?.loadPersistedQueueState() ?: return
        if (persisted.contextQueue.isNotEmpty() || persisted.userQueue.isNotEmpty()) {
            userQueue = persisted.userQueue.toMutableList()
            contextQueue = persisted.contextQueue.toMutableList()
            originalQueue = persisted.contextQueue.toMutableList()
            currentQueueIndex = persisted.currentIndex
            activePlaylistId = persisted.activePlaylistId
            val activeTrack = if (currentQueueIndex in contextQueue.indices) contextQueue[currentQueueIndex] else contextQueue.firstOrNull()
            _playbackState.update {
                it.copy(
                    currentTrack = activeTrack,
                    queue = contextQueue.toList(),
                    userQueue = userQueue.toList(),
                    queueIndex = currentQueueIndex,
                    activePlaylistId = activePlaylistId,
                    isPlaying = false
                )
            }
        }
    }

    private fun persistQueue() {
        preferencesRepository?.saveQueueState(
            userQueue = userQueue.toList(),
            contextQueue = contextQueue.toList(),
            currentIndex = currentQueueIndex,
            currentTrackId = _playbackState.value.currentTrack?.id,
            activePlaylistId = activePlaylistId
        )
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PrismPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                setupPlayerListener(controller)
                syncFromController(controller)
            } catch (e: Exception) {
                // Service may still be initializing
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    lastPlayTickRealtime = SystemClock.elapsedRealtime()
                    startPositionTicker()
                } else {
                    lastPlayTickRealtime = 0L
                    stopPositionTicker()
                    syncFromController(controller)
                }
            }

            override fun onPlaybackStateChanged(playbackStateInt: Int) {
                val duration = controller.duration.coerceAtLeast(0L)
                _playbackState.update { it.copy(durationMs = duration) }
                if (playbackStateInt == Player.STATE_ENDED) {
                    advanceNextTrack(isUserInitiated = false)
                }
            }

            override fun onRepeatModeChanged(repeatModeInt: Int) {
                val mode = when (repeatModeInt) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                }
                _playbackState.update { it.copy(repeatMode = mode) }
            }
        })
    }

    private fun syncFromController(controller: MediaController) {
        val repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

        val activeTrack = _playbackState.value.currentTrack

        _playbackState.update {
            it.copy(
                currentTrack = activeTrack,
                queueIndex = currentQueueIndex,
                isPlaying = controller.isPlaying,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                repeatMode = repeatMode,
                volume = controller.volume,
                queue = contextQueue.toList(),
                userQueue = userQueue.toList(),
                activePlaylistId = activePlaylistId
            )
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val now = SystemClock.elapsedRealtime()
                    if (lastPlayTickRealtime > 0L) {
                        val delta = (now - lastPlayTickRealtime).coerceAtLeast(0L)
                        if (delta in 1..2000) {
                            activeListeningMs += delta
                        }
                    }
                    lastPlayTickRealtime = now

                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _playbackState.update { it.copy(positionMs = pos, durationMs = dur) }
                } else {
                    lastPlayTickRealtime = 0L
                }
                delay(250)
            }
        }
    }

    private fun commitActiveListeningEvent(track: Track?, ms: Long, isCompleted: Boolean = false) {
        if (track == null || statsRepository == null) return
        if (!isStatsCollectionEnabled) return
        if (isRemoteOnlyActive && preventDoubleCounting) {
            // Audio rendered by remote desktop; remote logs this playback session. Prevent double counting.
            return
        }

        val durSecs = track.durationSecs
        val threshold = if (durSecs > 0) {
            Math.min(30000L, Math.max(5000L, (durSecs * 1000L) / 2))
        } else {
            15000L
        }

        if (ms >= threshold) {
            val durationToLog = if (isCompleted && durSecs > 0) durSecs * 1000L else ms
            scope.launch {
                statsRepository.logListeningEvent(track, durationToLog)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    override fun playTrack(track: Track, queue: List<Track>, startIndex: Int) {
        playTrack(track, queue, startIndex, playlistId = null)
    }

    override fun playTrack(track: Track, queue: List<Track>, playlistId: String?) {
        playTrack(track, queue, -1, playlistId)
    }

    override fun playTrack(track: Track, queue: List<Track>, startIndex: Int, playlistId: String?) {
        loadTrackInternal(track, queue, startIndex, playlistId, playWhenReady = true)
    }

    override fun prepareTrack(track: Track, queue: List<Track>, startIndex: Int) {
        loadTrackInternal(track, queue, startIndex, playlistId = null, playWhenReady = false)
    }

    private fun loadTrackInternal(
        track: Track,
        queue: List<Track>,
        startIndex: Int,
        playlistId: String?,
        playWhenReady: Boolean
    ) {
        val prevTrack = currentScrobbleTrack
        val prevMs = activeListeningMs
        commitActiveListeningEvent(prevTrack, prevMs, isCompleted = false)

        userQueue.clear()
        customQueueIds.clear()
        contextQueue = queue.toMutableList()
        originalQueue = queue.toMutableList()
        activePlaylistId = playlistId

        var targetIndex = if (startIndex != -1 && startIndex in contextQueue.indices) {
            startIndex
        } else {
            contextQueue.indexOfFirst { it.id == track.id }.takeIf { it != -1 } ?: 0
        }

        if (_playbackState.value.isShuffled && contextQueue.size > 1) {
            val otherTracks = contextQueue.filterIndexed { idx, _ -> idx != targetIndex }.shuffled()
            contextQueue = (listOf(track) + otherTracks).toMutableList()
            targetIndex = 0
        }

        currentQueueIndex = targetIndex
        currentScrobbleTrack = track
        activeListeningMs = 0L
        lastPlayTickRealtime = if (playWhenReady) SystemClock.elapsedRealtime() else 0L

        playSingleTrack(track, playWhenReady)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = contextQueue.toList(),
                userQueue = emptyList(),
                queueIndex = targetIndex,
                activePlaylistId = playlistId,
                isPlaying = playWhenReady,
                lastActionTimestampMs = System.currentTimeMillis()
            )
        }
        persistQueue()
    }

    private fun playSingleTrack(track: Track, playWhenReady: Boolean) {
        val controller = mediaController
        val mediaItem = track.toMediaItem()
        if (controller == null) {
            controllerFuture?.addListener({
                try {
                    val ctrl = controllerFuture?.get() ?: return@addListener
                    ctrl.setMediaItem(mediaItem)
                    ctrl.prepare()
                    if (playWhenReady) {
                        ctrl.play()
                    } else {
                        ctrl.pause()
                    }
                } catch (e: Exception) {}
            }, MoreExecutors.directExecutor())
        } else {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            if (playWhenReady) {
                controller.play()
            } else {
                controller.pause()
            }
        }
    }

    override fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun play() {
        val controller = mediaController
        if (controller != null) {
            val currentTrack = _playbackState.value.currentTrack
            if (currentTrack != null && (controller.currentMediaItem == null || controller.currentMediaItem?.mediaId != currentTrack.id)) {
                val mediaItem = currentTrack.toMediaItem()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                val targetPos = _playbackState.value.positionMs
                if (targetPos > 0) {
                    controller.seekTo(targetPos)
                }
            }
            controller.play()
        }
        _playbackState.update { it.copy(isPlaying = true, lastActionTimestampMs = System.currentTimeMillis()) }
    }

    override fun pause() {
        mediaController?.pause()
        _playbackState.update { it.copy(isPlaying = false, lastActionTimestampMs = System.currentTimeMillis()) }
    }

    override fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs, lastActionTimestampMs = System.currentTimeMillis()) }
    }

    override fun skipNext() {
        advanceNextTrack(isUserInitiated = true)
    }

    private fun advanceNextTrack(isUserInitiated: Boolean) {
        val prevTrack = currentScrobbleTrack
        val prevMs = activeListeningMs
        commitActiveListeningEvent(prevTrack, prevMs, isCompleted = !isUserInitiated)

        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
            if (isUserInitiated) {
                setRepeatMode(RepeatMode.ALL)
            } else {
                _playbackState.value.currentTrack?.let { playSingleTrack(it, playWhenReady = true) }
                return
            }
        }

        // 1. Priority User Queue ("Next Up") takes precedence over context queue
        if (userQueue.isNotEmpty()) {
            val nextUserTrack = userQueue.removeAt(0)
            customQueueIds.remove(nextUserTrack.id)
            currentScrobbleTrack = nextUserTrack
            activeListeningMs = 0L
            lastPlayTickRealtime = SystemClock.elapsedRealtime()
            playSingleTrack(nextUserTrack, playWhenReady = true)
            _playbackState.update {
                it.copy(
                    currentTrack = nextUserTrack,
                    userQueue = userQueue.toList(),
                    isPlaying = true,
                    lastActionTimestampMs = System.currentTimeMillis()
                )
            }
            persistQueue()
            return
        }

        // 2. Context Queue
        if (contextQueue.isEmpty()) return

        val effectiveRepeat = if (_playbackState.value.repeatMode == RepeatMode.ONE) RepeatMode.ALL else _playbackState.value.repeatMode
        val nextIdx = currentQueueIndex + 1
        if (nextIdx >= contextQueue.size) {
            if (effectiveRepeat == RepeatMode.ALL) {
                currentQueueIndex = 0
                val next = contextQueue[0]
                currentScrobbleTrack = next
                activeListeningMs = 0L
                lastPlayTickRealtime = SystemClock.elapsedRealtime()
                playSingleTrack(next, playWhenReady = true)
                _playbackState.update {
                    it.copy(
                        currentTrack = next,
                        queueIndex = 0,
                        isPlaying = true,
                        lastActionTimestampMs = System.currentTimeMillis()
                    )
                }
                persistQueue()
            } else {
                _playbackState.update {
                    it.copy(isPlaying = false, positionMs = 0L, lastActionTimestampMs = System.currentTimeMillis())
                }
                mediaController?.pause()
            }
        } else {
            currentQueueIndex = nextIdx
            val next = contextQueue[nextIdx]
            currentScrobbleTrack = next
            activeListeningMs = 0L
            lastPlayTickRealtime = SystemClock.elapsedRealtime()
            playSingleTrack(next, playWhenReady = true)
            _playbackState.update {
                it.copy(
                    currentTrack = next,
                    queueIndex = nextIdx,
                    isPlaying = true,
                    lastActionTimestampMs = System.currentTimeMillis()
                )
            }
            persistQueue()
        }
    }

    override fun skipPrevious() {
        val controller = mediaController
        val currentPos = controller?.currentPosition ?: _playbackState.value.positionMs
        if (currentPos > 3000L) {
            seekTo(0L)
            return
        }
        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
            setRepeatMode(RepeatMode.ALL)
        }
        if (contextQueue.isEmpty()) return
        val prevIdx = if (currentQueueIndex > 0) currentQueueIndex - 1 else contextQueue.lastIndex
        currentQueueIndex = prevIdx
        val prevTrack = contextQueue[prevIdx]
        currentScrobbleTrack = prevTrack
        activeListeningMs = 0L
        lastPlayTickRealtime = SystemClock.elapsedRealtime()
        playSingleTrack(prevTrack, playWhenReady = true)
        _playbackState.update {
            it.copy(
                currentTrack = prevTrack,
                queueIndex = prevIdx,
                isPlaying = true,
                lastActionTimestampMs = System.currentTimeMillis()
            )
        }
        persistQueue()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val controller = mediaController ?: return
        val exoMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        controller.repeatMode = exoMode
        _playbackState.update { it.copy(repeatMode = mode, lastActionTimestampMs = System.currentTimeMillis()) }
    }

    override fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffled
        if (newShuffle) {
            originalQueue = contextQueue.toMutableList()
            val current = _playbackState.value.currentTrack
            val currentIdx = currentQueueIndex
            if (current != null && contextQueue.isNotEmpty()) {
                val others = contextQueue.filterIndexed { idx, _ -> idx != currentIdx }.shuffled()
                contextQueue = (listOf(current) + others).toMutableList()
                currentQueueIndex = 0
            } else {
                contextQueue = contextQueue.shuffled().toMutableList()
            }
        } else {
            val current = _playbackState.value.currentTrack
            if (originalQueue.isNotEmpty()) {
                contextQueue = originalQueue.toMutableList()
                currentQueueIndex = if (current != null) {
                    contextQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                } else 0
                originalQueue.clear()
            }
        }
        _playbackState.update {
            it.copy(
                isShuffled = newShuffle,
                queue = contextQueue.toList(),
                queueIndex = currentQueueIndex,
                lastActionTimestampMs = System.currentTimeMillis()
            )
        }
        persistQueue()
    }

    override fun reshuffleUpcomingContext() {
        if (currentQueueIndex in 0 until contextQueue.lastIndex) {
            val head = contextQueue.take(currentQueueIndex + 1)
            val upcoming = contextQueue.drop(currentQueueIndex + 1).shuffled()
            contextQueue = (head + upcoming).toMutableList()
            _playbackState.update {
                it.copy(queue = contextQueue.toList(), lastActionTimestampMs = System.currentTimeMillis())
            }
            persistQueue()
        }
    }

    override fun setVolume(volume: Float) {
        val controller = mediaController ?: return
        val clamped = volume.coerceIn(0f, 1f)
        controller.volume = clamped
        _playbackState.update { it.copy(volume = clamped) }
    }

    override fun addToQueue(track: Track) {
        userQueue.add(track)
        customQueueIds.add(track.id)
        _playbackState.update {
            it.copy(userQueue = userQueue.toList(), lastActionTimestampMs = System.currentTimeMillis())
        }
        persistQueue()
    }

    override fun playNext(track: Track) {
        userQueue.add(0, track)
        customQueueIds.add(track.id)
        _playbackState.update {
            it.copy(userQueue = userQueue.toList(), lastActionTimestampMs = System.currentTimeMillis())
        }
        persistQueue()
    }

    override fun removeFromUserQueue(index: Int) {
        if (index in userQueue.indices) {
            userQueue.removeAt(index)
            _playbackState.update {
                it.copy(userQueue = userQueue.toList(), lastActionTimestampMs = System.currentTimeMillis())
            }
            persistQueue()
        }
    }

    override fun removeFromQueue(index: Int) {
        if (index in contextQueue.indices) {
            contextQueue.removeAt(index)
            if (index < currentQueueIndex) {
                currentQueueIndex = (currentQueueIndex - 1).coerceAtLeast(0)
            } else if (index == currentQueueIndex) {
                currentQueueIndex = currentQueueIndex.coerceAtMost(contextQueue.lastIndex)
            }
            _playbackState.update {
                it.copy(
                    queue = contextQueue.toList(),
                    queueIndex = currentQueueIndex,
                    lastActionTimestampMs = System.currentTimeMillis()
                )
            }
            persistQueue()
        }
    }

    override fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex in contextQueue.indices && toIndex in contextQueue.indices) {
            val item = contextQueue.removeAt(fromIndex)
            contextQueue.add(toIndex, item)
            _playbackState.update {
                it.copy(queue = contextQueue.toList(), lastActionTimestampMs = System.currentTimeMillis())
            }
            persistQueue()
        }
    }

    override fun clearQueue() {
        userQueue.clear()
        contextQueue.clear()
        originalQueue.clear()
        currentQueueIndex = -1
        activePlaylistId = null
        mediaController?.clearMediaItems()
        _playbackState.update {
            it.copy(
                queue = emptyList(),
                userQueue = emptyList(),
                currentTrack = null,
                queueIndex = -1,
                isPlaying = false,
                lastActionTimestampMs = System.currentTimeMillis()
            )
        }
        persistQueue()
    }

    override fun addTrackToActivePlaylist(track: Track, playlistId: String) {
        if (activePlaylistId == playlistId) {
            if (_playbackState.value.isShuffled) {
                val minIdx = (currentQueueIndex + 1).coerceAtMost(contextQueue.size)
                val insertIdx = if (minIdx < contextQueue.size) {
                    minIdx + (0..(contextQueue.size - minIdx)).random()
                } else {
                    contextQueue.size
                }
                contextQueue.add(insertIdx.coerceIn(0, contextQueue.size), track)
            } else {
                contextQueue.add(track)
            }
            originalQueue.add(track)
            _playbackState.update {
                it.copy(queue = contextQueue.toList(), lastActionTimestampMs = System.currentTimeMillis())
            }
            persistQueue()
        }
    }

    fun syncFromRemoteQueue(
        currentTrack: Track?,
        queue: List<Track>,
        userQueue: List<Track>,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        if (currentTrack == null && queue.isEmpty()) return
        this.contextQueue = queue.toMutableList()
        this.userQueue = userQueue.toMutableList()
        this.originalQueue = queue.toMutableList()
        this.currentQueueIndex = if (currentTrack != null) queue.indexOfFirst { it.id == currentTrack.id } else -1
        _playbackState.update {
            it.copy(
                currentTrack = currentTrack,
                queue = queue,
                userQueue = userQueue,
                queueIndex = currentQueueIndex,
                positionMs = positionMs,
                durationMs = currentTrack?.durationMs ?: it.durationMs,
                isPlaying = if (isRemoteOnlyActive) false else isPlaying,
                lastActionTimestampMs = System.currentTimeMillis()
            )
        }
        persistQueue()
    }

    fun release() {
        val prevTrack = currentScrobbleTrack
        val prevMs = activeListeningMs
        commitActiveListeningEvent(prevTrack, prevMs, isCompleted = false)
        currentScrobbleTrack = null
        activeListeningMs = 0L
        lastPlayTickRealtime = 0L

        stopPositionTicker()
        mediaController?.release()
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    fun syncRemoteMetadata(
        remoteTrack: Track,
        isPlaying: Boolean,
        positionMs: Long,
        serverName: String
    ) {
        val controller = mediaController ?: return

        val currentItem = controller.currentMediaItem
        val targetArtist = "${remoteTrack.artist} • Playing on $serverName"
        val needsNewItem = currentItem?.mediaId != remoteTrack.id ||
                currentItem.mediaMetadata.title?.toString() != remoteTrack.title

        if (needsNewItem) {
            val metadata = MediaMetadata.Builder()
                .setTitle(remoteTrack.title)
                .setArtist(targetArtist)
                .setAlbumTitle(remoteTrack.album)
                .setArtworkUri(remoteTrack.artworkUri?.let { Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(remoteTrack.id)
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
        }
    }

    fun restoreLocalOutput() {
        // Volume was never mutated; local playback can resume immediately
    }

    companion object {
        fun Track.toMediaItem(): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setUri(Uri.parse(uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(artworkUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
    }
}
