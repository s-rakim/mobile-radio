package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/**
 * Lyrics come from LRCLIB, a free community database. Audius doesn't carry lyrics, so many
 * independent releases won't have any and we simply hide the card.
 */
class LyricsApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val found = ConcurrentHashMap<String, Lyrics>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    @Serializable
    private data class Entry(
        val trackName: String? = null,
        val artistName: String? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    suspend fun find(track: Track): Lyrics? {
        found[track.id]?.let { return it }
        if (track.id in missing) return null
        val result = withContext(Dispatchers.IO) {
            runCatching { exact(track) ?: search(track) }.getOrNull()
        }
        if (result != null) {
            found[track.id] = result
            onFound?.invoke(track, result)
        } else {
            missing += track.id
        }
        return result
    }

    /** Told about every song whose lyrics were found, so they can go into the lyric index. */
    @Volatile
    var onFound: ((Track, Lyrics) -> Unit)? = null

    /** Whether a lookup already came back empty, so the indexer doesn't ask again this session. */
    fun knownMissing(track: Track) = track.id in missing

    // ---------- Finding a song by its words ----------

    /**
     * Songs whose lyrics contain [snippet], from the internet. Two places are asked at once:
     *  - Genius' public song search, which matches lyrics as well as titles;
     *  - LRCLIB, whose search matches titles, artists and albums but returns the lyrics too,
     *    so a chorus that's also the title is caught and the line can be checked.
     * Then the top few get their lyrics fetched so the matching line can be shown.
     */
    suspend fun searchText(snippet: String): List<LyricMatch> = coroutineScope {
        val q = snippet.trim()
        if (LyricText.words(q).size < 2) return@coroutineScope emptyList()
        val genius = async(Dispatchers.IO) { withTimeoutOrNull(ONLINE_TIMEOUT_MS) { runCatching { genius(q) }.getOrNull() }.orEmpty() }
        val lrclib = async(Dispatchers.IO) { withTimeoutOrNull(ONLINE_TIMEOUT_MS) { runCatching { lrclibText(q) }.getOrNull() }.orEmpty() }
        val inLyrics = lrclib.await().filter { it.line != null }
        val seen = HashSet<String>()
        val qn = LyricText.norm(q)
        val merged = (genius.await() + inLyrics + lrclib.await().filter { it.line == null })
            .filter { seen.add(LyricText.norm(it.title) + "|" + LyricText.norm(it.artist)) }
            // Originals before remixes, sped-up edits and uploaders named after the song's words.
            .sortedBy { m -> if (VERSION.containsMatchIn(m.title) || qn.contains(LyricText.norm(m.artist))) 1 else 0 }
            .take(MAX_ONLINE)
        // Fill in the matching line for the first few that don't have one yet.
        merged.mapIndexed { i, m ->
            async(Dispatchers.IO) {
                if (m.line != null || i >= LINE_LOOKUPS) return@async m
                val lyrics = withTimeoutOrNull(LINE_TIMEOUT_MS) {
                    runCatching { exact(Track(id = "lyric:" + m.title + "|" + m.artist, title = m.title, artist = m.artist)) }.getOrNull()
                }
                m.copy(line = lyrics?.let { LyricText.bestLine(it.lines.map { l -> l.text }, q)?.first })
            }
        }.awaitAll()
    }

    private fun genius(q: String): List<LyricMatch> {
        val url = "https://genius.com/api/search/song".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("per_page", "10")
            .build()
        val body = get(url.toString(), browser = true) ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val sections = (root["response"] as? JsonObject)?.get("sections") as? JsonArray ?: return emptyList()
        return sections.flatMap { sec ->
            ((sec as? JsonObject)?.get("hits") as? JsonArray).orEmpty().mapNotNull { hit ->
                val h = hit as? JsonObject ?: return@mapNotNull null
                val r = h["result"] as? JsonObject ?: return@mapNotNull null
                val title = r.s("title") ?: return@mapNotNull null
                val artist = (r["primary_artist"] as? JsonObject)?.s("name") ?: r.s("artist_names") ?: return@mapNotNull null
                // Genius marks which part of the lyrics matched.
                val line = (h["highlights"] as? JsonArray)?.firstNotNullOfOrNull { hl ->
                    (hl as? JsonObject)?.takeIf { it.s("property") == "lyrics" }?.s("value")
                }?.lines()?.firstOrNull { LyricText.norm(it).isNotBlank() }
                LyricMatch(title = title, artist = artist, artwork = r.s("song_art_image_thumbnail_url"), line = line, source = "Genius")
            }
        }
    }

    private fun lrclibText(q: String): List<LyricMatch> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build()
        val body = get(url.toString()) ?: return emptyList()
        return json.decodeFromString<List<Entry>>(body)
            .filter { !it.trackName.isNullOrBlank() && !it.artistName.isNullOrBlank() }
            .filterNot { KARAOKE.containsMatchIn(it.trackName.orEmpty()) }
            .mapNotNull { e ->
                val lines = (e.plainLyrics ?: e.syncedLyrics?.let(::parseLrc)?.joinToString("\n") { it.text }).orEmpty().lines()
                val hit = LyricText.bestLine(lines, q)
                // Title-only matches are kept only when the words are the title itself.
                if (hit == null && !LyricText.norm(e.trackName!!).contains(LyricText.norm(q))) return@mapNotNull null
                LyricMatch(title = e.trackName!!, artist = e.artistName!!, line = hit?.takeIf { it.second >= 0.8 }?.first, source = "LRCLIB")
            }
    }

    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun exact(track: Track): Lyrics? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .apply { if (track.durationSec > 0) addQueryParameter("duration", track.durationSec.toString()) }
            .build()
        val body = get(url.toString()) ?: return null
        return json.decodeFromString<Entry>(body).toLyrics()
    }

    private fun search(track: Track): Lyrics? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .build()
        val body = get(url.toString()) ?: return null
        return json.decodeFromString<List<Entry>>(body)
            .firstOrNull { it.artistName.equals(track.artist, ignoreCase = true) }
            ?.toLyrics()
    }

    private fun get(url: String, browser: Boolean = false): String? {
        val agent = if (browser) BROWSER_AGENT else "Spitify (https://github.com/s-rakim/mobile-radio)"
        val request = Request.Builder().url(url).header("User-Agent", agent).header("Accept", "application/json").build()
        return client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun Entry.toLyrics(): Lyrics? {
        if (instrumental) return null
        val synced = syncedLyrics?.let(::parseLrc).orEmpty()
        if (synced.isNotEmpty()) return Lyrics(synced, synced = true)
        val plain = plainLyrics?.lines()?.map { LyricLine(null, it) }.orEmpty()
        return if (plain.any { it.text.isNotBlank() }) Lyrics(plain, synced = false) else null
    }

    private fun parseLrc(text: String): List<LyricLine> {
        val pattern = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
        return text.lines().flatMap { raw ->
            val stamps = pattern.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val words = raw.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3].let { if (it.isEmpty()) 0L else it.padEnd(3, '0').take(3).toLong() }
                LyricLine(min * 60_000 + sec * 1000 + frac, words)
            }
        }.sortedBy { it.timeMs }
    }

    private companion object {
        const val ONLINE_TIMEOUT_MS = 7_000L
        const val LINE_TIMEOUT_MS = 4_000L
        const val LINE_LOOKUPS = 5
        const val MAX_ONLINE = 15
        const val BROWSER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val KARAOKE = Regex("karaoke|instrumental|originally performed", RegexOption.IGNORE_CASE)
        val VERSION = Regex("remix|paused|sped up|slowed|cover|tiktok|mashup|\\bx\\b|edit\\)|version|\\blive\\b", RegexOption.IGNORE_CASE)
    }
}

/** A song found by its lyrics. [track] is set when it's already playable (from your library). */
data class LyricMatch(
    val title: String,
    val artist: String,
    val artwork: String? = null,
    /** The line that matched, as written in the lyrics. */
    val line: String? = null,
    val track: Track? = null,
    val source: String = "",
)

/** Text helpers shared by the online search and the on-phone lyric index. */
object LyricText {
    /** Lower case, no accents or punctuation, "don't" → "dont", single spaces. */
    fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("['’`]"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun words(s: String): List<String> = norm(s).split(' ').filter { it.isNotBlank() }

    /**
     * The lyric line that best matches [query] and how well (1.0 = the words appear exactly).
     * Looks at each line and each pair of lines, since a remembered bit often runs across two.
     * Loose matches need most of the words, in any order; null when nothing comes close.
     */
    fun bestLine(lines: List<String>, query: String): Pair<String, Double>? {
        val q = norm(query)
        val qWords = q.split(' ').filter { it.length > 1 }.toSet()
        if (q.isEmpty()) return null
        val clean = lines.filter { it.isNotBlank() }
        fun score(text: String): Double {
            val n = norm(text)
            return when {
                n.contains(q) -> 1.0
                qWords.size >= 3 -> {
                    val have = n.split(' ').toSet()
                    qWords.count { it in have }.toDouble() / qWords.size * 0.95
                }
                else -> 0.0
            }
        }
        // Single lines first, so the line that holds the words is the one shown. Two lines
        // together only count when the words run across a line break, and then both are shown.
        var best: Pair<String, Double>? = null
        clean.forEach { line -> score(line).let { if (it > (best?.second ?: 0.0)) best = line to it } }
        if ((best?.second ?: 0.0) < 1.0) {
            clean.zipWithNext { a, b -> "$a / $b" }.forEach { pair ->
                // A pair has to beat the best single line clearly, or it's the same match.
                score(pair.replace(" / ", " ")).let { if (it > (best?.second ?: 0.0) + 0.1) best = pair to it }
            }
        }
        return best?.takeIf { it.second >= 0.75 }
    }
}
