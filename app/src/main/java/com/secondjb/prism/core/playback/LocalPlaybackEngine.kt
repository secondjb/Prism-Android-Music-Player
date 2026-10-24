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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) : PlaybackController {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var currentQueue: List<Track> = emptyList()
    private var positionTickerJob: Job? = null

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
                val currentMediaId = mediaItem?.mediaId
                val trackIndex = currentQueue.indexOfFirst { it.id == currentMediaId }
                val track = if (trackIndex != -1) currentQueue[trackIndex] else null

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
                }
                delay(200)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    override fun playTrack(track: Track, queue: List<Track>, startIndex: Int) {
        currentQueue = queue
        val targetIndex = if (startIndex in queue.indices) startIndex else queue.indexOf(track).coerceAtLeast(0)
        val mediaItems = queue.map { it.toMediaItem() }

        val controller = mediaController
        if (controller == null) {
            controllerFuture?.addListener({
                try {
                    val ctrl = controllerFuture?.get() ?: return@addListener
                    ctrl.setMediaItems(mediaItems, targetIndex, 0L)
                    ctrl.prepare()
                    ctrl.play()
                } catch (e: Exception) {}
            }, MoreExecutors.directExecutor())
        } else {
            controller.setMediaItems(mediaItems, targetIndex, 0L)
            controller.prepare()
            controller.play()
        }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = targetIndex,
                isPlaying = true
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
        mediaController?.addMediaItem(track.toMediaItem())
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
