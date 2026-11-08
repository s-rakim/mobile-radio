package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        if (result != null) found[track.id] = result else missing += track.id
        return result
    }

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

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "Spitify (https://github.com/s-rakim/mobile-radio)").build()
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
}
