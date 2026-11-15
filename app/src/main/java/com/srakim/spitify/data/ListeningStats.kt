package com.srakim.spitify.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Serializable
data class TrackListen(val track: Track, val ms: Long = 0, val plays: Int = 0)

@Serializable
data class ArtistListen(val name: String, val image: String? = null, val ms: Long = 0, val plays: Int = 0)

@Serializable
data class WeekListen(
    val week: String,
    val ms: Long = 0,
    val tracks: Map<String, TrackListen> = emptyMap(),
    val artists: Map<String, ArtistListen> = emptyMap(),
) {
    val minutes: Int get() = (ms / 60_000).toInt()
    val topTracks: List<TrackListen> get() = tracks.values.sortedWith(compareByDescending<TrackListen> { it.plays }.thenByDescending { it.ms })
    val topArtists: List<ArtistListen> get() = artists.values.sortedWith(compareByDescending<ArtistListen> { it.ms }.thenByDescending { it.plays })

    /** "9 – 15 Nov" */
    val range: String
        get() {
            val start = LocalDate.parse(week)
            val end = start.plusDays(6)
            val day = DateTimeFormatter.ofPattern("d", Locale.getDefault())
            val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            return if (start.month == end.month) "${start.format(day)} – ${end.format(dayMonth)}" else "${start.format(dayMonth)} – ${end.format(dayMonth)}"
        }
}

/**
 * Listening stats kept on the phone: minutes per week, top songs and top artists. A song
 * counts as played after 30 seconds, like most stats services. Private sessions aren't
 * counted. When signed in, each week's totals go to the Spitify server so friends can compare.
 */
class ListeningStats(context: Context, private val settings: SettingsStore) {

    private val file = File(context.filesDir, "stats.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _weeks = MutableStateFlow(load())
    /** Newest week first. */
    val weeks: StateFlow<Map<String, WeekListen>> = _weeks.asStateFlow()

    private var currentId: String? = null
    private var currentMs = 0L
    private var countedPlay = false
    private var dirty = false

    fun start(player: com.srakim.spitify.playback.PlayerController, social: SocialStore) {
        io.launch {
            var lastUpload = 0L
            while (isActive) {
                delay(1000)
                val s = player.state.value
                val t = s.current
                if (t == null || !s.isPlaying || t.isRadio || settings.current.privateSession) continue
                add(t, 1000)
                if (dirty && System.currentTimeMillis() - lastUpload > 5 * 60_000) {
                    lastUpload = System.currentTimeMillis()
                    save()
                    upload(social)
                }
            }
        }
    }

    private fun add(track: Track, ms: Long) {
        if (track.id != currentId) {
            currentId = track.id
            currentMs = 0
            countedPlay = false
        }
        currentMs += ms
        val play = !countedPlay && currentMs >= 30_000
        if (play) countedPlay = true

        val key = weekOf(LocalDate.now())
        val all = _weeks.value
        val wk = all[key] ?: WeekListen(key)
        val tl = wk.tracks[track.id] ?: TrackListen(track)
        val artist = track.artist.ifBlank { "Unknown artist" }
        val al = wk.artists[artist] ?: ArtistListen(artist, track.artistImage)
        val next = wk.copy(
            ms = wk.ms + ms,
            tracks = wk.tracks + (track.id to tl.copy(track = track, ms = tl.ms + ms, plays = tl.plays + if (play) 1 else 0)),
            artists = wk.artists + (artist to al.copy(image = al.image ?: track.artistImage, ms = al.ms + ms, plays = al.plays + if (play) 1 else 0)),
        )
        _weeks.value = all + (key to next)
        dirty = true
    }

    fun upload(social: SocialStore) {
        val key = weekOf(LocalDate.now())
        val wk = _weeks.value[key] ?: return
        social.uploadStats(
            key,
            wk.minutes,
            wk.topTracks.take(10).map { TrackPlays(it.track, it.plays) },
            wk.topArtists.take(10).map { ArtistPlays(it.name, it.image, it.plays) },
        )
    }

    fun clear() {
        _weeks.value = emptyMap()
        io.launch { file.delete() }
    }

    fun flush() = io.launch { save() }

    private fun save() {
        dirty = false
        runCatching {
            val tmp = File(file.parentFile, "stats.json.tmp")
            tmp.writeText(json.encodeToString(_weeks.value.values.sortedByDescending { it.week }.take(53)))
            tmp.renameTo(file)
        }
    }

    private fun load(): Map<String, WeekListen> = runCatching {
        json.decodeFromString<List<WeekListen>>(file.readText()).associateBy { it.week }
    }.getOrDefault(emptyMap())

    companion object {
        fun weekOf(day: LocalDate): String = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        fun thisWeek(): String = weekOf(LocalDate.now())
        fun lastWeek(): String = weekOf(LocalDate.now().minusWeeks(1))
    }
}
