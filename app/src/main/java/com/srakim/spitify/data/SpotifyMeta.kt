package com.srakim.spitify.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Spotify's catalogue info for Spitify's own pages: artist pages, song details, albums.
 *
 * Since February 2026 Spotify gives apps in development mode a much smaller Web API:
 *  - no `/artists/{id}/top-tracks`, no related artists, no new releases or browse categories
 *  - no follower counts or popularity on artists, albums or tracks, no record label on albums
 *  - search returns at most 10 results a call
 * So every part of an artist page has a second way in. Popular songs come from a search for
 * the artist's own songs, releases from their discography call (or album search), the bio from
 * Wikipedia. When one route is closed the next one fills the gap, and the page never comes up empty.
 */
class SpotifyMeta(private val spotify: SpotifyImport, private val client: OkHttpClient) {

    data class ArtistPage(
        val artist: Artist?,
        val top: List<Track>,
        /** Their own albums, singles and compilations, newest first, then "Appears on". */
        val releases: List<Playlist>,
        /** Spotify ids of people they've made songs with, for the "Works with" row. */
        val collaborators: List<String>,
        val loadedAt: Long = System.currentTimeMillis(),
    )

    /** What Spotify knows about one song. */
    data class SongInfo(
        val uri: String,
        val title: String,
        val artists: List<Pair<String, String>>,
        val album: String?,
        val albumUri: String?,
        val albumType: String?,
        val released: String?,
        val trackNumber: Int,
        val albumTracks: Int,
        val disc: Int,
        val explicit: Boolean,
        val isrc: String?,
        val durationMs: Long,
        val genres: List<String>,
        val link: String?,
        val artwork: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val pages = ConcurrentHashMap<String, ArtistPage>()
    private val known = ConcurrentHashMap<String, Artist>()
    private val bios = ConcurrentHashMap<String, String>()
    private val songs = ConcurrentHashMap<String, SongInfo>()

    /** Artists seen in search results, so a page can show the name and photo straight away. */
    fun remember(artists: List<Artist>) {
        artists.forEach { if (SpotifyImport.isSpotifyArtist(it.id)) known.putIfAbsent(it.id, it) }
    }

    fun cached(uri: String): Artist? = pages[uri]?.artist ?: known[uri]

    // ---------- Artist pages ----------

    /**
     * The artist screen asks for the page three times at once (songs, releases, "Works with").
     * They share one load instead of each firing its own twenty-odd calls at Spotify, which is
     * what tipped development-mode apps into rate limits and left pages empty.
     */
    private val loading = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ArtistPage>>()
    private val loadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    suspend fun page(uri: String): ArtistPage {
        pages[uri]?.takeIf { System.currentTimeMillis() - it.loadedAt < PAGE_TTL_MS }?.let { return it }
        val job = loading.computeIfAbsent(uri) { loadScope.async { loadPage(uri) } }
        return try {
            job.await()
        } finally {
            if (job.isCompleted) loading.remove(uri, job)
        }
    }

    private suspend fun loadPage(uri: String): ArtistPage {
        val page = load(uri)
        if (page.artist == null && page.top.isEmpty() && page.releases.isEmpty()) {
            // Nothing came back at all, which means no connection rather than a closed door.
            // The screen tries again by itself.
            throw IOException("Spotify didn't answer")
        }
        // Only a complete page is kept for half an hour. One missing its songs or releases (a
        // rate-limited moment, say) is shown, then asked for again on the next visit.
        if (page.top.isNotEmpty() && page.releases.isNotEmpty()) pages[uri] = page
        page.artist?.let { known[uri] = it }
        return page
    }

    private suspend fun load(uri: String): ArtistPage = coroutineScope {
        val id = uri.substringAfterLast(':')
        val infoJob = async(Dispatchers.IO) { retry { spotify.toArtist(spotify.api("$API/artists/$id").jsonObject) } }
        val releasesJob = async(Dispatchers.IO) { discography(uri) }
        val fetched = infoJob.await() ?: known[uri]
        val discography = releasesJob.await()
        val name = fetched?.name ?: discography.firstOrNull { it.ownerId == uri }?.owner
        val releases = if (discography.isEmpty() && name != null) albumSearch(uri, name) else discography
        val bioJob = async(Dispatchers.IO) { name?.let { bio(it) } }
        val (top, withSearch) = popular(uri, name)
        val songs = top.ifEmpty { songsFromLatest(releases) }
        val base = fetched ?: name?.let { Artist(id = uri, name = it, picture = releases.firstOrNull()?.artwork) }
        val info = base?.copy(
            bio = base.bio ?: withTimeoutOrNull(BIO_TIMEOUT_MS) { bioJob.await() },
            trackCount = songs.size,
        )
        val collaborators = (withSearch + releases.filter { it.label == APPEARS_ON }.map { it.ownerId })
            .filter { it.isNotBlank() && it != uri }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
        ArtistPage(info, songs, releases, collaborators)
    }

    /**
     * The artist's most played songs. The proper call is closed to development-mode apps, so when
     * it fails this searches Spotify for `artist:"Name"`, keeps only songs they're credited on,
     * and keeps Spotify's own order, which ranks the big songs first. Also returns the other
     * artists on those songs.
     */
    private suspend fun popular(uri: String, name: String?): Pair<List<Track>, List<String>> = coroutineScope {
        val id = uri.substringAfterLast(':')
        val direct = withContext(Dispatchers.IO) {
            runCatching {
                spotify.api("$API/artists/$id/top-tracks?market=from_token").jsonObject["tracks"]?.jsonArray.orEmpty()
                    .mapNotNull { it as? JsonObject }
            }.getOrDefault(emptyList())
        }
        val raw = direct.ifEmpty {
            if (name == null) return@coroutineScope emptyList<Track>() to emptyList<String>()
            val q = Uri.encode("artist:\"$name\"")
            (0 until SEARCH_PAGES).map { page ->
                async(Dispatchers.IO) {
                    retry {
                        spotify.api("$API/search?type=track&limit=10&offset=${page * 10}&q=$q").jsonObject["tracks"]
                            ?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                    }.orEmpty()
                }
            }.awaitAll().flatten().filter { o -> artistUris(o).contains(uri) }
        }
        val seen = HashSet<String>()
        val tracks = raw.mapNotNull { o -> spotify.toTrack(o)?.let { o to it } }
            // One copy of each song: the deluxe, clean and remastered versions all come back too.
            .filter { (_, t) -> seen.add(core(t.title)) }
            .take(TOP_SIZE)
        tracks.map { it.second } to tracks.flatMap { artistUris(it.first) }
    }

    private fun artistUris(o: JsonObject): List<String> =
        (o["artists"] as? JsonArray)?.mapNotNull { a -> ((a as? JsonObject)?.get("uri") as? JsonPrimitive)?.contentOrNull }.orEmpty()

    /** Albums, singles, compilations and "Appears on", paged 10 at a time. */
    private fun discography(uri: String): List<Playlist> {
        val id = uri.substringAfterLast(':')
        val own = mutableListOf<Pair<String, Playlist>>()
        val guest = mutableListOf<Playlist>()
        var url: String? = "$API/artists/$id/albums?include_groups=album,single,compilation,appears_on&limit=10"
        var pages = 0
        while (url != null && pages < DISCOGRAPHY_PAGES) {
            val next = url
            val page = retry { spotify.api(next).jsonObject } ?: break
            page["items"]?.jsonArray?.forEach { item ->
                val o = item as? JsonObject ?: return@forEach
                val album = spotify.toAlbum(o) ?: return@forEach
                val date = (o["release_date"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (artistUris(o).contains(uri)) own += date to album
                else guest += album.copy(label = APPEARS_ON)
            }
            url = (page["next"] as? JsonPrimitive)?.contentOrNull
            pages++
        }
        val seen = HashSet<String>()
        // Spotify lists the same record several times (explicit, clean, regional), keep one.
        return own.sortedByDescending { it.first }.map { it.second }.filter { seen.add(core(it.name) + it.label) } +
            guest.filter { seen.add(core(it.name) + APPEARS_ON) }
    }

    private suspend fun albumSearch(uri: String, name: String): List<Playlist> = coroutineScope {
        val q = Uri.encode("artist:\"$name\"")
        (0 until 2).map { page ->
            async(Dispatchers.IO) {
                retry {
                    spotify.api("$API/search?type=album&limit=10&offset=${page * 10}&q=$q").jsonObject["albums"]
                        ?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                }.orEmpty()
            }
        }.awaitAll().flatten()
            .filter { artistUris(it).contains(uri) }
            .sortedByDescending { (it["release_date"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
            .mapNotNull(spotify::toAlbum)
            .distinctBy { core(it.name) + it.label }
    }

    /** Last resort for Popular: the songs on their newest album or single. */
    private suspend fun songsFromLatest(releases: List<Playlist>): List<Track> {
        val latest = releases.firstOrNull { it.label != APPEARS_ON } ?: return emptyList()
        return runCatching { spotify.collection(latest.id).second.take(TOP_SIZE) }.getOrDefault(emptyList())
    }

    /** "Works with": artists they've made songs with, each with their photo. */
    suspend fun collaborators(uri: String): List<Artist> = coroutineScope {
        val page = runCatching { page(uri) }.getOrNull() ?: return@coroutineScope emptyList()
        page.collaborators.take(MAX_COLLABORATORS).map { other ->
            async(Dispatchers.IO) {
                known[other] ?: retry { spotify.toArtist(spotify.api("$API/artists/${other.substringAfterLast(':')}").jsonObject) }
                    ?.also { known[other] = it }
            }
        }.awaitAll().filterNotNull()
    }

    /** The Spotify artist with exactly this name, for opening their page from another source. */
    suspend fun findArtist(name: String): Artist? = withContext(Dispatchers.IO) {
        val wanted = core(name)
        if (wanted.isEmpty()) return@withContext null
        known.values.firstOrNull { core(it.name) == wanted }?.let { return@withContext it }
        val found = retry {
            spotify.api("$API/search?type=artist&limit=10&q=" + Uri.encode(name.trim())).jsonObject["artists"]
                ?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.let(spotify::toArtist) }
        }.orEmpty()
        remember(found)
        found.firstOrNull { core(it.name) == wanted }
    }

    // ---------- Lyric search ----------

    /**
     * Songs found by their lyrics, as Spotify knows them: the original artist and cover, and a
     * Spotify id that plays (from your own server first). Lyric sites are full of karaoke and
     * cover uploads, so a title match on Spotify, which ranks the real song first, sorts that
     * out. Genius results already name the right artist, so those must match it.
     */
    suspend fun canonical(matches: List<LyricMatch>): List<LyricMatch> = coroutineScope {
        if (!spotify.hasToken || matches.isEmpty()) return@coroutineScope matches
        val wanted = matches.filter { it.track == null }.map { core(it.title) }.filter { it.isNotBlank() }.distinct().take(CANON_LOOKUPS)
        val found = wanted.map { title ->
            async(Dispatchers.IO) {
                title to retry {
                    spotify.api("$API/search?type=track&limit=10&q=" + Uri.encode("track:\"$title\"")).jsonObject["tracks"]
                        ?.jsonObject?.get("items")?.jsonArray.orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(spotify::toTrack) }
                        .filter { core(it.title) == title }
                }.orEmpty()
            }
        }.awaitAll().toMap()
        val seen = HashSet<String>()
        matches.mapNotNull { m ->
            val options = if (m.track == null) found[core(m.title)].orEmpty() else emptyList()
            val pick = if (m.source == "Genius") {
                val artist = core(m.artist.split(",", "&", " feat", " ft.").first())
                options.firstOrNull { core(it.artist).contains(artist) }
            } else {
                options.firstOrNull()
            }
            val out = if (pick != null) m.copy(title = pick.title, artist = pick.artist, artwork = pick.artwork, track = pick, source = "Spotify") else m
            out.takeIf { seen.add(core(it.title) + "|" + core(it.artist)) }
        }
    }

    // ---------- Bio ----------

    /**
     * Spotify's API has no artist bio, so the About card uses the opening of the artist's
     * Wikipedia article, when there's one about a musician with that name.
     */
    private fun bio(name: String): String? {
        bios[name]?.let { return it.ifEmpty { null } }
        val titles = listOf(name, "$name (singer)", "$name (musician)", "$name (rapper)", "$name (band)")
        for (title in titles) {
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
            val o = runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                    if (r.isSuccessful) json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject else null
                }
            }.getOrNull() ?: continue
            val type = (o["type"] as? JsonPrimitive)?.contentOrNull
            val about = (o["description"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val extract = (o["extract"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (type == "standard" && MUSIC.containsMatchIn(about + " " + extract.take(300))) {
                bios[name] = extract
                return extract
            }
        }
        bios[name] = ""
        return null
    }

    // ---------- Song details ----------

    /**
     * Spotify's details for [track]. Spotify songs are looked up by id; songs from anywhere else
     * (your server, Audius, this phone) are found on Spotify by title and artist first.
     */
    suspend fun songInfo(track: Track): SongInfo? = withContext(Dispatchers.IO) {
        if (!spotify.hasToken) return@withContext null
        songs[track.id]?.let { return@withContext it }
        val o: JsonObject = if (SpotifyImport.isSpotify(track.id)) {
            retry { spotify.api("$API/tracks/${track.id.substringAfterLast(':')}").jsonObject }
        } else {
            findSong(track)
        } ?: return@withContext null
        val album = o["album"] as? JsonObject
        val artists = (o["artists"] as? JsonArray)?.mapNotNull { a ->
            val ao = a as? JsonObject ?: return@mapNotNull null
            val n = (ao["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            n to (ao["uri"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.orEmpty()
        val genres = artists.firstOrNull()?.second?.takeIf { it.isNotBlank() }?.let { a ->
            (pages[a]?.artist ?: known[a])?.genres?.takeIf { it.isNotEmpty() }
                ?: retry { spotify.toArtist(spotify.api("$API/artists/${a.substringAfterLast(':')}").jsonObject) }?.also { known[a] = it }?.genres
        }.orEmpty()
        fun JsonObject?.s(key: String) = (this?.get(key) as? JsonPrimitive)?.contentOrNull
        val images = (album?.get("images") as? JsonArray)?.mapNotNull { (it as? JsonObject).s("url") }.orEmpty()
        SongInfo(
            uri = o.s("uri").orEmpty(),
            title = o.s("name").orEmpty(),
            artists = artists,
            album = album.s("name"),
            albumUri = album.s("uri"),
            albumType = album.s("album_type"),
            released = album.s("release_date"),
            trackNumber = o.s("track_number")?.toIntOrNull() ?: 0,
            albumTracks = album.s("total_tracks")?.toIntOrNull() ?: 0,
            disc = o.s("disc_number")?.toIntOrNull() ?: 1,
            explicit = o.s("explicit") == "true",
            isrc = (o["external_ids"] as? JsonObject).s("isrc"),
            durationMs = o.s("duration_ms")?.toLongOrNull() ?: 0,
            genres = genres,
            link = (o["external_urls"] as? JsonObject).s("spotify"),
            artwork = images.firstOrNull(),
        ).also { songs[track.id] = it }
    }

    private fun findSong(track: Track): JsonObject? {
        val artist = track.artistSearchName.split(",", "&", " feat", " ft.").first().trim()
        val title = track.title.substringBefore(" (").substringBefore(" - ").trim()
        if (title.isBlank()) return null
        val q = Uri.encode(if (artist.isBlank()) "track:\"$title\"" else "track:\"$title\" artist:\"$artist\"")
        val items = retry {
            spotify.api("$API/search?type=track&limit=5&q=$q").jsonObject["tracks"]?.jsonObject?.get("items")?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
        }.orEmpty()
        return items.firstOrNull { core((it["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()) == core(title) } ?: items.firstOrNull()
    }

    // ---------- Helpers ----------

    /** Tries a call up to three times. A dropped connection shouldn't blank a page. */
    private fun <T> retry(block: () -> T): T? {
        repeat(3) { attempt ->
            val r = runCatching(block)
            r.getOrNull()?.let { return it }
            val m = r.exceptionOrNull()?.message.orEmpty()
            // A closed endpoint answers the same every time, and a rate limit only gets worse
            // with more calls: no point asking again straight away.
            if (m.contains("403") || m.contains("404") || m.contains("Sign in") || m.contains("rate limiting")) return null
            if (attempt < 2) Thread.sleep(400L * (attempt + 1))
        }
        return null
    }

    private fun core(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex(" - .*"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val APPEARS_ON = "Appears on"
        private const val API = "https://api.spotify.com/v1"
        private const val PAGE_TTL_MS = 30 * 60_000L
        private const val BIO_TIMEOUT_MS = 4_000L
        private const val SEARCH_PAGES = 3
        private const val DISCOGRAPHY_PAGES = 6
        private const val TOP_SIZE = 10
        private const val MAX_COLLABORATORS = 10
        private const val CANON_LOOKUPS = 6
        private const val USER_AGENT = "Spitify (https://github.com/s-rakim/mobile-radio)"
        private val MUSIC = Regex(
            "singer|rapper|musician|band|songwriter|record producer|\\bdj\\b|disc jockey|music group|duo|trio|composer|vocalist|boy band|girl group|hip hop|recording artist",
            RegexOption.IGNORE_CASE,
        )
    }
}
