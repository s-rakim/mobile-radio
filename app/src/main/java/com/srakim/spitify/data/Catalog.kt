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
 * Audius plus the listener's own music, with the open catalogues and the Spitify server's extra sources searched separately. Search only ever waits on Audius: songs on the phone are
 * already in memory, and a home server is asked only if one is set up, with a short time limit
 * so a server that's out of reach can't hold results up.
 */
class Catalog(
    val audius: AudiusApi,
    val server: SubsonicApi,
    val web: SpitifyServerApi,
    val radio: RadioBrowserApi,
    val local: LocalMusic,
    private val settings: SettingsStore,
    val archive: ArchiveApi,
    val ccMixter: CcMixterApi,
    val jamendo: JamendoApi,
    val soundCloud: SoundCloudApi,
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

    /**
     * YouTube through the Spitify server. Kept apart from [search] because it can take a while,
     * so the screen shows everything else first and adds these when they arrive.
     */
    suspend fun searchYouTube(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty() || !web.enabled) return emptyList()
        return runCatching { web.youtube(q) }.getOrDefault(emptyList())
    }

    /** SoundCloud through the Spitify server. Separate for the same reason as YouTube. */
    suspend fun searchSoundCloud(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty() || !web.enabled) return emptyList()
        return runCatching { web.soundcloud(q) }.getOrDefault(emptyList())
    }

    /** Archive, ccMixter and Jamendo through the Spitify server. Quick, but still optional. */
    suspend fun searchOpenCatalogs(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty() || !web.enabled) return emptyList()
        return withTimeoutOrNull(OPEN_CATALOG_TIMEOUT_MS) { runCatching { web.openCatalogs(q) }.getOrNull() }.orEmpty()
    }

    /**
     * The open catalogues straight from the phone: Internet Archive, ccMixter, Jamendo and
     * SoundCloud. Each one is a separate call so the search screen can show results as each
     * source answers, and each gives up after a few seconds so a slow one never holds the rest.
     */
    suspend fun searchArchive(query: String): List<Track> = direct(archive.enabled, query) { archive.searchTracks(it) }
    suspend fun searchCcMixter(query: String): List<Track> = direct(ccMixter.enabled, query) { ccMixter.searchTracks(it) }
    suspend fun searchJamendo(query: String): List<Track> = direct(jamendo.enabled, query) { jamendo.searchTracks(it) }
    suspend fun searchSoundCloudDirect(query: String): List<Track> = direct(soundCloud.enabled, query) { soundCloud.searchTracks(it) }

    /** Archive albums and Jamendo artists, which go into the Playlists and Artists tabs. */
    suspend fun searchOpenExtras(query: String): SearchResults = coroutineScope {
        val albums = async { directList(archive.enabled, query) { archive.searchAlbums(it) } }
        val artists = async { directList(jamendo.enabled, query) { jamendo.searchArtists(it) } }
        SearchResults(emptyList(), artists.await(), albums.await())
    }

    private suspend fun direct(on: Boolean, query: String, block: suspend (String) -> List<Track>): List<Track> =
        directList(on, query, block)

    private suspend fun <T> directList(on: Boolean, query: String, block: suspend (String) -> List<T>): List<T> {
        val q = query.trim()
        if (!on || q.isEmpty() || settings.current.offlineMode) return emptyList()
        return withTimeoutOrNull(DIRECT_TIMEOUT_MS) { runCatching { block(q) }.getOrNull() }.orEmpty()
    }

    /** Live radio stations matching the search. Straight from Radio Browser, no server needed. */
    suspend fun searchRadio(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return withTimeoutOrNull(RADIO_TIMEOUT_MS) { runCatching { radio.search(q) }.getOrNull() }.orEmpty()
    }

    /** Set by the app once Spotify sign-in exists, so Spotify pages open from the same screens. */
    var spotify: SpotifyImport? = null

    suspend fun artist(id: String): Pair<Artist?, List<Track>> = coroutineScope {
        if (SpotifyImport.isSpotifyArtist(id)) {
            val page = spotify?.artistPage(id) ?: return@coroutineScope null to emptyList()
            return@coroutineScope page.first to page.second
        }
        if (SubsonicApi.isServer(id)) return@coroutineScope server.artist(id)
        if (JamendoApi.isJamendo(id)) {
            val info = async { jamendo.artist(id) }
            val tracks = async { jamendo.artistTracks(id) }
            return@coroutineScope info.await() to tracks.await()
        }
        val info = async { audius.artist(id) }
        val tracks = async { audius.artistTracks(id) }
        info.await() to tracks.await()
    }

    /** Albums and singles for the artist page's Discography. */
    suspend fun releases(id: String): List<Playlist> = when {
        SpotifyImport.isSpotifyArtist(id) -> spotify?.artistPage(id)?.third.orEmpty()
        SubsonicApi.isServer(id) || JamendoApi.isJamendo(id) -> emptyList()
        else -> runCatching { audius.artistAlbums(id) }.getOrDefault(emptyList())
    }

    /** "Fans also like". Spotify closed this to new apps, so it's Audius only. */
    suspend fun related(id: String): List<Artist> = when {
        SpotifyImport.isSpotifyArtist(id) || SubsonicApi.isServer(id) || JamendoApi.isJamendo(id) -> emptyList()
        else -> runCatching { audius.relatedArtists(id) }.getOrDefault(emptyList())
    }

    suspend fun playlist(id: String): Pair<Playlist?, List<Track>> = coroutineScope {
        if (SpotifyImport.isSpotifyCollection(id)) return@coroutineScope spotify?.collection(id) ?: (null to emptyList())
        if (SubsonicApi.isServer(id)) return@coroutineScope server.playlist(id)
        if (ArchiveApi.isArchive(id)) return@coroutineScope archive.album(id)
        val info = async { audius.playlist(id) }
        val tracks = async { audius.playlistTracks(id) }
        info.await() to tracks.await()
    }

    private companion object {
        const val SERVER_SEARCH_TIMEOUT_MS = 3_000L
        const val OPEN_CATALOG_TIMEOUT_MS = 8_000L
        const val RADIO_TIMEOUT_MS = 8_000L
        const val DIRECT_TIMEOUT_MS = 4_000L
    }
}
