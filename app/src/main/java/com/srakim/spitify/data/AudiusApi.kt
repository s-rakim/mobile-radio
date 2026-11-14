package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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

    /** Full record for one song, used by the credits sheet and Go to album. */
    suspend fun trackDetails(id: String): TrackDetails? {
        val element = fetch("/v1/tracks/$id") ?: return null
        val obj = (element as? JsonArray)?.firstOrNull() ?: element
        val raw = runCatching { json.decodeFromJsonElement<ApiTrackDetails>(obj) }.getOrNull() ?: return null
        val albumId = raw.albumBacklink?.let { back ->
            val given = (back.playlistId as? JsonPrimitive)?.takeIf { it.isString }?.content
            given?.takeUnless { it.all(Char::isDigit) }
                ?: back.permalink?.let { resolvePlaylistId(it) }
        }
        return raw.toDetails(albumId)
    }

    /** Several songs by id in one request, kept in the order asked for. */
    suspend fun tracksByIds(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val found = ids.chunked(50).flatMap { chunk ->
            fetch("/v1/tracks", repeated = chunk.map { "id" to it }).decodeList<ApiTrack>().toTracks()
        }.associateBy { it.id }
        return ids.mapNotNull { found[it] }
    }

    /** Turns an audius.co path like /artist/album/name into the playlist id the API uses. */
    private suspend fun resolvePlaylistId(permalink: String): String? = runCatching {
        val element = fetch("/v1/resolve", mapOf("url" to "https://audius.co$permalink"))
        val obj = (element as? JsonArray)?.firstOrNull() ?: element
        (obj as? JsonObject)?.get("id")?.jsonPrimitive?.content
    }.getOrNull()

    private suspend fun fetch(
        path: String,
        params: Map<String, String> = emptyMap(),
        repeated: List<Pair<String, String>> = emptyList(),
    ): JsonElement? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL$path".toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
                repeated.forEach { (key, value) -> addQueryParameter(key, value) }
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

@Serializable
internal data class ApiAlbumBacklink(
    // Older nodes send the raw number, newer ones the short id. Kept loose so both decode.
    @SerialName("playlist_id") val playlistId: JsonElement? = null,
    @SerialName("playlist_name") val name: String? = null,
    val permalink: String? = null,
)

@Serializable
internal data class ApiContributor(val name: String = "", val roles: List<String> = emptyList())

@Serializable
internal data class ApiCopyright(val year: Int? = null, val text: String? = null)

@Serializable
internal data class ApiTrackDetails(
    val id: String,
    val title: String = "",
    val genre: String? = null,
    val mood: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val license: String? = null,
    val isrc: String? = null,
    val bpm: Double? = null,
    @SerialName("musical_key") val musicalKey: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("repost_count") val repostCount: Int = 0,
    val user: ApiUser? = null,
    @SerialName("album_backlink") val albumBacklink: ApiAlbumBacklink? = null,
    @SerialName("resource_contributors") val contributors: List<ApiContributor>? = null,
    @SerialName("indirect_resource_contributors") val indirectContributors: List<ApiContributor>? = null,
    @SerialName("copyright_line") val copyright: ApiCopyright? = null,
    @SerialName("producer_copyright_line") val producerCopyright: ApiCopyright? = null,
)

/** What the credits sheet shows. Empty fields are simply left off. */
data class TrackDetails(
    val id: String,
    val artistName: String,
    val artistHandle: String?,
    val credits: List<Pair<String, List<String>>>,
    val album: String?,
    val albumId: String?,
    val genre: String?,
    val mood: String?,
    val tags: List<String>,
    val description: String?,
    val license: String?,
    val isrc: String?,
    val bpm: Int?,
    val key: String?,
    val released: String?,
    val plays: Int,
    val likes: Int,
    val reposts: Int,
    val copyright: String?,
    val phonographic: String?,
)

private fun ApiTrackDetails.toDetails(albumId: String?): TrackDetails {
    // DDEX roles come as words like "Composer" or "Producer". Group the people under each role.
    val byRole = linkedMapOf<String, MutableList<String>>()
    (contributors.orEmpty() + indirectContributors.orEmpty()).forEach { c ->
        if (c.name.isBlank()) return@forEach
        c.roles.ifEmpty { listOf("Contributor") }.forEach { role ->
            val label = role.replace(Regex("([a-z])([A-Z])"), "$1 $2").trim()
            val people = byRole.getOrPut(label) { mutableListOf() }
            if (c.name !in people) people += c.name
        }
    }
    return TrackDetails(
        id = id,
        artistName = user?.name.orEmpty(),
        artistHandle = user?.handle?.takeIf { it.isNotBlank() },
        credits = byRole.map { it.key to it.value.toList() },
        album = albumBacklink?.name?.takeIf { it.isNotBlank() },
        albumId = albumId,
        genre = genre?.takeIf { it.isNotBlank() },
        mood = mood?.takeIf { it.isNotBlank() },
        tags = tags.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(8),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        license = license?.takeIf { it.isNotBlank() },
        isrc = isrc?.takeIf { it.isNotBlank() },
        bpm = bpm?.takeIf { it > 0 }?.let { kotlin.math.round(it).toInt() },
        key = musicalKey?.takeIf { it.isNotBlank() },
        released = releaseDate?.take(10)?.takeIf { it.length == 10 },
        plays = playCount,
        likes = favoriteCount,
        reposts = repostCount,
        copyright = copyright?.text?.takeIf { it.isNotBlank() }?.let { t -> copyright?.year?.let { "© $it $t" } ?: "© $t" },
        phonographic = producerCopyright?.text?.takeIf { it.isNotBlank() }?.let { t -> producerCopyright?.year?.let { "℗ $it $t" } ?: "℗ $t" },
    )
}
