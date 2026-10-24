package com.secondjb.prism.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

sealed interface UpdateResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val releaseUrl: String,
        val releaseNotes: String?
    ) : UpdateResult

    data class UpToDate(
        val currentVersion: String,
        val latestRemoteVersion: String? = null
    ) : UpdateResult

    data class Error(
        val message: String
    ) : UpdateResult
}

object UpdateChecker {
    const val CURRENT_VERSION = "0.1.0"
    const val GITHUB_REPO = "secondjb/Prism-Android-Music-Player"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO/releases"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Splits version strings into integer component lists (e.g. "v0.2.0" -> [0, 2, 0]).
     * Strips leading 'v'/'V' and any pre-release suffixes like "-beta", "-rc1", "+build".
     */
    fun parseVersionParts(version: String): List<Int> {
        val clean = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
        if (clean.isBlank()) return emptyList()
        return clean.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Returns true if remoteVersion is strictly greater than currentVersion using semantic version comparison.
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String = CURRENT_VERSION): Boolean {
        val remote = parseVersionParts(remoteVersion)
        val current = parseVersionParts(currentVersion)
        if (remote.isEmpty() || current.isEmpty()) return false

        val maxLen = maxOf(remote.size, current.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Queries the latest published GitHub release and checks if a newer version exists.
     */
    suspend fun checkForUpdates(currentVersion: String = CURRENT_VERSION): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(LATEST_RELEASE_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Prism-Android-Music-Player/$currentVersion")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val release = json.decodeFromString<GitHubRelease>(responseBody)
                    val remoteTag = release.tagName.ifBlank { release.name ?: "" }

                    if (isNewerVersion(remoteTag, currentVersion)) {
                        UpdateResult.UpdateAvailable(
                            latestVersion = remoteTag,
                            releaseName = release.name ?: remoteTag,
                            releaseUrl = release.htmlUrl.ifBlank { RELEASES_PAGE_URL },
                            releaseNotes = release.body
                        )
                    } else {
                        UpdateResult.UpToDate(
                            currentVersion = currentVersion,
                            latestRemoteVersion = remoteTag
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    // No releases published yet on the repository
                    UpdateResult.UpToDate(
                        currentVersion = currentVersion,
                        latestRemoteVersion = null
                    )
                }
                else -> {
                    UpdateResult.Error("HTTP $code")
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to connect to GitHub")
        }
    }
}
