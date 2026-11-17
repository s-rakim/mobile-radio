package com.srakim.spitify.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a song on your Spitify server (Settings → Music sources → Spitify server), so the player
 * and downloads take it from there instead of Spotify, Audius or the open catalogues. The server
 * is searched the way it already answers Search (its YouTube and SoundCloud routes) and plays
 * the result through itself. Only the exact recording counts (see [SongMatch.exact]); a song the
 * server can't find stays missing rather than being swapped for something close.
 *
 * Answers are kept on the phone. A song found once never needs another search; a song the
 * server didn't have is asked about again after a day, in case you've added it since.
 */
class ServerCopies(
    context: Context,
    private val server: SpitifyServerApi,
    private val settings: SettingsStore,
) {
    @Serializable
    private data class Entry(val copy: Track? = null, val checkedAt: Long = 0)

    @Serializable
    private data class Saved(val server: String = "", val entries: Map<String, Entry> = emptyMap())

    private val file = File(context.filesDir, "server-copies.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Track?>>()
    // Each lookup is a real search on your server, so only a couple run at once.
    private val gate = Semaphore(2)

    /** What's known so far, by song id: true when the server has it, false when it doesn't. */
    private val _onServer = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val onServer: StateFlow<Map<String, Boolean>> = _onServer.asStateFlow()

    @Volatile private var loadedFor: String? = null

    /** Set when a server copy failed to play, so the next few minutes use the other sources. */
    @Volatile private var downUntil = 0L

    /** The server is set up, switched on, and we're not in offline mode. */
    val enabled: Boolean
        get() = server.enabled && !settings.current.offlineMode

    /** Whether [track] is the kind of song that can have a copy on your server. */
    fun wants(track: Track): Boolean =
        !track.isLocal && !track.isRadio && !track.hls && !SpitifyServerApi.isServer(track.id) && !SubsonicApi.isServer(track.id)

    /** The server copy we already know about, without asking the server. */
    fun known(track: Track): Track? {
        if (!enabled || !wants(track) || System.currentTimeMillis() < downUntil) return null
        ensureLoaded()
        return entries[track.id]?.copy
    }

    /** The copy found earlier, even while offline or with the server switched off. */
    fun remembered(track: Track): Track? {
        if (!wants(track)) return null
        ensureLoaded()
        return entries[track.id]?.copy
    }

    /** True once the server has been asked about [track] and the answer is still fresh. */
    fun settled(track: Track): Boolean {
        if (!enabled || !wants(track)) return true
        ensureLoaded()
        val e = entries[track.id] ?: return false
        return e.copy != null || System.currentTimeMillis() - e.checkedAt < RECHECK_MS
    }

    /** The server's copy of [track], asking the server if we don't know yet. */
    suspend fun find(track: Track): Track? {
        if (!enabled || !wants(track)) return null
        ensureLoaded()
        if (settled(track)) return known(track)
        val job = inFlight.computeIfAbsent(track.id) { io.async { gate.withPermit { lookup(track) } } }
        return try {
            job.await()
        } finally {
            inFlight.remove(track.id, job)
        }?.takeIf { System.currentTimeMillis() >= downUntil }
    }

    fun markUnreachable() {
        downUntil = System.currentTimeMillis() + DOWN_MS
    }

    private suspend fun lookup(track: Track): Track? {
        val title = SongMatch.clean(track.title)
        if (title.isBlank()) return null
        val artist = SongMatch.clean(SongMatch.firstArtist(track.artist))
        val query = "$artist $title".trim()
        val searches = listOf<suspend (String) -> List<Track>>({ server.youtube(it) }, { server.soundcloud(it) })
        var best: Track? = null
        var anyAnswered = false
        for (search in searches) {
            val found = runCatching { search(query) }.getOrNull() ?: continue
            anyAnswered = true
            // Songs that only come as HLS pieces can't be saved for offline, so they don't count.
            val same = found.filter { !it.hls && SongMatch.exact(track, it) }
            // Same ISRC first, then the closest length.
            best = same.firstOrNull { !track.isrc.isNullOrBlank() && it.isrc.equals(track.isrc, ignoreCase = true) }
                ?: same.minByOrNull { if (track.durationSec > 0) kotlin.math.abs(it.durationSec - track.durationSec) else 0 }
            if (best != null) break
        }
        // The server didn't answer at all: that says nothing about the song, so nothing is remembered.
        if (!anyAnswered) return null
        remember(track.id, Entry(best, System.currentTimeMillis()))
        return best
    }

    private fun remember(id: String, entry: Entry) {
        entries[id] = entry
        _onServer.update { it + (id to (entry.copy != null)) }
        save()
    }

    private fun key(): String = "${SpitifyServerApi.base(settings.current.webServerUrl)}"

    @Synchronized
    private fun ensureLoaded() {
        val k = key()
        if (loadedFor == k) return
        entries.clear()
        val saved = runCatching { json.decodeFromString<Saved>(file.readText()) }.getOrNull()
        // Another server or account: its songs and ids are different, start over.
        if (saved != null && saved.server == k) entries.putAll(saved.entries)
        _onServer.value = entries.mapValues { it.value.copy != null }
        loadedFor = k
    }

    private fun save() {
        val snapshot = Saved(loadedFor.orEmpty(), entries.entries.sortedByDescending { it.value.checkedAt }.take(MAX_ENTRIES).associate { it.key to it.value })
        io.launch {
            synchronized(file) { runCatching { file.writeText(json.encodeToString(snapshot)) } }
        }
    }

    private companion object {
        const val RECHECK_MS = 24 * 60 * 60 * 1000L
        const val DOWN_MS = 3 * 60 * 1000L
        const val MAX_ENTRIES = 5000
    }
}
