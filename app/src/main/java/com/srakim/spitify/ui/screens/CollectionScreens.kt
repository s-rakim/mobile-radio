package com.srakim.spitify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.Genres
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.RecentItem
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalDownloads
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.EmptyState
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.LikedSongsArt
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.SectionHeader
import com.srakim.spitify.ui.components.SkeletonTrackList
import com.srakim.spitify.ui.components.TrackRow
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.components.formatLongDuration
import com.srakim.spitify.ui.components.rememberArtworkColor
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.sheets.CreatePlaylistDialog
import com.srakim.spitify.ui.sheets.SheetOption
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
private fun CoverBlock(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        content(Modifier.size(236.dp).shadow(24.dp, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun HeaderText(title: String, description: String?, owner: String?, ownerImage: String?, meta: String) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (!owner.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(ownerImage, Modifier.size(22.dp), CircleShape)
                Spacer(Modifier.width(8.dp))
                Text(owner, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(meta, style = MaterialTheme.typography.bodySmall)
    }
}

private fun metaLine(tracks: List<Track>): String {
    val seconds = tracks.sumOf { it.durationSec.toLong() }
    val songs = if (tracks.size == 1) "1 song" else "${tracks.size} songs"
    return if (seconds > 0) "$songs • ${formatLongDuration(seconds)}" else songs
}

@Composable
fun PlaylistScreen(id: String) {
    val catalog = LocalCatalog.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()

    val load = rememberLoad("playlist", id) { catalog.playlist(id) }
    val playlist = (load.state as? Load.Ready)?.data?.first
    val tracks = (load.state as? Load.Ready)?.data?.second.orEmpty()
    val color by rememberArtworkColor(playlist?.artwork)

    LaunchedEffect(playlist?.id) {
        playlist?.let { library.recordVisit(RecentItem(RecentItem.Kind.PLAYLIST, it.id, it.name, "${it.label} • ${it.owner}", it.artwork)) }
    }

    val ctx = PlayContext("playlist:$id", playlist?.name ?: "Playlist", (playlist?.label ?: "Playlist").uppercase())
    CollectionScaffold(title = playlist?.name.orEmpty(), color = color, header = {
        when (val state = load.state) {
            is Load.Failed -> ErrorState(state.message, load.retry)
            else -> {
                CoverBlock { m -> Artwork(playlist?.artwork, m) }
                HeaderText(
                    title = playlist?.name ?: " ",
                    description = playlist?.description,
                    owner = playlist?.owner,
                    ownerImage = playlist?.ownerImage,
                    meta = if (tracks.isEmpty()) " " else metaLine(tracks),
                )
                if (playlist != null) {
                    CollectionActions(
                        tracks = tracks,
                        context = ctx,
                        saved = lib.savedPlaylists.any { it.id == playlist.id },
                        onToggleSave = {
                            val now = library.toggleSaved(playlist)
                            actions.toast(if (now) "Added to Your Library" else "Removed from Your Library")
                        },
                        extraMenu = { close ->
                            SheetOption(Icons.Rounded.Share, "Share", onClick = {
                                shareText(context, "${playlist.name}\n" + (playlist.link ?: "https://audius.co/playlists/${playlist.id}"))
                                close()
                            })
                        },
                    )
                }
            }
        }
    }) {
        if (load.state is Load.Loading) item { SkeletonTrackList() }
        collectionTracks(
            tracks, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { player.play(tracks, it, ctx) },
            onMore = { actions.showTrackMenu(it, null) },
        )
    }
}

@Composable
fun LikedSongsScreen() {
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    val tracks = lib.liked
    val ctx = PlayContext("liked", "Liked Songs", "LIKED SONGS")

    LaunchedEffect(Unit) { library.recordVisit(RecentItem(RecentItem.Kind.LIKED, "liked", "Liked Songs")) }

    CollectionScaffold(title = "Liked Songs", color = SpitifyColors.AccentDeep, header = {
        CoverBlock { m -> LikedSongsArt(m) }
        HeaderText("Liked Songs", null, null, null, metaLine(tracks))
        CollectionActions(tracks, ctx)
    }) {
        if (tracks.isEmpty()) {
            item {
                EmptyState("Songs you like will appear here", "Save songs by tapping the heart icon.", action = "Find songs", onAction = actions::openSearchInput)
            }
        }
        collectionTracks(
            tracks, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { player.play(tracks, it, ctx) },
            onMore = { actions.showTrackMenu(it, null) },
        )
    }
}

@Composable
fun MixScreen(genre: String) {
    val api = LocalApi.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    val load = rememberLoad("mix", genre) { api.trendingTracks(genre = genre, limit = 50) }
    val tracks = (load.state as? Load.Ready)?.data.orEmpty()
    val tileColor = Color(Genres.colorFor(genre))
    val title = "$genre Mix"
    val ctx = PlayContext("mix:$genre", title, "MIX")

    LaunchedEffect(genre) { library.recordVisit(RecentItem(RecentItem.Kind.MIX, genre, title, "Mix")) }

    CollectionScaffold(title = title, color = tileColor, header = {
        CoverBlock { m -> MixCover(genre, tileColor, m) }
        HeaderText(
            title,
            "The ${genre.lowercase()} songs everyone is playing this week, refreshed all the time.",
            "Spitify",
            null,
            if (tracks.isEmpty()) " " else metaLine(tracks),
        )
        CollectionActions(tracks, ctx)
    }) {
        when (val state = load.state) {
            is Load.Loading -> item { SkeletonTrackList() }
            is Load.Failed -> item { ErrorState(state.message, load.retry) }
            is Load.Ready -> collectionTracks(
                tracks, ctx, playback.current?.id, playback.isPlaying, likedIds,
                onPlay = { player.play(tracks, it, ctx) },
                onMore = { actions.showTrackMenu(it, null) },
            )
        }
    }
}

@Composable
fun MyPlaylistScreen(id: String) {
    val api = LocalApi.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    val playlist = lib.playlists.firstOrNull { it.id == id }
    var renaming by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    if (playlist == null) {
        Box(Modifier.fillMaxSize().background(SpitifyColors.Background).statusBarsPadding()) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            EmptyState("This playlist is gone", "It may have been deleted.", Modifier.align(Alignment.Center))
        }
        return
    }

    LaunchedEffect(playlist.id, playlist.name, playlist.tracks.firstOrNull()?.artwork) {
        library.recordVisit(RecentItem(RecentItem.Kind.MY_PLAYLIST, playlist.id, playlist.name, "Playlist • You", playlist.tracks.firstOrNull()?.artwork))
    }

    val recommended = rememberLoad("recommended", id, refresh) {
        val genre = playlist.tracks.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        api.trendingTracks(genre = genre, limit = 40).shuffled()
    }
    val inPlaylist = playlist.tracks.mapTo(HashSet()) { it.id }
    val suggestions = (recommended.state as? Load.Ready)?.data.orEmpty().filterNot { it.id in inPlaylist }.take(10)

    val color by rememberArtworkColor(playlist.tracks.firstOrNull()?.artwork)
    val ctx = PlayContext("mine:$id", playlist.name, "PLAYLIST")

    CollectionScaffold(title = playlist.name, color = color, header = {
        CoverBlock { m -> MosaicArt(playlist.tracks.map { it.artwork }, m) }
        HeaderText(
            playlist.name,
            playlist.description.ifBlank { null },
            "You",
            null,
            metaLine(playlist.tracks),
        )
        CollectionActions(
            playlist.tracks,
            ctx,
            extraMenu = { close ->
                SheetOption(Icons.Rounded.Edit, "Rename", onClick = {
                    close()
                    renaming = true
                })
                SheetOption(Icons.Rounded.AddCircleOutline, "Add songs", onClick = {
                    close()
                    actions.openSearchInput()
                })
                SheetOption(Icons.Rounded.Delete, "Delete playlist", onClick = {
                    close()
                    library.deletePlaylist(playlist.id)
                    actions.toast("Playlist deleted")
                    actions.back()
                })
            },
        )
    }) {
        if (playlist.tracks.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Let's start building your playlist", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    WhiteButton("Add to this playlist", onClick = actions::openSearchInput)
                }
            }
        }
        collectionTracks(
            playlist.tracks, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { player.play(playlist.tracks, it, ctx) },
            onMore = { actions.showTrackMenu(it, playlist.id) },
        )
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Recommended songs", Modifier.weight(1f))
                TextButton(onClick = { refresh++ }, modifier = Modifier.padding(top = 16.dp, end = 8.dp)) {
                    Text("Refresh", style = MaterialTheme.typography.labelMedium, color = SpitifyColors.TextSecondary)
                }
            }
            Text(
                "Based on what's in this playlist",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        suggestions.forEach { t ->
            item(key = "rec:${t.id}") {
                TrackRow(
                    track = t,
                    isCurrent = playback.current?.id == t.id,
                    isPlaying = playback.isPlaying,
                    liked = t.id in likedIds,
                    onClick = { player.play(suggestions, suggestions.indexOf(t), PlayContext("rec:$id", "Recommended", "RECOMMENDED")) },
                    onMore = { actions.showTrackMenu(t, null) },
                    trailing = {
                        IconButton(onClick = {
                            library.addToPlaylist(playlist.id, t)
                            actions.toast("Added to ${playlist.name}")
                        }) {
                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Add ${t.title} to playlist", tint = SpitifyColors.TextSecondary)
                        }
                    },
                )
            }
        }
    }

    if (renaming) {
        CreatePlaylistDialog(
            onDismiss = { renaming = false },
            onCreate = { name ->
                library.renamePlaylist(playlist.id, name)
                renaming = false
            },
            title = "Rename playlist",
            initial = playlist.name,
            confirm = "Save",
        )
    }
}

/** Everything played recently, newest first. */
@Composable
fun HistoryScreen() {
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    val ctx = PlayContext("history", "Recently played", "RECENTLY PLAYED")
    SimpleListScreen("Recently played") {
        if (lib.recentTracks.isEmpty()) {
            item { EmptyState("Nothing here yet", "Songs you play show up here.") }
        }
        collectionTracks(
            lib.recentTracks, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { player.play(lib.recentTracks, it, ctx) },
            onMore = { actions.showTrackMenu(it, null) },
        )
    }
}

@Composable
fun DownloadsScreen() {
    val downloads = LocalDownloads.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val dl by downloads.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    val ctx = PlayContext("downloads", "Downloaded", "DOWNLOADS")
    SimpleListScreen("Downloaded") {
        if (dl.downloaded.isEmpty()) {
            item { EmptyState("No downloads yet", "Tap the download button on any playlist or song to listen offline.") }
        } else {
            item {
                Text(
                    "${dl.downloaded.size} songs • ${formatBytes(downloads.bytesUsed())}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        collectionTracks(
            dl.downloaded, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { player.play(dl.downloaded, it, ctx) },
            onMore = { actions.showTrackMenu(it, null) },
        )
    }
}

@Composable
fun SimpleListScreen(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
        Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LocalBottomInset.current + 16.dp), content = content)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toFloat())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toFloat())
    else -> "$bytes B"
}

fun shareText(context: android.content.Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    context.startActivity(android.content.Intent.createChooser(send, "Share"))
}
