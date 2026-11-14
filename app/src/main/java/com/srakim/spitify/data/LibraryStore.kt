package com.srakim.spitify.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Genres the listener leans towards, strongest first. Likes count double, recent plays once,
 * and songs left out of the taste profile don't count at all.
 */
fun LibraryState.tasteGenres(): List<String> {
    val skip = tasteExcluded.toHashSet()
    val score = HashMap<String, Int>()
    fun add(track: Track, weight: Int) {
        if (track.id in skip) return
        val genre = track.genre?.takeIf { it.isNotBlank() } ?: return
        val key = Genres.all.firstOrNull { it.name.equals(genre, true) }?.name ?: return
        score[key] = (score[key] ?: 0) + weight
    }
    liked.forEach { add(it, 2) }
    recentTracks.forEach { add(it, 1) }
    return score.entries.sortedByDescending { it.value }.map { it.key }
}

@Serializable
data class LibraryState(
    val liked: List<Track> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val savedPlaylists: List<Playlist> = emptyList(),
    val followedArtists: List<Artist> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val recentItems: List<RecentItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    /** Songs the listener asked to keep out of their taste profile. */
    val tasteExcluded: List<String> = emptyList(),
)

/**
 * Everything the listener owns: likes, playlists, follows and history.
 * Stored as one small JSON file in app storage. Reads come from memory, writes are async.
 */
class LibraryStore(context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun isLiked(trackId: String) = _state.value.liked.any { it.id == trackId }

    /** Returns true when the track ends up liked. */
    fun toggleLike(track: Track): Boolean {
        val next = edit { state ->
            if (state.liked.any { it.id == track.id }) {
                state.copy(liked = state.liked.filterNot { it.id == track.id })
            } else {
                state.copy(liked = listOf(track) + state.liked)
            }
        }
        return next.liked.any { it.id == track.id }
    }

    fun createPlaylist(name: String): UserPlaylist {
        val now = System.currentTimeMillis()
        val playlist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "My playlist #${_state.value.playlists.size + 1}" },
            createdAt = now,
            updatedAt = now,
        )
        edit { it.copy(playlists = listOf(playlist) + it.playlists) }
        return playlist
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylist(id) { it.copy(name = name.trim().ifBlank { it.name }) }

    fun updateDescription(id: String, description: String) = updatePlaylist(id) { it.copy(description = description.trim()) }

    fun deletePlaylist(id: String) {
        edit { state ->
            state.copy(
                playlists = state.playlists.filterNot { it.id == id },
                recentItems = state.recentItems.filterNot { it.kind == RecentItem.Kind.MY_PLAYLIST && it.id == id },
            )
        }
    }

    /** Returns false if the song was already in there. */
    fun addToPlaylist(id: String, track: Track): Boolean {
        val playlist = _state.value.playlists.firstOrNull { it.id == id } ?: return false
        if (playlist.tracks.any { it.id == track.id }) return false
        updatePlaylist(id) { it.copy(tracks = it.tracks + track) }
        return true
    }

    fun removeFromPlaylist(id: String, trackId: String) = updatePlaylist(id) { pl ->
        pl.copy(tracks = pl.tracks.filterNot { it.id == trackId })
    }

    fun isSaved(playlistId: String) = _state.value.savedPlaylists.any { it.id == playlistId }

    fun toggleSaved(playlist: Playlist): Boolean {
        val next = edit { state ->
            if (state.savedPlaylists.any { it.id == playlist.id }) {
                state.copy(savedPlaylists = state.savedPlaylists.filterNot { it.id == playlist.id })
            } else {
                state.copy(savedPlaylists = listOf(playlist) + state.savedPlaylists)
            }
        }
        return next.savedPlaylists.any { it.id == playlist.id }
    }

    fun isFollowing(artistId: String) = _state.value.followedArtists.any { it.id == artistId }

    fun toggleFollow(artist: Artist): Boolean {
        val next = edit { state ->
            if (state.followedArtists.any { it.id == artist.id }) {
                state.copy(followedArtists = state.followedArtists.filterNot { it.id == artist.id })
            } else {
                state.copy(followedArtists = listOf(artist) + state.followedArtists)
            }
        }
        return next.followedArtists.any { it.id == artist.id }
    }

    fun recordPlay(track: Track) {
        edit { state ->
            state.copy(recentTracks = (listOf(track) + state.recentTracks.filterNot { it.id == track.id }).take(50))
        }
    }

    fun isTasteExcluded(trackId: String) = trackId in _state.value.tasteExcluded

    /** Returns true when the song is now left out of the taste profile. */
    fun toggleTasteExcluded(track: Track): Boolean {
        val next = edit { state ->
            val ids = state.tasteExcluded
            state.copy(tasteExcluded = if (track.id in ids) ids - track.id else (listOf(track.id) + ids).take(500))
        }
        return track.id in next.tasteExcluded
    }

    fun recordVisit(item: RecentItem) {
        val stamped = item.copy(openedAt = System.currentTimeMillis())
        edit { state ->
            val rest = state.recentItems.filterNot { it.kind == item.kind && it.id == item.id }
            state.copy(recentItems = (listOf(stamped) + rest).take(30))
        }
    }

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        edit { state ->
            state.copy(recentSearches = (listOf(q) + state.recentSearches.filterNot { it.equals(q, true) }).take(15))
        }
    }

    fun removeRecentSearch(query: String) = edit { it.copy(recentSearches = it.recentSearches - query) }

    fun clearRecentSearches() = edit { it.copy(recentSearches = emptyList()) }

    /** Swaps in the account's copy of likes, playlists and follows. History stays as it is. */
    fun replaceSynced(lib: SyncedLibrary) {
        edit {
            it.copy(
                liked = lib.liked,
                playlists = lib.playlists,
                savedPlaylists = lib.savedPlaylists,
                followedArtists = lib.followedArtists,
            )
        }
    }

    fun clearHistory() = edit { it.copy(recentTracks = emptyList(), recentItems = emptyList()) }

    private fun updatePlaylist(id: String, block: (UserPlaylist) -> UserPlaylist) {
        edit { state ->
            state.copy(
                playlists = state.playlists.map {
                    if (it.id == id) block(it).copy(updatedAt = System.currentTimeMillis()) else it
                },
            )
        }
    }

    private fun edit(block: (LibraryState) -> LibraryState): LibraryState {
        val next = _state.updateAndGet(block)
        io.launch {
            writeLock.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(json.encodeToString(_state.value))
                    if (!tmp.renameTo(file)) {
                        file.writeText(tmp.readText())
                        tmp.delete()
                    }
                }
            }
        }
        return next
    }

    private fun load(): LibraryState = runCatching {
        if (file.exists()) json.decodeFromString<LibraryState>(file.readText()) else LibraryState()
    }.getOrElse { LibraryState() }
}
