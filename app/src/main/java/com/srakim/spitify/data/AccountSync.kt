package com.srakim.spitify.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** The part of the library that follows the account. Play history stays on each phone. */
@Serializable
data class SyncedLibrary(
    val liked: List<Track> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val savedPlaylists: List<Playlist> = emptyList(),
    val followedArtists: List<Artist> = emptyList(),
)

data class AccountState(
    val username: String? = null,
    val syncing: Boolean = false,
    val lastSynced: Long = 0,
    val error: String? = null,
) {
    val signedIn: Boolean get() = username != null
}

/**
 * Signs in to the listener's own Spitify server and keeps the library there, so playlists,
 * likes and follows come back on a new phone or after a reinstall.
 *
 * Changes are sent a couple of seconds after they happen. When the app opens it pulls the
 * server copy if another phone changed it since. The newest copy wins; the first sign-in on a
 * phone merges both sides so nothing already on it is lost.
 */
class AccountSync(
    context: Context,
    client: OkHttpClient,
    private val settings: SettingsStore,
    private val library: LibraryStore,
) {
    private val http = client
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val jsonType = "application/json".toMediaType()

    private val _state = MutableStateFlow(
        AccountState(username = prefs.getString(KEY_USER, null), lastSynced = prefs.getLong(KEY_SYNCED, 0)),
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** What the server last sent or accepted, so applying it doesn't trigger an upload. */
    @Volatile
    private var lastKnown: SyncedLibrary? = null

    private val token: String? get() = prefs.getString(KEY_TOKEN, null)

    /** Called once when the app starts: catch up with the server, then follow local changes. */
    fun start() {
        io.launch { if (token != null) sync() }
        io.launch {
            library.state
                .map { it.synced() }
                .distinctUntilChanged()
                .drop(1) // the library as it was at launch; sync() above deals with that
                .collectLatest { current ->
                    if (token == null || current == lastKnown) return@collectLatest
                    prefs.edit().putBoolean(KEY_DIRTY, true).apply()
                    delay(PUSH_DELAY_MS) // wait for the listener to finish a burst of changes
                    runCatching { lock.withLock { push(current) } }.onFailure(::fail)
                }
        }
    }

    fun signIn(username: String, password: String, create: Boolean, onDone: (Result<Unit>) -> Unit) {
        io.launch {
            val result = runCatching {
                val body = buildJsonObject {
                    put("username", username.trim())
                    put("password", password)
                }
                val reply = call("api/auth/${if (create) "register" else "login"}", "POST", body, auth = false)
                val name = reply["username"]?.jsonPrimitive?.content ?: throw IOException("Unexpected reply from the server")
                val newToken = reply["token"]?.jsonPrimitive?.content ?: throw IOException("Unexpected reply from the server")
                prefs.edit().putString(KEY_USER, name).putString(KEY_TOKEN, newToken).putLong(KEY_SYNCED, 0).apply()
                _state.update { it.copy(username = name, error = null) }
                sync(firstTime = true)
            }
            onDone(result)
        }
    }

    fun signOut() {
        val old = token
        prefs.edit().clear().apply()
        lastKnown = null
        _state.value = AccountState()
        if (old != null) io.launch { runCatching { call("api/auth/logout", "POST", buildJsonObject {}, tokenOverride = old) } }
    }

    fun syncNow() {
        io.launch { runCatching { sync() } }
    }

    private suspend fun sync(firstTime: Boolean = false) = lock.withLock {
        _state.update { it.copy(syncing = true, error = null) }
        try {
            val remote = call("api/library", "GET")
            val remoteAt = remote["updatedAt"]?.jsonPrimitive?.long ?: 0L
            val remoteLib = remote["library"]?.takeIf { it !is JsonNull }
                ?.let { runCatching { json.decodeFromJsonElement<SyncedLibrary>(it) }.getOrNull() }
            val local = library.state.value.synced()
            val dirty = prefs.getBoolean(KEY_DIRTY, false)
            val lastSynced = prefs.getLong(KEY_SYNCED, 0)

            when {
                remoteLib == null -> push(local)
                firstTime -> {
                    val merged = merge(local, remoteLib)
                    apply(merged)
                    push(merged)
                }
                remoteAt > lastSynced && dirty -> {
                    // changed here and on another phone: keep both
                    val merged = merge(local, remoteLib)
                    apply(merged)
                    push(merged)
                }
                remoteAt > lastSynced -> {
                    apply(remoteLib)
                    markSynced(remoteAt)
                }
                dirty -> push(local)
                else -> lastKnown = local
            }
        } catch (e: Exception) {
            fail(e)
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    private fun push(lib: SyncedLibrary) {
        val now = System.currentTimeMillis()
        val body = buildJsonObject {
            put("library", json.encodeToJsonElement(lib))
            put("updatedAt", now)
        }
        val reply = call("api/library", "PUT", body)
        lastKnown = lib
        markSynced(reply["updatedAt"]?.jsonPrimitive?.long ?: now)
    }

    private fun apply(lib: SyncedLibrary) {
        lastKnown = lib
        library.replaceSynced(lib)
    }

    private fun markSynced(at: Long) {
        prefs.edit().putLong(KEY_SYNCED, at).putBoolean(KEY_DIRTY, false).apply()
        _state.update { it.copy(lastSynced = at, error = null) }
    }

    private fun fail(e: Throwable) {
        if (e is SignedOut) {
            prefs.edit().clear().apply()
            lastKnown = null
            _state.value = AccountState(error = "You were signed out. Log in again to keep syncing.")
        } else {
            _state.update { it.copy(error = e.message ?: "Couldn't sync") }
        }
    }

    private class SignedOut : IOException("Signed out")

    private fun call(
        path: String,
        verb: String,
        body: JsonObject? = null,
        auth: Boolean = true,
        tokenOverride: String? = null,
    ): JsonObject {
        val base = SpitifyServerApi.base(settings.current.webServerUrl)
            ?: throw IOException("Add your Spitify server in Settings first")
        val url = base.newBuilder().apply { path.split("/").forEach { addPathSegment(it) } }.build()
        val request = Request.Builder().url(url).apply {
            val t = tokenOverride ?: token
            if (auth && t != null) header("Authorization", "Bearer $t")
            if (verb == "GET") get()
            else method(verb, (body ?: buildJsonObject {}).toString().toRequestBody(jsonType))
        }.build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (response.code == 401 && auth) throw SignedOut()
            if (!response.isSuccessful) {
                throw IOException(parsed?.get("error")?.jsonPrimitive?.content ?: "Server answered with ${response.code}")
            }
            return parsed ?: JsonObject(emptyMap())
        }
    }

    private companion object {
        const val KEY_USER = "username"
        const val KEY_TOKEN = "token"
        const val KEY_SYNCED = "last_synced"
        const val KEY_DIRTY = "dirty"
        const val PUSH_DELAY_MS = 2_000L

        fun LibraryState.synced() = SyncedLibrary(liked, playlists, savedPlaylists, followedArtists)

        /** Everything from both sides. For a playlist on both, the most recently edited copy wins. */
        fun merge(local: SyncedLibrary, remote: SyncedLibrary): SyncedLibrary {
            fun <T> union(a: List<T>, b: List<T>, id: (T) -> String) = (a + b).distinctBy(id)
            val playlists = (local.playlists + remote.playlists)
                .groupBy { it.id }
                .map { (_, copies) -> copies.maxBy { it.updatedAt } }
                .sortedByDescending { it.createdAt }
            return SyncedLibrary(
                liked = union(local.liked, remote.liked) { it.id },
                playlists = playlists,
                savedPlaylists = union(local.savedPlaylists, remote.savedPlaylists) { it.id },
                followedArtists = union(local.followedArtists, remote.followedArtists) { it.id },
            )
        }
    }
}
