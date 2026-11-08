package com.srakim.spitify.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

data class SearchResults(val tracks: List<Track>, val artists: List<Artist>, val playlists: List<Playlist>) {
    val isEmpty get() = tracks.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    companion object {
        val Empty = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Audius plus the listener's own music. Search only ever waits on Audius: songs on the phone are
 * already in memory, and a home server is asked only if one is set up, with a short time limit
 * so a server that's out of reach can't hold results up.
 */
class Catalog(
    val audius: AudiusApi,
    val server: SubsonicApi,
    val local: LocalMusic,
    private val settings: SettingsStore,
) {

    suspend fun search(query: String): SearchResults = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope SearchResults.Empty

        val tracks = async { audius.searchTracks(q) }
        val artists = async { audius.searchArtists(q) }
        val playlists = async { audius.searchPlaylists(q) }
        val mine = async {
            if (!server.enabled) null
            else withTimeoutOrNull(SERVER_SEARCH_TIMEOUT_MS) { runCatching { server.search(q) }.getOrNull() }
        }
        val onDevice = if (settings.current.showLocalFiles) local.search(q) else emptyList()

        val own = mine.await()
        SearchResults(
            tracks = onDevice + own?.tracks.orEmpty() + tracks.await(),
            artists = own?.artists.orEmpty() + artists.await(),
            playlists = own?.playlists.orEmpty() + playlists.await(),
        )
    }

    suspend fun artist(id: String): Pair<Artist?, List<Track>> = coroutineScope {
        if (SubsonicApi.isServer(id)) return@coroutineScope server.artist(id)
        val info = async { audius.artist(id) }
        val tracks = async { audius.artistTracks(id) }
        info.await() to tracks.await()
    }

    suspend fun playlist(id: String): Pair<Playlist?, List<Track>> = coroutineScope {
        if (SubsonicApi.isServer(id)) return@coroutineScope server.playlist(id)
        val info = async { audius.playlist(id) }
        val tracks = async { audius.playlistTracks(id) }
        info.await() to tracks.await()
    }

    private companion object {
        const val SERVER_SEARCH_TIMEOUT_MS = 3_000L
    }
}
