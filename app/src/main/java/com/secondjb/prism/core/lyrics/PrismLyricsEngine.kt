package com.secondjb.prism.core.lyrics

import android.content.Context
import android.net.Uri
import com.secondjb.prism.core.metadata.AudioTagMetadataExtractor
import com.secondjb.prism.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust 3-tier lyrics engine for Prism Android:
 * 1. Checks sidecar .lrc/.txt files in track directory
 * 2. Extracts embedded Vorbis/ID3/MP4 metadata tags
 * 3. Fallbacks to LRCLIB online API (if online auto-fetch is enabled)
 */
class PrismLyricsEngine(private val context: Context) {

    private val lyricsCache = ConcurrentHashMap<String, String>()

    suspend fun resolveLyrics(
        track: Track,
        allowOnlineFetch: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        // 0. Cache hit
        lyricsCache[track.id]?.let { return@withContext it }

        // 1. In-memory track model lyrics
        if (!track.lyrics.isNullOrBlank()) {
            lyricsCache[track.id] = track.lyrics
            return@withContext track.lyrics
        }

        // 2. Direct filesystem inspection (Sidecar .lrc or embedded tags)
        val path = track.filePath
        if (!path.isNullOrBlank()) {
            val audioFile = File(path)
            if (audioFile.exists()) {
                val extracted = AudioTagMetadataExtractor.extractLyrics(audioFile)
                if (!extracted.isNullOrBlank()) {
                    lyricsCache[track.id] = extracted
                    return@withContext extracted
                }
            }
        }

        // 3. Fallback: stream from ContentResolver Uri for embedded tags
        try {
            val uri = Uri.parse(track.uri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val embedded = AudioTagMetadataExtractor.extractFromStream(stream)
                if (!embedded.isNullOrBlank()) {
                    lyricsCache[track.id] = embedded
                    return@withContext embedded
                }
            }
        } catch (_: Exception) {}

        // 4. Online LRCLIB Fetch (if explicitly enabled)
        if (allowOnlineFetch) {
            val online = LrclibClient.fetchLyrics(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSecs = track.durationSecs
            )
            if (!online.isNullOrBlank()) {
                lyricsCache[track.id] = online
                return@withContext online
            }
        }

        null
    }

    fun clearCache() {
        lyricsCache.clear()
    }
}
