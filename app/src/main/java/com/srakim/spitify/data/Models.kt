package com.srakim.spitify.data

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val artistImage: String? = null,
    val artwork: String? = null,
    val artworkLarge: String? = null,
    val durationSec: Int = 0,
    val genre: String? = null,
    val link: String? = null,
    /** Direct audio address for sources that hand one out. Audius builds it from the id. */
    val streamUrl: String? = null,
    /**
     * Audius' own direct link to the file on a storage node. Starting from it skips the API's
     * redirect, which roughly halves the wait before a song starts. It can go stale, so the
     * player falls back to the regular stream address if it fails.
     */
    val directUrl: String? = null,
    /** Where a song from the Spitify server came from, e.g. "YouTube" or "Jamendo". */
    val origin: String? = null,
    /** Album name, when the source knows it. */
    val album: String? = null,
    /** Id that [com.srakim.spitify.data.Catalog.playlist] can open, when the album has a page. */
    val albumId: String? = null,
) {
    val source: String
        get() = when {
            id.startsWith(LocalMusic.ID_PREFIX) -> "This device"
            id.startsWith(SubsonicApi.ID_PREFIX) -> "Your server"
            id.startsWith(SpitifyServerApi.ID_PREFIX) -> origin ?: "Spitify server"
            else -> "Audius"
        }

    val isLocal: Boolean get() = id.startsWith(LocalMusic.ID_PREFIX)

    /**
     * The name to look up when there's no artist page, e.g. a YouTube channel.
     * Drops the channel noise YouTube adds ("Sauti Sol - Topic", "SautiSolVEVO").
     */
    val artistSearchName: String
        get() = artist
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*(VEVO|Official)$", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeUnless { it.isBlank() || it.equals("Unknown artist", true) || it == "<unknown>" }
            .orEmpty()

    /** Whether tapping the artist's name can go anywhere. */
    val hasArtistLink: Boolean get() = artistId.isNotBlank() || artistSearchName.isNotBlank()

    /** Plain Audius songs, the only ones another phone can look up by id. */
    val isAudius: Boolean get() = source == "Audius"

    /** Songs already on the phone don't need saving. */
    val canDownload: Boolean get() = !isLocal
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val owner: String,
    val ownerImage: String? = null,
    val artwork: String? = null,
    val trackCount: Int = 0,
    val description: String? = null,
    /** "Playlist", "Album" or "Live album". */
    val label: String = "Playlist",
    val link: String? = null,
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val handle: String = "",
    val picture: String? = null,
    val pictureLarge: String? = null,
    val cover: String? = null,
    val followers: Int = 0,
    val trackCount: Int = 0,
    val bio: String? = null,
    val verified: Boolean = false,
)

/** A playlist the listener made on this device. */
@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Something the listener opened recently, used for the home shortcuts and library sort. */
@Serializable
data class RecentItem(
    val kind: Kind,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val image: String? = null,
    val openedAt: Long = 0,
) {
    @Serializable
    enum class Kind { PLAYLIST, MY_PLAYLIST, LIKED, ARTIST, MIX }
}

/** Where the current queue came from, shown as "Playing from ..." in the player. */
data class PlayContext(
    val id: String,
    val title: String,
    val label: String,
)

object Genres {
    data class Genre(val name: String, val color: Long)

    // Tile colours come from the ui-ux-pro-max product palettes (Creative Agency, Dating, Productivity,
    // Sports, Recipe, Music Streaming and friends). Saturated enough to feel alive, dark enough for white text.
    val all = listOf(
        Genre("Hip-Hop/Rap", 0xFFEA580C),
        Genre("Electronic", 0xFF0891B2),
        Genre("Pop", 0xFFDB2777),
        Genre("R&B/Soul", 0xFF881337),
        Genre("Lo-Fi", 0xFF0D9488),
        Genre("Alternative", 0xFF0369A1),
        Genre("House", 0xFF86198F),
        Genre("Rock", 0xFFDC2626),
        Genre("Ambient", 0xFF059669),
        Genre("Jazz", 0xFFA16207),
        Genre("Latin", 0xFFC2410C),
        Genre("Techno", 0xFF4338CA),
        Genre("Dubstep", 0xFF7C3AED),
        Genre("Trap", 0xFFE11D48),
        Genre("Acoustic", 0xFF9A3412),
        Genre("Reggae", 0xFF16A34A),
        Genre("Soundtrack", 0xFF1E3A8A),
        Genre("Experimental", 0xFF164E63),
        Genre("Funk", 0xFFD97706),
        Genre("Classical", 0xFF831843),
    )

    fun colorFor(name: String): Long = all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.color ?: 0xFF535353
}
