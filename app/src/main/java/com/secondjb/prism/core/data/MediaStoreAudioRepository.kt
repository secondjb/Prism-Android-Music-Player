package com.secondjb.prism.core.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.secondjb.prism.domain.model.Album
import com.secondjb.prism.domain.model.Artist
import com.secondjb.prism.domain.model.ListeningStats
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans local storage audio files and album artwork via Android MediaStore.
 */
class MediaStoreAudioRepository(private val context: Context) {

    private val albumArtUriBase = Uri.parse("content://media/external/audio/albumart")

    suspend fun loadAllTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = ContentUris.withAppendedId(albumArtUriBase, albumId).toString()
                    val durationMs = cursor.getLong(durationCol)
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val year = cursor.getInt(yearCol).takeIf { it > 0 }
                    val trackNum = cursor.getInt(trackCol).takeIf { it > 0 }
                    val dateAdded = cursor.getLong(dateCol)
                    val filePath = if (dataCol != -1) cursor.getString(dataCol) else null

                    val format = resolveFormatFromMime(mimeType)

                    tracks.add(
                        Track(
                            id = id.toString(),
                            uri = contentUri,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            sampleRate = if (format == "FLAC") 96000 else 44100,
                            bitDepth = if (format == "FLAC") 24 else 16,
                            channels = 2,
                            bitRateKbps = if (format == "FLAC") 1411 else 320,
                            format = format,
                            artworkUri = artworkUri,
                            filePath = filePath,
                            year = year,
                            trackNumber = trackNum,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Return whatever was read or empty
        }

        tracks
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val tracks = loadAllTracks()
        tracks.groupBy { it.album to it.artist }.map { (key, albumTracks) ->
            val firstTrack = albumTracks.first()
            Album(
                id = "${key.first}|${key.second}".hashCode().toString(),
                title = key.first,
                artist = key.second,
                artworkUri = firstTrack.artworkUri,
                trackCount = albumTracks.size,
                year = albumTracks.mapNotNull { it.year }.minOrNull()
            )
        }.sortedBy { it.title.lowercase() }
    }

    suspend fun loadArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val tracks = loadAllTracks()
        tracks.groupBy { it.artist }.map { (artistName, artistTracks) ->
            val albumCount = artistTracks.map { it.album }.distinct().size
            Artist(
                id = artistName.hashCode().toString(),
                name = artistName,
                trackCount = artistTracks.size,
                albumCount = albumCount,
                artworkUri = artistTracks.firstOrNull()?.artworkUri
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun computeStats(tracks: List<Track>): ListeningStats {
        val totalSecs = tracks.sumOf { it.durationMs } / 1000
        val flacCount = tracks.count { it.format.equals("FLAC", ignoreCase = true) }
        val hiResCount = tracks.count { it.audioFormatInfo.isHiRes }
        val topArtist = tracks.groupBy { it.artist }
            .maxByOrNull { it.value.size }?.key

        val topAlbum = tracks.groupBy { it.album }
            .maxByOrNull { it.value.size }?.key

        return ListeningStats(
            totalTracksPlayed = tracks.size,
            totalListeningSeconds = totalSecs,
            topArtist = topArtist,
            topAlbum = topAlbum,
            flacTracksCount = flacCount,
            hiResTracksCount = hiResCount
        )
    }

    private fun resolveFormatFromMime(mime: String): String = when {
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("wav", ignoreCase = true) -> "WAV"
        mime.contains("aac", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> "AAC"
        mime.contains("ogg", ignoreCase = true) || mime.contains("opus", ignoreCase = true) -> "OGG"
        else -> "MP3"
    }
}
