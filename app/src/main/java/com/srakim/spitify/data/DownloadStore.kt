package com.srakim.spitify.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class DownloadsState(
    val downloaded: List<Track> = emptyList(),
    val inProgress: Set<String> = emptySet(),
) {
    private val ids by lazy { downloaded.mapTo(HashSet()) { it.id } }
    fun has(trackId: String) = trackId in ids
    fun isDownloading(trackId: String) = trackId in inProgress
}

/**
 * Saves songs into app storage so they play without a connection. The player picks up the
 * local file automatically whenever one exists.
 */
class DownloadStore(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)

    private val _state = MutableStateFlow(DownloadsState(downloaded = loadIndex()))
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    private fun fileName(trackId: String) = trackId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".mp3"

    fun fileFor(trackId: String): File? = File(dir, fileName(trackId)).takeIf { it.exists() && it.length() > 0 }

    /** Queues the songs that aren't saved yet. Returns false if blocked by the Wi-Fi only setting. */
    fun download(tracks: List<Track>): Boolean {
        if (isOnCellular() && !settings.current.downloadOnCellular) return false
        val todo = tracks.filter { !_state.value.has(it.id) && !_state.value.isDownloading(it.id) }
        if (todo.isEmpty()) return true
        _state.update { it.copy(inProgress = it.inProgress + todo.map { t -> t.id }) }
        todo.forEach { track ->
            io.launch {
                slots.withPermit {
                    val ok = runCatching { fetch(track) }.isSuccess
                    _state.update { state ->
                        state.copy(
                            inProgress = state.inProgress - track.id,
                            downloaded = if (ok) listOf(track) + state.downloaded.filterNot { it.id == track.id } else state.downloaded,
                        )
                    }
                    if (ok) saveIndex()
                }
            }
        }
        return true
    }

    fun remove(trackIds: Collection<String>) {
        trackIds.forEach { File(dir, fileName(it)).delete() }
        _state.update { s -> s.copy(downloaded = s.downloaded.filterNot { it.id in trackIds }) }
        io.launch { saveIndex() }
    }

    fun removeAll() = remove(_state.value.downloaded.map { it.id })

    fun bytesUsed(): Long = dir.listFiles()?.filter { it.extension == "mp3" }?.sumOf { it.length() } ?: 0L

    private fun fetch(track: Track) {
        val request = Request.Builder().url(track.streamUrl ?: AudiusApi.streamUrl(track.id)).build()
        val tmp = File(dir, fileName(track.id) + ".part")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (!tmp.renameTo(File(dir, fileName(track.id)))) throw IOException("Couldn't save file")
    }

    @Synchronized
    private fun saveIndex() {
        runCatching { index.writeText(json.encodeToString(_state.value.downloaded)) }
    }

    private fun loadIndex(): List<Track> = runCatching {
        json.decodeFromString<List<Track>>(index.readText())
    }.getOrDefault(emptyList()).filter { File(dir, fileName(it.id)).exists() }

    private fun isOnCellular(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }
}
