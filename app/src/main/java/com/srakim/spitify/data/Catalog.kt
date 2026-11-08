package com.srakim.spitify.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class SearchResults(val tracks: List<Track>, val artists: List<Artist>, val playlists: List<Playlist>) {
    val isEmpty get() = tracks.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    companion object {
        val Empty = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Every place music can come from, behind one front door. Search asks all of them at once and
 * merges what comes back; detail pages are routed to the right source by the id prefix.
 */
class Catalog(
    val audius: AudiusApi,
    val jamendo: JamendoApi,
    val archive: ArchiveApi,
    val ccMixter: CcMixterApi,
    val soundCloud: SoundCloudApi,
    val server: SubsonicApi,
    val local: LocalMusic,
    private val settings: SettingsStore,
) {

    suspend fun search(query: String): SearchResults = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope SearchResults.Empty

        val audiusTracks = async { attempt { audius.searchTracks(q) } }
        val audiusArtists = async { attempt { audius.searchArtists(q) } }
        val audiusPlaylists = async { attempt { audius.searchPlaylists(q) } }
        val serverHits = async { if (server.enabled) attempt { server.search(q) } else Result.success(SearchResults.Empty) }
        val jamendoTracks = async { optional(jamendo.enabled) { jamendo.searchTracks(q) } }
        val jamendoArtists = async { optional(jamendo.enabled) { jamendo.searchArtists(q) } }
        val archiveAlbums = async { optional(archive.enabled) { archive.searchAlbums(q) } }
        val archiveTracks = async { optional(archive.enabled) { archive.searchTracks(q) } }
        val ccTracks = async { optional(ccMixter.enabled) { ccMixter.searchTracks(q) } }
        val scTracks = async { optional(soundCloud.enabled) { soundCloud.searchTracks(q) } }
        val onDevice = if (settings.current.showLocalFiles) local.search(q) else emptyList()

        val audiusResults = listOf(audiusTracks.await(), audiusArtists.await(), audiusPlaylists.await())
        val mine = serverHits.await()
        val results = SearchResults(
            // Your own music first: songs on the phone, then your server. The open catalogues share the rest.
            tracks = onDevice + mine.getOrNull()?.tracks.orEmpty() + interleave(
                audiusTracks.await().getOrDefault(emptyList()),
                scTracks.await(),
                jamendoTracks.await(),
                ccTracks.await(),
                archiveTracks.await(),
            ),
            artists = mine.getOrNull()?.artists.orEmpty() + interleave(audiusArtists.await().getOrDefault(emptyList()), jamendoArtists.await()),
            playlists = mine.getOrNull()?.playlists.orEmpty() + interleave(audiusPlaylists.await().getOrDefault(emptyList()), archiveAlbums.await()),
        )
        // Only call it a failure when nothing at all came back and the main catalogue errored.
        if (results.isEmpty) audiusResults.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        results
    }

    suspend fun artist(id: String): Pair<Artist?, List<Track>> = coroutineScope {
        when {
            SubsonicApi.isServer(id) -> server.artist(id)
            JamendoApi.isJamendo(id) -> {
                val info = async { jamendo.artist(id) }
                val tracks = async { jamendo.artistTracks(id) }
                info.await() to tracks.await()
            }
            else -> {
                val info = async { audius.artist(id) }
                val tracks = async { audius.artistTracks(id) }
                info.await() to tracks.await()
            }
        }
    }

    suspend fun playlist(id: String): Pair<Playlist?, List<Track>> = coroutineScope {
        when {
            ArchiveApi.isArchive(id) -> archive.album(id)
            SubsonicApi.isServer(id) -> server.playlist(id)
            else -> {
                val info = async { audius.playlist(id) }
                val tracks = async { audius.playlistTracks(id) }
                info.await() to tracks.await()
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Extra sources never break search: if one is off or down it just adds nothing. */
    private suspend fun <T> optional(on: Boolean, block: suspend () -> List<T>): List<T> =
        if (!on) emptyList() else attempt(block).getOrDefault(emptyList())

    private fun <T> interleave(vararg lists: List<T>): List<T> {
        val out = ArrayList<T>(lists.sumOf { it.size })
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out
    }
}
