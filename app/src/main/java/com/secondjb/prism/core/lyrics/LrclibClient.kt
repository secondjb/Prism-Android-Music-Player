package com.secondjb.prism.core.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for fetching synced or plain lyrics from the LRCLIB open-source API.
 */
object LrclibClient {

    private const val BASE_URL = "https://lrclib.net/api/get"

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSecs: Long? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val params = StringBuilder()
            params.append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            params.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            if (!album.isNullOrBlank() && album != "Unknown Album") {
                params.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"))
            }
            if (durationSecs != null && durationSecs > 0) {
                params.append("&duration=").append(durationSecs)
            }

            val url = URL("$BASE_URL?$params")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Prism-Music-Player-Android/1.0.0")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
                if (synced != null) return@withContext synced

                val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
                if (plain != null) return@withContext plain
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
