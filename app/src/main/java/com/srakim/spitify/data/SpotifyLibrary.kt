package com.srakim.spitify.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Everything from your Spotify account that Spitify shows: Home shelves, Library, Liked Songs. */
data class SpotifyLibraryState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val liked: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val recent: List<Track> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val error: String? = null,
)

/**
 * Loads your Spotify account once you sign in and keeps it in memory. Each part loads on its
 * own, so one failing call (a missing permission, say) only leaves that shelf empty.
 */
class SpotifyLibrary(private val spotify: SpotifyImport) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SpotifyLibraryState())
    val state: StateFlow<SpotifyLibraryState> = _state
    private var lastLoad = 0L

    fun start() {
        scope.launch {
            spotify.signedIn.collect { on ->
                if (on) refresh(force = true) else _state.value = SpotifyLibraryState()
            }
        }
    }

    /** Reloads when signed in and the last load is more than a few minutes old. */
    fun refresh(force: Boolean = false) {
        if (!spotify.hasToken) return
        val now = System.currentTimeMillis()
        if (!force && now - lastLoad < 5 * 60_000) return
        lastLoad = now
        scope.launch {
            _state.value = _state.value.copy(signedIn = true, loading = true, error = null)
            val liked = async { runCatching { spotify.likedSongs() } }
            val playlists = async { runCatching { spotify.playlists() } }
            val albums = async { runCatching { spotify.savedAlbums() } }
            val artists = async { runCatching { spotify.followedArtists() } }
            val recent = async { runCatching { spotify.recentlyPlayed() } }
            val topTracks = async { runCatching { spotify.topTracks() } }
            val topArtists = async { runCatching { spotify.topArtists() } }
            val results = listOf(liked, playlists, albums, artists, recent, topTracks, topArtists).map { it.await() }
            val firstError = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            _state.value = SpotifyLibraryState(
                signedIn = spotify.hasToken,
                loading = false,
                liked = liked.await().getOrDefault(_state.value.liked),
                playlists = playlists.await().getOrDefault(_state.value.playlists),
                albums = albums.await().getOrDefault(_state.value.albums),
                artists = artists.await().getOrDefault(_state.value.artists),
                recent = recent.await().getOrDefault(_state.value.recent),
                topTracks = topTracks.await().getOrDefault(_state.value.topTracks),
                topArtists = topArtists.await().getOrDefault(_state.value.topArtists),
                error = if (results.all { it.isFailure }) firstError else null,
            )
        }
    }
}
