package com.srakim.spitify.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    data class SourceTrack(val title: String, val artist: String, val durationMs: Long = 0)

    data class SourcePlaylist(val id: String, val name: String, val tracks: List<SourceTrack>, val total: Int = tracks.size)

    data class Match(val source: SourceTrack, val track: Track?)

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("spotify_import", Context.MODE_PRIVATE)

    val clientId: String
        get() = settings.current.spotifyClientId.trim().ifBlank { com.srakim.spitify.BuildConfig.SPOTIFY_CLIENT_ID }

    val hasToken: Boolean get() = token() != null

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
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative user-library-read")
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
        val reply = execute(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).jsonObject
        val access = reply["access_token"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Spotify didn't hand over a token")
        val expires = reply["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
        prefs.edit()
            .putString(KEY_TOKEN, access)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expires * 1000 - 60_000)
            .remove(KEY_VERIFIER)
            .apply()
    }

    fun signOut() = prefs.edit().clear().apply()

    private fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES, 0) }

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
                out += SourceTrack(title, artist, t.str("duration_ms")?.toLongOrNull() ?: 0)
            }
            next = page.str("next")
        }
        out
    }

    private fun api(url: String): JsonElement {
        val t = token() ?: throw IOException("Sign in to Spotify again")
        return execute(Request.Builder().url(url).header("Authorization", "Bearer $t").build())
    }

    private fun execute(request: Request): JsonElement = client.newCall(request).execute().use { r ->
        val text = r.body?.string().orEmpty()
        if (r.code == 401) {
            prefs.edit().remove(KEY_TOKEN).apply()
            throw IOException("Spotify signed you out. Sign in again.")
        }
        if (!r.isSuccessful) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"] }.getOrNull()?.let {
                (it as? JsonObject)?.str("message") ?: (it as? JsonPrimitive)?.contentOrNull
            }
            throw IOException(msg ?: "Spotify answered with ${r.code}")
        }
        json.parseToJsonElement(text)
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

    /** Finds each song on Audius. [onProgress] gets (done, total) as it goes. */
    suspend fun match(tracks: List<SourceTrack>, catalog: Catalog, onProgress: (Int, Int) -> Unit): List<Match> = coroutineScope {
        val gate = Semaphore(4)
        var done = 0
        tracks.map { src ->
            async {
                gate.withPermit {
                    val found = runCatching { bestMatch(src, catalog) }.getOrNull()
                    synchronized(this@SpotifyImport) { done++ }
                    onProgress(done, tracks.size)
                    Match(src, found)
                }
            }
        }.awaitAll()
    }

    private suspend fun bestMatch(src: SourceTrack, catalog: Catalog): Track? {
        val firstArtist = src.artist.split(",", "&", " feat", " ft.").first().trim()
        val query = listOf(clean(src.title), firstArtist).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = catalog.audius.searchTracks(query).take(15)
        val scored = candidates.map { it to score(src, it) }.maxByOrNull { it.second } ?: return null
        return scored.first.takeIf { scored.second >= 0.62 }
    }

    private fun score(src: SourceTrack, t: Track): Double {
        val title = similarity(clean(src.title), clean(t.title))
        val artist = if (src.artist.isBlank()) 0.6 else similarity(clean(src.artist), clean(t.artist))
        val length = if (src.durationMs > 0 && t.durationSec > 0) {
            val diff = kotlin.math.abs(src.durationMs / 1000 - t.durationSec)
            if (diff < 5) 0.1 else if (diff > 40) -0.15 else 0.0
        } else 0.0
        return title * 0.6 + artist * 0.4 + length
    }

    private fun clean(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex(" - .*(remaster|version|edit|live|mix).*"), " ")
        .replace(Regex("\\b(feat|ft|featuring)\\b.*"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val ta = a.split(' ').toSet()
        val tb = b.split(' ').toSet()
        val jaccard = ta.intersect(tb).size.toDouble() / ta.union(tb).size
        val contains = if (a.contains(b) || b.contains(a)) 0.85 else 0.0
        return maxOf(jaccard, contains)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        const val REDIRECT = "spitify://spotify-auth"
        const val LIKED = "liked"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES = "expires"
    }
}
