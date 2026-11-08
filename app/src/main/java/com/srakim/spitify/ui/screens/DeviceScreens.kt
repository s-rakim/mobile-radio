package com.srakim.spitify.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.LocalMusic
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.EmptyState
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.MediaCard
import com.srakim.spitify.ui.components.PlayCircle
import com.srakim.spitify.ui.components.SectionHeader
import com.srakim.spitify.ui.components.SkeletonTrackList
import com.srakim.spitify.ui.rememberLoad
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Songs saved on the phone. Asks for the audio permission the first time. */
@Composable
fun LocalFilesScreen() {
    val local = LocalCatalog.current.local
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks by local.tracks.collectAsStateWithLifecycle()
    val scanned by local.scanned.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    var granted by remember { mutableStateOf(local.hasPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) scope.launch { local.refresh() }
    }
    LaunchedEffect(granted) { if (granted) local.refresh() }

    val ctx = PlayContext("local", "Local Files", "LOCAL FILES")
    SimpleListScreen("Local Files") {
        when {
            !granted -> item {
                EmptyState(
                    "Play songs saved on this phone",
                    if (denied) {
                        "Spitify wasn't allowed to see your music. Turn on Music and audio for Spitify in your phone's settings."
                    } else {
                        "Songs you've downloaded or copied to your phone show up here, and in Search."
                    },
                    action = if (denied) "Open settings" else "Allow access",
                    onAction = {
                        if (denied) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        } else {
                            ask.launch(LocalMusic.PERMISSION)
                        }
                    },
                )
            }
            !scanned -> item { SkeletonTrackList() }
            tracks.isEmpty() -> item {
                EmptyState(
                    "No music on this phone yet",
                    "Copy MP3, FLAC or M4A files into your Music folder and they'll appear here.",
                    action = "Look again",
                    onAction = { scope.launch { local.refresh() } },
                )
            }
            else -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${tracks.size} songs on this phone", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        PlayCircle(
                            playing = playback.isPlayingFrom(ctx.id) && playback.isPlaying,
                            onClick = {
                                if (playback.isPlayingFrom(ctx.id)) player.togglePlayPause() else player.play(tracks, 0, ctx)
                            },
                        )
                    }
                }
                collectionTracks(
                    tracks, ctx, playback.current?.id, playback.isPlaying, likedIds,
                    onPlay = { player.play(tracks, it, ctx) },
                    onMore = { actions.showTrackMenu(it, null) },
                )
            }
        }
    }
}

/** A Navidrome or other Subsonic server: starred songs, playlists and the newest albums. */
@Composable
fun ServerScreen() {
    val server = LocalCatalog.current.server
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()

    val load = rememberLoad("server-home") {
        coroutineScope {
            val starred = async { runCatching { server.starred() }.getOrDefault(emptyList()) }
            val playlists = async { runCatching { server.playlists() }.getOrDefault(emptyList()) }
            val albums = async { server.recentAlbums() }
            Triple(starred.await(), playlists.await(), albums.await())
        }
    }
    val ctx = PlayContext("server-starred", "Starred on your server", "YOUR SERVER")

    SimpleListScreen("Your server") {
        if (!server.enabled) {
            item {
                EmptyState(
                    "Connect your own music server",
                    "Add your Navidrome or Subsonic address, username and password in Settings, under Music sources.",
                    action = "Open Settings",
                    onAction = actions::openSettings,
                )
            }
            return@SimpleListScreen
        }
        when (val state = load.state) {
            is Load.Loading -> item { SkeletonTrackList() }
            is Load.Failed -> item { ErrorState(state.message, load.retry) }
            is Load.Ready -> {
                val (starred, playlists, albums) = state.data
                if (albums.isNotEmpty()) {
                    item {
                        SectionHeader("Recently added")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(albums, key = { it.id }) { a ->
                                MediaCard(a.name, a.owner, onClick = { actions.openPlaylist(a.id) }, image = a.artwork)
                            }
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        SectionHeader("Playlists")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(playlists, key = { it.id }) { p ->
                                MediaCard(p.name, "${p.trackCount} songs", onClick = { actions.openPlaylist(p.id) }, image = p.artwork)
                            }
                        }
                    }
                }
                if (starred.isNotEmpty()) {
                    item { SectionHeader("Starred songs") }
                    collectionTracks(
                        starred, ctx, playback.current?.id, playback.isPlaying, likedIds,
                        onPlay = { player.play(starred, it, ctx) },
                        onMore = { actions.showTrackMenu(it, null) },
                    )
                }
                if (albums.isEmpty() && playlists.isEmpty() && starred.isEmpty()) {
                    item { EmptyState("Your server is empty", "Add some music to your library folder and give it a minute to scan.") }
                }
                item { Spacer(Modifier.padding(8.dp)) }
            }
        }
    }
}
