package com.secondjb.prism.playback

import com.secondjb.prism.domain.model.AudioFormatInfo
import com.secondjb.prism.domain.model.PlaybackState
import com.secondjb.prism.domain.model.RepeatMode
import com.secondjb.prism.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests covering playback states, queue boundary conditions,
 * repeat modes, and audio metadata formatting.
 */
class PlaybackRegressionTest {

    @Test
    fun testProgressFractionBoundaryConditions() {
        // Zero duration should yield 0f without division by zero
        val zeroDurationState = PlaybackState(durationMs = 0L, positionMs = 5000L)
        assertEquals(0f, zeroDurationState.progressFraction, 0.0001f)

        // Position greater than duration should clamp to 1.0f
        val overflowState = PlaybackState(durationMs = 100000L, positionMs = 150000L)
        assertEquals(1.0f, overflowState.progressFraction, 0.0001f)

        // Negative position should clamp to 0.0f
        val negativePosState = PlaybackState(durationMs = 100000L, positionMs = -500L)
        assertEquals(0f, negativePosState.progressFraction, 0.0001f)

        // Normal fraction
        val normalState = PlaybackState(durationMs = 200000L, positionMs = 50000L)
        assertEquals(0.25f, normalState.progressFraction, 0.0001f)
    }

    @Test
    fun testHasNextAndHasPreviousMatrix() {
        val t1 = Track("1", "u1", "Track 1", "Artist", "Album", 100000L)
        val t2 = Track("2", "u2", "Track 2", "Artist", "Album", 100000L)

        // 1-track queue, RepeatMode.OFF
        val singleTrackOff = PlaybackState(
            queue = listOf(t1),
            queueIndex = 0,
            positionMs = 1000L,
            repeatMode = RepeatMode.OFF
        )
        assertFalse(singleTrackOff.hasNext)
        assertFalse(singleTrackOff.hasPrevious)

        // 1-track queue, RepeatMode.ALL
        val singleTrackRepeatAll = singleTrackOff.copy(repeatMode = RepeatMode.ALL)
        assertTrue(singleTrackRepeatAll.hasNext)

        // 1-track queue, position > 3s -> hasPrevious is true (rewind)
        val singleTrackRewind = singleTrackOff.copy(positionMs = 3500L)
        assertTrue(singleTrackRewind.hasPrevious)

        // 2-track queue at first track
        val multiFirst = PlaybackState(
            queue = listOf(t1, t2),
            queueIndex = 0,
            positionMs = 500L
        )
        assertTrue(multiFirst.hasNext)
        assertFalse(multiFirst.hasPrevious)

        // 2-track queue at last track
        val multiLast = PlaybackState(
            queue = listOf(t1, t2),
            queueIndex = 1,
            positionMs = 500L
        )
        assertFalse(multiLast.hasNext)
        assertTrue(multiLast.hasPrevious)
    }

    @Test
    fun testAudioFormatInfoFormattingAndHiRes() {
        // CD Quality: 16-bit / 44.1 kHz MP3
        val cdQuality = AudioFormatInfo(
            sampleRate = 44100,
            bitDepth = 16,
            channels = 2,
            bitRateKbps = 320,
            format = "MP3"
        )
        assertFalse(cdQuality.isHiRes)
        assertEquals("44.1 kHz", cdQuality.formattedSampleRate)
        assertEquals("MP3 • 16-bit / 44.1 kHz • 320 kbps", cdQuality.displayBadge)

        // Studio Quality: 24-bit / 96 kHz FLAC
        val studioFlac = AudioFormatInfo(
            sampleRate = 96000,
            bitDepth = 24,
            channels = 2,
            bitRateKbps = 2450,
            format = "FLAC"
        )
        assertTrue(studioFlac.isHiRes)
        assertEquals("96 kHz", studioFlac.formattedSampleRate)
        assertEquals("FLAC • 24-bit / 96 kHz • 2450 kbps", studioFlac.displayBadge)

        // Ultra Hi-Res: 24-bit / 192 kHz FLAC
        val ultraHiRes = AudioFormatInfo(
            sampleRate = 192000,
            bitDepth = 24,
            channels = 2,
            bitRateKbps = 4800,
            format = "FLAC"
        )
        assertTrue(ultraHiRes.isHiRes)
        assertEquals("192 kHz", ultraHiRes.formattedSampleRate)

        // Format with missing bitrate
        val noBitrate = AudioFormatInfo(
            sampleRate = 48000,
            bitDepth = 16,
            channels = 2,
            bitRateKbps = null,
            format = "WAV"
        )
        assertEquals("WAV • 16-bit / 48 kHz", noBitrate.displayBadge)
    }
}
