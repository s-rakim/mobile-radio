package com.srakim.spitify.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.srakim.spitify.playback.OutputDevice
import com.srakim.spitify.playback.QueueEntry
import com.srakim.spitify.playback.rememberOutputDevice
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.EqualizerBars
import com.srakim.spitify.ui.components.MenuIcons
import com.srakim.spitify.ui.components.Motion
import com.srakim.spitify.ui.components.PlayPauseIcon
import com.srakim.spitify.ui.components.springClick
import com.srakim.spitify.ui.theme.SpitifyColors

private val RowHeight = 68.dp

/**
 * The queue, laid out like Spotify's: what's playing and where from, the songs you queued,
 * then the rest of the playlist. Drag the handles to reorder, Edit to pick songs to remove,
 * and Shuffle, Repeat and the sleep timer along the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val state by player.state.collectAsStateWithLifecycle()
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }

    var editing by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }

    // A local copy of the upcoming songs, so a row can follow your finger before the player catches up.
    // Each row carries a key that survives reordering, so the dragged row keeps its gesture.
    val rows = remember { mutableStateListOf<Pair<String, QueueEntry>>() }
    val upNext by rememberUpdatedState(state.upNext)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    fun resetRows() {
        val seen = HashMap<String, Int>()
        rows.clear()
        rows.addAll(
            upNext.map { e ->
                val n = (seen[e.track.id] ?: 0) + 1
                seen[e.track.id] = n
                "${e.track.id}#$n" to e
            },
        )
    }
    LaunchedEffect(state.upNext) {
        if (dragKey == null) {
            resetRows()
            selected.retainAll(state.upNext.map { it.index }.toSet())
        }
    }
    fun positionOf(key: String) = rows.indexOfFirst { it.first == key }
    val queuedCount = rows.count { it.second.queued }

    SpitifySheet(onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            // Header: Queue, where it's playing from, the output device, and Edit.
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    state.context?.let { ctx ->
                        Row {
                            Text("Playing ", style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary)
                            Text(ctx.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutputDeviceButton(Modifier.padding(top = 6.dp))
                }
                if (state.upNext.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (editing) SpitifyColors.TextPrimary else SpitifyColors.Highlight)
                            .clickable {
                                editing = !editing
                                selected.clear()
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    ) {
                        Text(
                            if (editing) "Done" else "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (editing) Color.Black else SpitifyColors.TextPrimary,
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 72.dp)) {
                    val now = state.current
                    item(key = "now") {
                        if (now == null) {
                            Text(
                                "Nothing's playing. Add songs to the queue from any song's menu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpitifyColors.TextSecondary,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth().height(RowHeight + 8.dp).padding(start = 20.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(now.artwork, Modifier.size(52.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (state.isPlaying) {
                                            EqualizerBars(Modifier.size(width = 12.dp, height = 12.dp))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(now.title, style = MaterialTheme.typography.bodyLarge, color = SpitifyColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(now.artist, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .springClick(pressed = 0.85f, onClick = player::togglePlayPause)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PlayPauseIcon(state.isPlaying, tint = Color.Black, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    }

                    itemsIndexed(rows, key = { _, row -> row.first }) { pos, row ->
                        val (key, entry) = row
                        Column {
                            if (pos == 0 && entry.queued) {
                                SectionTitle("Next in queue", action = "Clear queue", onAction = {
                                    player.clearQueue()
                                    actions.toast("Queue cleared")
                                })
                            }
                            if (pos == queuedCount && !entry.queued) {
                                SectionTitle(state.context?.let { "Next from: ${it.title}" } ?: "Next up")
                            }
                            val dragging = dragKey == key
                            QueueRow(
                                entry = entry,
                                editing = editing,
                                checked = entry.index in selected,
                                modifier = Modifier
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (dragging) dragOffset else 0f
                                        shadowElevation = if (dragging) 16f else 0f
                                    }
                                    .background(if (dragging) SpitifyColors.Highlight else Color.Transparent),
                                onClick = {
                                    if (editing) {
                                        if (entry.index in selected) selected.remove(entry.index) else selected.add(entry.index)
                                    } else {
                                        player.skipTo(entry.index)
                                    }
                                },
                                handle = Modifier.pointerInput(key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragKey = key
                                            dragFrom = positionOf(key)
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            val to = positionOf(key)
                                            val from = dragFrom
                                            dragKey = null
                                            dragOffset = 0f
                                            if (from >= 0 && to >= 0 && from != to) player.moveInQueue(from, to) else resetRows()
                                        },
                                        onDragCancel = {
                                            dragKey = null
                                            dragOffset = 0f
                                            resetRows()
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        // Swap with the neighbour once the row is more than half way over it.
                                        val at = positionOf(key)
                                        if (dragOffset > rowPx / 2 && at in 0 until rows.lastIndex) {
                                            rows.add(at + 1, rows.removeAt(at))
                                            dragOffset -= rowPx
                                        } else if (dragOffset < -rowPx / 2 && at > 0) {
                                            rows.add(at - 1, rows.removeAt(at))
                                            dragOffset += rowPx
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                // Spotify floats Reshuffle over the list while shuffle is on.
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.shuffle && !editing && rows.size > 1,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                ) {
                    Box(
                        Modifier
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(SpitifyColors.Highlight)
                            .springClick(pressed = 0.92f) {
                                player.reshuffle()
                                actions.toast("Reshuffled")
                            }
                            .padding(horizontal = 22.dp, vertical = 11.dp),
                    ) {
                        Text("Reshuffle", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (editing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BarButton(
                        label = if (selected.isEmpty()) "Select songs" else "Remove ${selected.size}",
                        icon = null,
                        on = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (selected.isNotEmpty()) {
                            player.removeFromQueue(selected.toList())
                            actions.toast(if (selected.size == 1) "Removed 1 song" else "Removed ${selected.size} songs")
                            selected.clear()
                        }
                    }
                    BarButton(label = "Select all", icon = null, on = false, modifier = Modifier.weight(1f)) {
                        selected.clear()
                        selected.addAll(rows.map { it.second.index })
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BarButton("Shuffle", Icons.Rounded.Shuffle, state.shuffle, Modifier.weight(1f), onClick = player::toggleShuffle)
                    BarButton(
                        "Repeat",
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        state.repeatMode != Player.REPEAT_MODE_OFF,
                        Modifier.weight(1f),
                        onClick = player::cycleRepeat,
                    )
                    val timer = sleepTimerLabel(state)
                    BarButton(timer ?: "Timer", MenuIcons.Timer, timer != null, Modifier.weight(1f), onClick = actions.showSleepTimer)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = SpitifyColors.TextSecondary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    editing: Boolean,
    checked: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    handle: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = editing) {
            val fill by animateColorAsState(if (checked) SpitifyColors.Accent else Color.Transparent, tween(Motion.FAST), label = "check")
            Box(Modifier.padding(end = 14.dp)) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(1.5.dp, if (checked) SpitifyColors.Accent else SpitifyColors.TextSecondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = SpitifyColors.OnAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Artwork(entry.track.artwork, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.track.artist, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(MenuIcons.Drag, contentDescription = "Drag to reorder", tint = SpitifyColors.TextSecondary, modifier = Modifier.size(24.dp))
        }
    }
}

/** The square-ish buttons along the bottom of the queue. Green when switched on. */
@Composable
private fun BarButton(label: String, icon: ImageVector?, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) SpitifyColors.Accent else SpitifyColors.TextPrimary, tween(Motion.MEDIUM), label = "barTint")
    val lift by animateFloatAsState(if (on) 1f else 0f, Motion.pop(), label = "barLift")
    Column(
        modifier
            .height(if (icon != null) 76.dp else 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpitifyColors.Highlight)
            .springClick(pressed = 0.95f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp).graphicsLayer {
                    scaleX = 1f + lift * 0.06f
                    scaleY = 1f + lift * 0.06f
                },
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/** Where the sound is going. Tap to pick another speaker or headphones. */
@Composable
fun OutputDeviceButton(modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val output = rememberOutputDevice()
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { OutputDevice.openPicker(context) }
            .padding(vertical = 4.dp, horizontal = if (compact) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MenuIcons.Device, contentDescription = "Pick where to play", tint = SpitifyColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(output.name, style = MaterialTheme.typography.labelMedium, color = SpitifyColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
