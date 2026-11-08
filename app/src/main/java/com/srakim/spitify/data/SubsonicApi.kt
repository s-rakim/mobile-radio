package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * "Your server": any Subsonic-compatible server such as Navidrome. The listener enters a URL,
 * username and password in Settings. Requests use token auth (md5 of password + a fresh salt),
 * so the password itself never goes over the wire.
 */
class SubsonicApi(private val client: OkHttpClient, private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    val enabled: Boolean
        get() = settings.current.let { it.subsonicEnabled && base(it.subsonicUrl) != null && it.subsonicUser.isNotBlank() }

    suspend fun ping(): Result<Unit> = runCatching {
        call("ping")
        Unit
    }

    suspend fun search(query: String): SearchResults {
        val r = call("search3", mapOf("query" to query, "songCount" to "20", "albumCount" to "10", "artistCount" to "10"))
            .obj("searchResult3") ?: return SearchResults.Empty
        return SearchResults(
            tracks = r.array("song").map { it.toTrack() },
            artists = r.array("artist").map { it.toArtist() },
            playlists = r.array("album").map { it.toAlbum() },
        )
    }

    suspend fun recentAlbums(): List<Playlist> =
        call("getAlbumList2", mapOf("type" to "newest", "size" to "30")).obj("albumList2")?.array("album").orEmpty().map { it.toAlbum() }

    suspend fun playlists(): List<Playlist> =
        call("getPlaylists").obj("playlists")?.array("playlist").orEmpty().map { pl ->
            Playlist(
                id = PLAYLIST_PREFIX + pl.text("id").orEmpty(),
                name = pl.text("name") ?: "Playlist",
                owner = pl.text("owner") ?: "Your server",
                artwork = pl.text("coverArt")?.let(::coverUrl),
                trackCount = pl.text("songCount")?.toIntOrNull() ?: 0,
                description = pl.text("comment"),
                label = "Playlist",
            )
        }

    suspend fun starred(): List<Track> = call("getStarred2").obj("starred2")?.array("song").orEmpty().map { it.toTrack() }

    suspend fun playlist(id: String): Pair<Playlist?, List<Track>> = when {
        id.startsWith(ALBUM_PREFIX) -> {
            val album = call("getAlbum", mapOf("id" to id.removePrefix(ALBUM_PREFIX))).obj("album")
            album?.toAlbum() to album?.array("song").orEmpty().map { it.toTrack() }
        }
        else -> {
            val pl = call("getPlaylist", mapOf("id" to id.removePrefix(PLAYLIST_PREFIX))).obj("playlist")
            val info = pl?.let {
                Playlist(
                    id = id,
                    name = it.text("name") ?: "Playlist",
                    owner = it.text("owner") ?: "Your server",
                    artwork = it.text("coverArt")?.let(::coverUrl),
                    trackCount = it.text("songCount")?.toIntOrNull() ?: 0,
                    description = it.text("comment"),
                    label = "Playlist",
                )
            }
            info to pl?.array("entry").orEmpty().map { it.toTrack() }
        }
    }

    suspend fun artist(id: String): Pair<Artist?, List<Track>> = coroutineScope {
        val a = call("getArtist", mapOf("id" to id.removePrefix(ARTIST_PREFIX))).obj("artist") ?: return@coroutineScope null to emptyList()
        val albums = a.array("album").take(6)
        val tracks = albums.map { album ->
            async { runCatching { playlist(ALBUM_PREFIX + album.text("id").orEmpty()).second }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
        a.toArtist().copy(trackCount = tracks.size) to tracks
    }

    fun coverUrl(coverId: String, size: Int = 500): String? =
        endpoint("getCoverArt", mapOf("id" to coverId, "size" to size.toString()))?.toString()

    private fun streamUrl(songId: String): String? = endpoint("stream", mapOf("id" to songId))?.toString()

    private fun JsonObject.toTrack(): Track {
        val songId = text("id").orEmpty()
        val cover = text("coverArt")?.let { coverUrl(it, 300) }
        return Track(
            id = TRACK_PREFIX + songId,
            title = text("title") ?: "Unknown song",
            artist = text("artist") ?: "Unknown artist",
            artistId = text("artistId")?.let { ARTIST_PREFIX + it }.orEmpty(),
            artwork = cover,
            artworkLarge = text("coverArt")?.let { coverUrl(it, 800) } ?: cover,
            durationSec = text("duration")?.toIntOrNull() ?: 0,
            genre = text("genre"),
            streamUrl = streamUrl(songId),
        )
    }

    private fun JsonObject.toAlbum() = Playlist(
        id = ALBUM_PREFIX + text("id").orEmpty(),
        name = text("name") ?: text("title") ?: "Album",
        owner = text("artist") ?: "Your server",
        artwork = text("coverArt")?.let(::coverUrl),
        trackCount = text("songCount")?.toIntOrNull() ?: 0,
        label = "Album",
    )

    private fun JsonObject.toArtist() = Artist(
        id = ARTIST_PREFIX + text("id").orEmpty(),
        name = text("name") ?: "Artist",
        picture = text("coverArt")?.let { coverUrl(it, 300) } ?: text("artistImageUrl"),
        pictureLarge = text("coverArt")?.let(::coverUrl) ?: text("artistImageUrl"),
        trackCount = text("albumCount")?.toIntOrNull() ?: 0,
        bio = "On your server",
    )

    private suspend fun call(method: String, params: Map<String, String> = emptyMap()): JsonObject = withContext(Dispatchers.IO) {
        val url = endpoint(method, params) ?: throw IOException("Add your server's address in Settings")
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Your server answered with ${response.code}")
            response.body?.string().orEmpty()
        }
        val root = (json.parseToJsonElement(body) as? JsonObject)?.obj("subsonic-response")
            ?: throw IOException("That doesn't look like a Subsonic or Navidrome server")
        if (root.text("status") != "ok") {
            throw IOException(root.obj("error")?.text("message") ?: "Your server refused the request")
        }
        root
    }

    private fun endpoint(method: String, params: Map<String, String>): HttpUrl? {
        val s = settings.current
        val base = base(s.subsonicUrl) ?: return null
        val salt = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        return base.newBuilder().apply {
            addPathSegment("rest")
            addPathSegment("$method.view")
            addQueryParameter("u", s.subsonicUser.trim())
            addQueryParameter("t", md5(s.subsonicPassword + salt))
            addQueryParameter("s", salt)
            addQueryParameter("v", API_VERSION)
            addQueryParameter("c", "spitify")
            addQueryParameter("f", "json")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
    }

    companion object {
        const val ID_PREFIX = "ss:"
        private const val TRACK_PREFIX = ID_PREFIX
        const val ALBUM_PREFIX = "ss:album:"
        const val PLAYLIST_PREFIX = "ss:pl:"
        const val ARTIST_PREFIX = "ss:artist:"
        private const val API_VERSION = "1.16.1"

        fun isServer(id: String) = id.startsWith(ID_PREFIX)

        /** Accepts "192.168.1.20:4533" as well as a full URL, and drops a trailing slash. */
        fun base(raw: String): HttpUrl? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()
        }

        private fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
