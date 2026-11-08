package com.srakim.spitify.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.theme.SpitifyColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    liked: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    showArtwork: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent && isPlaying) {
                    EqualizerBars(Modifier.size(14.dp))
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                    )
                }
            }
        }
        if (showArtwork) {
            Artwork(track.artwork, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) SpitifyColors.Accent else SpitifyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liked) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Liked",
                        tint = SpitifyColors.Accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (number == null && isCurrent && isPlaying) {
                    EqualizerBars(Modifier.size(11.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${track.title}", tint = SpitifyColors.TextSecondary)
            }
        }
    }
}

/** Three bouncing bars, shown next to whatever is playing right now. */
@Composable
fun EqualizerBars(modifier: Modifier = Modifier, color: Color = SpitifyColors.Accent) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(0, 180, 90).map { offset ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, delayMillis = offset), RepeatMode.Reverse),
            label = "bar$offset",
        )
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            val value by h
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(value)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = SpitifyColors.TextSecondary)
            }
        }
    }
}

/** Square (or round, for artists) card used in every horizontal shelf. */
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: String? = null,
    round: Boolean = false,
    width: Dp = 148.dp,
    art: (@Composable (Modifier) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        val artModifier = Modifier.fillMaxWidth().aspectRatio(1f)
        when {
            art != null -> art(artModifier)
            round -> Artwork(image, artModifier, CircleShape, fallback = Icons.Rounded.Person)
            else -> Artwork(image, artModifier, RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = if (subtitle.isBlank()) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

/** The compact two-column tiles at the top of Home. */
@Composable
fun ShortcutTile(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, art: @Composable (Modifier) -> Unit) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SpitifyColors.Highlight.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art(Modifier.size(56.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .background(if (selected) SpitifyColors.Accent else SpitifyColors.Highlight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) SpitifyColors.OnAccent else SpitifyColors.TextPrimary,
        )
    }
}

/** The big round play/pause button on collection headers. */
@Composable
fun PlayCircle(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = SpitifyColors.Accent,
    content: Color = SpitifyColors.OnAccent,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = content,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** The small profile circle in the top-left of the main tabs. Opens settings. */
@Composable
fun ProfileBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(SpitifyColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Person, contentDescription = "Profile and settings", tint = SpitifyColors.OnAccent, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.clip(shape).alpha(alpha).background(SpitifyColors.Highlight))
}

@Composable
fun SkeletonShelf(modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 24.dp)) {
        SkeletonBlock(Modifier.padding(start = 16.dp).width(180.dp).height(22.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) {
                Column {
                    SkeletonBlock(Modifier.size(148.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.width(110.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun SkeletonTrackList(rows: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(rows) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    SkeletonBlock(Modifier.width(180.dp).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.width(110.dp).height(11.dp))
                }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load this", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(SpitifyColors.TextPrimary)
                .clickable(onClick = onRetry)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Try again", style = MaterialTheme.typography.labelLarge, color = Color.Black)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            WhiteButton(action, onAction)
        }
    }
}

@Composable
fun WhiteButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 120.dp)
            .clip(CircleShape)
            .background(SpitifyColors.TextPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(BorderStroke(1.dp, SpitifyColors.TextMuted), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

val ListPadding = PaddingValues(bottom = 24.dp)

fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatLongDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours} hr ${minutes} min"
        minutes > 0 -> "$minutes min"
        else -> "${totalSeconds} sec"
    }
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000f).replace(".0K", "K")
    else -> n.toString()
}
