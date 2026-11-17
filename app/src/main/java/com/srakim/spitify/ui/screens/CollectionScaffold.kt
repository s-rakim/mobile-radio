package com.srakim.spitify.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.ServerCoverage
import com.srakim.spitify.ui.components.missingFromServer
import com.srakim.spitify.ui.components.serverDecidesSpotifySongs
import com.srakim.spitify.data.SpotifyImport
import com.srakim.spitify.ui.components.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalDownloads
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.PlayCircle
import com.srakim.spitify.ui.components.appear
import com.srakim.spitify.ui.components.TrackRow
import com.srakim.spitify.ui.sheets.SheetOption
import com.srakim.spitify.ui.sheets.SpitifySheet
import com.srakim.spitify.ui.theme.SpitifyColors

/**
 * Who owns the floating play button. The header's button reports where it sits; once it
 * reaches the top bar, the scaffold pins a copy there, the way Spotify's does.
 */
class PinnedPlay {
    var top by mutableFloatStateOf(Float.POSITIVE_INFINITY)
    var contextId by mutableStateOf<String?>(null)
    var start by mutableStateOf<(() -> Unit)?>(null)
    var pinned by mutableStateOf(false)
}

val LocalPinnedPlay = compositionLocalOf<PinnedPlay?> { null }
val LocalCollectionList = compositionLocalOf<LazyListState?> { null }

private val TopBarHeight = 56.dp

/**
 * Shared layout for playlists, albums, mixes, Liked Songs and friends' pages: the artwork's
 * colour washes down from the top, the cover shrinks and fades as the list scrolls, the title
 * fades into a solid bar, and the play button sticks to that bar once it gets there.
 */
@Composable
fun CollectionScaffold(
    title: String,
    color: Color,
    listState: LazyListState = rememberLazyListState(),
    secondary: Color? = null,
    header: @Composable () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val actions = LocalActions.current
    val density = LocalDensity.current
    val pinnedPlay = remember { PinnedPlay() }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val barBottom = statusTop + with(density) { TopBarHeight.toPx() }
    val half = with(density) { 28.dp.toPx() }

    val barAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - 420f) / 380f).coerceIn(0f, 1f)
        }
    }
    val animatedAlpha by animateFloatAsState(barAlpha, tween(Motion.FAST), label = "barAlpha")
    val pin by remember {
        derivedStateOf { pinnedPlay.start != null && (listState.firstVisibleItemIndex > 0 || pinnedPlay.top <= barBottom - half) }
    }
    SideEffect { pinnedPlay.pinned = pin }
    val player = LocalPlayer.current
    val playback by player.state.collectAsStateWithLifecycle()
    val pinnedHere = pinnedPlay.contextId?.let { playback.isPlayingFrom(it) } == true
    val barColor = lerp(color, Color.Black, 0.45f)

    CompositionLocalProvider(LocalPinnedPlay provides pinnedPlay, LocalCollectionList provides listState) {
        Box(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp),
            ) {
                item(key = "header") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0f to lerp(color, Color.Black, 0.05f),
                                    0.45f to (secondary ?: color).copy(alpha = 0.55f),
                                    1f to SpitifyColors.Background,
                                ),
                            ),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = TopBarHeight),
                        ) {
                            header()
                        }
                    }
                }
                content()
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(barColor.copy(alpha = animatedAlpha))
                    .statusBarsPadding()
                    .height(TopBarHeight)
                    .padding(start = 4.dp, end = 84.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { actions.back() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alpha(animatedAlpha),
                )
            }

            AnimatedVisibility(
                visible = pin,
                enter = fadeIn(tween(Motion.FAST)),
                exit = fadeOut(tween(Motion.FAST)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(density) { (barBottom - half).toDp() }, end = 16.dp),
            ) {
                PlayCircle(
                    playing = pinnedHere && playback.isPlaying,
                    onClick = { if (pinnedHere) player.togglePlayPause() else pinnedPlay.start?.invoke() },
                )
            }
        }
    }
}

/**
 * The cover at the top of a collection. It shrinks to about two thirds and fades out as the page
 * scrolls, instead of sliding away with the list.
 */
@Composable
fun CoverBlock(modifier: Modifier = Modifier, size: Dp = 252.dp, content: @Composable (Modifier) -> Unit) {
    val list = LocalCollectionList.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    Box(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
        content(
            Modifier
                .size(size)
                .graphicsLayer {
                    val offset = if (list == null || list.firstVisibleItemIndex > 0) {
                        if (list == null) 0f else sizePx
                    } else {
                        list.firstVisibleItemScrollOffset.toFloat()
                    }
                    val p = (offset / sizePx).coerceIn(0f, 1f)
                    val scale = 1f - 0.32f * p
                    scaleX = scale
                    scaleY = scale
                    // Stay put while the list scrolls, so it reads as shrinking in place.
                    translationY = offset * 0.85f
                    alpha = (1f - p * 1.15f).coerceIn(0f, 1f)
                    shadowElevation = 24.dp.toPx()
                    shape = RoundedCornerShape(4.dp)
                    clip = false
                },
        )
    }
}

/**
 * The text block under the cover. [owner] gets a small round avatar, [meta] is the grey line
 * with saves and length (or "Album • 2024"), and [globe] puts Spotify's little public icon in front.
 */
@Composable
fun CollectionHeaderText(
    title: String,
    description: String? = null,
    owner: String? = null,
    ownerImage: String? = null,
    meta: String,
    globe: Boolean = false,
    onOwner: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = SpitifyColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!owner.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.then(if (onOwner != null) Modifier.clickable(onClick = onOwner) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(ownerImage, Modifier.size(24.dp), CircleShape, fallback = Icons.Rounded.Person)
                Spacer(Modifier.width(8.dp))
                Text(owner, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (globe) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = SpitifyColors.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = SpitifyColors.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** The "Find in playlist" box and Sort button that sit above a playlist's cover. */
@Composable
fun FindInCollection(query: String, onQuery: (String) -> Unit, sortLabel: String?, onSort: (() -> Unit)?, hint: String = "Find in playlist") {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Color.White),
                    cursorBrush = SolidColor(SpitifyColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear",
                    modifier = Modifier.size(18.dp).clickable { onQuery("") },
                )
            }
        }
        if (onSort != null && sortLabel != null) {
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onSort)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(sortLabel, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
            }
        }
    }
}

enum class TrackSort(val label: String) { CUSTOM("Sort"), TITLE("Title"), ARTIST("Artist"), LENGTH("Length") }

fun List<Track>.filterAndSort(query: String, sort: TrackSort): List<Track> {
    val q = query.trim()
    val found = if (q.isEmpty()) this else filter { it.title.contains(q, true) || it.artist.contains(q, true) || (it.album?.contains(q, true) == true) }
    return when (sort) {
        TrackSort.CUSTOM -> found
        TrackSort.TITLE -> found.sortedBy { it.title.lowercase() }
        TrackSort.ARTIST -> found.sortedBy { it.artist.lowercase() }
        TrackSort.LENGTH -> found.sortedBy { it.durationSec }
    }
}

@Composable
fun SortSheet(current: TrackSort, onPick: (TrackSort) -> Unit, onDismiss: () -> Unit) {
    SpitifySheet(onDismiss = onDismiss) {
        Text("Sort by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        TrackSort.entries.forEach { s ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(s); onDismiss() }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (s == TrackSort.CUSTOM) "Custom order" else s.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (s == current) SpitifyColors.Accent else SpitifyColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (s == current) Icon(Icons.Rounded.Check, contentDescription = null, tint = SpitifyColors.Accent)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The row under a collection header: save, download, more, then shuffle and the big play button. */
@Composable
fun CollectionActions(
    tracks: List<Track>,
    context: PlayContext,
    modifier: Modifier = Modifier,
    saved: Boolean? = null,
    onToggleSave: () -> Unit = {},
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
    thumb: (@Composable (Modifier) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val player = LocalPlayer.current
    val downloads = LocalDownloads.current
    val actions = LocalActions.current
    val playback by player.state.collectAsStateWithLifecycle()
    val dl by downloads.state.collectAsStateWithLifecycle()
    val offlineAccess = com.srakim.spitify.ui.LocalSettings.current.state.collectAsStateWithLifecycle().value.offlineAccess
    var menuOpen by remember { mutableStateOf(false) }

    val playingHere = playback.isPlayingFrom(context.id)
    // Songs that can't be saved (not on your server, say) don't stop the list counting as downloaded.
    val toSave = remember(tracks, dl) { downloads.countSaveable(tracks) }
    val allDownloaded = tracks.isNotEmpty() && toSave == 0 && tracks.any { dl.has(it.id) }
    val downloading = tracks.any { dl.isDownloading(it.id) }

    val pinned = LocalPinnedPlay.current
    val startPlay: () -> Unit = {
        if (playingHere) player.togglePlayPause()
        else player.play(tracks, 0, context, shuffle = playback.shuffle)
    }
    val startFresh = { player.play(tracks, 0, context, shuffle = player.state.value.shuffle) }
    SideEffect {
        if (pinned != null) {
            pinned.contextId = context.id
            pinned.start = startFresh
        }
    }

    Row(modifier.fillMaxWidth().padding(start = if (thumb != null || leading != null) 16.dp else 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (thumb != null) {
            // Spotify's little cover preview at the start of the row.
            Box(
                Modifier
                    .size(width = 30.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
            ) { thumb(Modifier.fillMaxSize()) }
            Spacer(Modifier.width(8.dp))
        }
        leading?.invoke()
        if (saved != null) {
            IconButton(onClick = onToggleSave) {
                Icon(
                    if (saved) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                    contentDescription = if (saved) "Remove from Your Library" else "Save to Your Library",
                    tint = if (saved) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (offlineAccess) IconButton(
            onClick = {
                when {
                    allDownloaded -> {
                        downloads.remove(tracks.map { it.id })
                        actions.toast("Removed from downloads")
                    }
                    toSave == 0 -> actions.toast(
                        if (tracks.any { SpotifyImport.isSpotify(it.id) }) "Spotify songs are saved from your server. Set it up in Settings → Music sources."
                        else "None of these songs can be saved for offline",
                    )
                    downloads.download(tracks) -> actions.toast("Downloading $toSave ${if (toSave == 1) "song" else "songs"}")
                    else -> actions.toast("Downloads are set to Wi-Fi only. Change it in Settings.")
                }
            },
            enabled = tracks.isNotEmpty(),
        ) {
            if (downloading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SpitifyColors.Accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    if (allDownloaded) Icons.Rounded.DownloadForOffline else Icons.Outlined.DownloadForOffline,
                    contentDescription = if (allDownloaded) "Remove download" else "Download",
                    tint = if (allDownloaded) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = SpitifyColors.TextSecondary)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = player::toggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = if (playback.shuffle) "Turn shuffle off" else "Turn shuffle on",
                tint = if (playback.shuffle) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        PlayCircle(
            playing = playingHere && playback.isPlaying,
            onClick = startPlay,
            modifier = Modifier
                .onGloballyPositioned { pinned?.top = it.positionInRoot().y }
                .alpha(if (pinned?.pinned == true) 0f else 1f),
        )
    }

    if (menuOpen) {
        val close = { menuOpen = false }
        SpitifySheet(onDismiss = close) {
            Text(
                context.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            SheetOption(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", onClick = {
                tracks.forEach(player::addToQueue)
                actions.toast("Added ${tracks.size} songs to queue")
                close()
            })
            extraMenu?.invoke(close)
        }
    }
}

/** Standard track list rows for a collection. */
fun LazyListScope.collectionTracks(
    tracks: List<Track>,
    context: PlayContext,
    currentId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    onPlay: (Int) -> Unit,
    onMore: (Track) -> Unit,
    numbered: Boolean = false,
    showArtwork: Boolean = true,
) {
    if (tracks.any { SpotifyImport.isSpotify(it.id) }) {
        item(key = "server-coverage:${context.id}") {
            if (serverDecidesSpotifySongs()) ServerCoverage(tracks)
        }
    }
    items(tracks.size, key = { "${context.id}:${tracks[it].id}:$it" }) { i ->
        val t = tracks[i]
        // Spotify songs your server doesn't have are greyed out: they won't play until you add them.
        val missing = missingFromServer(t, serverDecidesSpotifySongs())
        TrackRow(
            track = t,
            isCurrent = t.id == currentId,
            isPlaying = isPlaying,
            liked = t.id in likedIds,
            onClick = { onPlay(i) },
            onMore = { onMore(t) },
            number = if (numbered) i + 1 else null,
            showArtwork = showArtwork,
            subtitle = if (missing) "Not on your server · ${t.artist}" else null,
            modifier = Modifier.appear(i).alpha(if (missing) 0.45f else 1f),
        )
    }
}

/** Songs liked here and, when you're signed in, in your Spotify Liked Songs: both get the green tick. */
@Composable
fun rememberLikedIds(): Set<String> {
    val lib by LocalLibrary.current.state.collectAsStateWithLifecycle()
    val spotify by com.srakim.spitify.ui.LocalApp.current.spotifyLibrary.state.collectAsStateWithLifecycle()
    return remember(lib.liked, spotify.liked) { (lib.liked + spotify.liked).mapTo(HashSet()) { it.id } }
}

/** Liked in Spotify but not here, so Spitify can't take it out of Liked Songs on its own. */
@Composable
fun rememberSpotifyOnlyLikes(): Set<String> {
    val lib by LocalLibrary.current.state.collectAsStateWithLifecycle()
    val spotify by com.srakim.spitify.ui.LocalApp.current.spotifyLibrary.state.collectAsStateWithLifecycle()
    return remember(lib.liked, spotify.liked) {
        val here = lib.liked.mapTo(HashSet()) { it.id }
        spotify.liked.map { it.id }.filterTo(HashSet()) { it !in here }
    }
}

/** What to say when someone tries to unlike a song that only Spotify has as liked. */
const val SPOTIFY_LIKE_NOTE = "This one's in your Spotify Liked Songs. Unlike it in Spotify and it goes from here too."
