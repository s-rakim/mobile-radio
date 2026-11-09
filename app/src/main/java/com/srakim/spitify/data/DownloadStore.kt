package com.srakim.spitify.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
        val todo = tracks.filter { it.canDownload && !_state.value.has(it.id) && !_state.value.isDownloading(it.id) }
        if (todo.isEmpty()) return true
        _state.update { it.copy(inProgress = it.inProgress + todo.map { t -> t.id }) }
        todo.forEach { track ->
            io.launch {
                slots.withPermit {
                    val ok = runCatching { fetch(track) }.isSuccess
                    finish(track, ok)
                }
            }
        }
        return true
    }

    /**
     * Copies a song into the phone's Music/Spitify folder, where any music player or file manager
     * can see it. Downloads it first if it isn't saved in the app yet. Runs in the background and
     * reports back through [onDone] with the folder it went to, or what went wrong.
     */
    fun saveToPhone(track: Track, onDone: (Result<String>) -> Unit) {
        io.launch {
            val result = runCatching {
                val source = fileFor(track.id) ?: run {
                    if (isOnCellular() && !settings.current.downloadOnCellular) {
                        throw IOException("Downloads are set to Wi-Fi only")
                    }
                    _state.update { it.copy(inProgress = it.inProgress + track.id) }
                    val ok = runCatching { slots.withPermit { fetch(track) } }
                    finish(track, ok.isSuccess)
                    ok.getOrThrow()
                    fileFor(track.id) ?: throw IOException("Couldn't download the song")
                }
                export(source, track)
            }
            onDone(result)
        }
    }

    fun remove(trackIds: Collection<String>) {
        trackIds.forEach { File(dir, fileName(it)).delete() }
        _state.update { s -> s.copy(downloaded = s.downloaded.filterNot { it.id in trackIds }) }
        io.launch { saveIndex() }
    }

    fun removeAll() = remove(_state.value.downloaded.map { it.id })

    fun bytesUsed(): Long = dir.listFiles()?.filter { it.extension == "mp3" }?.sumOf { it.length() } ?: 0L

    private fun fetch(track: Track) {
        val request = Request.Builder().url(SpitifyServerApi.resolve(track.streamUrl) ?: AudiusApi.streamUrl(track.id)).build()
        val tmp = File(dir, fileName(track.id) + ".part")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (!tmp.renameTo(File(dir, fileName(track.id)))) throw IOException("Couldn't save file")
    }

    private fun finish(track: Track, ok: Boolean) {
        _state.update { state ->
            state.copy(
                inProgress = state.inProgress - track.id,
                downloaded = if (ok) listOf(track) + state.downloaded.filterNot { it.id == track.id } else state.downloaded,
            )
        }
        if (ok) saveIndex()
    }

    private fun export(source: File, track: Track): String {
        val (ext, mime) = audioType(source)
        val name = "${track.artist} - ${track.title}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120) + ".$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artist)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Spitify")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw IOException("Couldn't create the file")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't write the file")
                out.use { o -> source.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return "Music/Spitify"
        }

        // Android 9 and older: the app's own music folder, which needs no storage permission.
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Spitify").apply { mkdirs() }
        val target = File(folder, name)
        source.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        return "Android/data/${context.packageName}/files/Music/Spitify"
    }

    /** The download is saved as .mp3 whatever it really is, so look at the first bytes. */
    private fun audioType(file: File): Pair<String, String> {
        val head = ByteArray(12)
        file.inputStream().use { it.read(head) }
        fun text(from: Int, len: Int) = String(head, from, len, Charsets.ISO_8859_1)
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return when {
            text(0, 3) == "ID3" || (b0 == 0xFF && (b1 and 0xE0) == 0xE0) -> "mp3" to "audio/mpeg"
            text(4, 4) == "ftyp" -> "m4a" to "audio/mp4"
            text(0, 4) == "OggS" -> "ogg" to "audio/ogg"
            text(0, 4) == "fLaC" -> "flac" to "audio/flac"
            b0 == 0x1A && b1 == 0x45 -> "webm" to "audio/webm"
            else -> "mp3" to "audio/mpeg"
        }
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
