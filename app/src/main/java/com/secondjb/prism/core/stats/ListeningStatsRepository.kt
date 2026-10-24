package com.secondjb.prism.core.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.secondjb.prism.core.sync.protocol.SyncListeningEventItem
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Listening event representing a played song, interoperable with Prism Windows listening_stats.db.
 */
data class ListeningEventRecord(
    val id: Long = 0,
    val songTitle: String,
    val artistName: String,
    val albumName: String?,
    val genre: String?,
    val durationMs: Long,
    val playedAt: String
)

data class StatTopItem(
    val name: String,
    val count: Int,
    val listenedMs: Long,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val album: String? = null
)

enum class StatsFilterRange {
    ALL_TIME,
    THIRTY_DAYS,
    SEVEN_DAYS,
    TODAY
}

enum class StatsMetric {
    TIME,
    PLAYS
}

data class AggregatedStats(
    val totalTracksPlayed: Int = 0,
    val totalListeningSeconds: Long = 0L,
    val topArtist: String? = null,
    val topAlbum: String? = null,
    val topSong: String? = null,
    val topArtistsList: List<StatTopItem> = emptyList(),
    val topSongsList: List<StatTopItem> = emptyList(),
    val topAlbumsList: List<StatTopItem> = emptyList(),
    val listeningHabitsHours: List<Int> = List(24) { 0 }
)

class ListeningStatsDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "listening_stats.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                song_title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                album_name TEXT,
                genre TEXT,
                duration_ms INTEGER NOT NULL,
                played_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic when needed
    }
}

class ListeningStatsRepository(context: Context) {
    private val dbHelper = ListeningStatsDatabaseHelper(context.applicationContext)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val _statsUpdateTrigger = MutableStateFlow(0L)
    val statsUpdateTrigger: StateFlow<Long> = _statsUpdateTrigger.asStateFlow()

    suspend fun logListeningEvent(
        track: Track,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("song_title", track.title)
                put("artist_name", track.artist)
                put("album_name", track.album)
                put("genre", track.genre)
                put("duration_ms", durationMs)
                put("played_at", isoFormat.format(Date()))
            }
            db.insert("listening_events", null, values)
            _statsUpdateTrigger.value = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchListeningEvents(range: StatsFilterRange = StatsFilterRange.ALL_TIME): List<ListeningEventRecord> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<ListeningEventRecord>()
            try {
                val db = dbHelper.readableDatabase
                val (whereClause, whereArgs) = getWhereClauseForRange(range)

                val cursor = db.query(
                    "listening_events",
                    arrayOf("id", "song_title", "artist_name", "album_name", "genre", "duration_ms", "played_at"),
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    "id DESC"
                )

                cursor.use { c ->
                    val idCol = c.getColumnIndexOrThrow("id")
                    val titleCol = c.getColumnIndexOrThrow("song_title")
                    val artistCol = c.getColumnIndexOrThrow("artist_name")
                    val albumCol = c.getColumnIndexOrThrow("album_name")
                    val genreCol = c.getColumnIndexOrThrow("genre")
                    val durCol = c.getColumnIndexOrThrow("duration_ms")
                    val dateCol = c.getColumnIndexOrThrow("played_at")

                    while (c.moveToNext()) {
                        list.add(
                            ListeningEventRecord(
                                id = c.getLong(idCol),
                                songTitle = c.getString(titleCol),
                                artistName = c.getString(artistCol),
                                albumName = if (c.isNull(albumCol)) null else c.getString(albumCol),
                                genre = if (c.isNull(genreCol)) null else c.getString(genreCol),
                                durationMs = c.getLong(durCol),
                                playedAt = c.getString(dateCol)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list
        }

    /**
     * Seeds realistic listening history based on the user's music library when the database is empty.
     * Matches the behaviour of Prism Windows `generateMockListeningEvents`.
     */
    suspend fun seedMockEventsIfEmpty(libraryTracks: List<Track> = emptyList()) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM listening_events", null)
            val count = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (count > 0) return@withContext

            val sampleArtists = listOf(
                Triple("Queen", "Who Wants To Live Forever", "A Kind of Magic"),
                Triple("IU", "Dear my crazy soulmate", "Love poem"),
                Triple("JENNIE", "Less than a Lover", "Ruby"),
                Triple("Chance Emerson", "Doing A Number", "The Raspberry Men"),
                Triple("Olivia Rodrigo", "drop dead", "GUTS"),
                Triple("ITZY", "Asylum (LIA)", "GOLD"),
                Triple("fun.", "Some Nights", "Some Nights"),
                Triple("Yuqi", "I Like You", "YUQ1"),
                Triple("TUIDE", "SUN KISS", "SUN KISS"),
                Triple("JISOO", "CLICK", "ME"),
                Triple("NMIXX", "Blue Valentine", "Fe3O4: BREAK"),
                Triple("BLACKPINK", "Fxxkboy", "BORN PINK")
            )

            val pool: List<Triple<String, String, String>> = if (libraryTracks.size >= 3) {
                libraryTracks.map {
                    Triple(
                        it.artist.ifBlank { "Unknown Artist" },
                        it.title.ifBlank { "Untitled" },
                        it.album.ifBlank { "Unknown Album" }
                    )
                }
            } else {
                sampleArtists
            }

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val nowMs = System.currentTimeMillis()

            db.beginTransaction()
            try {
                for (i in 0 until 120) {
                    val item = pool[i % pool.size]
                    val dayOffset = Random.nextInt(0, 28)
                    val hour = if (Random.nextFloat() < 0.75f) 14 + Random.nextInt(0, 9) else Random.nextInt(0, 24)
                    val minute = Random.nextInt(0, 60)
                    val durationMs = (Random.nextInt(150, 320)) * 1000L

                    cal.timeInMillis = nowMs
                    cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
                    cal.set(Calendar.HOUR_OF_DAY, hour % 24)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, Random.nextInt(0, 60))

                    val playedAtStr = isoFormat.format(cal.time)

                    val values = ContentValues().apply {
                        put("song_title", item.second)
                        put("artist_name", item.first)
                        put("album_name", item.third)
                        put("genre", "Pop")
                        put("duration_ms", durationMs)
                        put("played_at", playedAtStr)
                    }
                    db.insert("listening_events", null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            _statsUpdateTrigger.value = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getAggregatedStats(
        range: StatsFilterRange = StatsFilterRange.ALL_TIME,
        metric: StatsMetric = StatsMetric.TIME,
        libraryTracks: List<Track> = emptyList()
    ): AggregatedStats = withContext(Dispatchers.IO) {
        val events = fetchListeningEvents(range)

        if (events.isEmpty()) {
            return@withContext AggregatedStats()
        }

        val totalPlays = events.size
        val totalMs = events.sumOf { it.durationMs }
        val totalSeconds = totalMs / 1000

        // Track Artwork & Album maps for matching
        val artBySong = libraryTracks.associate {
            "${it.title.lowercase()}::${it.artist.lowercase()}" to it.artworkUri
        }
        val artByAlbum = libraryTracks.filter { it.album.isNotBlank() }.associate {
            "${it.album.lowercase()}::${it.artist.lowercase()}" to it.artworkUri
        }
        val artByArtist = libraryTracks.filter { it.artist.isNotBlank() }.associate {
            it.artist.lowercase() to it.artworkUri
        }

        // Top Artists
        val artistMap = mutableMapOf<String, StatTopItem>()
        events.forEach { e ->
            val curr = artistMap[e.artistName] ?: StatTopItem(
                name = e.artistName,
                count = 0,
                listenedMs = 0L,
                subtitle = "Artist",
                artworkUri = artByArtist[e.artistName.lowercase()]
            )
            artistMap[e.artistName] = curr.copy(
                count = curr.count + 1,
                listenedMs = curr.listenedMs + e.durationMs,
                artworkUri = curr.artworkUri ?: artByArtist[e.artistName.lowercase()]
            )
        }
        val topArtists = artistMap.values.sortedWith(
            if (metric == StatsMetric.TIME) {
                compareByDescending<StatTopItem> { it.listenedMs }.thenByDescending { it.count }
            } else {
                compareByDescending<StatTopItem> { it.count }.thenByDescending { it.listenedMs }
            }
        )

        // Top Songs
        val songMap = mutableMapOf<String, StatTopItem>()
        events.forEach { e ->
            val key = "${e.songTitle} - ${e.artistName}"
            val songLookupKey = "${e.songTitle.lowercase()}::${e.artistName.lowercase()}"
            val curr = songMap[key] ?: StatTopItem(
                name = e.songTitle,
                count = 0,
                listenedMs = 0L,
                subtitle = e.artistName,
                album = e.albumName,
                artworkUri = artBySong[songLookupKey]
            )
            songMap[key] = curr.copy(
                count = curr.count + 1,
                listenedMs = curr.listenedMs + e.durationMs,
                artworkUri = curr.artworkUri ?: artBySong[songLookupKey]
            )
        }
        val topSongs = songMap.values.sortedWith(
            if (metric == StatsMetric.TIME) {
                compareByDescending<StatTopItem> { it.listenedMs }.thenByDescending { it.count }
            } else {
                compareByDescending<StatTopItem> { it.count }.thenByDescending { it.listenedMs }
            }
        )

        // Top Albums
        val albumMap = mutableMapOf<String, StatTopItem>()
        events.forEach { e ->
            if (!e.albumName.isNullOrBlank()) {
                val key = "${e.albumName} - ${e.artistName}"
                val albumLookupKey = "${e.albumName.lowercase()}::${e.artistName.lowercase()}"
                val curr = albumMap[key] ?: StatTopItem(
                    name = e.albumName,
                    count = 0,
                    listenedMs = 0L,
                    subtitle = e.artistName,
                    artworkUri = artByAlbum[albumLookupKey]
                )
                albumMap[key] = curr.copy(
                    count = curr.count + 1,
                    listenedMs = curr.listenedMs + e.durationMs,
                    artworkUri = curr.artworkUri ?: artByAlbum[albumLookupKey]
                )
            }
        }
        val topAlbums = albumMap.values.sortedWith(
            if (metric == StatsMetric.TIME) {
                compareByDescending<StatTopItem> { it.listenedMs }.thenByDescending { it.count }
            } else {
                compareByDescending<StatTopItem> { it.count }.thenByDescending { it.listenedMs }
            }
        )

        // Listening habits by hour (24 hours)
        val habits = IntArray(24) { 0 }
        events.forEach { e ->
            try {
                val date = isoFormat.parse(e.playedAt)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour in 0..23) {
                        habits[hour]++
                    }
                }
            } catch (_: Exception) {}
        }

        AggregatedStats(
            totalTracksPlayed = totalPlays,
            totalListeningSeconds = totalSeconds,
            topArtist = topArtists.firstOrNull()?.name,
            topAlbum = topAlbums.firstOrNull()?.name,
            topSong = topSongs.firstOrNull()?.name,
            topArtistsList = topArtists,
            topSongsList = topSongs,
            topAlbumsList = topAlbums,
            listeningHabitsHours = habits.toList()
        )
    }

    /**
     * Inserts listening events received from companion desktop sync with deduplication.
     * Matches the exact SQLite WHERE NOT EXISTS logic of Prism Windows insert_synced_events.
     */
    suspend fun insertSyncedEvents(events: List<SyncListeningEventItem>): Int = withContext(Dispatchers.IO) {
        var inserted = 0
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (ev in events) {
                    val cursor = db.rawQuery(
                        """
                        SELECT 1 FROM listening_events 
                        WHERE song_title = ? AND artist_name = ? AND duration_ms = ? AND played_at = ?
                        """.trimIndent(),
                        arrayOf(ev.songTitle, ev.artistName, ev.durationMs.toString(), ev.playedAt)
                    )
                    val exists = cursor.use { it.moveToFirst() }
                    if (!exists) {
                        val values = ContentValues().apply {
                            put("song_title", ev.songTitle)
                            put("artist_name", ev.artistName)
                            put("album_name", ev.albumName)
                            put("genre", ev.genre)
                            put("duration_ms", ev.durationMs)
                            put("played_at", ev.playedAt)
                        }
                        db.insert("listening_events", null, values)
                        inserted++
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (inserted > 0) {
                _statsUpdateTrigger.value = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        inserted
    }

    /**
     * Fetches all events formatted for P2P sync to desktop companion (no limit).
     */
    suspend fun fetchAllEventsForSync(limit: Int? = null): List<SyncListeningEventItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SyncListeningEventItem>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "listening_events",
                arrayOf("song_title", "artist_name", "album_name", "genre", "duration_ms", "played_at"),
                null,
                null,
                null,
                null,
                "played_at DESC",
                limit?.toString()
            )
            cursor.use { c ->
                val titleCol = c.getColumnIndexOrThrow("song_title")
                val artistCol = c.getColumnIndexOrThrow("artist_name")
                val albumCol = c.getColumnIndexOrThrow("album_name")
                val genreCol = c.getColumnIndexOrThrow("genre")
                val durCol = c.getColumnIndexOrThrow("duration_ms")
                val dateCol = c.getColumnIndexOrThrow("played_at")

                while (c.moveToNext()) {
                    list.add(
                        SyncListeningEventItem(
                            songTitle = c.getString(titleCol),
                            artistName = c.getString(artistCol),
                            albumName = if (c.isNull(albumCol)) null else c.getString(albumCol),
                            genre = if (c.isNull(genreCol)) null else c.getString(genreCol),
                            durationMs = c.getLong(durCol),
                            playedAt = c.getString(dateCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        try {
            dbHelper.writableDatabase.delete("listening_events", null, null)
            _statsUpdateTrigger.value = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getWhereClauseForRange(range: StatsFilterRange): Pair<String?, Array<String>?> {
        return when (range) {
            StatsFilterRange.ALL_TIME -> null to null
            StatsFilterRange.TODAY -> {
                "played_at >= datetime('now', 'start of day')" to null
            }
            StatsFilterRange.SEVEN_DAYS -> {
                "played_at >= datetime('now', '-7 days')" to null
            }
            StatsFilterRange.THIRTY_DAYS -> {
                "played_at >= datetime('now', '-30 days')" to null
            }
        }
    }
}
