package com.secondjb.prism.core.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Robust client for fetching synced or plain lyrics from the LRCLIB open-source API.
 * Features track/artist metadata cleaning and automatic fallback to query search.
 */
object LrclibClient {

    private const val GET_URL = "https://lrclib.net/api/get"
    private const val SEARCH_URL = "https://lrclib.net/api/search"

    fun cleanTitle(title: String): String {
        if (title.isBlank()) return ""
        return title
            .replace(Regex("^\\d+[\\s.-]+"), "") // Strip leading track numbers like "01. "
            .replace(Regex("\\s*[\\(\\[](feat|ft|with|prod\\.)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\(\\[](remaster(ed)?|deluxe|bonus|anniversary|live|explicit|official\\s*(video|audio|music\\s*video)?|mv|lyrics?|audio)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(remaster(ed)?|deluxe|bonus|live|official\\s*(video|audio)?).*$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun cleanArtist(artist: String): String {
        if (artist.isBlank()) return ""
        return artist
            .split(Regex("[,&/]|feat\\.|ft\\.", RegexOption.IGNORE_CASE))[0]
            .replace(Regex("\\s*-\\s*topic$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSecs: Long? = null
    ): String? = withContext(Dispatchers.IO) {
        val cTitle = cleanTitle(title).ifBlank { title.trim() }
        val cArtist = cleanArtist(artist).ifBlank { artist.trim() }

        // 1. Try exact match via /api/get
        val exactMatch = fetchExactMatch(cTitle, cArtist, album, durationSecs)
        if (!exactMatch.isNullOrBlank()) return@withContext exactMatch

        // 2. Fallback: Search via /api/search?q=artist+title
        val searchMatch = fetchSearchFallback(cTitle, cArtist)
        if (!searchMatch.isNullOrBlank()) return@withContext searchMatch

        null
    }

    private fun fetchExactMatch(
        title: String,
        artist: String,
        album: String?,
        durationSecs: Long?
    ): String? {
        return try {
            val params = StringBuilder()
            params.append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            params.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            if (!album.isNullOrBlank() && album != "Unknown Album") {
                params.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"))
            }
            if (durationSecs != null && durationSecs > 0) {
                params.append("&duration=").append(durationSecs)
            }

            val url = URL("$GET_URL?$params")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Prism-Music-Player-Android/1.0.0 (https://github.com/prism-player)")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
                if (synced != null) return synced.trim()

                val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
                if (plain != null) return plain.trim()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchSearchFallback(title: String, artist: String): String? {
        return try {
            val query = "$artist $title".trim()
            val url = URL("$SEARCH_URL?q=${URLEncoder.encode(query, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Prism-Music-Player-Android/1.0.0 (https://github.com/prism-player)")
            conn.connectTimeout = 4500
            conn.readTimeout = 4500

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                if (array.length() == 0) return null

                var bestSynced: String? = null
                var bestPlain: String? = null

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }

                    if (bestSynced == null && synced != null) {
                        bestSynced = synced.trim()
                    }
                    if (bestPlain == null && plain != null) {
                        bestPlain = plain.trim()
                    }
                    if (bestSynced != null) break
                }

                return bestSynced ?: bestPlain
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
