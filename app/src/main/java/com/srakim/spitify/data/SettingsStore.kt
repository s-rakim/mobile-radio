package com.srakim.spitify.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class VolumeLevel(val label: String, val description: String) {
    LOUD("Loud", "Might reduce dynamics. Use in noisy places."),
    NORMAL("Normal", "Default"),
    QUIET("Quiet", "Ideal for quiet places"),
}

@Serializable
enum class DataSaverMode(val label: String, val description: String?) {
    ON("Always on", null),
    AUTO("Automatic", "Adjusts based on Android's Data Saver setting or when you're on mobile data."),
    OFF("Always off", null),
}

@Serializable
enum class ShuffleStyle(val label: String, val description: String) {
    FEWER_REPEATS("Fewer repeats", "Randomizes while remembering recent plays, making songs you just heard less likely to repeat soon."),
    STANDARD("Standard", "Shuffles songs in a random order; every song has an equal shot, even if that means you hear some more often than others."),
}

@Serializable
enum class Quality(val label: String, val description: String) {
    AUTO("Automatic", "Best the connection allows"),
    LOW("Low", "Smaller artwork, saves data"),
    HIGH("Very high", "Full-size artwork and the best stream each source has"),
}

@Serializable
data class AppSettings(
    val profileName: String = "",
    val crossfadeSeconds: Int = 0,
    val volumeLevel: VolumeLevel = VolumeLevel.NORMAL,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: Int = -1,
    /** Millibel level per band, only used when [equalizerPreset] is -1 (custom). */
    val equalizerBands: List<Int> = emptyList(),
    val autoplay: Boolean = true,
    val privateSession: Boolean = false,
    val dataSaver: Boolean = false,
    val downloadOnCellular: Boolean = false,
    val showLyrics: Boolean = true,
    /** Shuffle stays on between songs and app restarts, like Spotify. */
    val shuffle: Boolean = false,
    /** Open catalogues the phone searches by itself, no server needed. */
    val archiveEnabled: Boolean = true,
    val ccMixterEnabled: Boolean = true,
    val jamendoEnabled: Boolean = true,
    /** Jamendo stays hidden until this is filled in (free from devportal.jamendo.com). */
    val jamendoClientId: String = "",
    /** Both come from an app registered at soundcloud.com/you/apps. SoundCloud stays off without them. */
    val soundCloudClientId: String = "",
    val soundCloudClientSecret: String = "",
    /** A Subsonic-compatible server such as Navidrome. */
    val subsonicEnabled: Boolean = true,
    val subsonicUrl: String = "",
    val subsonicUser: String = "",
    val subsonicPassword: String = "",
    val showLocalFiles: Boolean = true,
    /** The Spitify web server at home, for YouTube and the open catalogues. */
    val webServerEnabled: Boolean = true,
    val webServerUrl: String = "",

    // Playback
    /** Trims silence between and inside songs, so one flows straight into the next. */
    val gapless: Boolean = false,
    /** Fades songs into each other on mixed playlists and genre mixes, even with crossfade off. */
    val automix: Boolean = true,
    val monoAudio: Boolean = false,
    /** Sends the song that's playing to other apps (scrobblers, widgets) as a system broadcast. */
    val broadcastStatus: Boolean = false,
    /** Offers Smart Shuffle as a third shuffle mode, which slips in suggested songs. */
    val smartShuffle: Boolean = true,
    val shuffleStyle: ShuffleStyle = ShuffleStyle.FEWER_REPEATS,
    /** Evens out loudness between songs with a compressor on the output. */
    val volumeNormalization: Boolean = true,

    // Data saving and offline
    val dataSaverMode: DataSaverMode = DataSaverMode.OFF,
    /** Nothing goes online; only downloads and songs on the phone play. */
    val offlineMode: Boolean = false,
    /** Turning this off removes downloads and hides download buttons. */
    val offlineAccess: Boolean = true,

    // Media quality
    val wifiQuality: Quality = Quality.HIGH,
    val cellularQuality: Quality = Quality.AUTO,

    // Privacy and social
    /** Friends can see what you're playing. */
    val shareActivity: Boolean = true,

    // Notifications
    val notifyMessages: Boolean = true,
    val notifyFollows: Boolean = true,
    val notifyCollab: Boolean = true,

    // Connect
    /** While you host a Jam, phones with Spitify on the same Wi-Fi get an invite. */
    val autoInviteNearby: Boolean = false,

    /** The AI DJ talks between songs, like a radio host. */
    val djVoice: Boolean = true,

    /** Client id of Malcolm's Spotify developer app, for importing playlists. */
    val spotifyClientId: String = "",
    /** Spotify songs play inside the Spotify app when it's connected and the account has Premium. */
    val playThroughSpotify: Boolean = true,
    /** The looping visual behind the player. */
    val canvas: Boolean = true,
)

/** Playback and app preferences, kept in SharedPreferences as one JSON blob. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val next = _state.updateAndGet(block)
        prefs.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    private fun load(): AppSettings = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString<AppSettings>(it) }
    }.getOrNull() ?: AppSettings()

    private companion object {
        const val KEY = "app_settings"
    }
}
