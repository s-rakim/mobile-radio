package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Internet Archive audio. Only two collections are searched: netlabels (labels releasing under
 * Creative Commons) and etree (live shows from bands that allow taping and sharing), so
 * everything here is fine to stream. No key needed. An archive item is treated as an album and
 * its MP3 files as the tracks.
 */
class ArchiveApi(private val client: OkHttpClient, private val settings: SettingsStore) {

    val enabled: Boolean get() = settings.current.archiveEnabled

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()

    suspend fun searchAlbums(query: String, limit: Int = 12): List<Playlist> {
        val terms = query.toSearchTerms() ?: return emptyList()
        return albums("($terms) AND $SCOPE", limit, sortByDownloads = false)
    }

    /** Songs from the closest matching albums, a few from each so one album doesn't fill the list. */
    suspend fun searchTracks(query: String): List<Track> = coroutineScope {
        searchAlbums(query, limit = 3)
            .map { album -> async { runCatching { album(album.id).second }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatMap { tracks ->
                tracks.sortedByDescending { it.title.contains(query, ignoreCase = true) }.take(4)
            }
    }

    suspend fun popularNetlabelAlbums(): List<Playlist> = albums("$AUDIO AND collection:netlabels", 20, sortByDownloads = true)

    suspend fun popularLiveAlbums(): List<Playlist> = albums("$AUDIO AND collection:etree", 20, sortByDownloads = true)

    /** One album with its tracks, in disc order. */
    suspend fun album(id: String): Pair<Playlist?, List<Track>> {
        val identifier = id.removePrefix(ID_PREFIX)
        val root = getJson("https://archive.org/metadata/$identifier".toHttpUrl()) as? JsonObject ?: return null to emptyList()
        val meta = root.obj("metadata") ?: return null to emptyList()
        val creator = meta.text("creator").orEmpty().ifBlank { "Internet Archive" }
        val art = artwork(identifier)
        val page = "https://archive.org/details/$identifier"
        val files = root.array("files")
        val format = PREFERRED_FORMATS.firstOrNull { f -> files.any { it.text("format") == f } }
        val tracks = files
            .filter { format != null && it.text("format") == format }
            .sortedWith(
                compareBy<JsonObject>(
                    { it.text("track")?.substringBefore('/')?.trim()?.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.text("name").orEmpty() },
                ),
            )
            .mapNotNull { file ->
                val name = file.text("name") ?: return@mapNotNull null
                Track(
                    id = "$ID_PREFIX$identifier/$name",
                    title = file.text("title") ?: name.substringAfterLast('/').substringBeforeLast('.'),
                    artist = file.text("artist") ?: file.text("creator") ?: creator,
                    artwork = art,
                    artworkLarge = art,
                    durationSec = parseDuration(file.text("length")),
                    genre = genreFromTags(meta.texts("subject").flatMap { it.split(';', ',') }),
                    link = page,
                    streamUrl = downloadUrl(identifier, name),
                )
            }
        val playlist = Playlist(
            id = ID_PREFIX + identifier,
            name = meta.text("title") ?: identifier,
            owner = creator,
            artwork = art,
            trackCount = tracks.size,
            description = meta.text("description")?.stripHtml()?.take(280)?.takeIf { it.isNotBlank() },
            label = if ("etree" in meta.texts("collection")) "Live album" else "Album",
            link = page,
        )
        return playlist to tracks
    }

    private suspend fun albums(q: String, rows: Int, sortByDownloads: Boolean): List<Playlist> {
        if (!enabled) return emptyList()
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", q)
            listOf("identifier", "title", "creator", "collection").forEach { addQueryParameter("fl[]", it) }
            if (sortByDownloads) addQueryParameter("sort[]", "downloads desc")
            addQueryParameter("rows", rows.toString())
            addQueryParameter("output", "json")
        }.build()
        val docs = (getJson(url) as? JsonObject)?.obj("response")?.array("docs").orEmpty()
        return docs.mapNotNull { doc ->
            val identifier = doc.text("identifier") ?: return@mapNotNull null
            Playlist(
                id = ID_PREFIX + identifier,
                name = doc.text("title") ?: identifier,
                owner = doc.text("creator") ?: "Internet Archive",
                artwork = artwork(identifier),
                label = if ("etree" in doc.texts("collection")) "Live album" else "Album",
                link = "https://archive.org/details/$identifier",
            )
        }
    }

    private suspend fun getJson(url: HttpUrl): JsonElement? = withContext(Dispatchers.IO) {
        val key = url.toString()
        val now = System.currentTimeMillis()
        val body = cache[key]?.takeIf { now - it.first < CACHE_TTL_MS }?.second
            ?: client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Internet Archive request failed (${response.code})")
                response.body?.string().orEmpty()
            }.also { cache[key] = now to it }
        json.parseToJsonElement(body)
    }

    companion object {
        const val ID_PREFIX = "ia:"
        private const val AUDIO = "mediatype:audio"
        private const val SCOPE = "$AUDIO AND collection:(netlabels OR etree)"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private val PREFERRED_FORMATS = listOf("VBR MP3", "MP3", "128Kbps MP3", "64Kbps MP3")

        fun isArchive(id: String) = id.startsWith(ID_PREFIX)

        fun artwork(identifier: String) = "https://archive.org/services/img/$identifier"

        fun downloadUrl(identifier: String, fileName: String): String =
            "https://archive.org".toHttpUrl().newBuilder()
                .addPathSegment("download")
                .addPathSegment(identifier)
                .addPathSegments(fileName)
                .build()
                .toString()

        /** Keeps letters, digits and spaces so a query can't break the search syntax. */
        private fun String.toSearchTerms(): String? =
            replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() && it.uppercase() !in setOf("AND", "OR", "NOT", "TO") }
                .joinToString(" AND ")
                .ifBlank { null }
    }
}
