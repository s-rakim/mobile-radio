package com.srakim.spitify.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer

/**
 * Brings playlists over from Spotify. Three ways in:
 *  - Sign in with Spotify (PKCE, so there's no secret in the app). Needs a Spotify developer
 *    app's client id, set at build time or typed in Settings.
 *  - A file: Spotify's own account data download (Playlist1.json, YourLibrary.json) or a CSV
 *    from a playlist exporter.
 *  - Pasted lines like "Artist - Song".
 * Every song is then looked up on Audius by title and artist and only kept when it's a close
 * match, so nothing random sneaks in.
 */
class SpotifyImport(private val context: Context, private val client: OkHttpClient, private val settings: SettingsStore) {

    data class SourceTrack(
        val title: String,
        val artist: String,
        val durationMs: Long = 0,
        /** "spotify:track:…" when the song came from Spotify itself, not from a file. */
        val uri: String? = null,
        val isrc: String? = null,
        val artwork: String? = null,
        val album: String? = null,
    ) {
        /** The song as a Spotify track, so it can sit in a playlist until your server has it. */
        fun asTrack(): Track = Track(
            id = uri ?: ("import:" + SongMatch.clean("$artist $title").replace(' ', '-')),
            title = title,
            artist = artist,
            durationSec = (durationMs / 1000).toInt(),
            isrc = isrc,
            artwork = artwork,
            artworkLarge = artwork,
            album = album,
        )
    }

    /** A song from Spitify's own sources playing in place of a Spotify one, and how close it is. */
    data class StandIn(val track: Track, val kind: Kind) {
        enum class Kind {
            /** The same recording. Nothing else is ever played in a Spotify song's place. */
            EXACT,
        }
    }

    data class SourcePlaylist(val id: String, val name: String, val tracks: List<SourceTrack>, val total: Int = tracks.size)

    data class Match(val source: SourceTrack, val track: Track?)

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("spotify_import", Context.MODE_PRIVATE)

    val clientId: String
        get() = settings.current.spotifyClientId.trim().ifBlank { com.srakim.spitify.BuildConfig.SPOTIFY_CLIENT_ID }

    /** Signed in, even if the current access token has run out (it gets refreshed on use). */
    val hasToken: Boolean get() = token() != null || prefs.getString(KEY_REFRESH, null) != null

    /** Flips when you sign in or out, so Home and Library can switch to your Spotify account. */
    val signedIn = kotlinx.coroutines.flow.MutableStateFlow(hasToken)

    sealed interface SignIn {
        data object Idle : SignIn
        data object Working : SignIn
        data object Done : SignIn
        data class Failed(val message: String) : SignIn
    }

    /** Where the last sign-in got to, for the screens to show. */
    val signIn = kotlinx.coroutines.flow.MutableStateFlow<SignIn>(SignIn.Idle)

    // Sign-in runs here, not on a screen, so leaving or redrawing a screen can't cut it off
    // halfway (Spotify's code only works once, so a cut-off exchange meant signing in again).
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Called with what Spotify sent back to spitify://spotify-auth. */
    fun completeSignIn(code: String?, state: String?, error: String?) {
        signIn.value = SignIn.Working
        scope.launch {
            signIn.value = runCatching { finishSignIn(code, state, error) }
                .fold({ SignIn.Done }, { SignIn.Failed(it.message ?: "Spotify sign-in didn't finish. Try again.") })
        }
    }

    // ---------- Sign in with Spotify ----------

    /** The page to open in the browser. Spotify sends the browser back to spitify://spotify-auth. */
    fun authorizeUrl(): Uri {
        val verifier = randomString(64)
        val state = randomString(16)
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
    }

    suspend fun finishSignIn(code: String?, state: String?, error: String?) = withContext(Dispatchers.IO) {
        if (error != null) throw IOException(if (error == "access_denied") "Spotify sign-in was cancelled" else "Spotify said: $error")
        if (code == null || state != prefs.getString(KEY_STATE, null)) throw IOException("That sign-in didn't come from this phone. Try again.")
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT)
            .add("client_id", clientId)
            .add("code_verifier", prefs.getString(KEY_VERIFIER, "").orEmpty())
            .build()
        val reply = runCatching { execute(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).jsonObject }
            .getOrElse { e ->
                val m = e.message.orEmpty()
                throw IOException(
                    when {
                        m.contains("invalid_grant", true) || m.contains("authorization code", true) -> "That sign-in expired before it finished. Tap Sign in with Spotify again."
                        m.contains("invalid_client", true) -> "Spotify doesn't recognise the app id. Check it in Settings → Apps and devices → Spotify app id."
                        m.contains("redirect", true) -> "Add spitify://spotify-auth as a Redirect URI on your Spotify developer dashboard, then sign in again."
                        else -> "Spotify sign-in failed: $m"
                    },
                )
            }
        val access = reply["access_token"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Spotify didn't hand over a token")
        val expires = reply["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
        prefs.edit()
            .putString(KEY_TOKEN, access)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expires * 1000 - 60_000)
            .apply { reply["refresh_token"]?.jsonPrimitive?.contentOrNull?.let { putString(KEY_REFRESH, it) } }
            .remove(KEY_VERIFIER)
            .apply()
        signedIn.value = true
    }

    fun signOut() {
        prefs.edit().clear().apply()
        signedIn.value = false
    }

    private fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES, 0) }

    /** Access tokens last an hour. The refresh token from sign-in gets a new one without asking. */
    @Synchronized
    private fun freshToken(): String? {
        token()?.let { return it }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        val reply = runCatching {
            client.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).execute().use { r ->
                if (r.code == 400 || r.code == 401) {
                    // Refresh token revoked: really signed out now.
                    signOut()
                    return null
                }
                if (!r.isSuccessful) return null
                json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            }
        }.getOrNull() ?: return null
        val access = reply["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
        val expires = reply["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
        prefs.edit()
            .putString(KEY_TOKEN, access)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expires * 1000 - 60_000)
            .apply { reply["refresh_token"]?.jsonPrimitive?.contentOrNull?.let { putString(KEY_REFRESH, it) } }
            .apply()
        return access
    }

    /** Your Spotify playlists, plus Liked Songs first. Track lists load when you pick one. */
    suspend fun myPlaylists(): List<SourcePlaylist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SourcePlaylist>()
        val liked = api("https://api.spotify.com/v1/me/tracks?limit=1").jsonObject["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (liked > 0) out += SourcePlaylist(LIKED, "Liked Songs", emptyList(), liked)
        var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        while (next != null) {
            val page = api(next).jsonObject
            page["items"]?.jsonArray?.forEach { item ->
                val o = item as? JsonObject ?: return@forEach
                val id = o.str("id") ?: return@forEach
                val total = (o["tracks"] as? JsonObject ?: o["items"] as? JsonObject)?.get("total")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                out += SourcePlaylist(id, o.str("name") ?: "Playlist", emptyList(), total)
            }
            next = page.str("next")
        }
        out
    }

    suspend fun tracksOf(playlistId: String): List<SourceTrack> = withContext(Dispatchers.IO) {
        val first = if (playlistId == LIKED) {
            "https://api.spotify.com/v1/me/tracks?limit=50"
        } else {
            "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
        }
        val out = mutableListOf<SourceTrack>()
        var next: String? = first
        while (next != null) {
            val page = runCatching { api(next!!).jsonObject }.getOrElse { e ->
                // Spotify has been renaming /tracks to /items on playlists; try the new name once.
                if (next == first && playlistId != LIKED) api("https://api.spotify.com/v1/playlists/$playlistId/items?limit=100").jsonObject else throw e
            }
            page["items"]?.jsonArray?.forEach { item ->
                val t = ((item as? JsonObject)?.get("track") ?: (item as? JsonObject)?.get("item")) as? JsonObject ?: return@forEach
                val title = t.str("name") ?: return@forEach
                val artist = (t["artists"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ").orEmpty()
                val album = t["album"] as? JsonObject
                out += SourceTrack(
                    title,
                    artist,
                    t.str("duration_ms")?.toLongOrNull() ?: 0,
                    uri = t.str("uri")?.takeIf { it.startsWith(TRACK_PREFIX) },
                    isrc = (t["external_ids"] as? JsonObject)?.str("isrc"),
                    artwork = (album?.get("images") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("url") }?.let { it.getOrNull(1) ?: it.firstOrNull() },
                    album = album?.str("name"),
                )
            }
            next = page.str("next")
        }
        out
    }

    // ---------- Spotify's catalogue, for playing through the Spotify app ----------

    /**
     * Songs from Spotify's search. Their ids are Spotify URIs ("spotify:track:…"), which the
     * player hands to the Spotify app, or swaps for a match from Spitify's sources when the
     * Spotify app can't play them (no Premium, not installed, not connected).
     */
    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        if (!hasToken || query.isBlank()) return@withContext emptyList()
        val url = "https://api.spotify.com/v1/search?type=track&limit=$limit&q=" + Uri.encode(query.trim())
        val items = api(url).jsonObject["tracks"]?.jsonObject?.get("items")?.jsonArray.orEmpty()
        items.mapNotNull { (it as? JsonObject)?.let(::toTrack) }
    }

    private fun toTrack(t: JsonObject): Track? {
        val uri = t.str("uri")?.takeIf { it.startsWith(TRACK_PREFIX) } ?: return null
        val album = t["album"] as? JsonObject
        val images = (album?.get("images") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("url") }.orEmpty()
        val artists = (t["artists"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        return Track(
            id = uri,
            title = t.str("name") ?: return null,
            artist = artists.mapNotNull { it.str("name") }.joinToString(", "),
            // Spotify lists the biggest image first: 640, then 300, then 64.
            artwork = images.getOrNull(1) ?: images.firstOrNull(),
            artworkLarge = images.firstOrNull(),
            durationSec = ((t.str("duration_ms")?.toLongOrNull() ?: 0) / 1000).toInt(),
            link = (t["external_urls"] as? JsonObject)?.str("spotify"),
            album = album?.str("name"),
            albumId = album?.str("uri"),
            artistId = artists.firstOrNull()?.str("uri").orEmpty(),
            isrc = (t["external_ids"] as? JsonObject)?.str("isrc"),
        )
    }

    // ---------- Your Spotify account, shown in Spitify's own pages ----------
    // Ids keep Spotify's URI form ("spotify:playlist:…", "spotify:album:…", "spotify:artist:…")
    // so Catalog knows to send them here.

    suspend fun likedSongs(max: Int = 500): List<Track> = withContext(Dispatchers.IO) {
        pagedTracks("https://api.spotify.com/v1/me/tracks?limit=50", max)
    }

    suspend fun recentlyPlayed(): List<Track> = withContext(Dispatchers.IO) {
        api("https://api.spotify.com/v1/me/player/recently-played?limit=50").jsonObject["items"]?.jsonArray.orEmpty()
            .mapNotNull { ((it as? JsonObject)?.get("track") as? JsonObject)?.let(::toTrack) }
            .distinctBy { it.id }
    }

    suspend fun topTracks(): List<Track> = withContext(Dispatchers.IO) {
        api("https://api.spotify.com/v1/me/top/tracks?limit=30&time_range=short_term").jsonObject["items"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::toTrack) }
    }

    suspend fun topArtists(): List<Artist> = withContext(Dispatchers.IO) {
        api("https://api.spotify.com/v1/me/top/artists?limit=20&time_range=medium_term").jsonObject["items"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::toArtist) }
    }

    suspend fun followedArtists(): List<Artist> = withContext(Dispatchers.IO) {
        api("https://api.spotify.com/v1/me/following?type=artist&limit=50").jsonObject["artists"]?.jsonObject?.get("items")?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::toArtist) }
    }

    /** Your playlists as cards (no tracks yet). */
    suspend fun playlists(): List<Playlist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Playlist>()
        var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        while (next != null && out.size < 200) {
            val page = api(next).jsonObject
            page["items"]?.jsonArray?.forEach { item -> (item as? JsonObject)?.let(::toPlaylist)?.let { out += it } }
            next = page.str("next")
        }
        out
    }

    suspend fun savedAlbums(): List<Playlist> = withContext(Dispatchers.IO) {
        api("https://api.spotify.com/v1/me/albums?limit=50").jsonObject["items"]?.jsonArray.orEmpty()
            .mapNotNull { ((it as? JsonObject)?.get("album") as? JsonObject)?.let(::toAlbum) }
    }

    /** A playlist or album page. [uri] is "spotify:playlist:…" or "spotify:album:…". */
    suspend fun collection(uri: String): Pair<Playlist?, List<Track>> = withContext(Dispatchers.IO) {
        val id = uri.substringAfterLast(':')
        if (uri.startsWith(ALBUM_PREFIX)) {
            val o = api("https://api.spotify.com/v1/albums/$id").jsonObject
            val album = toAlbum(o)
            val images = (o["images"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("url") }.orEmpty()
            val items = (o["tracks"] as? JsonObject)?.get("items")?.jsonArray.orEmpty()
            // Album tracks come without the album attached, so give each one the cover.
            val tracks = items.mapNotNull { (it as? JsonObject)?.let(::toTrack) }
                .map { it.copy(artwork = images.getOrNull(1) ?: images.firstOrNull(), artworkLarge = images.firstOrNull(), album = album?.name, albumId = uri) }
            album to tracks
        } else {
            val o = api("https://api.spotify.com/v1/playlists/$id?fields=id,uri,name,description,images,owner(display_name,id),followers(total),external_urls").jsonObject
            o.let(::toPlaylist) to pagedTracks("https://api.spotify.com/v1/playlists/$id/tracks?limit=100", 500, fallback = "https://api.spotify.com/v1/playlists/$id/items?limit=100")
        }
    }

    /** An artist's page: who they are, their ten most played songs, and their releases. */
    suspend fun artistPage(uri: String): Triple<Artist?, List<Track>, List<Playlist>> = coroutineScope {
        val id = uri.substringAfterLast(':')
        val info = async(Dispatchers.IO) { runCatching { toArtist(api("https://api.spotify.com/v1/artists/$id").jsonObject) }.getOrNull() }
        val top = async(Dispatchers.IO) {
            runCatching {
                api("https://api.spotify.com/v1/artists/$id/top-tracks?market=from_token").jsonObject["tracks"]?.jsonArray.orEmpty()
                    .mapNotNull { (it as? JsonObject)?.let(::toTrack) }
            }.getOrDefault(emptyList())
        }
        val releases = async(Dispatchers.IO) {
            runCatching {
                api("https://api.spotify.com/v1/artists/$id/albums?include_groups=album,single&limit=30").jsonObject["items"]?.jsonArray.orEmpty()
                    .mapNotNull { (it as? JsonObject)?.let(::toAlbum) }
            }.getOrDefault(emptyList())
        }
        Triple(info.await(), top.await(), releases.await())
    }

    /** Spotify's search across songs, artists, albums and playlists. */
    suspend fun searchAll(query: String): SearchResults = withContext(Dispatchers.IO) {
        if (!hasToken || query.isBlank()) return@withContext SearchResults.Empty
        val o = api("https://api.spotify.com/v1/search?type=track,artist,album,playlist&limit=10&q=" + Uri.encode(query.trim())).jsonObject
        fun items(key: String) = (o[key] as? JsonObject)?.get("items")?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        SearchResults(
            tracks = items("tracks").mapNotNull(::toTrack),
            artists = items("artists").mapNotNull(::toArtist),
            playlists = items("albums").mapNotNull(::toAlbum) + items("playlists").mapNotNull(::toPlaylist),
        )
    }

    private fun pagedTracks(first: String, max: Int, fallback: String? = null): List<Track> {
        val out = mutableListOf<Track>()
        var next: String? = first
        while (next != null && out.size < max) {
            val url = next
            val page = runCatching { api(url).jsonObject }.getOrElse { e ->
                if (url == first && fallback != null) api(fallback).jsonObject else throw e
            }
            page["items"]?.jsonArray?.forEach { item ->
                val o = item as? JsonObject ?: return@forEach
                val t = (o["track"] ?: o["item"]) as? JsonObject ?: return@forEach
                toTrack(t)?.let { out += it }
            }
            next = page.str("next")
        }
        return out
    }

    private fun images(o: JsonObject): List<String> =
        (o["images"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("url") }.orEmpty()

    private fun toArtist(o: JsonObject): Artist? {
        val uri = o.str("uri") ?: return null
        val img = images(o)
        return Artist(
            id = uri,
            name = o.str("name") ?: return null,
            picture = img.getOrNull(1) ?: img.firstOrNull(),
            pictureLarge = img.firstOrNull(),
            cover = img.firstOrNull(),
            followers = (o["followers"] as? JsonObject)?.str("total")?.toIntOrNull() ?: 0,
        )
    }

    private fun toPlaylist(o: JsonObject): Playlist? {
        val uri = o.str("uri") ?: return null
        val img = images(o)
        val owner = o["owner"] as? JsonObject
        return Playlist(
            id = uri,
            name = o.str("name") ?: return null,
            owner = owner?.str("display_name") ?: "Spotify",
            artwork = img.firstOrNull(),
            trackCount = ((o["tracks"] ?: o["items"]) as? JsonObject)?.str("total")?.toIntOrNull() ?: 0,
            // Spotify sends descriptions with HTML entities and links in them.
            description = o.str("description")?.replace(Regex("<[^>]+>"), "")?.replace("&amp;", "&")?.replace("&#x27;", "'")?.replace("&quot;", "\"")?.takeIf { it.isNotBlank() },
            link = (o["external_urls"] as? JsonObject)?.str("spotify"),
            saves = (o["followers"] as? JsonObject)?.str("total")?.toIntOrNull() ?: 0,
        )
    }

    private fun toAlbum(o: JsonObject): Playlist? {
        val uri = o.str("uri") ?: return null
        val img = images(o)
        val artist = (o["artists"] as? JsonArray)?.firstOrNull() as? JsonObject
        val type = o.str("album_type")
        return Playlist(
            id = uri,
            name = o.str("name") ?: return null,
            owner = artist?.str("name").orEmpty(),
            artwork = img.firstOrNull(),
            trackCount = o.str("total_tracks")?.toIntOrNull() ?: 0,
            label = when (type) { "single" -> "Single"; "compilation" -> "Compilation"; else -> "Album" },
            link = (o["external_urls"] as? JsonObject)?.str("spotify"),
            ownerId = artist?.str("uri").orEmpty(),
            year = o.str("release_date")?.take(4),
        )
    }

    /** Stand-ins found so far, by Spotify id, so replaying a song or a playlist starts at once. */
    private val standIns = java.util.concurrent.ConcurrentHashMap<String, StandIn>()

    /**
     * The song from Spitify's own sources that plays when Spotify can't play [track]: the same
     * song when one of them has it, else a close version of it. Never a different song.
     */
    suspend fun standInFor(track: Track, catalog: Catalog): StandIn? {
        standIns[track.id]?.let { return it }
        val found = runCatching {
            bestMatch(SourceTrack(track.title, track.artist, track.durationSec * 1000L, uri = track.id, isrc = track.isrc), catalog)
        }.getOrNull()
        // Only answers are kept: a miss might just be a dropped connection.
        if (found != null) standIns[track.id] = found
        return found
    }

    private fun api(url: String): JsonElement {
        val t = freshToken() ?: throw IOException("Sign in to Spotify again")
        return try {
            execute(Request.Builder().url(url).header("Authorization", "Bearer $t").build())
        } catch (e: Expired) {
            // The access token died early: get a new one and try once more.
            val again = freshToken() ?: throw IOException("Spotify signed you out. Sign in again in Settings → Apps and devices.")
            execute(Request.Builder().url(url).header("Authorization", "Bearer $again").build())
        }
    }

    private class Expired : IOException("Spotify session expired")

    private fun execute(request: Request): JsonElement = client.newCall(request).execute().use { r ->
        val text = r.body?.string().orEmpty()
        if (r.code == 401 && request.header("Authorization") != null) {
            prefs.edit().remove(KEY_TOKEN).apply()
            throw Expired()
        }
        if (!r.isSuccessful) {
            val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val err = body?.get("error")
            val msg = (err as? JsonObject)?.str("message")
                ?: listOfNotNull((err as? JsonPrimitive)?.contentOrNull, body?.str("error_description")).joinToString(": ").ifBlank { null }
            throw IOException(explain(r.code, msg))
        }
        json.parseToJsonElement(text)
    }

    /** Spotify's error, in words that say what to do about it. */
    private fun explain(code: Int, msg: String?): String {
        val m = msg.orEmpty()
        return when {
            code == 403 && m.contains("not registered", true) ->
                "Spotify hasn't let this account into your developer app yet. On developer.spotify.com open the app → User Management, add the name and email of the Spotify account you sign in with, then sign in again."
            code == 403 && m.contains("scope", true) ->
                "Spitify needs a few more Spotify permissions. Sign out and sign in again in Settings → Apps and devices."
            code == 403 -> "Spotify said no (403${if (m.isNotBlank()) ": $m" else ""}). If you're the only user, add your account under User Management on the Spotify developer dashboard."
            code == 429 -> "Spotify is rate limiting Spitify. Wait a minute and try again."
            code >= 500 -> "Spotify is having trouble right now (${code}). Try again in a bit."
            m.isNotBlank() -> m
            else -> "Spotify answered with $code"
        }
    }

    // ---------- Files and pasted text ----------

    /** Reads Spotify's data download (Playlist*.json, YourLibrary.json), a CSV export, or plain lines. */
    suspend fun readFile(uri: Uri): List<SourcePlaylist> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Couldn't open that file")
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported"
        parseText(text, name)
    }

    fun parseText(text: String, fallbackName: String = "Imported"): List<SourcePlaylist> {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
            if (root != null) return parseJson(root, fallbackName)
        }
        val lines = trimmed.lines().filter { it.isNotBlank() }
        if (lines.size > 1 && lines.first().contains(",") && lines.first().contains("track", ignoreCase = true)) {
            return listOf(parseCsv(lines, fallbackName))
        }
        val tracks = lines.mapNotNull { line ->
            val clean = line.trim().removePrefix("-").trim()
            val parts = clean.split(" - ", " – ", " — ", limit = 2)
            if (parts.size == 2) SourceTrack(title = parts[1].trim(), artist = parts[0].trim())
            else clean.takeIf { it.isNotBlank() }?.let { SourceTrack(it, "") }
        }
        return if (tracks.isEmpty()) emptyList() else listOf(SourcePlaylist("text", fallbackName, tracks))
    }

    private fun parseJson(root: JsonElement, fallbackName: String): List<SourcePlaylist> {
        val obj = root as? JsonObject
        // Spotify data download: {"playlists":[{"name":..,"items":[{"track":{"trackName","artistName"}}]}]}
        obj?.get("playlists")?.jsonArray?.let { lists ->
            return lists.mapIndexedNotNull { i, p ->
                val po = p as? JsonObject ?: return@mapIndexedNotNull null
                val tracks = po["items"]?.jsonArray?.mapNotNull { item ->
                    val t = (item as? JsonObject)?.get("track") as? JsonObject ?: return@mapNotNull null
                    val title = t.str("trackName") ?: return@mapNotNull null
                    SourceTrack(title, t.str("artistName").orEmpty())
                }.orEmpty()
                SourcePlaylist("file$i", po.str("name") ?: "Playlist ${i + 1}", tracks)
            }.filter { it.tracks.isNotEmpty() }
        }
        // YourLibrary.json: {"tracks":[{"artist","album","track"}]}
        obj?.get("tracks")?.jsonArray?.let { arr ->
            val tracks = arr.mapNotNull { t ->
                val o = t as? JsonObject ?: return@mapNotNull null
                val title = o.str("track") ?: o.str("trackName") ?: o.str("name") ?: return@mapNotNull null
                SourceTrack(title, o.str("artist") ?: o.str("artistName").orEmpty())
            }
            return if (tracks.isEmpty()) emptyList() else listOf(SourcePlaylist("library", "Liked Songs from Spotify", tracks))
        }
        // A plain list of {title/artist} objects.
        (root as? JsonArray)?.let { arr ->
            val tracks = arr.mapNotNull { t ->
                val o = t as? JsonObject ?: return@mapNotNull null
                val title = o.str("title") ?: o.str("trackName") ?: o.str("track") ?: o.str("name") ?: return@mapNotNull null
                SourceTrack(title, o.str("artist") ?: o.str("artistName").orEmpty())
            }
            if (tracks.isNotEmpty()) return listOf(SourcePlaylist("json", fallbackName, tracks))
        }
        return emptyList()
    }

    private fun parseCsv(lines: List<String>, name: String): SourcePlaylist {
        val header = splitCsv(lines.first()).map { it.lowercase() }
        val titleCol = header.indexOfFirst { it == "track name" || it == "title" || it == "name" || it == "track" }
        val artistCol = header.indexOfFirst { it.startsWith("artist name") || it == "artist" || it == "artists" }
        val tracks = lines.drop(1).mapNotNull { line ->
            val cells = splitCsv(line)
            val title = cells.getOrNull(titleCol)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SourceTrack(title, cells.getOrNull(artistCol).orEmpty().replace(";", ", "))
        }
        return SourcePlaylist("csv", name, tracks)
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    // ---------- Matching ----------

    /** Finds each song in Spitify's own sources. [onProgress] gets (done, total) as it goes. */
    suspend fun match(tracks: List<SourceTrack>, catalog: Catalog, onProgress: (Int, Int) -> Unit): List<Match> = coroutineScope {
        val gate = Semaphore(4)
        var done = 0
        tracks.map { src ->
            async {
                gate.withPermit {
                    val found = runCatching { bestMatch(src, catalog)?.track }.getOrNull()
                    synchronized(this@SpotifyImport) { done++ }
                    onProgress(done, tracks.size)
                    Match(src, found)
                }
            }
        }.awaitAll()
    }

    /** Your server's copies. Once a server is set up, Spotify songs come from there and nowhere else. */
    var serverCopies: ServerCopies? = null

    /**
     * Finds the exact recording of [src]. With your server set up, only your server is asked: the
     * song plays from there or it's missing. Without one, every source Spitify plays from directly
     * is searched (this phone first), and still only the same recording counts. A remix, another
     * version or another song by the same artist is never passed off as it.
     */
    private suspend fun bestMatch(src: SourceTrack, catalog: Catalog): StandIn? {
        val wanted = src.asTrack()
        serverCopies?.takeIf { it.enabled }?.let { copies ->
            return copies.find(wanted)?.let { StandIn(it, StandIn.Kind.EXACT) }
        }
        val firstArtist = clean(SongMatch.firstArtist(src.artist))
        val title = clean(src.title)
        if (title.isBlank()) return null
        val queries = listOf(listOf(title, firstArtist).filter { it.isNotBlank() }.joinToString(" "), title).distinct()
        for (q in queries) {
            val same = catalog.searchForMatch(q).filter { SongMatch.exact(wanted, it) }
            val pick = same.firstOrNull(::isYours) ?: same.firstOrNull()
            if (pick != null) return StandIn(pick, StandIn.Kind.EXACT)
        }
        return null
    }

    /** Songs on your own server (Navidrome or any Subsonic server) or on this phone. */
    private fun isYours(t: Track) = t.isLocal || SubsonicApi.isServer(t.id) || SpitifyServerApi.isServer(t.id)

    private fun clean(s: String): String = SongMatch.clean(s)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        const val REDIRECT = "spitify://spotify-auth"
        const val TRACK_PREFIX = "spotify:track:"

        fun isSpotify(id: String) = id.startsWith(TRACK_PREFIX)
        const val ALBUM_PREFIX = "spotify:album:"
        const val PLAYLIST_PREFIX = "spotify:playlist:"
        const val ARTIST_PREFIX = "spotify:artist:"
        fun isSpotifyCollection(id: String) = id.startsWith(ALBUM_PREFIX) || id.startsWith(PLAYLIST_PREFIX)
        fun isSpotifyArtist(id: String) = id.startsWith(ARTIST_PREFIX)
        const val LIKED = "liked"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH = "refresh"
        private const val SCOPES = "playlist-read-private playlist-read-collaborative user-library-read " +
            "user-top-read user-read-recently-played user-follow-read user-read-private"
        private const val KEY_EXPIRES = "expires"
    }
}
