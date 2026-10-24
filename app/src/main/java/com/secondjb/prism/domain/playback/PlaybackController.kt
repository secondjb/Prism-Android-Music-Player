package com.secondjb.prism.domain.playback

import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Common playback abstraction decoupling the UI and ViewModels from the underlying engine.
 * Can be backed by LocalPlaybackEngine (Media3 ExoPlayer) or RemotePlaybackEngine (P2P companion).
 */
interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>

    fun playTrack(track: Track, queue: List<Track> = listOf(track), startIndex: Int = -1)
    fun togglePlayPause()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setRepeatMode(mode: RepeatMode)
    fun toggleShuffle()
    fun setVolume(volume: Float)
    fun addToQueue(track: Track)
    fun playNext(track: Track)
    fun removeFromQueue(index: Int)
    fun reorderQueue(fromIndex: Int, toIndex: Int)
    fun clearQueue()
}
