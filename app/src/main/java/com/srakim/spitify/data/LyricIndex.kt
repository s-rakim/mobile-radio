package com.srakim.spitify.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The lyrics of your own songs, kept on the phone so Search can find one of them from a line
 * you remember, instantly and offline. It fills itself two ways:
 *  - every time the lyrics card loads a song's words, they're kept here;
 *  - in the background it fetches lyrics for your liked songs, recent plays and Spotify likes,
 *    a few at a time.
 */
class LyricIndex(context: Context, private val lyrics: LyricsApi) {

    @Serializable
    data class Entry(val track: Track, val text: String)

    @Serializable
    private data class Stored(val entries: List<Entry> = emptyList(), val missing: List<String> = emptyList())

    private val file = File(context.filesDir, "lyric_index.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val entries = ConcurrentHashMap<String, Entry>()
    /** Songs LRCLIB has no words for, so the background fill doesn't keep asking. */
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var fillJob: Job? = null

    private val _size = MutableStateFlow(0)
    /** How many songs' lyrics are searchable, for the Lyrics tab's footer. */
    val size: StateFlow<Int> = _size

    init {
        scope.launch {
            runCatching {
                if (file.exists()) {
                    val stored = json.decodeFromString<Stored>(file.readText())
                    stored.entries.forEach { entries.putIfAbsent(key(it.track), it) }
                    missing += stored.missing
                    _size.value = entries.size
                }
            }
        }
    }

    fun add(track: Track, found: Lyrics) {
        if (track.isRadio || track.id.startsWith("lyric:")) return
        val text = found.lines.joinToString("\n") { it.text }.trim()
        if (text.isEmpty()) return
        // Keep only what a search result needs; stream addresses go stale anyway.
        entries[key(track)] = Entry(track.copy(directUrl = null), text)
        missing -= key(track)
        _size.value = entries.size
        saveSoon()
    }

    /** Your songs with these words in them, best match first. */
    fun search(query: String, limit: Int = 30): List<LyricMatch> {
        if (LyricText.words(query).size < 2) return emptyList()
        return entries.values.mapNotNull { e ->
            val (line, score) = LyricText.bestLine(e.text.lines(), query) ?: return@mapNotNull null
            score to LyricMatch(e.track.title, e.track.artist, e.track.artwork, line, e.track, "Your library")
        }.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /**
     * Fetches lyrics for [tracks] that aren't indexed yet, two at a time with a short pause, so
     * it never hammers LRCLIB. Calling it again while it runs restarts with the new list.
     */
    fun fill(tracks: List<Track>, max: Int = 300) {
        val todo = tracks.asSequence()
            .filter { !it.isRadio && !entries.containsKey(key(it)) && key(it) !in missing && !lyrics.knownMissing(it) }
            .distinctBy { key(it) }
            .take(max)
            .toList()
        if (todo.isEmpty()) return
        fillJob?.cancel()
        fillJob = scope.launch {
            todo.chunked(2).forEach { pair ->
                pair.forEach { t ->
                    // find() hands found lyrics to add() through LyricsApi.onFound.
                    val got = runCatching { lyrics.find(t) }.getOrNull()
                    if (got == null) {
                        missing += key(t)
                        saveSoon()
                    }
                }
                delay(PAUSE_MS)
            }
        }
    }

    private fun key(t: Track) = LyricText.norm(t.title) + "|" + LyricText.norm(t.artist)

    private fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_500)
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(Stored.serializer(), Stored(entries.values.toList(), missing.toList())))
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val PAUSE_MS = 700L
    }
}
