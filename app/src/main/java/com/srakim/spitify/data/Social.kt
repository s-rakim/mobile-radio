package com.srakim.spitify.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.srakim.spitify.MainActivity
import com.srakim.spitify.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
data class Profile(
    val username: String,
    val displayName: String = username,
    val color: String = "#22C55E",
    val activity: Boolean = true,
) {
    val initial: String get() = displayName.trim().firstOrNull()?.uppercase() ?: "?"
}

@Serializable
data class NowPlaying(val track: Track? = null, val at: Long = 0, val live: Boolean = false)

@Serializable
data class Friend(
    val username: String,
    val displayName: String = username,
    val color: String = "#22C55E",
    val activity: Boolean = true,
    val following: Boolean = false,
    val followsYou: Boolean = false,
    val nowPlaying: NowPlaying? = null,
) {
    val profile: Profile get() = Profile(username, displayName, color, activity)
}

@Serializable
data class FriendsReply(val me: Profile, val friends: List<Friend> = emptyList())

@Serializable
data class SharedPlaylist(val id: String = "", val name: String = "Playlist", val artwork: String? = null, val tracks: List<Track> = emptyList())

@Serializable
data class Message(
    val id: String,
    val from: String,
    val at: Long,
    val kind: String = "text",
    val text: String? = null,
    val track: Track? = null,
    val playlist: SharedPlaylist? = null,
    val reactions: Map<String, String> = emptyMap(),
)

@Serializable
data class Conversation(val with: Profile, val last: Message, val unread: Int = 0)

@Serializable
data class Chat(val with: Profile, val messages: List<Message> = emptyList())

@Serializable
data class CollabEntry(val track: Track, val addedBy: String, val at: Long = 0)

@Serializable
data class Collab(
    val id: String,
    val name: String,
    val owner: String,
    val members: List<Profile> = emptyList(),
    val tracks: List<CollabEntry> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val link: String get() = "spitify://collab/$id"
}

@Serializable
data class CollabPreview(val id: String, val name: String, val owner: Profile, val members: Int = 0, val tracks: Int = 0)

@Serializable
data class BlendResult(
    val name: String,
    val with: Profile,
    val match: Int,
    val sharedArtists: List<String> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class TrackPlays(val track: Track, val plays: Int = 0)

@Serializable
data class ArtistPlays(val name: String, val image: String? = null, val plays: Int = 0)

@Serializable
data class PersonStats(
    val username: String,
    val displayName: String = username,
    val color: String = "#22C55E",
    val minutes: Int = 0,
    val topTracks: List<TrackPlays> = emptyList(),
    val topArtists: List<ArtistPlays> = emptyList(),
)

@Serializable
data class FriendStats(val week: String = "", val people: List<PersonStats> = emptyList())

@Serializable
data class JamSession(val host: Profile, val tracks: List<Track> = emptyList(), val index: Int = 0, val positionMs: Long = 0, val at: Long = 0)

/** Links into the app other than Jam invites, waiting for the UI to pick them up. */
object Links {
    sealed interface Link {
        data class CollabInvite(val id: String) : Link
        data class User(val name: String) : Link
        data class SpotifyAuth(val code: String?, val state: String?, val error: String?) : Link
    }

    val incoming = MutableStateFlow<Link?>(null)

    fun parse(uri: Uri?): Link? {
        if (uri == null || uri.scheme != "spitify") return null
        return when (uri.host) {
            "collab" -> uri.pathSegments.firstOrNull()?.takeIf { it.all(Char::isLetterOrDigit) }?.let { Link.CollabInvite(it) }
            "user" -> uri.pathSegments.firstOrNull()?.lowercase()?.let { Link.User(it) }
            "spotify-auth" -> Link.SpotifyAuth(uri.getQueryParameter("code"), uri.getQueryParameter("state"), uri.getQueryParameter("error"))
            else -> null
        }
    }
}

data class SocialState(
    val me: Profile? = null,
    val friends: List<Friend> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val collabs: List<Collab> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false,
) {
    val unread: Int get() = conversations.sumOf { it.unread }
    val following: List<Friend> get() = friends.filter { it.following }
}

/**
 * The social side of the app, talking to the Spitify server: friends and what they're playing,
 * messages, collaborative playlists, Blends, Jams and shared listening stats. It only ever
 * connects people with an account on the same Spitify server.
 *
 * While the app is open it refreshes every half minute and checks in with what you're playing,
 * unless you're in a private session or have listening activity turned off.
 */
class SocialStore(
    private val context: Context,
    private val account: AccountSync,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state.asStateFlow()

    private var notifiedUpTo = 0L
    private var knownFollowers: Set<String>? = null
    private var knownCollabSizes: Map<String, Int>? = null

    val signedIn: Boolean get() = account.state.value.signedIn
    val myName: String? get() = account.state.value.username

    fun start() {
        io.launch {
            while (isActive) {
                if (signedIn) runCatching { refresh() }
                delay(30_000)
            }
        }
    }

    fun refreshNow() {
        io.launch { runCatching { refresh() }.onFailure { e -> _state.update { it.copy(error = e.message) } } }
    }

    private suspend fun refresh() {
        val friends = decode<FriendsReply>(account.request("api/friends"))
        val convos = decode<List<Conversation>>(account.request("api/messages"))
        val collabs = decode<List<Collab>>(account.request("api/collab"))
        _state.value = SocialState(friends.me, friends.friends, convos, collabs, null, true)
        notify(friends, convos, collabs)
    }

    // ---- profile and friends ----

    suspend fun searchUsers(query: String): List<Friend> = call {
        decode(account.request("api/users/search", query = mapOf("q" to query)))
    }

    suspend fun profile(name: String): Profile = call { decode(account.request("api/users/$name")) }

    suspend fun follow(name: String) = call {
        val r = decode<FriendsReply>(account.request("api/friends/$name", "POST"))
        _state.update { it.copy(me = r.me, friends = r.friends) }
    }

    suspend fun unfollow(name: String) = call {
        val r = decode<FriendsReply>(account.request("api/friends/$name", "DELETE"))
        _state.update { it.copy(me = r.me, friends = r.friends) }
    }

    suspend fun updateProfile(displayName: String? = null, activity: Boolean? = null) = call {
        val p = decode<Profile>(
            account.request(
                "api/social/profile",
                "PUT",
                buildJsonObject {
                    displayName?.let { put("displayName", it) }
                    activity?.let { put("activity", it) }
                },
            ),
        )
        _state.update { it.copy(me = p) }
    }

    /** What's playing now, or null when it stops. Quietly does nothing when not signed in. */
    fun reportPlaying(track: Track?, playing: Boolean) {
        if (!signedIn || settings.current.privateSession || !settings.current.shareActivity) return
        io.launch {
            runCatching {
                account.request(
                    "api/activity",
                    "PUT",
                    buildJsonObject {
                        if (track != null && !track.isLocal) put("track", json.encodeToJsonElement(track))
                        put("playing", playing)
                    },
                )
            }
        }
    }

    // ---- messages ----

    suspend fun chat(name: String): Chat = call { decode<Chat>(account.request("api/messages/$name")) }.also { refreshNow() }

    suspend fun sendText(to: String, text: String): Message = send(to, buildJsonObject { put("kind", "text"); put("text", text) })

    suspend fun sendTrack(to: String, track: Track, note: String? = null): Message = send(
        to,
        buildJsonObject {
            put("kind", "track")
            put("track", json.encodeToJsonElement(track))
            note?.takeIf { it.isNotBlank() }?.let { put("text", it) }
        },
    )

    suspend fun sendPlaylist(to: String, playlist: SharedPlaylist, note: String? = null): Message = send(
        to,
        buildJsonObject {
            put("kind", "playlist")
            put("playlist", json.encodeToJsonElement(playlist))
            note?.takeIf { it.isNotBlank() }?.let { put("text", it) }
        },
    )

    private suspend fun send(to: String, body: kotlinx.serialization.json.JsonObject): Message = call {
        decode<Message>(account.request("api/messages/$to", "POST", body))
    }.also { refreshNow() }

    suspend fun react(with: String, messageId: String, emoji: String?) = call {
        decode<Message>(account.request("api/messages/$with/$messageId/react", "POST", buildJsonObject { emoji?.let { put("emoji", it) } }))
    }

    // ---- collaborative playlists ----

    suspend fun createCollab(name: String, tracks: List<Track> = emptyList()): Collab = call {
        decode<Collab>(
            account.request(
                "api/collab",
                "POST",
                buildJsonObject {
                    put("name", name)
                    put("tracks", json.encodeToJsonElement(tracks))
                },
            ),
        )
    }.also { refreshNow() }

    suspend fun collab(id: String): Collab = call { decode(account.request("api/collab/$id")) }

    suspend fun previewCollab(id: String): CollabPreview = call { decode(account.request("api/collab/$id/preview")) }

    suspend fun joinCollab(id: String): Collab = call { decode<Collab>(account.request("api/collab/$id/join", "POST")) }.also { refreshNow() }

    suspend fun leaveCollab(id: String) = call { account.request("api/collab/$id/leave", "POST") }.also { refreshNow() }

    suspend fun renameCollab(id: String, name: String): Collab = call {
        decode(account.request("api/collab/$id", "PUT", buildJsonObject { put("name", name) }))
    }

    suspend fun addToCollab(id: String, track: Track): Collab = call {
        decode(account.request("api/collab/$id/tracks", "POST", buildJsonObject { put("track", json.encodeToJsonElement(track)) }))
    }

    suspend fun removeFromCollab(id: String, trackId: String): Collab = call {
        decode(account.request("api/collab/$id/tracks/$trackId", "DELETE"))
    }

    // ---- Blend, stats, Jam ----

    suspend fun blend(with: String): BlendResult = call { decode(account.request("api/blend/$with")) }

    suspend fun friendStats(week: String): FriendStats = call {
        decode(account.request("api/stats/friends", query = mapOf("week" to week)))
    }

    fun uploadStats(week: String, minutes: Int, topTracks: List<TrackPlays>, topArtists: List<ArtistPlays>) {
        if (!signedIn) return
        io.launch {
            runCatching {
                account.request(
                    "api/stats",
                    "PUT",
                    buildJsonObject {
                        put("week", week)
                        put("minutes", minutes)
                        put("topTracks", json.encodeToJsonElement(topTracks))
                        put("topArtists", json.encodeToJsonElement(topArtists))
                    },
                )
            }
        }
    }

    suspend fun hostJam(tracks: List<Track>, index: Int, positionMs: Long) = call {
        account.request(
            "api/jam",
            "PUT",
            buildJsonObject {
                put("tracks", json.encodeToJsonElement(tracks.filter { !it.isLocal }.take(100)))
                put("index", index)
                put("positionMs", positionMs)
            },
        )
    }

    suspend fun endJam() = call { account.request("api/jam", "DELETE") }

    suspend fun jamOf(host: String): JamSession = call { decode(account.request("api/jam/$host")) }

    // ---- plumbing ----

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private inline fun <reified T> decode(element: JsonElement): T = json.decodeFromJsonElement(element)

    /** Phone notifications for new messages, new followers and songs added to shared playlists. */
    private fun notify(friends: FriendsReply, convos: List<Conversation>, collabs: List<Collab>) {
        val s = settings.current
        val me = friends.me.username
        if (s.notifyMessages) {
            convos.filter { it.unread > 0 && it.last.from != me && it.last.at > notifiedUpTo }.forEach { c ->
                val body = when (c.last.kind) {
                    "track" -> "Sent ${c.last.track?.title ?: "a song"}"
                    "playlist" -> "Shared ${c.last.playlist?.name ?: "a playlist"}"
                    else -> c.last.text.orEmpty()
                }
                post("msg:${c.with.username}", c.with.displayName, body, "spitify://user/${c.with.username}")
            }
        }
        convos.maxOfOrNull { it.last.at }?.let { notifiedUpTo = maxOf(notifiedUpTo, it) }

        val followers = friends.friends.filter { it.followsYou }.map { it.username }.toSet()
        knownFollowers?.let { before ->
            if (s.notifyFollows) (followers - before).forEach { n ->
                val f = friends.friends.first { it.username == n }
                post("follow:$n", "${f.displayName} followed you", "Follow back to see what each other is playing.", "spitify://user/$n")
            }
        }
        knownFollowers = followers

        val sizes = collabs.associate { it.id to it.tracks.size }
        knownCollabSizes?.let { before ->
            if (s.notifyCollab) collabs.forEach { c ->
                val was = before[c.id] ?: return@forEach
                val added = c.tracks.drop(was).filter { it.addedBy != me }
                if (added.isNotEmpty()) {
                    post("collab:${c.id}", c.name, "${added.size} new song${if (added.size == 1) "" else "s"} added", c.link)
                }
            }
        }
        knownCollabSizes = sizes
    }

    private fun post(key: String, title: String, text: String, link: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Friends and messages", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context,
            key.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(key.hashCode(), n) }
    }

    private companion object {
        const val CHANNEL = "social"
    }
}
