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
) {
    val source: String
        get() = when {
            id.startsWith(LocalMusic.ID_PREFIX) -> "This device"
            id.startsWith(SubsonicApi.ID_PREFIX) -> "Your server"
            else -> "Audius"
        }

    val isLocal: Boolean get() = id.startsWith(LocalMusic.ID_PREFIX)

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

    // Hand-mixed tile colours. Deliberately earthy and slightly desaturated so artwork stays the hero.
    val all = listOf(
        Genre("Hip-Hop/Rap", 0xFFB5651D),
        Genre("Electronic", 0xFF2D5D9F),
        Genre("Pop", 0xFFC93F6E),
        Genre("R&B/Soul", 0xFF8E3B5E),
        Genre("Lo-Fi", 0xFF5F7352),
        Genre("Alternative", 0xFF4B6E7A),
        Genre("House", 0xFF6A4E9A),
        Genre("Rock", 0xFF9C2F2F),
        Genre("Ambient", 0xFF3E7C6E),
        Genre("Jazz", 0xFFA6782C),
        Genre("Latin", 0xFFC4502E),
        Genre("Techno", 0xFF3A3F6B),
        Genre("Dubstep", 0xFF5E3A7A),
        Genre("Trap", 0xFF7D2E46),
        Genre("Acoustic", 0xFF7A5C44),
        Genre("Reggae", 0xFF4E8A3A),
        Genre("Soundtrack", 0xFF5A6470),
        Genre("Experimental", 0xFF2F6F8F),
        Genre("Funk", 0xFFC0612B),
        Genre("Classical", 0xFF6E5A7E),
    )

    fun colorFor(name: String): Long = all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.color ?: 0xFF535353
}
