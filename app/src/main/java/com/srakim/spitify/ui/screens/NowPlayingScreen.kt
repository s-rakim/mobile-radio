package com.srakim.spitify.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.srakim.spitify.data.Lyrics
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalLyrics
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.LocalSettings
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.formatDuration
import com.srakim.spitify.ui.components.rememberArtworkColor
import com.srakim.spitify.ui.sheets.SheetOption
import com.srakim.spitify.ui.sheets.SpitifySheet
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val player = LocalPlayer.current
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val lyricsApi = LocalLyrics.current
    val context = LocalContext.current
    val settings by LocalSettings.current.state.collectAsStateWithLifecycle()
    val state by player.state.collectAsStateWithLifecycle()
    val lib by library.state.collectAsStateWithLifecycle()
    val track = state.current ?: return
    val liked = lib.liked.any { it.id == track.id }
    val color by rememberArtworkColor(track.artworkLarge ?: track.artwork, SpitifyColors.Highlight)

    var queueOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(track.id, settings.showLyrics) {
        lyrics = if (settings.showLyrics) lyricsApi.find(track) else null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.55f), SpitifyColors.Background)))
            .clickable(enabled = false) {},
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val pageHeight = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().height(pageHeight.coerceAtLeast(560.dp)).padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", modifier = Modifier.size(30.dp))
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val ctx = state.context
                            Text(
                                if (ctx != null) "PLAYING FROM ${ctx.label}" else "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpitifyColors.TextPrimary.copy(alpha = 0.8f),
                            )
                            if (ctx != null) {
                                Text(ctx.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    var drag by remember { mutableFloatStateOf(0f) }
                    Artwork(
                        url = track.artworkLarge ?: track.artwork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(28.dp, RoundedCornerShape(6.dp))
                            .pointerInput(track.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (drag < -140f) player.next() else if (drag > 140f) player.previous()
                                        drag = 0f
                                    },
                                    onDragCancel = { drag = 0f },
                                ) { _, amount -> drag += amount }
                            },
                        shape = RoundedCornerShape(6.dp),
                        contentDescription = "Artwork for ${track.title}",
                    )

                    Spacer(Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SpitifyColors.TextPrimary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(enabled = track.artistId.isNotBlank()) { actions.openArtist(track.artistId) },
                            )
                            if (track.source != "Audius") {
                                // Songs from your phone or your server say so, so they never get mistaken for the catalogue.
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                val link = track.link
                                Text(
                                    when {
                                        link != null -> "On ${track.source} ↗"
                                        track.isLocal -> "From this device"
                                        else -> "From your server"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SpitifyColors.TextPrimary.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable(enabled = link != null) { link?.let { runCatching { uriHandler.openUri(it) } } },
                                )
                            }
                        }
                        IconButton(onClick = {
                            val now = library.toggleLike(track)
                            actions.toast(if (now) "Added to Liked Songs" else "Removed from Liked Songs")
                        }) {
                            Icon(
                                if (liked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                                contentDescription = if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                                tint = if (liked) SpitifyColors.Accent else SpitifyColors.TextPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SeekBar(state.positionMs, state.durationMs, onSeek = player::seekTo)

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToggleIcon(
                            icon = Icons.Rounded.Shuffle,
                            on = state.shuffle,
                            description = if (state.shuffle) "Turn shuffle off" else "Turn shuffle on",
                            onClick = player::toggleShuffle,
                        )
                        IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                        }
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = player::togglePlayPause),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                        }
                        ToggleIcon(
                            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            on = state.repeatMode != Player.REPEAT_MODE_OFF,
                            description = when (state.repeatMode) {
                                Player.REPEAT_MODE_OFF -> "Repeat all"
                                Player.REPEAT_MODE_ALL -> "Repeat one"
                                else -> "Turn repeat off"
                            },
                            onClick = player::cycleRepeat,
                        )
                    }

                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.sleepTimerEndsAt != null) {
                            Icon(Icons.Rounded.Bedtime, contentDescription = "Sleep timer on", tint = SpitifyColors.Accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            shareText(context, "${track.title} by ${track.artist}${track.link?.let { "\n$it" }.orEmpty()}")
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { queueOpen = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", modifier = Modifier.size(24.dp))
                        }
                    }
                }

            lyrics?.let { lyr ->
                LyricsCard(lyr, state.positionMs, lerp(color, Color.Black, 0.25f), onExpand = { lyricsOpen = true })
            }

            if (track.artistId.isNotBlank()) {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpitifyColors.Highlight)
                        .clickable { actions.openArtist(track.artistId) },
                ) {
                    Box(Modifier.fillMaxWidth().height(180.dp)) {
                        Artwork(track.artistImage, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                        Text(
                            "About the artist",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(track.artist, style = MaterialTheme.typography.titleMedium)
                        Text("Tap to see their top tracks", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (queueOpen) QueueSheet(onDismiss = { queueOpen = false })
    if (menuOpen) PlayerMenu(onDismiss = { menuOpen = false })
    if (lyricsOpen) lyrics?.let { FullLyrics(it, color, onDismiss = { lyricsOpen = false }) }
}

@Composable
private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, description: String, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.BottomCenter) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = if (on) SpitifyColors.Accent else SpitifyColors.TextPrimary, modifier = Modifier.size(26.dp))
        }
        if (on) {
            Box(Modifier.padding(bottom = 2.dp).size(4.dp).clip(CircleShape).background(SpitifyColors.Accent))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val shown = if (dragging) dragValue else progress

    Column {
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            thumb = {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
            modifier = Modifier.height(24.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(((shown * durationMs) / 1000).toLong()), style = MaterialTheme.typography.labelMedium, color = SpitifyColors.TextPrimary.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs / 1000), style = MaterialTheme.typography.labelMedium, color = SpitifyColors.TextPrimary.copy(alpha = 0.7f))
        }
    }
}

private fun currentLine(lyrics: Lyrics, positionMs: Long): Int {
    if (!lyrics.synced) return -1
    return lyrics.lines.indexOfLast { (it.timeMs ?: 0) <= positionMs + 300 }
}

@Composable
private fun LyricsCard(lyrics: Lyrics, positionMs: Long, color: Color, onExpand: () -> Unit) {
    val current = currentLine(lyrics, positionMs)
    val start = if (current < 0) 0 else (current - 1).coerceAtLeast(0)
    val window = lyrics.lines.drop(start).filter { it.text.isNotBlank() }.take(6)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onExpand)
            .padding(16.dp),
    ) {
        Text("Lyrics", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        window.forEach { line ->
            val active = lyrics.synced && lyrics.lines.indexOf(line) == current
            Text(
                line.text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (active || !lyrics.synced) Color.White else Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Lyrics from LRCLIB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun FullLyrics(lyrics: Lyrics, color: Color, onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val current = currentLine(lyrics, state.positionMs)
    LaunchedEffect(current) {
        if (current > 2) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(color).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close lyrics") }
                Column(Modifier.weight(1f)) {
                    Text(state.current?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(state.current?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
                itemsIndexed(lyrics.lines) { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (!lyrics.synced || i <= current) Color.White else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.timeMs != null) { line.timeMs?.let(player::seekTo) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Color.White).clickable(onClick = player::togglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsStateWithLifecycle()
    SpitifySheet(onDismiss) {
        Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        state.current?.let { now ->
            Text("Now playing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(now.artwork, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(now.title, style = MaterialTheme.typography.bodyLarge, color = SpitifyColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(now.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        if (state.upNext.isNotEmpty()) {
            Text(
                state.context?.let { "Next from: ${it.title}" } ?: "Next up",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            itemsIndexed(state.upNext, key = { pos, e -> "${e.index}:${e.track.id}:$pos" }) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { player.skipTo(entry.index) }
                        .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(entry.track.artwork, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    IconButton(onClick = { player.removeFromQueue(entry.index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue", tint = SpitifyColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMenu(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val state by player.state.collectAsStateWithLifecycle()
    val track = state.current ?: return
    var sleepPicker by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    SpitifySheet(onDismiss) {
        if (!sleepPicker) {
            SheetOption(Icons.Rounded.AddCircleOutline, "Add to playlist", onClick = {
                onDismiss()
                actions.showAddToPlaylist(track)
            })
            val ends = state.sleepTimerEndsAt
            SheetOption(
                Icons.Rounded.Bedtime,
                if (ends != null) "Sleep timer: ${formatDuration(((ends - now) / 1000).coerceAtLeast(0))} left" else "Sleep timer",
                tint = if (ends != null) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                onClick = { sleepPicker = true },
            )
            if (track.artistId.isNotBlank()) {
                SheetOption(Icons.Rounded.MoreVert, "Go to artist", onClick = {
                    onDismiss()
                    actions.openArtist(track.artistId)
                })
            }
        } else {
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
                SheetOption(Icons.Rounded.Bedtime, "$minutes minutes", onClick = {
                    player.setSleepTimer(minutes)
                    actions.toast("Sleep timer set for $minutes minutes")
                    onDismiss()
                })
            }
            if (state.sleepTimerEndsAt != null) {
                SheetOption(Icons.Rounded.Close, "Turn off timer", onClick = {
                    player.setSleepTimer(null)
                    actions.toast("Sleep timer off")
                    onDismiss()
                })
            }
        }
    }
}
