package com.srakim.spitify.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Song radio: the seed song, then a stream of songs like it. Audius has no "similar tracks"
 * call, so the station is built from the same artist's catalogue and what's trending in the
 * song's genre, mixed so the artist turns up every few songs instead of all at once.
 */
object SongRadio {
    private const val SIZE = 50

    suspend fun build(seed: Track, catalog: Catalog, skip: Set<String>): List<Track> = coroutineScope {
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
}
