package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin client for the public Audius API. No key is needed, only an app name.
 * Responses are cached in memory for a few minutes so moving between screens is instant.
 */
class AudiusApi(private val client: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()

    suspend fun trendingTracks(genre: String? = null, time: String = "week", limit: Int = 40): List<Track> =
        fetch(
            "/v1/tracks/trending",
            buildMap {
                put("time", time)
                put("limit", limit.toString())
                if (genre != null) put("genre", genre)
            },
        ).decodeList<ApiTrack>().toTracks()

    suspend fun trendingPlaylists(limit: Int = 20): List<Playlist> =
        fetch("/v1/playlists/trending", mapOf("time" to "week", "limit" to limit.toString()))
            .decodeList<ApiPlaylist>().map { it.toPlaylist() }

    suspend fun searchTracks(query: String): List<Track> =
        fetch("/v1/tracks/search", mapOf("query" to query, "limit" to "30")).decodeList<ApiTrack>().toTracks()

    suspend fun searchPlaylists(query: String): List<Playlist> =
        fetch("/v1/playlists/search", mapOf("query" to query, "limit" to "20"))
            .decodeList<ApiPlaylist>().map { it.toPlaylist() }

    suspend fun searchArtists(query: String): List<Artist> =
        fetch("/v1/users/search", mapOf("query" to query, "limit" to "20"))
            .decodeList<ApiUser>().map { it.toArtist() }

    suspend fun playlist(id: String): Playlist? =
        fetch("/v1/playlists/$id").decodeList<ApiPlaylist>().firstOrNull()?.toPlaylist()

    suspend fun playlistTracks(id: String): List<Track> =
        fetch("/v1/playlists/$id/tracks").decodeList<ApiTrack>().toTracks()

    suspend fun artist(id: String): Artist? {
        val element = fetch("/v1/users/$id") ?: return null
        return runCatching { json.decodeFromJsonElement<ApiUser>(element) }.getOrNull()?.toArtist()
    }

    /** Every song the artist has uploaded (up to a generous cap), most played first. */
    suspend fun artistTracks(id: String): List<Track> {
        val all = mutableListOf<ApiTrack>()
        var offset = 0
        while (all.size < MAX_ARTIST_TRACKS) {
            val page = fetch(
                "/v1/users/$id/tracks",
                mapOf("limit" to ARTIST_PAGE.toString(), "offset" to offset.toString()),
            ).decodeList<ApiTrack>()
            all += page
            if (page.size < ARTIST_PAGE) break
            offset += ARTIST_PAGE
        }
        return all.distinctBy { it.id }.sortedByDescending { it.playCount }.toTracks()
    }

    private suspend fun fetch(path: String, params: Map<String, String> = emptyMap()): JsonElement? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL$path".toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
                addQueryParameter("app_name", APP_NAME)
            }.build()
            val key = url.toString()
            val now = System.currentTimeMillis()
            val cached = cache[key]
            val body = if (cached != null && now - cached.first < CACHE_TTL_MS) {
                cached.second
            } else {
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Request failed (${response.code})")
                    response.body?.string().orEmpty()
                }.also { cache[key] = now to it }
            }
            json.parseToJsonElement(body).jsonObject["data"]
        }

    // Decode item by item so one odd record doesn't take down a whole shelf.
    private inline fun <reified T> JsonElement?.decodeList(): List<T> =
        (this as? JsonArray)?.mapNotNull { item ->
            runCatching { json.decodeFromJsonElement<T>(item) }.getOrNull()
        } ?: emptyList()

    companion object {
        const val BASE_URL = "https://api.audius.co"
        const val APP_NAME = "spitify"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val ARTIST_PAGE = 50
        private const val MAX_ARTIST_TRACKS = 500

        fun streamUrl(trackId: String) = "$BASE_URL/v1/tracks/$trackId/stream?app_name=$APP_NAME"
    }
}

@Serializable
internal data class ApiArtwork(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null,
)

@Serializable
internal data class ApiCover(
    @SerialName("640x") val medium: String? = null,
    @SerialName("2000x") val large: String? = null,
)

@Serializable
internal data class ApiUser(
    val id: String,
    val name: String = "",
    val handle: String = "",
    val bio: String? = null,
    @SerialName("profile_picture") val profilePicture: ApiArtwork? = null,
    @SerialName("cover_photo") val coverPhoto: ApiCover? = null,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

@Serializable
internal data class ApiTrack(
    val id: String,
    val title: String = "",
    val duration: Int = 0,
    val genre: String? = null,
    val permalink: String? = null,
    val artwork: ApiArtwork? = null,
    val user: ApiUser? = null,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("is_streamable") val isStreamable: Boolean = true,
    val stream: ApiStream? = null,
    val access: ApiAccess? = null,
)

@Serializable
internal data class ApiStream(val url: String? = null)

@Serializable
internal data class ApiAccess(val stream: Boolean = true)

@Serializable
internal data class ApiPlaylist(
    val id: String,
    @SerialName("playlist_name") val name: String = "",
    val description: String? = null,
    val artwork: ApiArtwork? = null,
    val user: ApiUser? = null,
    @SerialName("track_count") val trackCount: Int = 0,
)

private fun List<ApiTrack>.toTracks(): List<Track> = filter { it.isStreamable && it.access?.stream != false }.map { it.toTrack() }

private fun ApiTrack.toTrack() = Track(
    id = id,
    title = title,
    artist = user?.name.orEmpty(),
    artistId = user?.id.orEmpty(),
    artistImage = user?.profilePicture?.medium ?: user?.profilePicture?.small,
    artwork = artwork?.medium ?: artwork?.small,
    artworkLarge = artwork?.large ?: artwork?.medium,
    durationSec = duration,
    genre = genre,
    link = permalink?.let { "https://audius.co$it" },
    directUrl = stream?.url?.takeIf { it.startsWith("https://") },
)

private fun ApiPlaylist.toPlaylist() = Playlist(
    id = id,
    name = name,
    owner = user?.name.orEmpty(),
    ownerImage = user?.profilePicture?.small,
    artwork = artwork?.large ?: artwork?.medium ?: artwork?.small,
    trackCount = trackCount,
    description = description?.takeIf { it.isNotBlank() },
)

private fun ApiUser.toArtist() = Artist(
    id = id,
    name = name,
    handle = handle,
    picture = profilePicture?.medium ?: profilePicture?.small,
    pictureLarge = profilePicture?.large ?: profilePicture?.medium,
    cover = coverPhoto?.large ?: coverPhoto?.medium,
    followers = followerCount,
    trackCount = trackCount,
    bio = bio?.takeIf { it.isNotBlank() },
    verified = isVerified,
)
