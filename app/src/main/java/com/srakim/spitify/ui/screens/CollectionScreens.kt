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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MergeType
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.Animatable
import com.srakim.spitify.ui.components.Motion
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
import kotlinx.coroutines.delay
import com.srakim.spitify.ui.components.TrackRow
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.components.formatLongDuration
import com.srakim.spitify.ui.components.rememberArtworkColor
import com.srakim.spitify.ui.components.rememberArtworkColors
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.sheets.CreatePlaylistDialog
import com.srakim.spitify.ui.sheets.SheetOption
import com.srakim.spitify.ui.sheets.LIKED_PLAYLIST
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Old name kept for the friends' pages. */
@Composable
private fun HeaderText(title: String, description: String?, owner: String?, ownerImage: String?, meta: String) =
    CollectionHeaderText(title, description, owner, ownerImage, meta)

private fun metaLine(tracks: List<Track>, saves: Int = 0): String {
    val seconds = tracks.sumOf { it.durationSec.toLong() }
    val parts = buildList {
        if (saves > 0) add("${"%,d".format(saves)} saves")
        if (seconds > 0) add(formatLongDuration(seconds)) else add(if (tracks.size == 1) "1 song" else "${tracks.size} songs")
    }
    return parts.joinToString(" • ")
}

@Composable
fun PlaylistScreen(id: String) {
    val catalog = LocalCatalog.current
    val api = LocalApi.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TrackSort.CUSTOM) }
    var sorting by remember { mutableStateOf(false) }

    val load = rememberLoad("playlist", id) { catalog.playlist(id) }
    val playlist = (load.state as? Load.Ready)?.data?.first
    val tracks = (load.state as? Load.Ready)?.data?.second.orEmpty()
    val shown = remember(tracks, query, sort) { tracks.filterAndSort(query, sort) }
    val isAlbum = playlist?.label?.contains("Album", ignoreCase = true) == true
    val art = rememberArtworkColors(playlist?.artwork)
    val color = art.primary

    // Albums end with more from the same artist, like Spotify's "More by".
    val moreBy = rememberLoad("moreBy", playlist?.ownerId, isAlbum) {
        val owner = playlist?.ownerId.orEmpty()
        // Spotify albums get the artist's own Spotify releases; Audius ones their Audius albums.
        if (!isAlbum || owner.isBlank()) emptyList()
        else runCatching { catalog.releases(owner) }.getOrDefault(emptyList())
            .filter { it.id != id && it.label != com.srakim.spitify.data.SpotifyMeta.APPEARS_ON }
    }
    // A dropped connection gets a few quiet retries behind the skeleton before saying anything.
    var tries by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(load.state) {
        if (load.state is Load.Failed && tries < 4) {
            delay(2_000L shl tries)
            tries++
            load.retry()
        }
    }
    val gaveUp = load.state is Load.Failed && tries >= 4

    LaunchedEffect(playlist?.id) {
        playlist?.let { library.recordVisit(RecentItem(RecentItem.Kind.PLAYLIST, it.id, it.name, "${it.label} • ${it.owner}", it.artwork)) }
    }

    val ctx = PlayContext("playlist:$id", playlist?.name ?: "Playlist", (playlist?.label ?: "Playlist").uppercase())
    CollectionScaffold(title = playlist?.name.orEmpty(), color = color, secondary = art.secondary, header = {
        when (val state = load.state) {
            is Load.Failed -> if (gaveUp) ErrorState(state.message, { tries = 0; load.retry() }) else SkeletonTrackList()
            else -> {
                if (!isAlbum) {
                    FindInCollection(query, { query = it }, sortLabel = sort.label, onSort = { sorting = true })
                }
                CoverBlock { m -> Artwork(playlist?.artwork, m) }
                if (isAlbum) {
                    CollectionHeaderText(
                        title = playlist?.name ?: " ",
                        owner = playlist?.owner,
                        ownerImage = playlist?.ownerImage,
                        meta = listOfNotNull(playlist?.label, playlist?.year).joinToString(" • "),
                        onOwner = playlist?.ownerId?.takeIf { it.isNotBlank() }?.let { o -> { actions.openArtist(o) } },
                    )
                } else {
                    CollectionHeaderText(
                        title = playlist?.name ?: " ",
                        description = playlist?.description,
                        owner = playlist?.owner,
                        ownerImage = playlist?.ownerImage,
                        meta = if (tracks.isEmpty()) " " else metaLine(tracks, playlist?.saves ?: 0),
                        globe = true,
                        onOwner = playlist?.ownerId?.takeIf { it.isNotBlank() }?.let { o -> { actions.openArtist(o) } },
                    )
                }
                if (playlist != null) {
                    CollectionActions(
                        tracks = tracks,
                        context = ctx,
                        saved = lib.savedPlaylists.any { it.id == playlist.id },
                        onToggleSave = {
                            val now = library.toggleSaved(playlist)
                            actions.toast(if (now) "Added to Your Library" else "Removed from Your Library")
                        },
                        thumb = { m -> Artwork(playlist.artwork, m, RoundedCornerShape(2.dp)) },
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
        if (playlist?.songsWithheld == true && tracks.isEmpty()) {
            item {
                EmptyState(
                    "Spotify won't share these songs",
                    "Spotify shows apps like Spitify this playlist but not what's in it. That happens with playlists other people made. Save the songs into a playlist of your own in Spotify and it'll open here.",
                )
            }
        }
        collectionTracks(
            shown, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { i -> player.play(tracks, tracks.indexOf(shown[i]).coerceAtLeast(0), ctx) },
            onMore = { actions.showTrackMenu(it, null) },
            showArtwork = !isAlbum,
        )
        if (query.isNotBlank() && shown.isEmpty() && tracks.isNotEmpty()) {
            item { EmptyState("Couldn't find \"$query\"", "Try searching again using a different spelling or keyword.") }
        }
        if (playlist != null && tracks.isNotEmpty()) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                    if (isAlbum && playlist.year != null) {
                        Text(playlist.year, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "${tracks.size} ${if (tracks.size == 1) "song" else "songs"} • ${formatLongDuration(tracks.sumOf { it.durationSec.toLong() })}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (isAlbum) {
                        Spacer(Modifier.height(14.dp))
                        Text("© ${playlist.year ?: ""} ${playlist.owner}".trim(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                    }
                }
            }
        }
        val more = (moreBy.state as? Load.Ready)?.data.orEmpty()
        if (more.isNotEmpty()) {
            item {
                SectionHeader("More by ${playlist?.owner.orEmpty()}")
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(more.size, key = { more[it].id }) { i ->
                        val p = more[i]
                        com.srakim.spitify.ui.components.MediaCard(p.name, listOfNotNull(p.year, "Album").joinToString(" • "), onClick = { actions.openPlaylist(p.id) }, image = p.artwork)
                    }
                }
            }
        }
    }
    if (sorting) SortSheet(sort, { sort = it }, { sorting = false })
}

@Composable
fun LikedSongsScreen() {
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    // Signed in to Spotify: your Spotify likes join the ones saved in Spitify.
    val spotifyLib by com.srakim.spitify.ui.LocalApp.current.spotifyLibrary.state.collectAsStateWithLifecycle()
    val tracks = remember(lib.liked, spotifyLib.liked) { (lib.liked + spotifyLib.liked).distinctBy { it.id } }
    val ctx = PlayContext("liked", "Liked Songs", "LIKED SONGS")

    LaunchedEffect(Unit) { library.recordVisit(RecentItem(RecentItem.Kind.LIKED, "liked", "Liked Songs")) }

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TrackSort.CUSTOM) }
    var sorting by remember { mutableStateOf(false) }
    val shown = remember(tracks, query, sort) { tracks.filterAndSort(query, sort) }
    if (sorting) SortSheet(sort, { sort = it }, { sorting = false })

    CollectionScaffold(title = "Liked Songs", color = androidx.compose.ui.graphics.lerp(com.srakim.spitify.ui.components.LikedIndigo, Color.Black, 0.25f), header = {
        FindInCollection(query, { query = it }, sort.label, { sorting = true }, hint = "Find in Liked Songs")
        CoverBlock { m -> LikedSongsArt(m) }
        CollectionHeaderText("Liked Songs", meta = if (tracks.size == 1) "1 song" else "${tracks.size} songs")
        CollectionActions(tracks, ctx)
    }) {
        if (tracks.isEmpty()) {
            item {
                EmptyState("Songs you like will appear here", "Save songs by tapping the heart icon.", action = "Find songs", onAction = actions::openSearchInput)
            }
        }
        collectionTracks(
            shown, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { i -> player.play(tracks, tracks.indexOf(shown[i]).coerceAtLeast(0), ctx) },
            onMore = { actions.showTrackMenu(it, LIKED_PLAYLIST) },
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

    CollectionScaffold(title = title, color = tileColor, secondary = lerp(tileColor, Color.Black, 0.55f), header = {
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

    val art = rememberArtworkColors(playlist.tracks.firstOrNull()?.artwork)
    val color = art.primary
    val ctx = PlayContext("mine:$id", playlist.name, "PLAYLIST")

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TrackSort.CUSTOM) }
    var sorting by remember { mutableStateOf(false) }
    val shown = remember(playlist.tracks, query, sort) { playlist.tracks.filterAndSort(query, sort) }
    if (sorting) SortSheet(sort, { sort = it }, { sorting = false })

    CollectionScaffold(title = playlist.name, color = color, secondary = art.secondary, header = {
        if (playlist.tracks.isNotEmpty()) FindInCollection(query, { query = it }, sort.label, { sorting = true })
        CoverBlock { m -> MosaicArt(playlist.tracks.map { it.artwork }, m) }
        CollectionHeaderText(
            playlist.name,
            playlist.description.ifBlank { null },
            "You",
            null,
            metaLine(playlist.tracks),
        )
        CollectionActions(
            playlist.tracks,
            ctx,
            thumb = { m -> MosaicArt(playlist.tracks.map { it.artwork }, m) },
            extraMenu = { close ->
                SheetOption(Icons.Rounded.Edit, "Rename", onClick = {
                    close()
                    renaming = true
                })
                SheetOption(Icons.Rounded.AddCircleOutline, "Add songs", onClick = {
                    close()
                    actions.openSearchInput()
                })
                SheetOption(Icons.Rounded.MergeType, if (playlist.mixed) "Mixed playlist • On" else "Mixed playlist • Off", onClick = {
                    close()
                    library.setMixed(playlist.id, !playlist.mixed)
                    actions.toast(if (playlist.mixed) "Songs play one after another again" else "Songs now blend into each other")
                })
                SheetOption(Icons.Rounded.Folder, if (playlist.folderId == null) "Move to folder" else "Move to another folder", onClick = {
                    close()
                    actions.showSheet(com.srakim.spitify.ui.AppSheet.MoveToFolder(playlist.id))
                })
                if (playlist.tracks.isNotEmpty()) {
                    SheetOption(Icons.AutoMirrored.Rounded.Send, "Send to a friend", onClick = {
                        close()
                        actions.showSheet(
                            com.srakim.spitify.ui.AppSheet.SendTo(
                                playlist = com.srakim.spitify.data.SharedPlaylist(playlist.id, playlist.name, playlist.tracks.firstOrNull()?.artwork, playlist.tracks),
                            ),
                        )
                    })
                }
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
            shown, ctx, playback.current?.id, playback.isPlaying, likedIds,
            onPlay = { i -> player.play(playlist.tracks, playlist.tracks.indexOf(shown[i]).coerceAtLeast(0), ctx) },
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
    val ctx = PlayContext("history", "Recent songs", "RECENT SONGS")
    SimpleListScreen("Recent songs") {
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
