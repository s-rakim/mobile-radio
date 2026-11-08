package com.srakim.spitify.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * SoundCloud through its official API only. It stays off until a client ID and secret from an
 * app registered at soundcloud.com/you/apps are entered in Settings. Only tracks the API marks
 * as playable are shown, uploads are credited to their uploader with a link back to SoundCloud,
 * and nothing is ever downloaded.
 */
class SoundCloudApi(private val client: OkHttpClient, private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var token: String? = null
    @Volatile private var tokenExpiresAt = 0L
    @Volatile private var tokenFor = ""

    val enabled: Boolean
        get() = settings.current.let { it.soundCloudClientId.isNotBlank() && it.soundCloudClientSecret.isNotBlank() }

    suspend fun verify(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { accessToken(force = true) }
        Unit
    }

    suspend fun searchTracks(query: String): List<Track> {
        if (!enabled) return emptyList()
        val url = "$API/tracks".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("access", "playable")
            .addQueryParameter("limit", "20")
            .build()
        val root = withContext(Dispatchers.IO) { get(url.toString()) }
        val items = (root as? JsonArray) ?: ((root as? JsonObject)?.get("collection") as? JsonArray) ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.toTrack() }
    }

    /**
     * Turns a soundcloud://tracks/<id> placeholder into a real stream address. Called by the
     * player right before it loads a song, on a background thread, so it can block.
     */
    fun resolveStreamBlocking(uri: Uri): Uri {
        val id = uri.lastPathSegment ?: throw IOException("Bad SoundCloud address")
        val streams = get("$API/tracks/$id/streams") as? JsonObject ?: throw IOException("SoundCloud has no stream for this track")
        val url = streams.text("http_mp3_128_url") ?: throw IOException("SoundCloud has no stream for this track")
        return Uri.parse(url)
    }

    private fun JsonObject.toTrack(): Track? {
        val id = text("id") ?: return null
        if (text("streamable") == "false") return null
        if (text("access")?.let { it != "playable" } == true) return null
        val user = obj("user")
        val art = text("artwork_url") ?: user?.text("avatar_url")
        return Track(
            id = ID_PREFIX + id,
            title = text("title") ?: "Untitled",
            artist = user?.text("username") ?: "SoundCloud",
            artistImage = user?.text("avatar_url"),
            artwork = art?.replace("-large.", "-t300x300."),
            artworkLarge = art?.replace("-large.", "-t500x500."),
            durationSec = ((text("duration")?.toLongOrNull() ?: 0L) / 1000).toInt(),
            genre = text("genre")?.let { genreFromTags(listOf(it)) },
            link = text("permalink_url"),
            streamUrl = "$SCHEME://tracks/$id",
        )
    }

    private fun get(url: String): JsonElement {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json; charset=utf-8")
            .header("Authorization", "OAuth ${accessToken()}")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.code == 401) token = null
            if (!response.isSuccessful) throw IOException("SoundCloud request failed (${response.code})")
            response.body?.string().orEmpty()
        }
        return json.parseToJsonElement(body)
    }

    @Synchronized
    private fun accessToken(force: Boolean = false): String {
        val s = settings.current
        val key = s.soundCloudClientId.trim() + ":" + s.soundCloudClientSecret.trim()
        val cached = token
        if (!force && cached != null && key == tokenFor && System.currentTimeMillis() < tokenExpiresAt) return cached
        if (!enabled) throw IOException("Add your SoundCloud app keys in Settings")
        val basic = Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("https://secure.soundcloud.com/oauth/token")
            .header("Authorization", "Basic $basic")
            .header("Accept", "application/json; charset=utf-8")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("SoundCloud didn't accept those keys (${response.code})")
            response.body?.string().orEmpty()
        }
        val obj = json.parseToJsonElement(body) as? JsonObject ?: throw IOException("Unexpected answer from SoundCloud")
        val fresh = obj.text("access_token") ?: throw IOException("SoundCloud didn't return a token")
        val seconds = obj.text("expires_in")?.toLongOrNull() ?: 3600L
        token = fresh
        tokenFor = key
        tokenExpiresAt = System.currentTimeMillis() + (seconds - 60).coerceAtLeast(60) * 1000
        return fresh
    }

    companion object {
        const val ID_PREFIX = "sc:"
        const val SCHEME = "soundcloud"
        private const val API = "https://api.soundcloud.com"
    }
}
