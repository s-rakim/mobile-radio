package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.rememberArtworkColor
import com.srakim.spitify.ui.theme.SpitifyColors

/** The floating bar above the tabs. Tap to open the player, swipe sideways to skip. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalPlayer.current
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val state by player.state.collectAsStateWithLifecycle()
    val lib by library.state.collectAsStateWithLifecycle()
    val track = state.current ?: return
    val liked = lib.liked.any { it.id == track.id }
    val color by rememberArtworkColor(track.artwork, SpitifyColors.Highlight)
    var drag by remember { mutableFloatStateOf(0f) }

    Box(Modifier.padding(horizontal = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .clickable(onClick = onOpen)
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag < -120f) player.next() else if (drag > 120f) player.previous()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { _, amount -> drag += amount }
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artwork, Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpitifyColors.TextPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    val now = library.toggleLike(track)
                    actions.toast(if (now) "Added to Liked Songs" else "Removed from Liked Songs")
                }) {
                    Icon(
                        if (liked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                        contentDescription = if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                        tint = if (liked) SpitifyColors.Accent else SpitifyColors.TextPrimary,
                    )
                }
                IconButton(onClick = player::togglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = SpitifyColors.TextPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(SpitifyColors.TextPrimary.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(state.progress)
                        .background(SpitifyColors.TextPrimary),
                )
            }
        }
    }
}
