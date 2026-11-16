package com.srakim.spitify.playback

import com.srakim.spitify.data.SettingsStore
import com.srakim.spitify.data.SpotifyImport.StandIn
import com.srakim.spitify.data.Track

/**
 * Decides whether a Spotify song can go to the Spotify app, and finds a stand-in from
 * Spitify's own sources when it can't. Spotify only lets Premium accounts pick songs, so free
 * accounts, phones without the Spotify app and builds without the SDK all get the stand-in,
 * played by Spitify's own player like any other song.
 */
class SpotifyBridge(
    val remote: SpotifyRemote,
    private val settings: SettingsStore,
    private val hasClientId: () -> Boolean,
    private val findStandIn: suspend (Track) -> StandIn?,
) {
    val ready: Boolean
        get() = settings.current.playThroughSpotify && remote.state.value.let { it.connected && it.canPlayOnDemand }

    private var explained: String? = null

    /**
     * Why the Spotify app isn't playing, said once per reason so the toast doesn't come back on
     * every song. Null when there's nothing worth saying: no Spotify set up, or it was turned off.
     */
    fun whyNotOnce(): String? {
        val s = remote.state.value
        val why = when {
            !remote.included || !settings.current.playThroughSpotify || !hasClientId() -> null
            s.connecting -> null
            s.connected && !s.canPlayOnDemand -> "Spotify only lets Premium accounts pick songs, so Spitify is playing them from its own sources"
            s.problem != null -> "Spotify app: ${s.problem.trimEnd('.')}. Playing through Spitify instead."
            else -> null
        } ?: return null
        if (why == explained) return null
        explained = why
        return why
    }

    suspend fun standIn(track: Track): StandIn? = findStandIn(track)
}
