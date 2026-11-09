package com.srakim.spitify.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.srakim.spitify.SpitifyApp
import com.srakim.spitify.data.AudiusApi
import com.srakim.spitify.data.Catalog
import com.srakim.spitify.data.DownloadStore
import com.srakim.spitify.data.LyricsApi
import com.srakim.spitify.data.SettingsStore
import com.srakim.spitify.data.ThemeStore
import com.srakim.spitify.data.LibraryStore
import com.srakim.spitify.data.Track
import com.srakim.spitify.playback.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

val LocalApi = staticCompositionLocalOf<AudiusApi> { error("No AudiusApi provided") }
val LocalLibrary = staticCompositionLocalOf<LibraryStore> { error("No LibraryStore provided") }
val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("No PlayerController provided") }
val LocalActions = staticCompositionLocalOf<AppActions> { error("No AppActions provided") }
val LocalSettings = staticCompositionLocalOf<SettingsStore> { error("No SettingsStore provided") }
val LocalDownloads = staticCompositionLocalOf<DownloadStore> { error("No DownloadStore provided") }
val LocalThemes = staticCompositionLocalOf<ThemeStore> { error("No ThemeStore provided") }
val LocalLyrics = staticCompositionLocalOf<LyricsApi> { error("No LyricsApi provided") }
val LocalApp = staticCompositionLocalOf<SpitifyApp> { error("No app provided") }
val LocalCatalog = staticCompositionLocalOf<Catalog> { error("No Catalog provided") }

/** Data saver swaps artwork for the smallest size the API offers. */
val LocalDataSaver = staticCompositionLocalOf { false }

/** Space the floating mini player and nav bar take up, so lists can scroll clear of them. */
val LocalBottomInset = staticCompositionLocalOf<Dp> { 0.dp }

enum class Tab(val route: String, val label: String) {
    HOME("home", "Home"),
    SEARCH("search", "Search"),
    LIBRARY("library", "Your Library"),
}

/** Detail destinations exist once per tab so each tab keeps its own back stack. */
object Dest {
    const val PLAYLIST = "playlist/{id}"
    const val MY_PLAYLIST = "mine/{id}"
    const val LIKED = "liked"
    const val MIX = "mix/{genre}"
    const val ARTIST = "artist/{id}"
    const val SEARCH_INPUT = "search/find"
    const val SETTINGS = "settings"
    const val EQUALIZER = "settings/equalizer"
    const val THEMES = "settings/theme"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val LOCAL_FILES = "local"
    const val SERVER = "server"
    const val ACCOUNT = "account"
}

/** App-wide actions any screen can trigger: navigation, sheets, toasts. */
class AppActions(
    private val nav: NavHostController,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
    val showTrackMenu: (track: Track, playlistId: String?) -> Unit,
    val showAddToPlaylist: (track: Track) -> Unit,
    val showCreatePlaylist: (withTrack: Track?) -> Unit,
    val openPlayer: () -> Unit,
    val closePlayer: () -> Unit,
) {
    private fun currentTab(): String =
        nav.currentBackStackEntry?.destination?.route?.substringBefore('/') ?: Tab.HOME.route

    private fun go(path: String) {
        closePlayer()
        nav.navigate("${currentTab()}/$path")
    }

    fun openPlaylist(id: String) = go("playlist/${android.net.Uri.encode(id)}")
    fun openMyPlaylist(id: String) = go("mine/$id")
    fun openLiked() = go("liked")
    fun openMix(genre: String) = go("mix/${android.net.Uri.encode(genre)}")
    fun openArtist(id: String) = go("artist/${android.net.Uri.encode(id)}")
    fun openSearchInput() = nav.navigate("search/find")

    /** Opens the search screen already searching for [query]. */
    fun searchFor(query: String) {
        closePlayer()
        nav.navigate("search/find/${android.net.Uri.encode(query)}")
    }

    /**
     * Tapping an artist's name, like in Spotify: their page when the song's source has one,
     * otherwise a search for their name, which lists their songs from every source.
     */
    fun openArtistOf(track: Track) = when {
        track.artistId.isNotBlank() -> openArtist(track.artistId)
        track.artistSearchName.isNotBlank() -> searchFor(track.artistSearchName)
        else -> Unit
    }
    fun openSettings() = go("settings")
    fun openEqualizer() = go("settings/equalizer")
    fun openThemes() = go("settings/theme")
    fun openHistory() = go("history")
    fun openDownloads() = go("downloads")
    fun openAccount() = go("account")
    fun openLocalFiles() = go("local")
    fun openServer() = go("server")
    fun back() = nav.popBackStack()

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val retry: () -> Unit)

/** Runs [fetch] whenever [keys] change and exposes loading, data and error with a retry hook. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, fetch: suspend () -> T): Loader<T> {
    var attempt by remember { mutableIntStateOf(0) }
    val latestFetch by rememberUpdatedState(fetch)
    val state by produceState<Load<T>>(Load.Loading, *keys, attempt) {
        value = Load.Loading
        value = try {
            Load.Ready(latestFetch())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e.friendlyMessage())
        }
    }
    return Loader(state) { attempt++ }
}

fun Throwable.friendlyMessage(): String = when (this) {
    is UnknownHostException -> "You're offline. Check your connection and try again."
    is IOException -> "Couldn't reach the music service. Try again in a moment."
    else -> "Something went wrong. Try again."
}
