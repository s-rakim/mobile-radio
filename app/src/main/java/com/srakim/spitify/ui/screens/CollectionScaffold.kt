package com.srakim.spitify.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
 * Shared layout for playlists, mixes, Liked Songs and friends: a colour-washed header that
 * scrolls away and a slim top bar that fades in with the title.
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
    val barAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f)
        }
    }
    val animatedAlpha by animateFloatAsState(barAlpha, label = "barAlpha")

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
                                listOf(lerp(color, Color.White, 0.06f), color, secondary ?: color.copy(alpha = 0.55f), SpitifyColors.Background),
                            ),
                        ),
                ) {
                    // The header drifts up slower than the list and fades as it goes, so scrolling
                    // feels layered instead of flat.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (listState.firstVisibleItemIndex == 0) {
                                    val offset = listState.firstVisibleItemScrollOffset.toFloat()
                                    translationY = offset * 0.35f
                                    alpha = (1f - offset / 650f).coerceIn(0f, 1f)
                                }
                            }
                            .statusBarsPadding()
                            .padding(top = 52.dp),
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
                .background(lerp(color, Color.Black, 0.25f).copy(alpha = animatedAlpha))
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
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
) {
    val player = LocalPlayer.current
    val downloads = LocalDownloads.current
    val actions = LocalActions.current
    val playback by player.state.collectAsStateWithLifecycle()
    val dl by downloads.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    val playingHere = playback.isPlayingFrom(context.id)
    val allDownloaded = tracks.isNotEmpty() && tracks.all { dl.has(it.id) }
    val downloading = tracks.any { dl.isDownloading(it.id) }

    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
        IconButton(
            onClick = {
                when {
                    allDownloaded -> {
                        downloads.remove(tracks.map { it.id })
                        actions.toast("Removed from downloads")
                    }
                    downloads.download(tracks) -> actions.toast("Downloading ${tracks.size} songs")
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
            onClick = {
                if (playingHere) player.togglePlayPause()
                else player.play(tracks, 0, context, shuffle = playback.shuffle)
            },
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
) {
    items(tracks.size, key = { "${context.id}:${tracks[it].id}:$it" }) { i ->
        val t = tracks[i]
        TrackRow(
            track = t,
            isCurrent = t.id == currentId,
            isPlaying = isPlaying,
            liked = t.id in likedIds,
            onClick = { onPlay(i) },
            onMore = { onMore(t) },
            number = if (numbered) i + 1 else null,
            modifier = Modifier.appear(i),
        )
    }
}

@Composable
fun rememberLikedIds(): Set<String> {
    val lib by LocalLibrary.current.state.collectAsStateWithLifecycle()
    return remember(lib.liked) { lib.liked.mapTo(HashSet()) { it.id } }
}
