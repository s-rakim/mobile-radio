package com.srakim.spitify.data

import android.content.Context
import android.telephony.TelephonyManager
import com.srakim.spitify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Live internet radio from Radio Browser (radio-browser.info), a free, community-run directory
 * of about 50,000 stations. No key needed, and it doesn't go through the Spitify server, so
 * radio works even when the home server is off.
 *
 * Stations are handed around as [Track]s with no length, so liking, queueing and the recent
 * list all work for them like for songs.
 */
class RadioBrowserApi(private val context: Context, client: OkHttpClient) {

    private val http = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** The directory runs on a few mirrors; whichever answered last is tried first. */
    @Volatile
    private var hosts: List<String> = FALLBACK_HOSTS
    @Volatile
    private var discovered = false

    /** Stations whose name or genre tags match, most listened first. */
    suspend fun search(query: String, limit: Int = 20): List<Track> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val byName = async { runCatching { stations("search", mapOf("name" to q), limit) }.getOrDefault(emptyList()) }
        val byTag = async { runCatching { stations("search", mapOf("tag" to q.lowercase()), limit) }.getOrDefault(emptyList()) }
        (byName.await() + byTag.await())
            .distinctBy { it.id }
            .take(limit)
    }

    /** Popular stations in the listener's country, or worldwide if it can't be told. */
    suspend fun nearby(limit: Int = 24): List<Track> {
        val country = countryCode()
        if (country != null) {
            val local = runCatching { stations("search", mapOf("countrycode" to country), limit) }.getOrDefault(emptyList())
            if (local.isNotEmpty()) return local
        }
        return runCatching { stations("search", emptyMap(), limit) }.getOrDefault(emptyList())
    }

    /** The country name to put on the home screen ("Radio in Kenya"). */
    fun countryName(): String? = countryCode()?.let { Locale("", it).displayCountry.takeIf(String::isNotBlank) }

    /**
     * Radio Browser asks apps to report each play; that's how its "most listened" order works.
     * Fire and forget.
     */
    suspend fun reportPlay(track: Track) {
        val uuid = uuidOf(track.id) ?: return
        withContext(Dispatchers.IO) {
            runCatching { get("json/url/$uuid", emptyMap()) }
        }
    }

    private suspend fun stations(path: String, params: Map<String, String>, limit: Int): List<Track> {
        val body = get(
            "json/stations/$path",
            params + mapOf(
                "limit" to limit.toString(),
                "hidebroken" to "true",
                "order" to "clickcount",
                "reverse" to "true",
            ),
        )
        val list = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return list.mapNotNull { (it as? JsonObject)?.toStation() }
    }

    private fun JsonObject.toStation(): Track? {
        val uuid = text("stationuuid") ?: return null
        val stream = text("url_resolved") ?: text("url") ?: return null
        val name = text("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val tags = text("tags").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val place = listOfNotNull(text("state"), text("country")).distinct().joinToString(", ")
        val description = listOfNotNull(
            place.takeIf { it.isNotBlank() },
            tags.take(2).joinToString(", ") { tag -> tag.replaceFirstChar { c -> c.titlecase() } }.takeIf { it.isNotBlank() },
        ).joinToString(" • ").ifBlank { "Live radio" }
        val logo = text("favicon")?.takeIf { it.startsWith("http") }
        return Track(
            id = ID_PREFIX + uuid,
            title = name,
            artist = description,
            artwork = logo,
            artworkLarge = logo,
            durationSec = 0,
            genre = tags.firstOrNull(),
            link = text("homepage"),
            streamUrl = stream,
            origin = "Live radio",
            hls = text("hls") == "1",
        )
    }

    private suspend fun get(path: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        discover()
        var lastError: Exception? = null
        for (host in hosts) {
            try {
                val url: HttpUrl = "https://$host/".toHttpUrl().newBuilder().apply {
                    path.split('/').forEach { addPathSegment(it) }
                    params.forEach { (k, v) -> addQueryParameter(k, v) }
                }.build()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Spitify/${BuildConfig.VERSION_NAME}")
                    .build()
                val text = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Radio Browser answered with ${response.code}")
                    response.body?.string().orEmpty()
                }
                if (hosts.first() != host) hosts = listOf(host) + (hosts - host)
                return@withContext text
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Couldn't reach Radio Browser")
    }

    /** Asks the directory for its current mirrors once per app run; the built-in list is the fallback. */
    private fun discover() {
        if (discovered) return
        discovered = true
        runCatching {
            val request = Request.Builder()
                .url("https://all.api.radio-browser.info/json/servers")
                .header("User-Agent", "Spitify/${BuildConfig.VERSION_NAME}")
                .build()
            val body = http.newCall(request).execute().use { it.body?.string().orEmpty() }
            val names = (json.parseToJsonElement(body) as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.text("name") }
                ?.filter { it.endsWith(".radio-browser.info") }
                ?.distinct()
                ?.shuffled()
                .orEmpty()
            if (names.isNotEmpty()) hosts = names + (FALLBACK_HOSTS - names.toSet())
        }
    }

    private fun countryCode(): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val fromNetwork = runCatching { telephony?.networkCountryIso }.getOrNull()
        val fromSim = runCatching { telephony?.simCountryIso }.getOrNull()
        return listOf(fromNetwork, fromSim, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() && it.length == 2 }
            ?.uppercase(Locale.ROOT)
    }

    companion object {
        const val ID_PREFIX = "rb:"
        private val FALLBACK_HOSTS = listOf("de1.api.radio-browser.info", "fi1.api.radio-browser.info", "de2.api.radio-browser.info")

        fun isRadio(id: String) = id.startsWith(ID_PREFIX)
        private fun uuidOf(id: String) = id.takeIf(::isRadio)?.removePrefix(ID_PREFIX)
    }
}
