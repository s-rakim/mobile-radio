package com.srakim.spitify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.Artist
import com.srakim.spitify.data.Genres
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Playlist
import com.srakim.spitify.data.RecentItem
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.LocalSettings
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.LikedSongsArt
import com.srakim.spitify.ui.components.MediaCard
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.Pill
import com.srakim.spitify.ui.components.ProfileBadge
import com.srakim.spitify.ui.components.SectionHeader
import com.srakim.spitify.ui.components.ShortcutTile
import com.srakim.spitify.ui.components.SkeletonShelf
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Calendar

private enum class HomeFilter(val label: String) { ALL("All"), MUSIC("Music"), PLAYLISTS("Playlists"), ARTISTS("Artists") }

private data class HomeFeed(
    val trending: List<Track>,
    val monthly: List<Track>,
    val allTime: List<Track>,
    val playlists: List<Playlist>,
) {
    val artists: List<Artist> = (trending + monthly)
        .distinctBy { it.artistId }
        .filter { it.artistId.isNotBlank() }
        .take(12)
        .map { Artist(id = it.artistId, name = it.artist, picture = it.artistImage) }
}

/** One of the 2x4 tiles at the top. */
private data class Shortcut(val key: String, val title: String, val art: @Composable (Modifier) -> Unit, val open: () -> Unit)

@Composable
fun HomeScreen() {
    val api = LocalApi.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val settings by LocalSettings.current.state.collectAsStateWithLifecycle()
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    val catalog = LocalCatalog.current
    val freePicks = rememberLoad("jamendo-popular", settings.jamendoEnabled, settings.jamendoClientId) {
        runCatching { catalog.jamendo.popularTracks() }.getOrDefault(emptyList())
    }
    val netlabels = rememberLoad("ia-netlabels", settings.archiveEnabled) {
        runCatching { catalog.archive.popularNetlabelAlbums() }.getOrDefault(emptyList())
    }
    val liveShows = rememberLoad("ia-live", settings.archiveEnabled) {
        runCatching { catalog.archive.popularLiveAlbums() }.getOrDefault(emptyList())
    }
    val remixes = rememberLoad("cc-popular", settings.ccMixterEnabled) {
        runCatching { catalog.ccMixter.popularTracks() }.getOrDefault(emptyList())
    }
    val serverAlbums = rememberLoad("server-albums", settings.subsonicUrl, settings.subsonicUser, settings.subsonicPassword, settings.subsonicEnabled) {
        if (catalog.server.enabled) runCatching { catalog.server.recentAlbums() }.getOrDefault(emptyList()) else emptyList()
    }

    val feed = rememberLoad("home") {
        coroutineScope {
            val week = async { api.trendingTracks(time = "week") }
            val month = async { api.trendingTracks(time = "month") }
            val all = async { api.trendingTracks(time = "allTime", limit = 30) }
            val playlists = async { api.trendingPlaylists() }
            HomeFeed(week.await(), month.await(), all.await(), playlists.await())
        }
    }

    val accentGlow = SpitifyColors.Accent.copy(alpha = 0.22f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to accentGlow, 0.25f to SpitifyColors.Background)),
        contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp),
    ) {
        item {
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileBadge(onClick = actions::openSettings)
                Spacer(Modifier.width(2.dp))
                HomeFilter.entries.forEach { f ->
                    Pill(f.label, selected = filter == f, onClick = { filter = if (filter == f && f != HomeFilter.ALL) HomeFilter.ALL else f })
                }
            }
        }

        item {
            Text(
                greeting(settings.profileName),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp),
            )
        }

        item {
            val trendingPlaylists = (feed.state as? Load.Ready)?.data?.playlists.orEmpty()
            val tiles = buildShortcuts(lib.recentItems, lib.liked.isNotEmpty() || lib.recentItems.isEmpty(), trendingPlaylists, actions)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { tile -> ShortcutTile(tile.title, tile.open, Modifier.weight(1f), tile.art) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        val showMusic = filter == HomeFilter.ALL || filter == HomeFilter.MUSIC
        val showPlaylists = filter == HomeFilter.ALL || filter == HomeFilter.PLAYLISTS
        val showArtists = filter == HomeFilter.ALL || filter == HomeFilter.ARTISTS

        if (showMusic && lib.recentTracks.isNotEmpty()) {
            item {
                SectionHeader("Recently played", action = "Show all", onAction = actions::openHistory)
                TrackShelf(lib.recentTracks.take(15), PlayContext("recent", "Recently played", "RECENTLY PLAYED"), playback.current?.id)
            }
        }

        when (val state = feed.state) {
            is Load.Loading -> items(3) { SkeletonShelf() }
            is Load.Failed -> item { ErrorState(state.message, feed.retry) }
            is Load.Ready -> {
                val data = state.data
                if (showMusic) {
                    item {
                        SectionHeader("Made for you")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(Genres.all.take(10), key = { it.name }) { genre ->
                                MediaCard(
                                    title = "${genre.name} Mix",
                                    subtitle = "The biggest ${genre.name.lowercase()} tracks right now",
                                    onClick = { actions.openMix(genre.name) },
                                    art = { m -> MixCover(genre.name, Color(genre.color), m) },
                                )
                            }
                        }
                    }
                    item {
                        SectionHeader("Trending this week")
                        TrackShelf(data.trending, PlayContext("trending-week", "Trending this week", "TRENDING"), playback.current?.id)
                    }
                }
                val mine = (serverAlbums.state as? Load.Ready)?.data.orEmpty()
                if (showMusic && mine.isNotEmpty()) {
                    item {
                        SectionHeader("New on your server", action = "Show all", onAction = actions::openServer)
                        AlbumShelf(mine)
                    }
                }
                val picks = (freePicks.state as? Load.Ready)?.data.orEmpty()
                if (showMusic && picks.isNotEmpty()) {
                    item {
                        SectionHeader("Free picks from Jamendo")
                        TrackShelf(picks, PlayContext("jamendo-popular", "Free picks from Jamendo", "JAMENDO"), playback.current?.id)
                    }
                }
                val labelAlbums = (netlabels.state as? Load.Ready)?.data.orEmpty()
                if (showMusic && labelAlbums.isNotEmpty()) {
                    item {
                        SectionHeader("Netlabel albums")
                        AlbumShelf(labelAlbums)
                    }
                }
                val ccPicks = (remixes.state as? Load.Ready)?.data.orEmpty()
                if (showMusic && ccPicks.isNotEmpty()) {
                    item {
                        SectionHeader("Remixes from ccMixter")
                        TrackShelf(ccPicks, PlayContext("cc-popular", "Remixes from ccMixter", "CCMIXTER"), playback.current?.id)
                    }
                }
                if (showPlaylists && data.playlists.isNotEmpty()) {
                    item {
                        SectionHeader("Popular playlists")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(data.playlists, key = { it.id }) { pl ->
                                MediaCard(pl.name, "By ${pl.owner}", onClick = { actions.openPlaylist(pl.id) }, image = pl.artwork)
                            }
                        }
                    }
                }
                if (showArtists && data.artists.isNotEmpty()) {
                    item {
                        SectionHeader("Artists you might like")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(data.artists, key = { it.id }) { artist ->
                                MediaCard(artist.name, "Artist", onClick = { actions.openArtist(artist.id) }, image = artist.picture, round = true)
                            }
                        }
                    }
                }
                if (showMusic) {
                    item {
                        SectionHeader("Top of the month")
                        TrackShelf(data.monthly, PlayContext("trending-month", "Top of the month", "CHART"), playback.current?.id)
                    }
                    item {
                        SectionHeader("All-time greats")
                        TrackShelf(data.allTime, PlayContext("trending-all", "All-time greats", "CHART"), playback.current?.id)
                    }
                }
                val live = (liveShows.state as? Load.Ready)?.data.orEmpty()
                if (showMusic && live.isNotEmpty()) {
                    item {
                        SectionHeader("Live concert recordings")
                        AlbumShelf(live)
                    }
                }
                if (showPlaylists && lib.savedPlaylists.isNotEmpty()) {
                    item {
                        SectionHeader("Your saved playlists")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(lib.savedPlaylists, key = { it.id }) { pl ->
                                MediaCard(pl.name, "By ${pl.owner}", onClick = { actions.openPlaylist(pl.id) }, image = pl.artwork)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A horizontal row of albums. */
@Composable
private fun AlbumShelf(albums: List<Playlist>) {
    val actions = LocalActions.current
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { it.id }) { album ->
            MediaCard(album.name, album.owner, onClick = { actions.openPlaylist(album.id) }, image = album.artwork)
        }
    }
}

/** A horizontal row of songs. Tapping one plays the whole row from there. */
@Composable
private fun TrackShelf(tracks: List<Track>, context: PlayContext, currentId: String?) {
    val player = LocalPlayer.current
    val actions = LocalActions.current
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            MediaCard(
                title = track.title,
                subtitle = track.artist,
                image = track.artworkLarge ?: track.artwork,
                onClick = {
                    if (track.id == currentId) actions.openPlayer() else player.play(tracks, index, context)
                },
            )
        }
    }
}

/** Generated cover for a genre mix: a colour block with the genre name, like a daily mix tile. */
@Composable
fun MixCover(genre: String, color: Color, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f).compositeOver(Color.Black)))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .width(28.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SpitifyColors.Accent),
        )
        Text(
            genre,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
                .padding(10.dp),
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

private fun buildShortcuts(
    recents: List<RecentItem>,
    includeLiked: Boolean,
    fallbackPlaylists: List<Playlist>,
    actions: com.srakim.spitify.ui.AppActions,
): List<Shortcut> {
    val tiles = mutableListOf<Shortcut>()
    if (includeLiked) {
        tiles += Shortcut("liked", "Liked Songs", { m -> LikedSongsArt(m, RoundedCornerShape(0.dp)) }, actions::openLiked)
    }
    recents.filter { it.kind != RecentItem.Kind.LIKED }.forEach { item ->
        val art: @Composable (Modifier) -> Unit = { m ->
            when (item.kind) {
                RecentItem.Kind.MIX -> MixCover(item.title.removeSuffix(" Mix"), Color(Genres.colorFor(item.title.removeSuffix(" Mix"))), m)
                RecentItem.Kind.ARTIST -> Artwork(item.image, m, RoundedCornerShape(0.dp))
                RecentItem.Kind.MY_PLAYLIST -> MosaicArt(listOf(item.image), m, RoundedCornerShape(0.dp))
                else -> Artwork(item.image, m, RoundedCornerShape(0.dp))
            }
        }
        val open: () -> Unit = when (item.kind) {
            RecentItem.Kind.PLAYLIST -> { { actions.openPlaylist(item.id) } }
            RecentItem.Kind.MY_PLAYLIST -> { { actions.openMyPlaylist(item.id) } }
            RecentItem.Kind.ARTIST -> { { actions.openArtist(item.id) } }
            RecentItem.Kind.MIX -> { { actions.openMix(item.id) } }
            RecentItem.Kind.LIKED -> actions::openLiked
        }
        tiles += Shortcut("${item.kind}:${item.id}", item.title, art, open)
    }
    fallbackPlaylists.forEach { pl ->
        if (tiles.none { it.key == "PLAYLIST:${pl.id}" }) {
            tiles += Shortcut("PLAYLIST:${pl.id}", pl.name, { m -> Artwork(pl.artwork, m, RoundedCornerShape(0.dp)) }, { actions.openPlaylist(pl.id) })
        }
    }
    return tiles.take(8)
}

private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val base = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) base else "$base, ${name.trim()}"
}
