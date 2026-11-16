package com.srakim.spitify.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
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

    private val main = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var lastClientId = ""

    override fun connect(context: Context, clientId: String, interactive: Boolean) {
        if (clientId.isBlank()) {
            _state.update { it.copy(problem = "Add your Spotify app id in Settings first") }
            return
        }
        if (remote?.isConnected == true) return
        // A quiet reconnect never gets in the way of you tapping Connect.
        if (_state.value.connecting && !interactive) return
        if (!SpotifyAppRemote.isSpotifyInstalled(context)) {
            _state.update { it.copy(problem = "Install the Spotify app to play through Spotify") }
            return
        }
        if (clientId != lastClientId || interactive) attempt = 0
        lastClientId = clientId
        _state.update { it.copy(connecting = true, problem = null) }
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(SpotifyRemote.REDIRECT_URI)
            .showAuthView(interactive)
            .build()
        // The Spotify app sometimes never answers (asleep, or stuck behind battery limits).
        // Don't wait on it for ever: give up after a while so the next try can happen.
        val stalled = Runnable {
            if (_state.value.connecting && remote == null) {
                _state.update { it.copy(connecting = false, problem = "The Spotify app didn't answer. Open Spotify once, then try again") }
            }
        }
        main.postDelayed(stalled, if (interactive) 90_000 else 15_000)
        SpotifyAppRemote.connect(if (interactive) context else appContext, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                main.removeCallbacks(stalled)
                attempt = 0
                remote = appRemote
                _state.update { it.copy(connected = true, connecting = false, problem = null, needsApproval = false) }
                appRemote.userApi.subscribeToCapabilities().setEventCallback { caps ->
                    _state.update { it.copy(canPlayOnDemand = caps.canPlayOnDemand) }
                }
                playerSub = appRemote.playerApi.subscribeToPlayerState().setEventCallback { ps -> onPlayerState(ps) }
            }

            override fun onFailure(error: Throwable) {
                main.removeCallbacks(stalled)
                remote = null
                val kind = error.javaClass.simpleName
                val approval = kind == "UserNotAuthorizedException" && error.text().contains("explicit", ignoreCase = true)
                _state.value = RemoteState(problem = explain(error), needsApproval = approval)
                // Hiccups (the Spotify app still waking up, a dropped link) get two more quiet tries.
                val passing = kind in TRANSIENT || kind.isBlank()
                if (!interactive && passing && attempt < RETRY_DELAYS.size) {
                    val wait = RETRY_DELAYS[attempt++]
                    main.postDelayed({ if (remote == null) connect(appContext, clientId, interactive = false) }, wait)
                }
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

    override fun play(uri: String, onError: (String) -> Unit) {
        val api = remote?.playerApi ?: return onError("not connected to the Spotify app")
        api.play(uri).setErrorCallback { e -> main.post { onError(explain(e).trimEnd('.')) } }
    }
    override fun queue(uri: String) { remote?.playerApi?.queue(uri) }
    override fun pause() { remote?.playerApi?.pause() }
    override fun resume() { remote?.playerApi?.resume() }
    override fun next() { remote?.playerApi?.skipNext() }
    override fun previous() { remote?.playerApi?.skipPrevious() }
    override fun seekTo(positionMs: Long) { remote?.playerApi?.seekTo(positionMs) }

    /** "spotify:image:ab67…" is the same id Spotify's image server uses. */
    private fun imageUrl(raw: String?): String? =
        raw?.substringAfterLast(':')?.takeIf { it.isNotBlank() }?.let { "https://i.scdn.co/image/$it" }

    /** The SDK's message, without the JSON it sometimes comes wrapped in. */
    private fun Throwable.text(): String {
        val raw = message.orEmpty()
        return Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: raw
    }

    /** What went wrong, in words that say what to do about it. */
    private fun explain(error: Throwable): String {
        val text = error.text()
        return when (error.javaClass.simpleName) {
            "CouldNotFindSpotifyApp" -> "Install the Spotify app to play through Spotify"
            "NotLoggedInException", "LoggedOutException" -> "Log in to the Spotify app first"
            "UserNotAuthorizedException" -> when {
                text.contains("explicit", true) ->
                    "Spotify needs you to allow Spitify once. Tap Connect to Spotify in Settings → Apps and devices"
                text.contains("redirect", true) ->
                    "Spotify doesn't know the redirect URI. Add ${SpotifyRemote.REDIRECT_URI} to your app on developer.spotify.com"
                text.contains("client", true) || text.contains("fingerprint", true) || text.contains("package", true) ->
                    "Spotify doesn't recognise this app. On developer.spotify.com check the app id, the Android package com.srakim.spitify and its SHA-1 ($text)"
                else -> "Spotify didn't allow Spitify" + if (text.isNotBlank()) " ($text)" else ""
            }
            "AuthenticationFailedException" -> "Spotify sign-in failed" + if (text.isNotBlank()) " ($text)" else ". Try again"
            "UnsupportedFeatureVersionException" -> "Update the Spotify app"
            "OfflineModeException" -> "The Spotify app is in offline mode"
            "SpotifyConnectionTerminatedException", "SpotifyDisconnectedException" -> "The Spotify app closed the connection"
            "SpotifyRemoteServiceException" -> "The Spotify app had a problem" + if (text.isNotBlank()) " ($text)" else ""
            else -> text.ifBlank { "Couldn't reach the Spotify app" }
        }
    }

    private companion object {
        val TRANSIENT = setOf("SpotifyConnectionTerminatedException", "SpotifyDisconnectedException", "SpotifyRemoteServiceException")
        val RETRY_DELAYS = longArrayOf(2_000, 6_000)
    }
}
