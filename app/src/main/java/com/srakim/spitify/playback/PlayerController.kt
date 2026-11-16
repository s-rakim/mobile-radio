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
import com.srakim.spitify.data.SpotifyImport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** One song after the current one. [queued] is true for songs added with Add to Queue or Play next. */
data class QueueEntry(val index: Int, val track: Track, val queued: Boolean = false, val suggested: Boolean = false)

data class PlaybackState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    /** Smart Shuffle: shuffle plus suggested songs slipped in every few tracks. */
    val smartShuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val upNext: List<QueueEntry> = emptyList(),
    val context: PlayContext? = null,
    val sleepTimerEndsAt: Long? = null,
    /** Sleep timer set to "End of track". */
    val sleepAtTrackEnd: Boolean = false,
    /** What a radio station says it's playing right now, when it says. */
    val onAir: String? = null,
) {
    val queuedCount: Int get() = upNext.count { it.queued }

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
    private var sleepAtTrackEnd = false

    /**
     * Shuffle is done by actually reordering the queue rather than with the player's shuffle
     * mode. That way the order you see in the queue is the order things play in, songs can be
     * dragged around while shuffled, and Reshuffle is just another reorder.
     */
    private var shuffleOn = settings.current.shuffle
    private var smartOn = false

    /** Songs Smart Shuffle added, so turning it off can take them out again. */
    private val suggestedIds = HashSet<String>()
    private var lastBroadcast: Pair<String?, Boolean>? = null

    /** The order the songs came in, so turning shuffle off can put them back. */
    private var originalOrder: List<Track> = emptyList()

    /** Songs added with Add to Queue or Play next that haven't played yet. They sit right after the current song. */
    private val queuedIds = LinkedHashSet<String>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Hands Spotify songs to the Spotify app. Set by the app at start-up. */
    var spotify: SpotifyBridge? = null

    /** True while the Spotify app is doing the playing and this class only mirrors it. */
    private var remoteMode = false
    private var remoteQueue: List<Track> = emptyList()
    private var remoteJob: Job? = null
    private var remoteQueuedAfter = -1
    private var standInJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val finished = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (sleepAtTrackEnd && finished) {
                sleepAtTrackEnd = false
                controller?.run {
                    pause()
                    seekToDefaultPosition()
                }
                _messages.tryEmit("Sleep timer: paused at the end of the song")
                sync()
            }
        }

        // The notification and lock screen still flip the player's own shuffle mode. Turn that
        // back off and shuffle the queue our way instead.
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            val c = controller ?: return
            if (!shuffleModeEnabled) return
            c.shuffleModeEnabled = false
            if (!shuffleOn) toggleShuffle()
        }

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
            if (c.shuffleModeEnabled) c.shuffleModeEnabled = false
            if (originalOrder.isEmpty() && c.mediaItemCount > 0) originalOrder = allTracks(c)
            pending.toList().forEach { it(c) }
            pending.clear()
            sync()
        }, ContextCompat.getMainExecutor(context))

        scope.launch {
            while (isActive) {
                delay(250)
                if (remoteMode) {
                    tickRemote()
                    continue
                }
                val c = controller ?: continue
                if (c.isPlaying || c.playbackState == Player.STATE_BUFFERING) updatePosition(c)
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, from: PlayContext? = null, shuffle: Boolean? = null) {
        if (settings.current.offlineMode) {
            // Offline mode only plays what's on the phone: downloads and local files.
            val wanted = tracks.getOrNull(startIndex)
            val playable = tracks.filter { it.isLocal || downloads.fileFor(it.id) != null }
            if (playable.isEmpty()) {
                _messages.tryEmit("Offline mode is on. Download songs to play them offline.")
                return
            }
            if (playable.size != tracks.size) {
                val start = playable.indexOfFirst { it.id == wanted?.id }.coerceAtLeast(0)
                if (wanted != null && playable.none { it.id == wanted.id }) _messages.tryEmit("That song isn't downloaded, playing your downloads")
                return play(playable, start, from, shuffle)
            }
        }
        if (tracks.isEmpty()) return
        val first = tracks.getOrNull(startIndex.coerceIn(tracks.indices))
        if (first != null && SpotifyImport.isSpotify(first.id)) {
            playSpotify(tracks, startIndex.coerceIn(tracks.indices), from)
            return
        }
        leaveRemote()
        tracks.forEach { known[it.id] = it }
        playContext = from
        if (shuffle != null) setShuffle(shuffle)
        withController { c ->
            // Like Spotify, whatever you queued up survives starting something else.
            val pending = pendingQueued(c)
            originalOrder = tracks
            val start = if (shuffle == true) tracks.indices.random() else startIndex.coerceIn(tracks.indices)
            val ordered = if (shuffleOn) listOf(tracks[start]) + shuffleOrder(tracks.filterIndexed { i, _ -> i != start }) else tracks
            val startAt = if (shuffleOn) 0 else start
            val items = ordered.toMutableList().apply { addAll(startAt + 1, pending) }
            c.setMediaItems(items.map { mediaItemFor(it) }, startAt, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() {
        if (remoteMode) {
            val r = spotify?.remote ?: return
            if (r.state.value.paused) r.resume() else r.pause()
            return
        }
        togglePlayPauseLocal()
    }

    private fun togglePlayPauseLocal() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() {
        if (remoteMode) return spotify?.remote?.next() ?: Unit
        withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        if (remoteMode) return spotify?.remote?.previous() ?: Unit
        withController { it.seekToPrevious() }
    }

    fun seekTo(positionMs: Long) {
        if (remoteMode) {
            spotify?.remote?.seekTo(positionMs)
            _state.update { it.copy(positionMs = positionMs) }
            return
        }
        withController {
            it.seekTo(positionMs)
            updatePosition(it)
        }
    }

    /** Off, then shuffle, then Smart Shuffle (when it's turned on in settings), then off again. */
    fun toggleShuffle() {
        when {
            !shuffleOn -> {
                setShuffle(true)
                smartOn = false
                withController { c -> shuffleRest(c) }
            }
            !smartOn && settings.current.smartShuffle -> {
                smartOn = true
                withController { c -> addSuggestions(c) }
            }
            else -> {
                setShuffle(false)
                val wasSmart = smartOn
                smartOn = false
                withController { c ->
                    if (wasSmart) removeSuggestions(c)
                    unshuffle(c)
                }
            }
        }
        sync()
    }

    /** Smart Shuffle: one suggested song after every three of yours, picked from your taste. */
    private fun addSuggestions(c: MediaController) {
        val current = c.currentMediaItem?.toTrack() ?: return
        scope.launch {
            val lib = library.state.value
            val genres = (listOfNotNull(current.genre?.takeIf { it.isNotBlank() }) + lib.tasteGenres()).distinct().take(2)
            val queued = (0 until c.mediaItemCount).mapTo(HashSet()) { c.getMediaItemAt(it).mediaId }
            val picks = genres.ifEmpty { listOf(null) }.flatMap { g ->
                runCatching { api.trendingTracks(genre = g, time = "month", limit = 30) }.getOrDefault(emptyList())
            }.filterNot { it.id in queued || it.id in lib.tasteExcluded }.distinctBy { it.id }.shuffled().take(15)
            if (picks.isEmpty() || !smartOn) return@launch
            picks.forEach { known[it.id] = it }
            val base = c.currentMediaItemIndex + 1 + pendingQueued(c).size
            var at = base + 3
            for (t in picks) {
                if (at > c.mediaItemCount) break
                suggestedIds += t.id
                c.addMediaItem(at, mediaItemFor(t))
                at += 4
            }
            sync()
        }
    }

    private fun removeSuggestions(c: MediaController) {
        val cur = c.currentMediaItemIndex
        (c.mediaItemCount - 1 downTo cur + 1).forEach { i ->
            if (c.getMediaItemAt(i).mediaId in suggestedIds) c.removeMediaItem(i)
        }
        suggestedIds.clear()
    }

    /** Shuffles everything that isn't queued again, keeping the current song playing. */
    fun reshuffle() = withController { c ->
        if (shuffleOn) shuffleRest(c)
    }

    private fun setShuffle(on: Boolean) {
        shuffleOn = on
        if (settings.current.shuffle != on) settings.update { it.copy(shuffle = on) }
    }

    private fun allTracks(c: MediaController): List<Track> = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }

    private fun pendingQueued(c: MediaController): List<Track> {
        val cur = c.currentMediaItemIndex
        if (cur == C.INDEX_UNSET || c.mediaItemCount == 0) return emptyList()
        return (cur + 1 until c.mediaItemCount).map { c.getMediaItemAt(it) }.takeWhile { it.mediaId in queuedIds }.map { it.toTrack() }
    }

    /** Queued songs stay on top, the rest of the songs (played or not) get shuffled in after them. */
    private fun shuffleRest(c: MediaController) {
        val cur = c.currentMediaItemIndex
        if (cur == C.INDEX_UNSET || c.mediaItemCount < 2) return
        val all = allTracks(c)
        val pending = pendingQueued(c)
        val rest = all.filterIndexed { i, _ -> i != cur && i !in (cur + 1)..(cur + pending.size) }
        rebuildAround(c, emptyList(), pending + shuffleOrder(rest))
    }

    /**
     * "Fewer repeats" pushes songs you heard recently towards the end, the rest is random.
     * "Standard" is a plain random order.
     */
    private fun shuffleOrder(tracks: List<Track>): List<Track> {
        if (settings.current.shuffleStyle == com.srakim.spitify.data.ShuffleStyle.STANDARD) return tracks.shuffled()
        val recent = library.state.value.recentTracks.take(50).withIndex().associate { (i, t) -> t.id to (50 - i) / 50.0 }
        return tracks.map { it to Math.random() + (recent[it.id] ?: 0.0) * 1.5 }.sortedBy { it.second }.map { it.first }
    }

    /** Back to the original order, carrying on from the song that's playing. */
    private fun unshuffle(c: MediaController) {
        val cur = c.currentMediaItemIndex
        if (cur == C.INDEX_UNSET || originalOrder.isEmpty()) return
        val all = allTracks(c)
        val current = all[cur]
        val pending = pendingQueued(c)
        val inOriginal = originalOrder.mapTo(HashSet()) { it.id }
        // Songs autoplay added along the way weren't in the original list. Keep them at the end.
        val extras = all.drop(cur + 1 + pending.size).filter { it.id !in inOriginal }
        val pos = originalOrder.indexOfFirst { it.id == current.id }
        val before = if (pos >= 0) originalOrder.take(pos) else emptyList()
        val after = if (pos >= 0) originalOrder.drop(pos + 1) else originalOrder
        rebuildAround(c, before, pending + after + extras)
    }

    /** Swaps out everything around the current song without interrupting it. */
    private fun rebuildAround(c: MediaController, before: List<Track>, after: List<Track>) {
        val cur = c.currentMediaItemIndex
        if (cur == C.INDEX_UNSET) return
        val count = c.mediaItemCount
        if (cur + 1 < count) c.removeMediaItems(cur + 1, count)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (before.isNotEmpty()) c.addMediaItems(0, before.map { mediaItemFor(it) })
        if (after.isNotEmpty()) c.addMediaItems(after.map { mediaItemFor(it) })
    }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Straight after the current song, ahead of anything else in the queue. */
    fun playNext(track: Track) {
        known[track.id] = track
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(listOf(track))
            } else {
                queuedIds += track.id
                c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemFor(track))
            }
        }
    }

    /** After the current song and anything queued before it, but ahead of the rest of the playlist, like Spotify. */
    fun addToQueue(track: Track) {
        known[track.id] = track
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(listOf(track))
            } else {
                val at = c.currentMediaItemIndex + 1 + pendingQueued(c).size
                queuedIds += track.id
                c.addMediaItem(at, mediaItemFor(track))
            }
        }
    }

    /** Adds songs to the very end, after the playlist. The DJ keeps its set going this way. */
    fun append(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        tracks.forEach { known[it.id] = it }
        withController { c ->
            if (c.mediaItemCount == 0) play(tracks, 0, playContext) else c.addMediaItems(tracks.map { mediaItemFor(it) })
        }
    }

    /** Swaps everything after the current song (keeping songs you queued) for [tracks]. */
    fun replaceUpcoming(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        tracks.forEach { known[it.id] = it }
        withController { c ->
            if (c.mediaItemCount == 0) return@withController play(tracks, 0, playContext)
            val from = c.currentMediaItemIndex + 1 + pendingQueued(c).size
            if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
            c.addMediaItems(tracks.map { mediaItemFor(it) })
        }
    }

    /**
     * Drag and drop in the queue. Positions count from the first song after the current one.
     * Dropping a song among the queued ones makes it part of the queue; dragging a queued song
     * below them lets it go back to being part of the playlist.
     */
    fun moveInQueue(from: Int, to: Int) = withController { c ->
        val base = c.currentMediaItemIndex + 1
        if (base <= 0 || from == to || base + maxOf(from, to) >= c.mediaItemCount) return@withController
        val queued = pendingQueued(c).size
        val id = c.getMediaItemAt(base + from).mediaId
        val wasQueued = from < queued
        c.moveMediaItem(base + from, base + to)
        if (wasQueued && to >= queued) queuedIds -= id
        if (!wasQueued && to < queued) queuedIds += id
        sync()
    }

    /** Empties "Next in queue" and leaves the playlist alone. */
    fun clearQueue() = withController { c ->
        val n = pendingQueued(c).size
        val base = c.currentMediaItemIndex + 1
        queuedIds.clear()
        if (n > 0) c.removeMediaItems(base, base + n)
    }

    /** Removes several songs at once, from the queue's Edit mode. */
    fun removeFromQueue(indices: Collection<Int>) = withController { c ->
        val pending = pendingQueued(c).size
        val base = c.currentMediaItemIndex + 1
        indices.filter { it in 0 until c.mediaItemCount && it != c.currentMediaItemIndex }
            .sortedDescending()
            .forEach { i ->
                if (i in base until base + pending) queuedIds -= c.getMediaItemAt(i).mediaId
                c.removeMediaItem(i)
            }
    }

    fun skipTo(index: Int) {
        if (remoteMode) {
            val t = remoteQueue.getOrNull(index) ?: return
            remoteQueuedAfter = index - 1
            spotify?.remote?.play(t.id)
            return
        }
        skipToLocal(index)
    }

    private fun skipToLocal(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun removeFromQueue(index: Int) = removeFromQueue(listOf(index))

    /** Pass null to cancel. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepAtTrackEnd = false
        if (minutes == null) {
            sleepEndsAt = null
        } else {
            val endsAt = System.currentTimeMillis() + minutes * 60_000L
            sleepEndsAt = endsAt
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                if (remoteMode) spotify?.remote?.pause() else controller?.pause()
                sleepEndsAt = null
                sync()
            }
        }
        sync()
    }

    /** Pauses when the current song finishes instead of after a set time. */
    fun setSleepAtTrackEnd() {
        sleepJob?.cancel()
        sleepEndsAt = null
        sleepAtTrackEnd = true
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

    // ---------- Spotify ----------

    private fun playSpotify(tracks: List<Track>, start: Int, from: PlayContext?) {
        tracks.forEach { known[it.id] = it }
        val bridge = spotify
        if (bridge != null && bridge.ready) {
            // Only Spotify songs can go to the Spotify app; anything else in the list is left out.
            val startId = tracks[start].id
            val spotifyOnly = tracks.filter { SpotifyImport.isSpotify(it.id) }
            standInJob?.cancel()
            controller?.pause()
            playContext = from
            remoteQueue = spotifyOnly
            remoteQueuedAfter = -1
            remoteMode = true
            bridge.remote.play(startId)
            remoteJob?.cancel()
            remoteJob = scope.launch { bridge.remote.state.collect { publishRemote(it) } }
            return
        }
        // No Spotify app, no Premium or not connected: the same songs from Spitify's sources.
        _messages.tryEmit(bridge?.whyNot() ?: "Playing the closest match from Spitify's sources")
        if (bridge == null) return
        standInJob?.cancel()
        standInJob = scope.launch {
            suspend fun resolve(t: Track): Track? = if (SpotifyImport.isSpotify(t.id)) bridge.standIn(t) else t
            val wanted = tracks.drop(start).take(STAND_IN_MAX)
            var firstAt = -1
            var firstMatch: Track? = null
            for ((i, t) in wanted.withIndex().take(5)) {
                firstMatch = resolve(t)
                if (firstMatch != null) {
                    firstAt = i
                    break
                }
            }
            val found = firstMatch ?: run {
                _messages.tryEmit("Couldn't find that one anywhere else")
                return@launch
            }
            play(listOf(found), 0, from)
            val gate = Semaphore(4)
            val rest = coroutineScope {
                wanted.drop(firstAt + 1).map { t -> async { gate.withPermit { resolve(t) } } }.awaitAll()
            }.filterNotNull()
            if (rest.isNotEmpty()) append(rest)
        }
    }

    /** Back to playing on the phone. The Spotify app pauses so the two don't play over each other. */
    private fun leaveRemote() {
        standInJob?.cancel()
        if (!remoteMode) return
        remoteMode = false
        remoteJob?.cancel()
        remoteJob = null
        spotify?.remote?.pause()
    }

    private fun publishRemote(rs: RemoteState) {
        if (!remoteMode) return
        if (!rs.connected) {
            remoteMode = false
            remoteJob?.cancel()
            _messages.tryEmit("Lost the connection to the Spotify app")
            sync()
            return
        }
        val rt = rs.track ?: return
        val current = known[rt.uri] ?: Track(
            id = rt.uri,
            title = rt.title,
            artist = rt.artist,
            artwork = rt.imageUrl,
            artworkLarge = rt.imageUrl,
            durationSec = (rt.durationMs / 1000).toInt(),
            album = rt.album,
        )
        val at = remoteQueue.indexOfFirst { it.id == rt.uri }
        // Keep exactly one song lined up in the Spotify app, so skipping and auto-advance follow our queue.
        if (at >= 0 && remoteQueuedAfter != at) {
            remoteQueuedAfter = at
            remoteQueue.getOrNull(at + 1)?.let { spotify?.remote?.queue(it.id) }
        }
        if (current.id != lastRecordedId && !rs.paused) {
            lastRecordedId = current.id
            if (!settings.current.privateSession) library.recordPlay(current)
        }
        _state.value = PlaybackState(
            current = current,
            isPlaying = !rs.paused,
            positionMs = rs.positionMs,
            durationMs = rt.durationMs,
            upNext = if (at >= 0) remoteQueue.drop(at + 1).mapIndexed { i, t -> QueueEntry(at + 1 + i, t) } else emptyList(),
            context = playContext,
            sleepTimerEndsAt = sleepEndsAt,
            sleepAtTrackEnd = sleepAtTrackEnd,
        )
    }

    /** The Spotify app only reports position on changes, so the bar is moved along here in between. */
    private fun tickRemote() {
        val rs = spotify?.remote?.state?.value ?: return
        if (rs.paused) return
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(positionMs = (rs.positionMs + (now - rs.positionAt)).coerceIn(0, it.durationMs.coerceAtLeast(0)))
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
        if (remoteMode) return
        val c = controller ?: return
        val current = c.currentMediaItem?.toTrack()
        // A queued song that's now playing has left the queue.
        if (current != null) queuedIds -= current.id
        broadcast(current, c.isPlaying, c.duration)
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
            var inQueueBlock = true
            while (i != C.INDEX_UNSET && upNext.size < 100) {
                val item = c.getMediaItemAt(i)
                inQueueBlock = inQueueBlock && item.mediaId in queuedIds
                upNext += QueueEntry(i, item.toTrack(), queued = inQueueBlock, suggested = item.mediaId in suggestedIds)
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }

        _state.value = PlaybackState(
            current = current,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = durationOf(c, current),
            shuffle = shuffleOn,
            smartShuffle = shuffleOn && smartOn,
            repeatMode = c.repeatMode,
            upNext = upNext,
            context = playContext,
            sleepTimerEndsAt = sleepEndsAt,
            sleepAtTrackEnd = sleepAtTrackEnd,
            onAir = if (current?.isRadio == true) onAirText(c, current) else null,
        )
    }

    /**
     * "Device broadcast status": the standard intents music apps send so scrobblers, widgets
     * and car head units can see what's playing.
     */
    private fun broadcast(track: Track?, playing: Boolean, durationMs: Long) {
        if (!settings.current.broadcastStatus) return
        val key = track?.id to playing
        if (key == lastBroadcast) return
        val changedTrack = lastBroadcast?.first != track?.id
        lastBroadcast = key
        val action = if (changedTrack) "com.android.music.metachanged" else "com.android.music.playstatechanged"
        val intent = android.content.Intent(action)
            .putExtra("id", track?.id?.hashCode()?.toLong() ?: -1L)
            .putExtra("track", track?.title)
            .putExtra("artist", track?.artist)
            .putExtra("album", track?.album)
            .putExtra("duration", if (durationMs > 0) durationMs else (track?.durationSec ?: 0) * 1000L)
            .putExtra("playing", playing)
            .putExtra("package", context.packageName)
        runCatching { context.sendBroadcast(intent) }
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
        if (remoteMode) return
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

/** How many songs of a Spotify list get swapped for stand-ins when Spotify can't play them. */
private const val STAND_IN_MAX = 30

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
