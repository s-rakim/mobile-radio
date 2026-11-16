package com.srakim.spitify.playback

import android.content.Context
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The real thing: drives the Spotify app through the App Remote SDK. */
class SpotifyRemoteImpl(context: Context) : SpotifyRemote {
    private val appContext = context.applicationContext
    private var remote: SpotifyAppRemote? = null
    private var playerSub: Subscription<PlayerState>? = null

    override val included = true

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    override fun isSpotifyInstalled(context: Context): Boolean = SpotifyAppRemote.isSpotifyInstalled(context)

    override fun connect(context: Context, clientId: String, interactive: Boolean) {
        if (clientId.isBlank()) {
            _state.update { it.copy(problem = "Add your Spotify app id in Settings first") }
            return
        }
        if (remote?.isConnected == true || _state.value.connecting) return
        if (!SpotifyAppRemote.isSpotifyInstalled(context)) {
            _state.update { it.copy(problem = "Install the Spotify app to play through Spotify") }
            return
        }
        _state.update { it.copy(connecting = true, problem = null) }
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(SpotifyRemote.REDIRECT_URI)
            .showAuthView(interactive)
            .build()
        SpotifyAppRemote.connect(if (interactive) context else appContext, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                remote = appRemote
                _state.update { it.copy(connected = true, connecting = false, problem = null) }
                appRemote.userApi.subscribeToCapabilities().setEventCallback { caps ->
                    _state.update { it.copy(canPlayOnDemand = caps.canPlayOnDemand) }
                }
                playerSub = appRemote.playerApi.subscribeToPlayerState().setEventCallback { ps -> onPlayerState(ps) }
            }

            override fun onFailure(error: Throwable) {
                remote = null
                _state.update { RemoteState(problem = explain(error)) }
            }
        })
    }

    private fun onPlayerState(ps: PlayerState) {
        val t = ps.track
        _state.update {
            it.copy(
                track = t?.let { track ->
                    RemoteTrack(
                        uri = track.uri,
                        title = track.name.orEmpty(),
                        artist = track.artist?.name.orEmpty(),
                        album = track.album?.name,
                        durationMs = track.duration,
                        imageUrl = imageUrl(track.imageUri?.raw),
                    )
                },
                paused = ps.isPaused,
                positionMs = ps.playbackPosition,
                positionAt = System.currentTimeMillis(),
            )
        }
    }

    override fun disconnect() {
        runCatching { playerSub?.cancel() }
        playerSub = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        _state.value = RemoteState()
    }

    override fun play(uri: String) { remote?.playerApi?.play(uri) }
    override fun queue(uri: String) { remote?.playerApi?.queue(uri) }
    override fun pause() { remote?.playerApi?.pause() }
    override fun resume() { remote?.playerApi?.resume() }
    override fun next() { remote?.playerApi?.skipNext() }
    override fun previous() { remote?.playerApi?.skipPrevious() }
    override fun seekTo(positionMs: Long) { remote?.playerApi?.seekTo(positionMs) }

    /** "spotify:image:ab67…" is the same id Spotify's image server uses. */
    private fun imageUrl(raw: String?): String? =
        raw?.substringAfterLast(':')?.takeIf { it.isNotBlank() }?.let { "https://i.scdn.co/image/$it" }

    private fun explain(error: Throwable): String = when (error.javaClass.simpleName) {
        "CouldNotFindSpotifyApp" -> "Install the Spotify app to play through Spotify"
        "NotLoggedInException", "LoggedOutException" -> "Log in to the Spotify app first"
        "UserNotAuthorizedException" -> "Spotify didn't allow Spitify. Check the app id and redirect URI on the Spotify dashboard."
        "AuthenticationFailedException" -> "Spotify sign-in failed. Try again."
        "UnsupportedFeatureVersionException" -> "Update the Spotify app"
        "OfflineModeException" -> "The Spotify app is in offline mode"
        else -> error.message ?: "Couldn't reach the Spotify app"
    }
}
