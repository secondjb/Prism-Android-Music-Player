package com.secondjb.prism.playback

import com.secondjb.prism.core.playback.SleepTimerManager
import com.secondjb.prism.core.playback.SleepTimerMode
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import com.secondjb.prism.domain.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaybackController : PlaybackController {
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    var pauseCalled = false
    var playCalled = false
    val queueList = mutableListOf<Track>()

    override fun playTrack(track: Track, queue: List<Track>, startIndex: Int) {
        queueList.clear()
        queueList.addAll(queue)
        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            queue = queue,
            queueIndex = startIndex.coerceAtLeast(0),
            isPlaying = true
        )
    }

    override fun togglePlayPause() {}
    override fun play() { playCalled = true }
    override fun pause() {
        pauseCalled = true
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }
    override fun seekTo(positionMs: Long) {}
    override fun skipNext() {}
    override fun skipPrevious() {}
    override fun setRepeatMode(mode: RepeatMode) {}
    override fun toggleShuffle() {}
    override fun setVolume(volume: Float) {}
    override fun addToQueue(track: Track) { queueList.add(track) }
    override fun playNext(track: Track) { queueList.add(0, track) }
    override fun removeFromQueue(index: Int) {}
    override fun reorderQueue(fromIndex: Int, toIndex: Int) {}
    override fun clearQueue() { queueList.clear() }

    fun transitionToTrack(track: Track) {
        _playbackState.value = _playbackState.value.copy(currentTrack = track)
    }
}

class SleepTimerManagerTest {

    private lateinit var controller: FakePlaybackController
    private lateinit var manager: SleepTimerManager

    private val track1 = Track(id = "1", uri = "uri1", title = "Track 1", artist = "Artist", album = "Album", durationMs = 180000)
    private val track2 = Track(id = "2", uri = "uri2", title = "Track 2", artist = "Artist", album = "Album", durationMs = 200000)
    private val track3 = Track(id = "3", uri = "uri3", title = "Track 3", artist = "Artist", album = "Album", durationMs = 220000)

    @Before
    fun setup() {
        controller = FakePlaybackController()
        controller.playTrack(track1, listOf(track1, track2, track3))
        manager = SleepTimerManager(controller, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun testStartTimerByMinutes() {
        manager.startTimerByMinutes(15, finishLastSong = true, playToEndOfQueue = false)
        val state = manager.timerState.value

        assertTrue(state.isActive)
        assertEquals(SleepTimerMode.TIME, state.mode)
        assertEquals(15 * 60L, state.remainingSeconds)
        assertTrue(state.finishLastSong)
        assertFalse(state.playToEndOfQueue)

        manager.cancelTimer()
        assertFalse(manager.timerState.value.isActive)
    }

    @Test
    fun testStartTimerByTracksCountdown() {
        manager.startTimerByTracks(2, finishLastSong = false, playToEndOfQueue = false)
        assertTrue(manager.timerState.value.isActive)
        assertEquals(2, manager.timerState.value.remainingTracks)

        // Simulate 1st track transition
        controller.transitionToTrack(track2)
        assertEquals(1, manager.timerState.value.remainingTracks)
        assertFalse(controller.pauseCalled)

        // Simulate 2nd track transition -> should stop!
        controller.transitionToTrack(track3)
        assertTrue(controller.pauseCalled)
        assertFalse(manager.timerState.value.isActive)
    }

    @Test
    fun testFinishLastSongWaitsForTrackTransition() {
        manager.startTimerByTracks(1, finishLastSong = true)
        controller.transitionToTrack(track2)
        assertTrue(controller.pauseCalled)
        assertFalse(manager.timerState.value.isActive)
    }
}
