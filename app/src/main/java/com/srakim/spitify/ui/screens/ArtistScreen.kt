package com.srakim.spitify.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.Artist
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Playlist
import com.srakim.spitify.data.RecentItem
import com.srakim.spitify.data.SubsonicApi
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.MediaCard
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.components.PlayCircle
import com.srakim.spitify.ui.components.SectionHeader
import com.srakim.spitify.ui.components.SkeletonTrackList
import com.srakim.spitify.ui.components.TrackRow
import com.srakim.spitify.ui.components.appear
import com.srakim.spitify.ui.components.formatCount
import com.srakim.spitify.ui.components.rememberArtworkColor
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.sheets.SheetOption
import com.srakim.spitify.ui.theme.SpitifyColors

private val HeroHeight = 380.dp

/**
 * Laid out like Spotify's artist page: a full-width photo with the name across the bottom,
 * the listener line, Follow and play, Popular with play counts, releases, Fans also like and
 * About. The photo zooms out and darkens as you scroll, and the play button sticks to the bar.
 */
@Composable
fun ArtistScreen(id: String) {
    val catalog = LocalCatalog.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var discFilter by rememberSaveable { mutableStateOf("Popular releases") }
    val listState = rememberLazyListState()

    val load = rememberLoad("artist", id) { catalog.artist(id) }
    val releasesLoad = rememberLoad("artist-releases", id) { catalog.releases(id) }
    val relatedLoad = rememberLoad("artist-related", id) { catalog.related(id) }
    val artist = (load.state as? Load.Ready)?.data?.first
    val tracks = (load.state as? Load.Ready)?.data?.second.orEmpty()
    val releases = (releasesLoad.state as? Load.Ready)?.data.orEmpty()
    val related = (relatedLoad.state as? Load.Ready)?.data.orEmpty()
    // Spotify fills the header with a photo of the artist, so the square portrait beats a wide banner.
    val heroImage = artist?.pictureLarge ?: artist?.cover
    val color by rememberArtworkColor(artist?.picture)
    val following = lib.followedArtists.any { it.id == id }
    val ctx = PlayContext("artist:$id", artist?.name ?: "Artist", "ARTIST")

    LaunchedEffect(artist?.id) {
        artist?.let { library.recordVisit(RecentItem(RecentItem.Kind.ARTIST, it.id, it.name, "Artist", it.picture)) }
    }

    val heroPx = with(density) { HeroHeight.toPx() }
    val barAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - heroPx * 0.55f) / (heroPx * 0.3f)).coerceIn(0f, 1f)
        }
    }
    val alpha by animateFloatAsState(barAlpha, tween(150), label = "artistBar")
    val pinnedPlay = remember { PinnedPlay() }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val barBottom = statusTop + with(density) { 56.dp.toPx() }
    val half = with(density) { 28.dp.toPx() }
    val pin by remember { derivedStateOf { pinnedPlay.start != null && (listState.firstVisibleItemIndex > 1 || pinnedPlay.top <= barBottom - half) } }
    SideEffect { pinnedPlay.pinned = pin }
    val pinnedHere = playback.isPlayingFrom(ctx.id)

    CompositionLocalProvider(LocalPinnedPlay provides pinnedPlay) {
        Box(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth().height(HeroHeight).background(color).clipToBounds()) {
                        Artwork(
                            heroImage,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else heroPx
                                    val p = (offset / heroPx).coerceIn(0f, 1f)
                                    // Pull-down feel: the photo sits still and zooms slightly as the page moves over it.
                                    translationY = offset * 0.6f
                                    scaleX = 1f + 0.12f * (1f - p)
                                    scaleY = 1f + 0.12f * (1f - p)
                                    this.alpha = 1f - p * 0.6f
                                },
                            RoundedCornerShape(0.dp),
                            fallback = Icons.Rounded.Person,
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))),
                        )
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            if (artist?.verified == true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = Color(0xFF4CB3FF), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verified Artist", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                                }
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                artist?.name.orEmpty(),
                                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                when (val state = load.state) {
                    is Load.Loading -> item { SkeletonTrackList(6) }
                    is Load.Failed -> item { ErrorState(state.message, load.retry) }
                    is Load.Ready -> {
                        item(key = "listeners") {
                            Text(
                                when {
                                    SubsonicApi.isServer(id) -> "On your server"
                                    (artist?.followers ?: 0) > 0 -> "${"%,d".format(artist?.followers ?: 0)} followers"
                                    else -> "${tracks.size} songs"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = SpitifyColors.TextSecondary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                            )
                            CollectionActions(
                                tracks = tracks,
                                context = ctx,
                                thumb = { m -> Artwork(artist?.picture, m, RoundedCornerShape(2.dp)) },
                                leading = {
                                    FollowButton(following) {
                                        artist?.let {
                                            val now = library.toggleFollow(it)
                                            actions.toast(if (now) "Following ${it.name}" else "Unfollowed ${it.name}")
                                        }
                                    }
                                },
                                extraMenu = { close ->
                                    SheetOption(Icons.Rounded.Share, "Share", onClick = {
                                        shareText(context, "${artist?.name} on Spitify\n" + if (id.startsWith("spotify:")) "https://open.spotify.com/artist/${id.substringAfterLast(':')}" else "https://audius.co/${artist?.handle}")
                                        close()
                                    })
                                },
                            )
                        }
                        item { SectionHeader("Popular") }
                        val shown = if (showAll) tracks.take(10) else tracks.take(5)
                        items(shown.size, key = { "pop:${shown[it].id}" }) { i ->
                            val t = shown[i]
                            TrackRow(
                                track = t,
                                isCurrent = t.id == playback.current?.id,
                                isPlaying = playback.isPlaying,
                                liked = t.id in likedIds,
                                onClick = { player.play(tracks, i, ctx) },
                                onMore = { actions.showTrackMenu(t, null) },
                                number = i + 1,
                                subtitle = if (t.playCount > 0) "%,d".format(t.playCount) else t.album ?: t.artist,
                                modifier = Modifier.appear(i),
                            )
                        }
                        if (tracks.size > 5) {
                            item {
                                Text(
                                    if (showAll) "Show less" else "See more",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                                    color = SpitifyColors.TextSecondary,
                                    modifier = Modifier.clickable { showAll = !showAll }.padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        }

                        if (releases.isNotEmpty()) {
                            item {
                                SectionHeader("Discography")
                                val kinds = listOf("Popular releases", "Albums", "Singles and EPs").filter { k ->
                                    when (k) {
                                        "Albums" -> releases.any { it.label == "Album" }
                                        "Singles and EPs" -> releases.any { it.label == "Single" }
                                        else -> true
                                    }
                                }
                                if (kinds.size > 1) {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                        items(kinds) { k -> com.srakim.spitify.ui.components.Pill(k, discFilter == k, onClick = { discFilter = k }) }
                                    }
                                }
                            }
                            val pickedReleases = when (discFilter) {
                                "Albums" -> releases.filter { it.label == "Album" }
                                "Singles and EPs" -> releases.filter { it.label == "Single" }
                                else -> releases
                            }.take(6)
                            items(pickedReleases.size, key = { "rel:${pickedReleases[it].id}" }) { i ->
                                ReleaseRow(pickedReleases[i], latest = i == 0 && discFilter == "Popular releases") { actions.openPlaylist(pickedReleases[i].id) }
                            }
                        }

                        // Every other song, so nothing the artist uploaded is out of reach.
                        if (tracks.size > shown.size) {
                            item { SectionHeader("All songs") }
                            val rest = tracks.drop(shown.size)
                            items(rest.size, key = { "all:${rest[it].id}" }) { i ->
                                val t = rest[i]
                                TrackRow(
                                    track = t,
                                    isCurrent = t.id == playback.current?.id,
                                    isPlaying = playback.isPlaying,
                                    liked = t.id in likedIds,
                                    onClick = { player.play(tracks, i + shown.size, ctx) },
                                    onMore = { actions.showTrackMenu(t, null) },
                                )
                            }
                        }

                        if (related.isNotEmpty()) {
                            item {
                                SectionHeader("Fans also like")
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(related, key = { it.id }) { a ->
                                        MediaCard(a.name, "Artist", onClick = { actions.openArtist(a.id) }, image = a.picture, round = true, width = 140.dp)
                                    }
                                }
                            }
                        }
                        if (artist != null) item { AboutCard(artist, tracks.size) }
                    }
                }
            }

            // Top bar: the name fades in over a strip of the photo's colour.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(lerp(color, Color.Black, 0.4f).copy(alpha = alpha))
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(start = 8.dp, end = 84.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f * (1 - alpha))).clickable { actions.back() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(12.dp))
                Text(artist?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(
                visible = pin,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = with(density) { (barBottom - half).toDp() }, end = 16.dp),
            ) {
                PlayCircle(
                    playing = pinnedHere && playback.isPlaying,
                    onClick = { if (pinnedHere) player.togglePlayPause() else pinnedPlay.start?.invoke() },
                )
            }
        }
    }
}

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (following) Color.White else SpitifyColors.TextMuted, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
    }
}

/** One release in the Discography list: cover, title, then "Latest release • Single" or "2023 • Album". */
@Composable
private fun ReleaseRow(release: Playlist, latest: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(release.artwork, Modifier.size(72.dp), RoundedCornerShape(2.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(release.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(if (latest) "Latest release" else release.year, release.label).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = SpitifyColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AboutCard(artist: Artist, songs: Int) {
    SectionHeader("About")
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpitifyColors.Highlight),
    ) {
        Artwork(artist.pictureLarge ?: artist.picture, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), fallback = Icons.Rounded.Person)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (artist.followers > 0) "${formatCount(artist.followers)} followers" else "$songs songs",
                style = MaterialTheme.typography.titleMedium,
            )
            artist.bio?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
