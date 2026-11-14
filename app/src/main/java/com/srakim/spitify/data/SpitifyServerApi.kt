package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The Spitify web server running at home (see /server in the repo). It adds YouTube, the
 * Internet Archive, ccMixter and Jamendo on top of what the app finds by itself.
 *
 * YouTube songs are played through the server, because YouTube's own links only work from the
 * machine that asked for them. Those tracks keep a server-relative address, so they still play
 * after the server's address changes in Settings.
 */
class SpitifyServerApi(client: OkHttpClient, private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val quick = client.newBuilder().readTimeout(8, TimeUnit.SECONDS).build()

    // A first YouTube search on a cold server can take a minute or more.
    private val patient = client.newBuilder()
        .readTimeout(100, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .build()

    init {
        store = settings
    }

    val enabled: Boolean
        get() = settings.current.let { it.webServerEnabled && base(it.webServerUrl) != null }

    suspend fun ping(): Result<Unit> = runCatching {
        val body = get(quick, "health", emptyMap())
        if (body.trim() != "ok") throw IOException("That doesn't look like a Spitify server")
    }

    suspend fun youtube(query: String): List<Track> = streamed("api/youtube", query, "YouTube")

    /** SoundCloud through the server's yt-dlp, played through the server like YouTube. */
    suspend fun soundcloud(query: String): List<Track> = streamed("api/soundcloud", query, "SoundCloud")

    private suspend fun streamed(route: String, query: String, label: String): List<Track> {
        val items = parseArray(get(patient, route, mapOf("q" to query)))
        return items.mapNotNull { o ->
            if (o.text("error") != null) return@mapNotNull null
            val id = o.text("id") ?: return@mapNotNull null
            Track(
                id = ID_PREFIX + id,
                title = o.text("title") ?: "Unknown song",
                artist = o.text("artist") ?: label,
                artwork = o.text("image"),
                artworkLarge = o.text("image"),
                durationSec = o.text("duration")?.toIntOrNull() ?: 0,
                // played through the server, whatever its address is at the time
                streamUrl = "$SCHEME/stream/$id",
                origin = label,
                hls = o.text("hls") == "true",
            )
        }
    }

    /** Archive, ccMixter and Jamendo. Each one fails quietly so the others still show up. */
    suspend fun openCatalogs(query: String): List<Track> = coroutineScope {
        val archive = async { runCatching { simple("archive", query, "Internet Archive") }.getOrDefault(emptyList()) }
        val ccmixter = async { runCatching { simple("ccmixter", query, "ccMixter") }.getOrDefault(emptyList()) }
        val jamendo = async { runCatching { jamendo(query) }.getOrDefault(emptyList()) }
        jamendo.await() + archive.await() + ccmixter.await()
    }

    private suspend fun simple(route: String, query: String, label: String): List<Track> =
        parseArray(get(quick, "api/$route", mapOf("q" to query))).mapNotNull { o ->
            val url = o.text("audioUrl") ?: return@mapNotNull null
            Track(
                id = "$ID_PREFIX$route:${o.text("id") ?: url.hashCode()}",
                title = o.text("title") ?: "Unknown song",
                artist = o.text("artist") ?: label,
                artwork = o.text("image"),
                artworkLarge = o.text("image"),
                streamUrl = url,
                origin = label,
            )
        }.take(5)

    private suspend fun jamendo(query: String): List<Track> {
        val root = json.parseToJsonElement(get(quick, "api/jamendo", mapOf("q" to query))) as? JsonObject
            ?: return emptyList()
        return root.array("results").mapNotNull { o ->
            val url = o.text("audio") ?: return@mapNotNull null
            Track(
                id = "${ID_PREFIX}jamendo:${o.text("id") ?: url.hashCode()}",
                title = o.text("name") ?: "Unknown song",
                artist = o.text("artist_name") ?: "Jamendo",
                artwork = o.text("album_image") ?: o.text("image"),
                artworkLarge = o.text("album_image") ?: o.text("image"),
                durationSec = o.text("duration")?.toIntOrNull() ?: 0,
                streamUrl = url,
                origin = "Jamendo",
            )
        }.take(5)
    }

    private fun parseArray(body: String): List<JsonObject> =
        (json.parseToJsonElement(body) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private suspend fun get(client: OkHttpClient, path: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val base = base(settings.current.webServerUrl) ?: throw IOException("Add your Spitify server in Settings")
            val url = base.newBuilder().apply {
                path.split("/").forEach { addPathSegment(it) }
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Spitify server answered with ${response.code}")
                response.body?.string().orEmpty()
            }
        }

    companion object {
        const val ID_PREFIX = "sp:"

        /** Stands in for the server's address inside saved stream links. */
        private const val SCHEME = "spitify-server:"

        @Volatile
        private var store: SettingsStore? = null

        fun isServer(id: String) = id.startsWith(ID_PREFIX)

        /** Accepts "100.101.102.103:3000" as well as a full URL, and drops a trailing slash. */
        fun base(raw: String): HttpUrl? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()
        }

        /** Turns a saved "spitify-server:/stream/..." link into a real one using today's address. */
        fun resolve(url: String?): String? {
            if (url == null || !url.startsWith(SCHEME)) return url
            val base = store?.current?.webServerUrl?.let(::base) ?: return url
            return base.toString().trimEnd('/') + url.removePrefix(SCHEME)
        }
    }
}
