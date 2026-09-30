package com.nendo.argosy.data.remote.romm

import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TRACK_PAGE_SIZE = 500
private const val MAX_QUEUE_TRACKS = 5_000

@Singleton
class RomMMusicLibraryService @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val apiClient: RomMApiClient
) {
    fun isConnected(): Boolean = connectionManager.isConnected()

    fun capabilities(): RomMCapabilities = connectionManager.getCapabilities()

    suspend fun getPlaylists(): RomMResult<List<RomMMusicPlaylist>> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return try {
            val response = api.getMusicPlaylists()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music playlists", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music playlists")
        }
    }

    suspend fun getGames(
        search: String?,
        minDurationSeconds: Double,
        limit: Int,
        offset: Int
    ): RomMResult<RomMMusicGamePage> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        val params = buildMap {
            search?.let { put("search", it) }
            put("min_duration", minDurationSeconds.toString())
            put("order_by", "value")
            put("order_dir", "asc")
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
        return try {
            val response = api.getMusicGames(params)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                RomMResult.Success(body)
            } else {
                RomMResult.Error("Failed to fetch music games", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music games")
        }
    }

    suspend fun getPlaylistTracks(playlistId: Long): RomMResult<List<RomMMusicTrack>> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return collectPages { limit, offset ->
            api.getMusicPlaylistTracks(
                playlistId,
                mapOf(
                    "order_by" to "position",
                    "order_dir" to "asc",
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
        }
    }

    suspend fun getGameTracks(romId: Long, minDurationSeconds: Double): RomMResult<List<RomMMusicTrack>> {
        val api = connectionManager.getApi() ?: return RomMResult.Error("Not connected")
        return collectPages { limit, offset ->
            api.getMusicTracks(
                mapOf(
                    "rom_id" to romId.toString(),
                    "min_duration" to minDurationSeconds.toString(),
                    "order_by" to "title",
                    "order_dir" to "asc",
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
        }
    }

    private suspend fun collectPages(
        fetch: suspend (limit: Int, offset: Int) -> retrofit2.Response<RomMMusicTrackPage>
    ): RomMResult<List<RomMMusicTrack>> {
        val collected = mutableListOf<RomMMusicTrack>()
        return try {
            while (collected.size < MAX_QUEUE_TRACKS) {
                val response = fetch(TRACK_PAGE_SIZE, collected.size)
                val page = response.body()
                if (!response.isSuccessful || page == null) {
                    return RomMResult.Error("Failed to fetch music tracks", response.code())
                }
                collected += page.items
                if (page.items.size < TRACK_PAGE_SIZE || collected.size >= page.total) break
            }
            RomMResult.Success(collected.toList())
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to fetch music tracks")
        }
    }

    fun streamUrl(encodedPath: String?): String? =
        if (connectionManager.getBaseUrl().isBlank()) null else apiClient.buildMediaUrl(encodedPath)

    /**
     * Absolute URL for a raw resource path RomM reports unencoded, such as a music `cover_url`.
     * Each path segment is percent-encoded; a query string is kept as sent.
     */
    fun resourceUrl(rawPath: String?): String? {
        if (connectionManager.getBaseUrl().isBlank()) return null
        val trimmed = rawPath?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.startsWith("http")) return apiClient.buildMediaUrl(trimmed)
        return apiClient.buildMediaUrl(encodeResourcePath(trimmed))
    }

    fun isServerOrigin(url: String): Boolean = sameOrigin(url, connectionManager.getBaseUrl())

    companion object {
        fun encodeResourcePath(rawPath: String): String {
            val pathPart = rawPath.substringBefore('?')
            val query = rawPath.substringAfter('?', "")
            val encoded = pathPart.trimStart('/').split('/').joinToString("/") { encodeSegment(it) }
            return "/" + encoded + if (query.isNotEmpty()) "?$query" else ""
        }

        fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

        fun sameOrigin(url: String, baseUrl: String): Boolean {
            if (baseUrl.isBlank()) return false
            val target = runCatching { URI(url) }.getOrNull() ?: return false
            val base = runCatching { URI(baseUrl) }.getOrNull() ?: return false
            return target.scheme.equals(base.scheme, ignoreCase = true) &&
                target.host.equals(base.host, ignoreCase = true) &&
                effectivePort(target) == effectivePort(base)
        }

        private fun effectivePort(uri: URI): Int = when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            uri.scheme.equals("http", ignoreCase = true) -> 80
            else -> -1
        }
    }
}
