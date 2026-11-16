package com.srakim.spitify.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Used when the build didn't include Spotify's SDK. Everything plays from Spitify's own sources. */
class SpotifyRemoteImpl(@Suppress("UNUSED_PARAMETER") context: Context) : SpotifyRemote {
    override val included = false
    override val state: StateFlow<RemoteState> =
        MutableStateFlow(RemoteState(problem = "This version of Spitify was built without Spotify playback"))

    override fun isSpotifyInstalled(context: Context) =
        runCatching { context.packageManager.getPackageInfo("com.spotify.music", 0) }.isSuccess

    override fun connect(context: Context, clientId: String, interactive: Boolean) = Unit
    override fun disconnect() = Unit
    override fun play(uri: String) = Unit
    override fun queue(uri: String) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
}
