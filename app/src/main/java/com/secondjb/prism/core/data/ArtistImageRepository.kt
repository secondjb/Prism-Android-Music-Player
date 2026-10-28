package com.secondjb.prism.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * High-performance, offline-first Artist Image lookup service powered by the Deezer API,
 * matching the architecture of PixelPlayer.
 *
 * 1. Checks memory LRU cache.
 * 2. Checks local disk cache (filesDir/artist_images/).
 * 3. If connected to Wi-Fi (or mobile data if Wi-Fi only restriction is disabled) and enabled in settings,
 *    queries Deezer API for 250x250 artist artwork and caches it locally.
 */
class ArtistImageRepository(
    private val context: Context,
    private val preferencesRepository: PrismPreferencesRepository? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val memCache = LruCache<String, String>(128)
    private val cacheDir = File(context.cacheDir, "artist_images").apply {
        if (!exists()) mkdirs()
    }

    suspend fun getArtistArtworkUri(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank() || cleanName.equals("<unknown>", ignoreCase = true) || cleanName.equals("Unknown Artist", ignoreCase = true)) {
            return@withContext null
        }

        // 1. Check in-memory cache
        memCache.get(cleanName)?.let { return@withContext it }

        // 2. Check local disk cache
        val fileHash = md5(cleanName.lowercase())
        val localFile = File(cacheDir, "$fileHash.jpg")
        if (localFile.exists() && localFile.length() > 0L) {
            val fileUri = "file://${localFile.absolutePath}"
            memCache.put(cleanName, fileUri)
            return@withContext fileUri
        }

        // 3. Check preferences
        val prefs = preferencesRepository?.preferences?.value
        val isFetchEnabled = prefs?.downloadArtistImages ?: true
        if (!isFetchEnabled) return@withContext null

        val isWifiOnly = prefs?.artistImagesWifiOnly ?: true
        if (isWifiOnly && !isWifiConnected()) {
            return@withContext null
        }

        // 4. Query Deezer API (PixelPlayer method)
        try {
            val encoded = URLEncoder.encode(cleanName, "UTF-8")
            val url = "https://api.deezer.com/search/artist?q=$encoded&limit=1"
            val req = Request.Builder().url(url).build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext null
                if (data.length() == 0) return@withContext null

                val first = data.getJSONObject(0)
                val pictureUrl = first.optString("picture_medium").takeIf { it.isNotBlank() }
                    ?: first.optString("picture_big").takeIf { it.isNotBlank() }
                    ?: return@withContext null

                // Download image and save to disk
                val imgReq = Request.Builder().url(pictureUrl).build()
                client.newCall(imgReq).execute().use { imgResp ->
                    if (imgResp.isSuccessful) {
                        imgResp.body?.byteStream()?.use { input ->
                            FileOutputStream(localFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (localFile.exists() && localFile.length() > 0L) {
                            val resultUri = "file://${localFile.absolutePath}"
                            memCache.put(cleanName, resultUri)
                            Log.d(TAG, "Cached artist image for '$cleanName' from Deezer API")
                            return@withContext resultUri
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch artist image for '$cleanName': ${e.message}")
        }

        null
    }

    fun clearCache() {
        memCache.evictAll()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear artist image cache: ${e.message}")
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ArtistImageRepo"
    }
}
