package com.secondjb.prism.stats

import com.secondjb.prism.core.stats.AggregatedStats
import com.secondjb.prism.core.stats.StatTopItem
import com.secondjb.prism.core.stats.StatsFilterRange
import com.secondjb.prism.core.stats.StatsMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    @Test
    fun testStatTopItemCreationAndProperties() {
        val item = StatTopItem(
            name = "Who Wants To Live Forever",
            count = 39,
            listenedMs = 11040000L, // 3h 4m
            subtitle = "Queen",
            album = "A Kind of Magic",
            artworkUri = "content://media/external/audio/albumart/1"
        )

        assertEquals("Who Wants To Live Forever", item.name)
        assertEquals(39, item.count)
        assertEquals(11040000L, item.listenedMs)
        assertEquals("Queen", item.subtitle)
        assertEquals("A Kind of Magic", item.album)
        assertEquals("content://media/external/audio/albumart/1", item.artworkUri)
    }

    @Test
    fun testSortingByTimeVsPlays() {
        val itemA = StatTopItem(name = "Song A", count = 100, listenedMs = 50000L) // high plays, low time
        val itemB = StatTopItem(name = "Song B", count = 20, listenedMs = 300000L) // low plays, high time

        val list = listOf(itemA, itemB)

        // Sort by TIME
        val sortedByTime = list.sortedWith(
            compareByDescending<StatTopItem> { it.listenedMs }.thenByDescending { it.count }
        )
        assertEquals("Song B", sortedByTime.first().name)

        // Sort by PLAYS
        val sortedByPlays = list.sortedWith(
            compareByDescending<StatTopItem> { it.count }.thenByDescending { it.listenedMs }
        )
        assertEquals("Song A", sortedByPlays.first().name)
    }

    @Test
    fun testAggregatedStatsStructure() {
        val topSongs = listOf(
            StatTopItem("Song 1", 50, 100000L, subtitle = "Artist 1"),
            StatTopItem("Song 2", 30, 80000L, subtitle = "Artist 2")
        )
        val topArtists = listOf(
            StatTopItem("Artist 1", 80, 200000L, subtitle = "Artist"),
            StatTopItem("Artist 2", 30, 80000L, subtitle = "Artist")
        )
        val topAlbums = listOf(
            StatTopItem("Album 1", 40, 150000L, subtitle = "Artist 1")
        )

        val aggregated = AggregatedStats(
            totalTracksPlayed = 110,
            totalListeningSeconds = 280L,
            topArtist = "Artist 1",
            topAlbum = "Album 1",
            topSong = "Song 1",
            topArtistsList = topArtists,
            topSongsList = topSongs,
            topAlbumsList = topAlbums,
            listeningHabitsHours = List(24) { if (it == 14) 10 else 0 }
        )

        assertEquals(110, aggregated.totalTracksPlayed)
        assertEquals(280L, aggregated.totalListeningSeconds)
        assertEquals("Artist 1", aggregated.topArtist)
        assertEquals("Album 1", aggregated.topAlbum)
        assertEquals("Song 1", aggregated.topSong)
        assertEquals(2, aggregated.topArtistsList.size)
        assertEquals(2, aggregated.topSongsList.size)
        assertEquals(1, aggregated.topAlbumsList.size)
        assertEquals(24, aggregated.listeningHabitsHours.size)
        assertEquals(10, aggregated.listeningHabitsHours[14])
    }

    @Test
    fun testStatsFilterRangeEnum() {
        val ranges = StatsFilterRange.values()
        assertEquals(4, ranges.size)
        assertTrue(ranges.contains(StatsFilterRange.ALL_TIME))
        assertTrue(ranges.contains(StatsFilterRange.THIRTY_DAYS))
        assertTrue(ranges.contains(StatsFilterRange.SEVEN_DAYS))
        assertTrue(ranges.contains(StatsFilterRange.TODAY))
    }

    @Test
    fun testStatsMetricEnum() {
        val metrics = StatsMetric.values()
        assertEquals(2, metrics.size)
        assertEquals(StatsMetric.TIME, StatsMetric.valueOf("TIME"))
        assertEquals(StatsMetric.PLAYS, StatsMetric.valueOf("PLAYS"))
    }
}
