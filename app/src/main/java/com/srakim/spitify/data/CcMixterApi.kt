package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** ccMixter: Creative Commons remixes and originals. Open query API, no key. */
class CcMixterApi(private val client: OkHttpClient, private val settings: SettingsStore) {

    val enabled: Boolean get() = settings.current.ccMixterEnabled

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()

    suspend fun searchTracks(query: String): List<Track> =
        query(mapOf("search" to query, "search_type" to "all", "sort" to "rank", "limit" to "20"))

    suspend fun popularTracks(): List<Track> = query(mapOf("sort" to "rank", "limit" to "30"))

    private suspend fun query(params: Map<String, String>): List<Track> {
        if (!enabled) return emptyList()
        val url = "https://ccmixter.org/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("f", "json")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val body = withContext(Dispatchers.IO) {
            val key = url.toString()
            val now = System.currentTimeMillis()
            cache[key]?.takeIf { now - it.first < CACHE_TTL_MS }?.second
                ?: client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("ccMixter request failed (${response.code})")
                    response.body?.string().orEmpty()
                }.also { cache[key] = now to it }
        }
        val uploads = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return uploads.mapNotNull { (it as? JsonObject)?.toTrack() }
    }

    private fun JsonObject.toTrack(): Track? {
        val id = text("upload_id") ?: return null
        val audio = array("files").filter { it.obj("file_format_info")?.text("mime_type") == "audio/mpeg" }
        // Stems and a cappellas ride along with the main mix; the mix comes first and has no part type.
        val main = audio.firstOrNull { it.obj("file_extra")?.text("type") !in STEM_TYPES } ?: audio.firstOrNull() ?: return null
        val url = main.text("download_url") ?: return null
        return Track(
            id = ID_PREFIX + id,
            title = text("upload_name") ?: "Untitled",
            artist = text("user_real_name") ?: text("user_name") ?: "ccMixter",
            durationSec = parseDuration(main.obj("file_format_info")?.text("ps")),
            genre = genreFromTags(text("upload_tags").orEmpty().split(',')),
            link = text("file_page_url"),
            streamUrl = url,
        )
    }

    companion object {
        const val ID_PREFIX = "cc:"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private val STEM_TYPES = setOf("pella", "samples", "sample", "acappella")
    }
}
