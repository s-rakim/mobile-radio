package com.srakim.spitify.playback

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** A song as the Spotify app reports it. */
data class RemoteTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val imageUrl: String?,
)

data class RemoteState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val track: RemoteTrack? = null,
    val paused: Boolean = true,
    val positionMs: Long = 0,
    /** When [positionMs] was read, so the bar can keep moving between updates. */
    val positionAt: Long = 0,
    /** Premium accounts can pick any song. Free accounts can't, so Spitify plays its own sources instead. */
    val canPlayOnDemand: Boolean = false,
    /** Why the last connect didn't work, in plain words. */
    val problem: String? = null,
    /** The Spotify app wants you to allow Spitify once, on its own screen. */
    val needsApproval: Boolean = false,
)

/**
 * Remote control for the Spotify app on this phone, through Spotify's App Remote SDK.
 * The music itself plays inside the Spotify app; Spitify picks the songs and shows them.
 *
 * Two builds of this exist: the real one in src/spotify (used when CI fetched the SDK) and a
 * stand-in in src/nospotify that always reports "not included", so the app builds either way.
 */
interface SpotifyRemote {
    /** False when this build was made without the SDK. */
    val included: Boolean
    val state: StateFlow<RemoteState>

    fun isSpotifyInstalled(context: Context): Boolean

    /**
     * Connects to the Spotify app. With [interactive] the Spotify app may show its own
     * "allow Spitify" screen, so pass an Activity for [context] then.
     */
    fun connect(context: Context, clientId: String, interactive: Boolean)
    fun disconnect()

    /** [onError] hears back, in plain words, when the Spotify app refuses the song. */
    fun play(uri: String, onError: (String) -> Unit = {})
    fun queue(uri: String)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)

    companion object {
        /** Must be listed under Redirect URIs on the Spotify developer dashboard. */
        const val REDIRECT_URI = "spitify://spotify-remote"
    }
}
