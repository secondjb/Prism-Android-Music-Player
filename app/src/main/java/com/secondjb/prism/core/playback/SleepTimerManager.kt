package com.secondjb.prism.core.playback

import com.secondjb.prism.domain.model.PlaybackState
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

enum class SleepTimerMode {
    TIME,
    TRACKS,
    END_OF_QUEUE
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val mode: SleepTimerMode = SleepTimerMode.TIME,
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val remainingTracks: Int = 0,
    val finishLastSong: Boolean = false,
    val playToEndOfQueue: Boolean = false,
    val waitingForLastSongToFinish: Boolean = false
)

class SleepTimerManager(
    private val playbackController: PlaybackController,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {
    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    private var countdownJob: Job? = null
    private var lastObservedTrackKey: String? = null

    var onStopPlaybackCallback: (() -> Unit)? = null
    var onStartSleepTimerCallback: ((SleepTimerMode, Long) -> Unit)? = null
    var onCancelSleepTimerCallback: (() -> Unit)? = null

    init {
        // Observe playback controller state changes to count track completions
        scope.launch {
            playbackController.playbackState.collect { state ->
                handlePlaybackStateUpdate(state)
            }
        }
    }

    private fun getTrackKey(track: Track?): String? {
        if (track == null) return null
        val title = track.title.trim().lowercase()
        val artist = track.artist.trim().lowercase()
        if (title.isNotEmpty()) {
            return "$title|$artist"
        }
        return track.id
    }

    fun handlePlaybackStateUpdate(state: PlaybackState) {
        val currentTrack = state.currentTrack ?: return
        val currentTrackKey = getTrackKey(currentTrack) ?: return

        val current = _timerState.value
        if (current.isActive) {
            // If waiting for last song to finish or about to expire on track count:
            if (current.waitingForLastSongToFinish || (current.mode == SleepTimerMode.TRACKS && current.remainingTracks <= 1)) {
                // If song is within 1200ms of finishing, stop playback before transition leaks audio
                if (state.durationMs > 0 && state.positionMs >= (state.durationMs - 1200L).coerceAtLeast(0L)) {
                    stopPlayback()
                    return
                }
            }
        }

        if (lastObservedTrackKey != null && lastObservedTrackKey != currentTrackKey) {
            // Track transitioned!
            onTrackFinished()
        }
        lastObservedTrackKey = currentTrackKey
    }

    fun startTimerByMinutes(
        minutes: Int,
        finishLastSong: Boolean = false,
        playToEndOfQueue: Boolean = false
    ) {
        cancelTimer()
        val totalSecs = (minutes * 60).toLong()

        _timerState.value = SleepTimerState(
            isActive = true,
            mode = if (playToEndOfQueue) SleepTimerMode.END_OF_QUEUE else SleepTimerMode.TIME,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            finishLastSong = finishLastSong,
            playToEndOfQueue = playToEndOfQueue
        )
        onStartSleepTimerCallback?.invoke(SleepTimerMode.TIME, minutes.toLong())

        countdownJob = scope.launch {
            while (isActive && _timerState.value.remainingSeconds > 0) {
                delay(1000L)
                _timerState.update { curr ->
                    if (curr.remainingSeconds > 1) {
                        curr.copy(remainingSeconds = curr.remainingSeconds - 1)
                    } else {
                        curr.copy(remainingSeconds = 0)
                    }
                }
            }

            // Time reached 0
            val current = _timerState.value
            if (current.finishLastSong) {
                _timerState.update { it.copy(waitingForLastSongToFinish = true) }
            } else {
                stopPlayback()
            }
        }
    }

    fun startTimerByTracks(
        tracksCount: Int,
        finishLastSong: Boolean = false,
        playToEndOfQueue: Boolean = false
    ) {
        cancelTimer()

        // Snapshot current track key so we only count down when a track actually finishes
        lastObservedTrackKey = getTrackKey(playbackController.playbackState.value.currentTrack)

        _timerState.value = SleepTimerState(
            isActive = true,
            mode = if (playToEndOfQueue) SleepTimerMode.END_OF_QUEUE else SleepTimerMode.TRACKS,
            remainingTracks = tracksCount,
            finishLastSong = finishLastSong,
            playToEndOfQueue = playToEndOfQueue
        )
        onStartSleepTimerCallback?.invoke(SleepTimerMode.TRACKS, tracksCount.toLong())
    }

    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
        lastObservedTrackKey = null
        _timerState.value = SleepTimerState()
        onCancelSleepTimerCallback?.invoke()
    }

    private fun onTrackFinished() {
        val current = _timerState.value
        if (!current.isActive) return

        if (current.waitingForLastSongToFinish) {
            stopPlayback()
            return
        }

        if (current.mode == SleepTimerMode.TRACKS) {
            val updatedTracks = current.remainingTracks - 1
            if (updatedTracks <= 0) {
                stopPlayback()
            } else {
                _timerState.update { it.copy(remainingTracks = updatedTracks) }
            }
            return
        }

        if (current.playToEndOfQueue) {
            val localEngine = playbackController as? LocalPlaybackEngine
            val queue = playbackController.playbackState.value.queue
            val queueIdx = playbackController.playbackState.value.queueIndex

            // If user added specific custom queue tracks and we're at or past the end of the custom queue
            if (localEngine != null && localEngine.customQueueIds.isNotEmpty()) {
                val currentTrack = playbackController.playbackState.value.currentTrack
                if (currentTrack != null && !localEngine.customQueueIds.contains(currentTrack.id)) {
                    stopPlayback()
                    return
                }
            } else if (queueIdx >= queue.lastIndex) {
                stopPlayback()
                return
            }
        }
    }

    private fun stopPlayback() {
        cancelTimer()
        playbackController.pause()
        onStopPlaybackCallback?.invoke()
    }
}
