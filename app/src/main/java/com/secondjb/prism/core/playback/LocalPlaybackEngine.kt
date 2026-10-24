package com.secondjb.prism.core.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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
 */
class LocalPlaybackEngine(
    private val context: Context,
    private val statsRepository: com.secondjb.prism.core.stats.ListeningStatsRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) : PlaybackController {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var currentQueue: List<Track> = emptyList()
    val customQueueIds = mutableSetOf<String>()
    private var positionTickerJob: Job? = null

    // Listening stats accumulation
    private var activeListeningMs = 0L
    private var currentScrobbleTrack: Track? = null
    private var hasLoggedCurrentTrack = false

    init {
        initializeController()
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
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                    syncFromController(controller)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // If previous track had accumulated listening time, check if it reached threshold
                commitActiveListeningEvent()

                val currentMediaId = mediaItem?.mediaId
                val trackIndex = currentQueue.indexOfFirst { it.id == currentMediaId }
                val track = if (trackIndex != -1) currentQueue[trackIndex] else null

                currentScrobbleTrack = track
                activeListeningMs = 0L
                hasLoggedCurrentTrack = false

                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        queueIndex = trackIndex,
                        durationMs = controller.duration.coerceAtLeast(0L),
                        positionMs = controller.currentPosition.coerceAtLeast(0L)
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackStateInt: Int) {
                val duration = controller.duration.coerceAtLeast(0L)
                _playbackState.update { it.copy(durationMs = duration) }
                if (playbackStateInt == Player.STATE_ENDED) {
                    _playbackState.update { it.copy(isPlaying = false) }
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

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(isShuffled = shuffleModeEnabled) }
            }
        })
    }

    private fun syncFromController(controller: MediaController) {
        val currentMediaId = controller.currentMediaItem?.mediaId
        val trackIndex = currentQueue.indexOfFirst { it.id == currentMediaId }
        val track = if (trackIndex != -1) currentQueue[trackIndex] else null

        val repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queueIndex = trackIndex,
                isPlaying = controller.isPlaying,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                isShuffled = controller.shuffleModeEnabled,
                repeatMode = repeatMode,
                volume = controller.volume,
                queue = currentQueue
            )
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _playbackState.update { it.copy(positionMs = pos, durationMs = dur) }

                    // Accumulate listening time for stats
                    activeListeningMs += 250L
                    checkThresholdAndLogEvent()
                }
                delay(250)
            }
        }
    }

    private fun checkThresholdAndLogEvent() {
        val track = currentScrobbleTrack ?: return
        if (hasLoggedCurrentTrack || statsRepository == null) return

        val durSecs = track.durationSecs
        val threshold = if (durSecs > 0) {
            Math.min(30000L, Math.max(5000L, (durSecs * 1000L) / 2))
        } else {
            15000L
        }

        if (activeListeningMs >= threshold) {
            hasLoggedCurrentTrack = true
            scope.launch {
                val durationToLog = if (durSecs > 0) durSecs * 1000L else activeListeningMs
                statsRepository.logListeningEvent(track, durationToLog)
            }
        }
    }

    private fun commitActiveListeningEvent() {
        checkThresholdAndLogEvent()
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    override fun playTrack(track: Track, queue: List<Track>, startIndex: Int) {
        loadTrackInternal(track, queue, startIndex, playWhenReady = true)
    }

    override fun prepareTrack(track: Track, queue: List<Track>, startIndex: Int) {
        loadTrackInternal(track, queue, startIndex, playWhenReady = false)
    }

    private fun loadTrackInternal(track: Track, queue: List<Track>, startIndex: Int, playWhenReady: Boolean) {
        currentQueue = queue
        currentScrobbleTrack = track
        activeListeningMs = 0L
        hasLoggedCurrentTrack = false
        val targetIndex = if (startIndex != -1 && startIndex in queue.indices) {
            startIndex
        } else {
            queue.indexOfFirst { it.id == track.id }.takeIf { it != -1 } ?: 0
        }
        val mediaItems = queue.map { it.toMediaItem() }

        val controller = mediaController
        if (controller == null) {
            controllerFuture?.addListener({
                try {
                    val ctrl = controllerFuture?.get() ?: return@addListener
                    ctrl.setMediaItems(mediaItems, targetIndex, 0L)
                    ctrl.prepare()
                    if (playWhenReady) {
                        ctrl.play()
                    } else {
                        ctrl.pause()
                    }
                } catch (e: Exception) {}
            }, MoreExecutors.directExecutor())
        } else {
            controller.setMediaItems(mediaItems, targetIndex, 0L)
            controller.prepare()
            if (playWhenReady) {
                controller.play()
            } else {
                controller.pause()
            }
        }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = targetIndex,
                isPlaying = playWhenReady
            )
        }
    }

    override fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    override fun play() {
        mediaController?.play()
    }

    override fun pause() {
        commitActiveListeningEvent()
        mediaController?.pause()
    }

    override fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    override fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    override fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val controller = mediaController ?: return
        val exoMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        controller.repeatMode = exoMode
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    override fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffled = newShuffle) }
    }

    override fun setVolume(volume: Float) {
        val controller = mediaController ?: return
        val clamped = volume.coerceIn(0f, 1f)
        controller.volume = clamped
        _playbackState.update { it.copy(volume = clamped) }
    }

    override fun addToQueue(track: Track) {
        val updatedQueue = currentQueue + track
        currentQueue = updatedQueue
        customQueueIds.add(track.id)
        mediaController?.addMediaItem(track.toMediaItem())
        _playbackState.update { it.copy(queue = updatedQueue) }
    }

    override fun playNext(track: Track) {
        val currentIndex = _playbackState.value.queueIndex
        val insertIndex = if (currentIndex != -1 && currentIndex in currentQueue.indices) currentIndex + 1 else currentQueue.size
        val updatedQueue = currentQueue.toMutableList().apply { add(insertIndex, track) }
        currentQueue = updatedQueue
        customQueueIds.add(track.id)
        mediaController?.addMediaItem(insertIndex, track.toMediaItem())
        _playbackState.update { it.copy(queue = updatedQueue) }
    }

    override fun removeFromQueue(index: Int) {
        if (index in currentQueue.indices) {
            val updatedQueue = currentQueue.toMutableList().apply { removeAt(index) }
            currentQueue = updatedQueue
            mediaController?.removeMediaItem(index)
            _playbackState.update { it.copy(queue = updatedQueue) }
        }
    }

    override fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val updatedQueue = currentQueue.toMutableList()
            val item = updatedQueue.removeAt(fromIndex)
            updatedQueue.add(toIndex, item)
            currentQueue = updatedQueue
            mediaController?.moveMediaItem(fromIndex, toIndex)
            _playbackState.update { it.copy(queue = updatedQueue) }
        }
    }

    override fun clearQueue() {
        currentQueue = emptyList()
        mediaController?.clearMediaItems()
        _playbackState.update { it.copy(queue = emptyList(), currentTrack = null, queueIndex = -1) }
    }

    fun release() {
        stopPositionTicker()
        mediaController?.release()
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    fun syncRemoteMetadata(
        remoteTrack: Track,
        playableUri: String,
        isPlaying: Boolean,
        positionMs: Long,
        serverName: String
    ) {
        val controller = mediaController ?: return

        // Mute local volume while running in remote-only output mode
        controller.volume = 0f

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
                .setUri(Uri.parse(playableUri))
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
        }

        if (isPlaying && !controller.isPlaying) {
            controller.play()
        } else if (!isPlaying && controller.isPlaying) {
            controller.pause()
        }

        val drift = Math.abs(controller.currentPosition - positionMs)
        if (drift > 2000L) {
            controller.seekTo(positionMs)
        }
    }

    fun restoreLocalOutput() {
        val controller = mediaController ?: return
        controller.volume = 1f
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
