package com.secondjb.prism.ui

import com.secondjb.prism.ui.components.calculateScrollProgress
import com.secondjb.prism.ui.components.calculateTargetIndex
import com.secondjb.prism.ui.components.getSectionHeader
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

    @Test
    fun testFastScrollbarSectionHeader() {
        assertEquals("B", getSectionHeader("Bohemian Rhapsody"))
        assertEquals("S", getSectionHeader("Starman"))
        assertEquals("A", getSectionHeader("All Along the Watchtower"))
        assertEquals("B", getSectionHeader("...Baby One More Time"))
        assertEquals("#", getSectionHeader("1999"))
        assertEquals("#", getSectionHeader("21 Guns"))
        assertEquals("#", getSectionHeader("!@#$%"))
        assertEquals("#", getSectionHeader(""))
        assertEquals("#", getSectionHeader("   "))
        assertEquals("#", getSectionHeader(null))
    }

    @Test
    fun testFastScrollbarScrollProgress() {
        // Zero or 1 item
        assertEquals(0f, calculateScrollProgress(0, 0, 100, 1, 5), 0.001f)
        assertEquals(0f, calculateScrollProgress(0, 0, 100, 0, 0), 0.001f)

        // 100 items total, 10 visible -> max scroll index is 90
        // Top
        assertEquals(0f, calculateScrollProgress(0, 0, 100, 100, 10), 0.001f)
        // Middle (index 45)
        assertEquals(0.5f, calculateScrollProgress(45, 0, 100, 100, 10), 0.001f)
        // Bottom (index 90)
        assertEquals(1.0f, calculateScrollProgress(90, 0, 100, 100, 10), 0.001f)
        // Clamping beyond boundary
        assertEquals(1.0f, calculateScrollProgress(95, 0, 100, 100, 10), 0.001f)
    }

    @Test
    fun testFastScrollbarTargetIndexCalculation() {
        val total = 100
        assertEquals(0, calculateTargetIndex(0.0f, total))
        assertEquals(50, calculateTargetIndex(0.5f, total))
        assertEquals(99, calculateTargetIndex(1.0f, total))

        // Clamping negative or exceeding 1.0f
        assertEquals(0, calculateTargetIndex(-0.2f, total))
        assertEquals(99, calculateTargetIndex(1.5f, total))

        // Single or zero items
        assertEquals(0, calculateTargetIndex(0.5f, 1))
        assertEquals(0, calculateTargetIndex(0.5f, 0))
    }
}
