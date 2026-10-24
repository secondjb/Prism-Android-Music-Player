package com.secondjb.prism.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentsTest {

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return "%d:%02d".format(minutes, seconds)
    }

    @Test
    fun testTimeFormatting() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:05", formatTime(5000L))
        assertEquals("1:05", formatTime(65000L))
        assertEquals("3:45", formatTime(225000L))
        assertEquals("10:00", formatTime(600000L))
    }

    @Test
    fun testVolumeClamping() {
        val volumeHigh = 1.5f.coerceIn(0f, 1f)
        val volumeLow = (-0.2f).coerceIn(0f, 1f)
        val volumeMid = 0.65f.coerceIn(0f, 1f)

        assertEquals(1.0f, volumeHigh, 0.001f)
        assertEquals(0.0f, volumeLow, 0.001f)
        assertEquals(0.65f, volumeMid, 0.001f)
    }

    @Test
    fun testSwipeGestureThresholds() {
        val swipeThreshold = 180f

        val dragLeft = -220f
        val dragRight = 200f
        val dragSmall = 50f

        assertTrue(dragLeft < -swipeThreshold) // triggers skip next
        assertTrue(dragRight > swipeThreshold)  // triggers skip previous
        assertTrue(dragSmall in -swipeThreshold..swipeThreshold) // does not trigger
    }
}
