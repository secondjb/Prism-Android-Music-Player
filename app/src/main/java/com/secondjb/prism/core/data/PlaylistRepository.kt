package com.secondjb.prism.core.data

import android.content.Context
import com.secondjb.prism.domain.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persisted user playlists and liked songs in local JSON storage.
 */
class PlaylistRepository(
    context: Context? = null,
    baseDir: File? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val mutex = Mutex()
    private val playlistFile = File(baseDir ?: context?.filesDir ?: File("."), "prism_playlists.json")

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    init {
        loadFromDiskSync()
    }

    private fun loadFromDiskSync() {
        if (!playlistFile.exists()) {
            val initial = listOf(
                Playlist(
                    id = FAVORITES_ID,
                    name = "Favorites",
                    description = "Your liked songs",
                    isPinned = true
                )
            )
            _playlists.value = initial
            persistToDisk(initial)
            return
        }

        try {
            val jsonStr = playlistFile.readText(Charsets.UTF_8)
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("playlists") ?: JSONArray()
            val list = mutableListOf<Playlist>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val desc = obj.optString("description").takeIf { it.isNotBlank() }
                val isPinned = obj.optBoolean("isPinned", false)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val customCoverUri = obj.optString("customCoverUri").takeIf { it.isNotBlank() }

                val trackIdsArray = obj.optJSONArray("trackIds") ?: JSONArray()
                val trackIds = mutableListOf<String>()
                for (j in 0 until trackIdsArray.length()) {
                    trackIds.add(trackIdsArray.getString(j))
                }

                list.add(
                    Playlist(
                        id = id,
                        name = name,
                        description = desc,
                        trackIds = trackIds,
                        isPinned = isPinned,
                        createdAt = createdAt,
                        customCoverUri = customCoverUri
                    )
                )
            }

            // Ensure Favorites playlist always exists
            val fav = list.find { it.id == FAVORITES_ID }
            if (fav == null) {
                list.add(0, Playlist(id = FAVORITES_ID, name = "Favorites", description = "Your liked songs", isPinned = true))
            }

            _playlists.value = list
            _likedTrackIds.value = list.find { it.id == FAVORITES_ID }?.trackIds?.toSet() ?: emptySet()
        } catch (_: Exception) {
            val fallback = listOf(Playlist(id = FAVORITES_ID, name = "Favorites", description = "Your liked songs", isPinned = true))
            _playlists.value = fallback
        }
    }

    private fun persistToDisk(list: List<Playlist>) {
        try {
            val root = JSONObject()
            val array = JSONArray()
            for (p in list) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("description", p.description ?: "")
                obj.put("isPinned", p.isPinned)
                obj.put("createdAt", p.createdAt)
                if (p.customCoverUri != null) obj.put("customCoverUri", p.customCoverUri)
                val trackIdsArr = JSONArray()
                p.trackIds.forEach { trackIdsArr.put(it) }
                obj.put("trackIds", trackIdsArr)
                array.put(obj)
            }
            root.put("playlists", array)
            playlistFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    suspend fun createPlaylist(name: String, description: String? = null): Playlist = mutex.withLock {
        val newPl = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name.trim(),
            description = description?.trim(),
            trackIds = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        val updated = _playlists.value + newPl
        _playlists.value = updated
        persistToDisk(updated)
        newPl
    }

    suspend fun deletePlaylist(playlistId: String) = mutex.withLock {
        if (playlistId == FAVORITES_ID) return@withLock
        val updated = _playlists.value.filter { it.id != playlistId }
        _playlists.value = updated
        persistToDisk(updated)
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = mutex.withLock {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.trackIds.contains(trackId)) pl
                else pl.copy(trackIds = pl.trackIds + trackId)
            } else pl
        }
        _playlists.value = updated
        if (playlistId == FAVORITES_ID) {
            _likedTrackIds.value = _likedTrackIds.value + trackId
        }
        persistToDisk(updated)
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = mutex.withLock {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackIds = pl.trackIds.filter { it != trackId })
            } else pl
        }
        _playlists.value = updated
        if (playlistId == FAVORITES_ID) {
            _likedTrackIds.value = _likedTrackIds.value - trackId
        }
        persistToDisk(updated)
    }

    suspend fun toggleLikeTrack(trackId: String): Boolean = mutex.withLock {
        val currentLiked = _likedTrackIds.value
        val isLiked = currentLiked.contains(trackId)
        val newSet = if (isLiked) currentLiked - trackId else currentLiked + trackId
        _likedTrackIds.value = newSet

        val updated = _playlists.value.map { pl ->
            if (pl.id == FAVORITES_ID) {
                pl.copy(trackIds = newSet.toList())
            } else pl
        }
        _playlists.value = updated
        persistToDisk(updated)
        !isLiked
    }

    suspend fun togglePinPlaylist(playlistId: String) = mutex.withLock {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(isPinned = !pl.isPinned) else pl
        }
        _playlists.value = updated
        persistToDisk(updated)
    }

    suspend fun updatePlaylistCover(playlistId: String, coverUri: String?) = mutex.withLock {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val updated = current[index].copy(customCoverUri = coverUri)
            current[index] = updated
            _playlists.value = current
            persistToDisk(current)
        }
    }

    suspend fun syncIncomingPlaylists(incomingPlaylists: List<Playlist>) = mutex.withLock {
        val current = _playlists.value.toMutableList()
        for (incoming in incomingPlaylists) {
            val existingIndex = current.indexOfFirst {
                it.id == incoming.id || it.name.equals(incoming.name, ignoreCase = true)
            }
            if (existingIndex >= 0) {
                val existing = current[existingIndex]
                val mergedTrackIds = if (incoming.trackIds.isNotEmpty()) {
                    (existing.trackIds + incoming.trackIds).distinct()
                } else {
                    existing.trackIds
                }
                val effectiveCover = incoming.customCoverUri?.takeIf { it.isNotBlank() } ?: existing.customCoverUri
                current[existingIndex] = existing.copy(
                    name = incoming.name.ifBlank { existing.name },
                    description = incoming.description?.ifBlank { existing.description } ?: existing.description,
                    trackIds = mergedTrackIds,
                    isPinned = incoming.isPinned,
                    customCoverUri = effectiveCover
                )
            } else {
                current.add(incoming)
            }
        }
        _playlists.value = current
        _likedTrackIds.value = current.find { it.id == FAVORITES_ID }?.trackIds?.toSet() ?: emptySet()
        persistToDisk(current)
    }

    companion object {
        const val FAVORITES_ID = "favorites"
    }
}
