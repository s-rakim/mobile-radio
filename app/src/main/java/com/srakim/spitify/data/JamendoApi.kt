package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Second catalogue: Jamendo's Creative Commons library. Needs a client id from
 * devportal.jamendo.com, which can be set in Settings. Ids are prefixed with "jm:" so they
 * never collide with Audius ids.
 */
class JamendoApi(private val client: OkHttpClient, private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()

    val enabled: Boolean get() = settings.current.jamendoEnabled

    private val clientId: String get() = settings.current.jamendoClientId.trim().ifBlank { DEFAULT_CLIENT_ID }

    suspend fun searchTracks(query: String): List<Track> =
        tracks(mapOf("search" to query, "limit" to "20", "order" to "relevance"))

    suspend fun popularTracks(): List<Track> =
        tracks(mapOf("order" to "popularity_week", "limit" to "30"))

    suspend fun searchArtists(query: String): List<Artist> =
        get("/artists/", mapOf("namesearch" to query, "limit" to "10")).decodeAll<JmArtist>().map { it.toArtist() }

    suspend fun artist(id: String): Artist? =
        get("/artists/", mapOf("id" to id.removePrefix(ID_PREFIX))).decodeAll<JmArtist>().firstOrNull()?.toArtist()

    suspend fun artistTracks(id: String): List<Track> =
        tracks(mapOf("artist_id" to id.removePrefix(ID_PREFIX), "order" to "popularity_total", "limit" to "50"))

    /** Quick check used by Settings to tell the listener whether their client id works. */
    suspend fun verify(): Result<Unit> = runCatching {
        get("/tracks/", mapOf("limit" to "1"), useCache = false)
        Unit
    }

    private suspend fun tracks(params: Map<String, String>): List<Track> {
        if (!enabled) return emptyList()
        val base = mapOf("audioformat" to "mp32", "imagesize" to "300", "include" to "musicinfo")
        return get("/tracks/", base + params).decodeAll<JmTrack>()
            .filter { !it.audio.isNullOrBlank() }
            .map { it.toTrack() }
    }

    private suspend fun get(path: String, params: Map<String, String>, useCache: Boolean = true): JsonArray? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL$path".toHttpUrl().newBuilder().apply {
                addQueryParameter("client_id", clientId)
                addQueryParameter("format", "json")
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val key = url.toString()
            val now = System.currentTimeMillis()
            val cached = cache[key]?.takeIf { useCache && now - it.first < CACHE_TTL_MS }?.second
            val body = cached ?: client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Jamendo request failed (${response.code})")
                response.body?.string().orEmpty()
            }
            val root = json.parseToJsonElement(body).jsonObject
            val headers = root["headers"]?.jsonObject
            if (headers?.get("status")?.jsonPrimitive?.content == "failed") {
                throw IOException(headers["error_message"]?.jsonPrimitive?.content ?: "Jamendo request failed")
            }
            if (cached == null) cache[key] = now to body
            root["results"] as? JsonArray
        }

    private inline fun <reified T> JsonArray?.decodeAll(): List<T> =
        this?.mapNotNull { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() } ?: emptyList()

    companion object {
        const val ID_PREFIX = "jm:"
        const val DEFAULT_CLIENT_ID = "56d30c55"
        private const val BASE_URL = "https://api.jamendo.com/v3.0"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        fun isJamendo(id: String) = id.startsWith(ID_PREFIX)
    }
}

@Serializable
internal data class JmTags(val genres: List<String> = emptyList())

@Serializable
internal data class JmMusicInfo(val tags: JmTags? = null)

@Serializable
internal data class JmTrack(
    val id: String,
    val name: String = "",
    val duration: Int = 0,
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("album_image") val albumImage: String? = null,
    val image: String? = null,
    val audio: String? = null,
    val shareurl: String? = null,
    val musicinfo: JmMusicInfo? = null,
)

@Serializable
internal data class JmArtist(
    val id: String,
    val name: String = "",
    val image: String? = null,
    val shareurl: String? = null,
)

private fun JmTrack.toTrack(): Track {
    val art = (image ?: albumImage)?.takeIf { it.isNotBlank() }
    return Track(
        id = JamendoApi.ID_PREFIX + id,
        title = name,
        artist = artistName,
        artistId = if (artistId.isBlank()) "" else JamendoApi.ID_PREFIX + artistId,
        artwork = art,
        artworkLarge = art?.replace("width=300", "width=600"),
        durationSec = duration,
        genre = musicinfo?.tags?.genres?.firstOrNull()?.replaceFirstChar { it.uppercase() },
        link = shareurl,
        streamUrl = audio,
    )
}

private fun JmArtist.toArtist() = Artist(
    id = JamendoApi.ID_PREFIX + id,
    name = name,
    picture = image?.takeIf { it.isNotBlank() },
    pictureLarge = image?.takeIf { it.isNotBlank() },
    bio = "Creative Commons music on Jamendo.",
)
