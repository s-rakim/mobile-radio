package com.srakim.spitify.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.srakim.spitify.data.AudiusApi
import com.srakim.spitify.data.DownloadStore
import com.srakim.spitify.data.SettingsStore
import java.io.File
import com.srakim.spitify.data.LibraryStore
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.RadioBrowserApi
import com.srakim.spitify.data.SpitifyServerApi
import com.srakim.spitify.data.Track
import com.srakim.spitify.data.tasteGenres
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(val index: Int, val track: Track)

data class PlaybackState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val upNext: List<QueueEntry> = emptyList(),
    val context: PlayContext? = null,
    val sleepTimerEndsAt: Long? = null,
    /** What a radio station says it's playing right now, when it says. */
    val onAir: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun isPlayingFrom(contextId: String) = context?.id == contextId
}

/**
 * The UI's handle on playback. Talks to [PlaybackService] through a MediaController and
 * republishes everything as a single StateFlow the composables can collect.
 */
class PlayerController(
    private val context: Context,
    private val api: AudiusApi,
    private val library: LibraryStore,
    private val settings: SettingsStore,
    private val downloads: DownloadStore,
    private val radio: RadioBrowserApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val known = HashMap<String, Track>()

    private var playContext: PlayContext? = null
    private var lastRecordedId: String? = null
    private var sleepJob: Job? = null
    private var sleepEndsAt: Long? = null
    private var autoplayFetchedFor: String? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            _messages.tryEmit("Couldn't play that one, skipping ahead")
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                controllerFuture = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            pending.toList().forEach { it(c) }
            pending.clear()
            sync()
        }, ContextCompat.getMainExecutor(context))

        scope.launch {
            while (isActive) {
                delay(250)
                val c = controller ?: continue
                if (c.isPlaying || c.playbackState == Player.STATE_BUFFERING) updatePosition(c)
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, from: PlayContext? = null, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        tracks.forEach { known[it.id] = it }
        playContext = from
        withController { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            val start = if (shuffle == true) tracks.indices.random() else startIndex.coerceIn(tracks.indices)
            c.setMediaItems(tracks.map { mediaItemFor(it) }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        updatePosition(it)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(track: Track) {
        known[track.id] = track
        withController { c ->
            if (c.mediaItemCount == 0) play(listOf(track)) else c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemFor(track))
        }
    }

    fun addToQueue(track: Track) {
        known[track.id] = track
        withController { c ->
            if (c.mediaItemCount == 0) play(listOf(track)) else c.addMediaItem(mediaItemFor(track))
        }
    }

    fun skipTo(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun removeFromQueue(index: Int) = withController {
        if (index in 0 until it.mediaItemCount && index != it.currentMediaItemIndex) it.removeMediaItem(index)
    }

    /** Pass null to cancel. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            sleepEndsAt = null
        } else {
            val endsAt = System.currentTimeMillis() + minutes * 60_000L
            sleepEndsAt = endsAt
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                controller?.pause()
                sleepEndsAt = null
                sync()
            }
        }
        sync()
    }

    /** When the queue is about to run out, top it up with similar songs, like Spotify's autoplay. */
    private fun maybeAutoplay(c: MediaController, last: Track) {
        if (!settings.current.autoplay || autoplayFetchedFor == last.id) return
        autoplayFetchedFor = last.id
        scope.launch {
            val queued = (0 until c.mediaItemCount).mapTo(HashSet()) { c.getMediaItemAt(it).mediaId }
            val lib = library.state.value
            // A song left out of the taste profile doesn't get to steer what plays next.
            val genre = if (last.id in lib.tasteExcluded) lib.tasteGenres().firstOrNull() else last.genre
            val more = runCatching { api.trendingTracks(genre = genre, limit = 30) }.getOrDefault(emptyList())
                .filterNot { it.id in queued || it.id in lib.tasteExcluded }
                .shuffled()
                .take(10)
            if (more.isEmpty()) return@launch
            more.forEach { known[it.id] = it }
            c.addMediaItems(more.map { mediaItemFor(it) })
        }
    }

    private fun mediaItemFor(track: Track): MediaItem = track.toMediaItem(downloads.fileFor(track.id))

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            action(c)
        } else {
            pending += action
            connect()
        }
    }

    private fun sync() {
        val c = controller ?: return
        val current = c.currentMediaItem?.toTrack()
        if (current != null && current.id != lastRecordedId && c.playWhenReady) {
            lastRecordedId = current.id
            if (!settings.current.privateSession) library.recordPlay(current)
            if (current.isRadio) scope.launch { radio.reportPlay(current) }
        }
        if (current != null && !current.isRadio && !c.hasNextMediaItem() && c.repeatMode == Player.REPEAT_MODE_OFF) {
            maybeAutoplay(c, current)
        }

        val upNext = mutableListOf<QueueEntry>()
        val timeline = c.currentTimeline
        val currentIndex = c.currentMediaItemIndex
        if (!timeline.isEmpty && currentIndex != C.INDEX_UNSET && currentIndex < timeline.windowCount) {
            var i = timeline.getNextWindowIndex(currentIndex, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && upNext.size < 100) {
                upNext += QueueEntry(i, c.getMediaItemAt(i).toTrack())
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }

        _state.value = PlaybackState(
            current = current,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = durationOf(c, current),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            upNext = upNext,
            context = playContext,
            sleepTimerEndsAt = sleepEndsAt,
            onAir = if (current?.isRadio == true) onAirText(c, current) else null,
        )
    }

    /**
     * Stations send the song title inside the stream (ICY metadata), and the player puts it in
     * its media metadata. Only worth showing when it isn't just the station's own name again.
     */
    private fun onAirText(c: MediaController, station: Track): String? {
        val meta = c.mediaMetadata
        val title = meta.title?.toString()?.trim().orEmpty()
        val artist = meta.artist?.toString()?.trim().orEmpty()
        val text = when {
            title.isEmpty() -> ""
            artist.isEmpty() || artist == station.artist || title.contains(artist) -> title
            else -> "$artist - $title"
        }
        return text.takeIf { it.isNotEmpty() && it != station.title && it != station.artist }
    }

    private fun updatePosition(c: MediaController) {
        _state.update {
            it.copy(
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = durationOf(c, it.current),
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
            )
        }
    }

    private fun durationOf(c: MediaController, track: Track?): Long {
        val d = c.duration
        return if (d != C.TIME_UNSET && d > 0) d else (track?.durationSec ?: 0) * 1000L
    }

    private fun MediaItem.toTrack(): Track = known[mediaId] ?: Track(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artwork = mediaMetadata.artworkUri?.toString(),
        artworkLarge = mediaMetadata.artworkUri?.toString(),
    )
}

internal const val EXTRA_MIME = "spitify.mime"

fun Track.remoteUri(): Uri = Uri.parse(SpitifyServerApi.resolve(streamUrl) ?: AudiusApi.streamUrl(id))

/**
 * What the player should open. For Audius songs that's the direct storage-node link when there
 * is one, with the regular stream address tucked into the fragment as a fallback.
 */
fun Track.playbackUri(): Uri {
    val direct = directUrl
    if (streamUrl != null || direct == null) return remoteUri()
    return FallbackDataSource.withFallback(Uri.parse(direct), remoteUri())
}

fun Track.toMediaItem(localFile: File? = null): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(localFile?.let(Uri::fromFile) ?: playbackUri())
    // The URI itself doesn't survive the trip to the playback service, request metadata does.
    .setRequestMetadata(
        MediaItem.RequestMetadata.Builder()
            .setMediaUri(playbackUri())
            // The MIME type doesn't survive the trip either; HLS radio needs it when the address doesn't end in .m3u8.
            .setExtras(if (hls) android.os.Bundle().apply { putString(EXTRA_MIME, MimeTypes.APPLICATION_M3U8) } else null)
            .build(),
    )
    .apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setGenre(genre)
            .setArtworkUri((artworkLarge ?: artwork)?.let(Uri::parse))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()
