package com.srakim.spitify.data

import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.srakim.spitify.SpitifyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale

data class DjState(
    val busy: Boolean = false,
    /** What the DJ last said on air. */
    val intro: String? = null,
    val byAgent: Boolean = false,
)

/**
 * AI DJ, like Spotify's. It starts a set from what you've been listening to, keeps it going
 * as the queue runs low, and takes requests ("more energy", "something chill", "switch it
 * up"). The picking happens on the Spitify server's DJ agent, which matches tempo, key and
 * energy from one song to the next. Without the server it builds a song radio on the phone.
 * Between batches it says a line on air through the phone's text-to-speech voice.
 */
class AiDj(private val app: SpitifyApp) {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DjState())
    val state: StateFlow<DjState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val played = LinkedHashSet<String>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    val isOn: Boolean get() = app.player.state.value.context?.id == CONTEXT_ID

    /** Keeps the set going: when two songs are left, fetch the next batch. */
    fun start() {
        io.launch {
            app.player.state.collect { s ->
                if (s.context?.id != CONTEXT_ID) return@collect
                s.current?.let { played += it.id }
                if (s.upNext.size <= 2 && !_state.value.busy) next(request = null, replace = false)
            }
        }
    }

    /** Starts a fresh DJ set. */
    fun play(request: String? = null) {
        played.clear()
        io.launch { next(request, replace = true, fresh = true) }
    }

    /** A request while the DJ is on: the songs after this one change to match it. */
    fun ask(request: String) {
        io.launch { next(request, replace = true) }
    }

    private suspend fun next(request: String?, replace: Boolean, fresh: Boolean = false) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        try {
            val s = app.player.state.value
            val lib = app.library.state.value
            val current = if (fresh) null else s.current
            val seed = current ?: lib.recentTracks.firstOrNull() ?: lib.liked.firstOrNull()
            val skip = played + lib.tasteExcluded + s.upNext.map { it.track.id }
            val reply = if (app.account.state.value.signedIn) {
                runCatching { fromServer(seed, lib.recentTracks.take(15), request, skip) }.getOrNull()
            } else null

            val tracks: List<Track>
            val intro: String?
            if (reply != null && reply.tracks.isNotEmpty()) {
                MixPlan.remember(reply.tracks)
                tracks = app.catalog.audius.tracksByIds(reply.tracks.map { it.id })
                intro = reply.intro
                _state.update { it.copy(byAgent = reply.engine == "agent") }
            } else {
                tracks = onPhone(seed, skip, request)
                intro = tracks.firstOrNull()?.let { "Up next, ${it.artist} with ${it.title}." }
                _state.update { it.copy(byAgent = false) }
            }
            if (tracks.isEmpty()) {
                _messages.tryEmit("The DJ couldn't find anything right now. Check your connection.")
                return
            }
            withContext(Dispatchers.Main) {
                when {
                    fresh || !isOn -> app.player.play(tracks, 0, PlayContext(CONTEXT_ID, "DJ", "DJ"), shuffle = false)
                    replace -> app.player.replaceUpcoming(tracks)
                    else -> app.player.append(tracks)
                }
            }
            _state.update { it.copy(intro = intro) }
            if (intro != null && (fresh || replace)) say(intro)
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun fromServer(seed: Track?, history: List<Track>, request: String?, skip: Set<String>): DjReply =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                if (seed != null) putJsonObject("current") {
                    put("id", seed.id)
                    put("title", seed.title)
                    put("artist", seed.artist)
                    seed.genre?.let { put("genre", it) }
                }
                put("history", JsonArray(history.map { t ->
                    buildJsonObject {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        t.genre?.let { put("genre", it) }
                    }
                }))
                request?.let { put("request", it) }
                put("count", 10)
                put("skip", JsonArray(skip.take(300).map { JsonPrimitive(it) }))
            }
            djJson.decodeFromJsonElement<DjReply>(app.account.request("api/ai/dj", "POST", body, slow = true))
        }

    private suspend fun onPhone(seed: Track?, skip: Set<String>, request: String?): List<Track> {
        val r = request.orEmpty().lowercase()
        val pool = when {
            r.isNotBlank() -> AiPlaylist.generate(r, app.catalog, skip, 30).tracks
            seed != null -> SongRadio.build(seed, app.catalog, skip).drop(1)
            else -> app.catalog.audius.trendingTracks(limit = 40)
        }.filterNot { it.id in skip }
        // Lean the energy the way they asked, then keep the set's shape.
        val lean = when {
            Regex("more energy|turn it up|hype|harder|faster|party").containsMatchIn(r) -> 1
            Regex("chill|calm|slow|softer|relax|mellow").containsMatchIn(r) -> -1
            else -> 0
        }
        val sorted = if (lean == 0) pool else pool.sortedBy { lean * -SetShaper.energyOf(it) }
        return SetShaper.shape(sorted.take(12))
    }

    private fun say(text: String) {
        if (!app.settings.current.djVoice) return
        val engine = tts ?: TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dj")
            }
        }.also { t ->
            tts = t
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            return
        }
        if (ttsReady) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dj")
    }

    companion object {
        const val CONTEXT_ID = "dj"
    }
}
