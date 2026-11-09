package com.srakim.spitify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.RecentItem
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalDownloads
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalSettings
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.appear
import com.srakim.spitify.ui.components.EmptyState
import com.srakim.spitify.ui.components.IconArt
import com.srakim.spitify.ui.components.LikedSongsArt
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.Pill
import com.srakim.spitify.ui.components.ProfileBadge
import com.srakim.spitify.ui.theme.SpitifyColors

private enum class LibraryFilter(val label: String) { PLAYLISTS("Playlists"), ARTISTS("Artists"), DOWNLOADED("Downloaded") }
private enum class LibrarySort(val label: String) { RECENTS("Recents"), ADDED("Recently added"), ALPHA("Alphabetical") }

private data class LibraryEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val kind: RecentItem.Kind,
    val id: String,
    val image: String?,
    val images: List<String?> = emptyList(),
    val addedAt: Long = 0,
    val pinned: Boolean = false,
    /** "local", "server" or "recent" for the built-in collections that aren't playlists. */
    val special: String? = null,
)

@Composable
fun LibraryScreen() {
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val downloads = LocalDownloads.current
    val lib by library.state.collectAsStateWithLifecycle()
    val dl by downloads.state.collectAsStateWithLifecycle()
    val settings by LocalSettings.current.state.collectAsStateWithLifecycle()
    val catalog = LocalCatalog.current
    val localTracks by catalog.local.tracks.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf<LibraryFilter?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENTS) }
    var grid by rememberSaveable { mutableStateOf(false) }

    val recency = lib.recentItems.withIndex().associate { (i, item) -> "${item.kind}:${item.id}" to i }

    val entries = buildList {
        if (filter == null || filter == LibraryFilter.PLAYLISTS) {
            add(LibraryEntry("LIKED:liked", "Liked Songs", "Playlist • ${lib.liked.size} songs", RecentItem.Kind.LIKED, "liked", null, pinned = true))
            val recentSubtitle = if (lib.recentTracks.isEmpty()) "Songs you play show up here" else "Playlist • ${lib.recentTracks.size} songs"
            add(LibraryEntry("RECENT:recent", "Recent songs", recentSubtitle, RecentItem.Kind.PLAYLIST, "recent", null, pinned = true, special = "recent"))
            if (settings.showLocalFiles) {
                val subtitle = if (localTracks.isEmpty()) "Songs saved on this phone" else "Playlist • ${localTracks.size} songs"
                add(LibraryEntry("LOCAL:local", "Local Files", subtitle, RecentItem.Kind.PLAYLIST, "local", null, pinned = true, special = "local"))
            }
            if (catalog.server.enabled) {
                add(LibraryEntry("SERVER:server", "Your server", "Navidrome / Subsonic", RecentItem.Kind.PLAYLIST, "server", null, pinned = true, special = "server"))
            }
            lib.playlists.forEach { p ->
                add(
                    LibraryEntry(
                        "MY_PLAYLIST:${p.id}", p.name, "Playlist • You", RecentItem.Kind.MY_PLAYLIST, p.id,
                        p.tracks.firstOrNull()?.artwork, p.tracks.map { it.artwork }, p.createdAt,
                    ),
                )
            }
            lib.savedPlaylists.forEachIndexed { i, p ->
                add(LibraryEntry("PLAYLIST:${p.id}", p.name, "${p.label} • ${p.owner}", RecentItem.Kind.PLAYLIST, p.id, p.artwork, addedAt = -i.toLong()))
            }
        }
        if (filter == null || filter == LibraryFilter.ARTISTS) {
            lib.followedArtists.forEachIndexed { i, a ->
                add(LibraryEntry("ARTIST:${a.id}", a.name, "Artist", RecentItem.Kind.ARTIST, a.id, a.picture, addedAt = -i.toLong()))
            }
        }
    }.let { list ->
        val (pinned, rest) = list.partition { it.pinned }
        pinned + when (sort) {
            LibrarySort.RECENTS -> rest.sortedBy { recency[it.key] ?: Int.MAX_VALUE }
            LibrarySort.ADDED -> rest.sortedByDescending { it.addedAt }
            LibrarySort.ALPHA -> rest.sortedBy { it.title.lowercase() }
        }
    }

    fun openKind(e: LibraryEntry) = when (e.kind) {
        RecentItem.Kind.LIKED -> actions.openLiked()
        RecentItem.Kind.MY_PLAYLIST -> actions.openMyPlaylist(e.id)
        RecentItem.Kind.PLAYLIST -> actions.openPlaylist(e.id)
        RecentItem.Kind.ARTIST -> actions.openArtist(e.id)
        RecentItem.Kind.MIX -> actions.openMix(e.id)
    }

    fun open(e: LibraryEntry) = when {
        e.special == "local" -> actions.openLocalFiles()
        e.special == "server" -> actions.openServer()
        e.special == "recent" -> actions.openHistory()
        else -> openKind(e)
    }

    LazyVerticalGrid(
        columns = if (grid) GridCells.Fixed(3) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize().background(SpitifyColors.Background),
        contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileBadge(onClick = actions::openSettings)
                    Spacer(Modifier.width(12.dp))
                    Text("Your Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = actions::openHistory) {
                        Icon(Icons.Rounded.History, contentDescription = "Listening history")
                    }
                    IconButton(onClick = { actions.showCreatePlaylist(null) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create playlist")
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (filter != null) {
                        IconButton(onClick = { filter = null }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear filter", modifier = Modifier.size(18.dp))
                        }
                    }
                    LibraryFilter.entries.filter { filter == null || filter == it }.forEach { f ->
                        Pill(f.label, selected = filter == f, onClick = {
                            if (f == LibraryFilter.DOWNLOADED) actions.openDownloads() else filter = if (filter == f) null else f
                        })
                    }
                }
                Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clickable { sort = LibrarySort.entries[(sort.ordinal + 1) % LibrarySort.entries.size] }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(sort.label, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { grid = !grid }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView,
                            contentDescription = if (grid) "Show as list" else "Show as grid",
                        )
                    }
                }
            }
        }

        if (dl.downloaded.isNotEmpty() && filter == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = actions::openDownloads).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DownloadForOffline, contentDescription = null, tint = SpitifyColors.Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Downloaded • ${dl.downloaded.size} songs", style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary)
                }
            }
        }

        if (entries.size <= 1 && filter == LibraryFilter.ARTISTS) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("Follow your first artist", "Tap Follow on any artist page and they'll show up here.")
            }
        }

        itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
            Box(Modifier.appear(i)) {
                if (grid) GridEntry(e, onClick = { open(e) }) else ListEntry(e, onClick = { open(e) })
            }
        }

        if (lib.playlists.isEmpty() && filter != LibraryFilter.ARTISTS) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    "Create your first playlist",
                    "It's easy, we'll help you.",
                    action = "Create playlist",
                    onAction = { actions.showCreatePlaylist(null) },
                )
            }
        }
    }
}

@Composable
private fun EntryArt(e: LibraryEntry, modifier: Modifier) {
    when {
        e.special == "local" -> IconArt(Icons.Rounded.Folder, Color(0xFF059669), Color(0xFF064E3B), modifier)
        e.special == "server" -> IconArt(Icons.Rounded.Dns, Color(0xFF0891B2), Color(0xFF164E63), modifier)
        e.special == "recent" -> IconArt(Icons.Rounded.History, Color(0xFFEA580C), Color(0xFF9A3412), modifier)
        else -> KindArt(e, modifier)
    }
}

@Composable
private fun KindArt(e: LibraryEntry, modifier: Modifier) {
    when (e.kind) {
        RecentItem.Kind.LIKED -> LikedSongsArt(modifier)
        RecentItem.Kind.ARTIST -> Artwork(e.image, modifier, CircleShape)
        RecentItem.Kind.MY_PLAYLIST -> MosaicArt(e.images, modifier)
        else -> Artwork(e.image, modifier)
    }
}

@Composable
private fun ListEntry(e: LibraryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryArt(e, Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = SpitifyColors.Accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(e.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GridEntry(e: LibraryEntry, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(start = 0.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .padding(horizontal = 4.dp),
    ) {
        EntryArt(e, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(e.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(e.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
