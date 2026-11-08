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
    val jamendoEnabled: Boolean = true,
    /** Blank means use the built-in id. */
    val jamendoClientId: String = "",
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
