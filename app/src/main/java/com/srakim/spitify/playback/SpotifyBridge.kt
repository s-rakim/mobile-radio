package com.srakim.spitify.playback

import com.srakim.spitify.data.SettingsStore
import com.srakim.spitify.data.Track

/**
 * Decides whether a Spotify song can go to the Spotify app, and finds a stand-in from
 * Spitify's own sources when it can't. Spotify only lets Premium accounts pick songs, so free
 * accounts, phones without the Spotify app and builds without the SDK all get the stand-in.
 */
class SpotifyBridge(
    val remote: SpotifyRemote,
    private val settings: SettingsStore,
    private val findStandIn: suspend (Track) -> Track?,
) {
    val ready: Boolean
        get() = settings.current.playThroughSpotify && remote.state.value.let { it.connected && it.canPlayOnDemand }

    /** One line for the toast when a Spotify song plays from somewhere else instead. */
    fun whyNot(): String {
        val s = remote.state.value
        return when {
            !remote.included -> "Playing the closest match from Spitify's sources"
            !settings.current.playThroughSpotify -> "Playing the closest match from Spitify's sources"
            s.connected && !s.canPlayOnDemand -> "Spotify needs Premium to pick songs, so this is the closest match from Spitify's sources"
            s.problem != null -> "${s.problem}. Playing the closest match for now."
            else -> "Not connected to Spotify, playing the closest match from Spitify's sources"
        }
    }

    suspend fun standIn(track: Track): Track? = findStandIn(track)
}
