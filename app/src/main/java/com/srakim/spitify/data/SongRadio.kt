package com.srakim.spitify.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Song radio: the seed song, then a stream of songs like it. Audius has no "similar tracks"
 * call, so the station is built from the same artist's catalogue and what's trending in the
 * song's genre, mixed so the artist turns up every few songs instead of all at once.
 */
object SongRadio {
    private const val SIZE = 50

    suspend fun build(seed: Track, catalog: Catalog, skip: Set<String>): List<Track> = coroutineScope {
        val sp = catalog.spotify
        if (SpotifyImport.isSpotify(seed.id) && sp != null && sp.hasToken) {
            spotifyStation(seed, sp, skip).takeIf { it.size > 1 }?.let { return@coroutineScope it }
        }
        val fromArtist = async {
            runCatching {
                when {
                    seed.artistId.isNotBlank() -> catalog.artist(seed.artistId).second
                    seed.artistSearchName.isNotBlank() -> catalog.audius.searchTracks(seed.artistSearchName)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
        }
        val fromGenre = async {
            runCatching {
                val genre = seed.genre?.takeIf { it.isNotBlank() }
                if (genre != null) catalog.audius.trendingTracks(genre = genre, time = "month", limit = 60)
                else catalog.audius.trendingTracks(limit = 60)
            }.getOrDefault(emptyList())
        }

        val keep = { t: Track -> t.id != seed.id && t.id !in skip }
        val artist = fromArtist.await().filter(keep).take(20).shuffled().take(12).toMutableList()
        val genre = fromGenre.await().filter(keep).shuffled().toMutableList()

        val out = mutableListOf(seed)
        val seen = hashSetOf(seed.id)
        var turn = 0
        while (out.size < SIZE && (artist.isNotEmpty() || genre.isNotEmpty())) {
            // Two from the genre, then one from the artist.
            val pool = if (turn++ % 3 == 2 && artist.isNotEmpty() || genre.isEmpty()) artist else genre
            val next = pool.removeAt(0)
            if (seen.add(next.id)) out += next
        }
        out
    }

    /**
     * Radio for a Spotify song. Spotify closed its recommendations to apps like this one, so the
     * station is built the way its radio feels: the artist's best known songs, mixed with songs
     * by the people they record with, in turn. Every song is a real Spotify song, so it plays
     * from your server like any other (and is skipped if your server doesn't have it). Nothing
     * from Audius' charts gets mixed in.
     */
    private suspend fun spotifyStation(seed: Track, sp: SpotifyImport, skip: Set<String>): List<Track> = coroutineScope {
        val main = SongMatch.firstArtist(seed.artist)
        val page = if (SpotifyImport.isSpotifyArtist(seed.artistId)) runCatching { sp.meta.page(seed.artistId) }.getOrNull() else null
        val own = page?.top?.takeIf { it.isNotEmpty() } ?: runCatching { sp.meta.songsBy(main) }.getOrDefault(emptyList())
        // Whoever turns up on the seed and on the artist's big songs.
        val others = (listOf(seed) + own).flatMap { it.artist.split(", ") }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(main, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(OTHER_ARTISTS)
        val theirs = others.map { name -> async { runCatching { sp.meta.songsBy(name) }.getOrDefault(emptyList()).shuffled().take(5) } }
            .awaitAll()
        val keep = { t: Track -> t.id != seed.id && t.id !in skip }
        val artistPool = own.filter(keep).shuffled().toMutableList()
        val otherPool = interleave(theirs).filter(keep).toMutableList()
        val out = mutableListOf(seed)
        val seen = hashSetOf(seed.id, SongMatch.clean(seed.title))
        var turn = 0
        while (out.size < SIZE && (artistPool.isNotEmpty() || otherPool.isNotEmpty())) {
            val pool = if ((turn++ % 2 == 0 && artistPool.isNotEmpty()) || otherPool.isEmpty()) artistPool else otherPool
            val next = pool.removeAt(0)
            if (seen.add(next.id) && seen.add(SongMatch.clean(next.title))) out += next
        }
        out
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        var i = 0
        while (lists.any { i < it.size }) {
            lists.forEach { if (i < it.size) out += it[i] }
            i++
        }
        return out
    }

    private const val OTHER_ARTISTS = 5
}
