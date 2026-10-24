package com.secondjb.prism.core.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val subtitle: String? = null
)

enum class StatsFilterRange {
    TODAY,
    SEVEN_DAYS,
    THIRTY_DAYS,
    ALL_TIME
}

data class AggregatedStats(
    val totalTracksPlayed: Int = 0,
    val totalListeningSeconds: Long = 0L,
    val topArtist: String? = null,
    val topAlbum: String? = null,
    val topSong: String? = null,
    val topArtistsList: List<StatTopItem> = emptyList(),
    val topSongsList: List<StatTopItem> = emptyList()
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
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

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

    suspend fun getAggregatedStats(range: StatsFilterRange = StatsFilterRange.ALL_TIME): AggregatedStats =
        withContext(Dispatchers.IO) {
            val events = fetchListeningEvents(range)
            if (events.isEmpty()) {
                return@withContext AggregatedStats()
            }

            val totalPlays = events.size
            val totalMs = events.sumOf { it.durationMs }
            val totalSeconds = totalMs / 1000

            // Top Artist
            val artistMap = mutableMapOf<String, StatTopItem>()
            events.forEach { e ->
                val curr = artistMap[e.artistName] ?: StatTopItem(e.artistName, 0, 0L)
                artistMap[e.artistName] = curr.copy(
                    count = curr.count + 1,
                    listenedMs = curr.listenedMs + e.durationMs
                )
            }
            val topArtists = artistMap.values.sortedByDescending { it.listenedMs }

            // Top Songs
            val songMap = mutableMapOf<String, StatTopItem>()
            events.forEach { e ->
                val key = "${e.songTitle} - ${e.artistName}"
                val curr = songMap[key] ?: StatTopItem(e.songTitle, 0, 0L, subtitle = e.artistName)
                songMap[key] = curr.copy(
                    count = curr.count + 1,
                    listenedMs = curr.listenedMs + e.durationMs
                )
            }
            val topSongs = songMap.values.sortedByDescending { it.listenedMs }

            // Top Album
            val albumMap = mutableMapOf<String, Int>()
            events.forEach { e ->
                if (!e.albumName.isNullOrBlank()) {
                    albumMap[e.albumName] = (albumMap[e.albumName] ?: 0) + 1
                }
            }
            val topAlbum = albumMap.maxByOrNull { it.value }?.key

            AggregatedStats(
                totalTracksPlayed = totalPlays,
                totalListeningSeconds = totalSeconds,
                topArtist = topArtists.firstOrNull()?.name,
                topAlbum = topAlbum,
                topSong = topSongs.firstOrNull()?.name,
                topArtistsList = topArtists.take(10),
                topSongsList = topSongs.take(10)
            )
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
