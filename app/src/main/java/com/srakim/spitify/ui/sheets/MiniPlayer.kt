package com.srakim.spitify.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.Motion
import com.srakim.spitify.ui.components.PlayPauseIcon
import com.srakim.spitify.ui.components.rememberArtworkColors
import com.srakim.spitify.ui.components.rememberPop
import com.srakim.spitify.ui.components.rememberSmoothProgress
import com.srakim.spitify.ui.components.springClick
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val colors = rememberArtworkColors(track.artwork, SpitifyColors.Highlight)
    val progress by rememberSmoothProgress(state.progress)
    val likePop by rememberPop(liked)

    // The bar follows your finger while you swipe, then springs home once the skip fires.
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }

    Box(Modifier.padding(horizontal = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .springClick(pressed = 0.98f, onClick = onOpen)
                .graphicsLayer {
                    translationX = dragX.value
                    alpha = 1f - (abs(dragX.value) / (size.width * 1.4f)).coerceIn(0f, 0.5f)
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(colors.primary, colors.secondary)))
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val moved = dragX.value
                            when {
                                moved < -120f -> player.next()
                                moved > 120f -> player.previous()
                            }
                            scope.launch { dragX.animateTo(0f, Motion.press()) }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f, Motion.press()) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { dragX.snapTo(dragX.value + amount * 0.8f) }
                    }
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artwork, Modifier.size(40.dp), RoundedCornerShape(4.dp))
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (slideInVertically(Motion.settle()) { it / 2 } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                            (slideOutVertically(tween(Motion.FAST)) { -it / 2 } + fadeOut(tween(Motion.FAST)))
                    },
                    contentKey = { it.id },
                    modifier = Modifier.weight(1f),
                    label = "miniTitle",
                ) { t ->
                    Column {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            t.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpitifyColors.TextPrimary.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                        modifier = Modifier.graphicsLayer {
                            scaleX = likePop
                            scaleY = likePop
                        },
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .springClick(pressed = 0.8f, onClick = player::togglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(state.isPlaying, tint = SpitifyColors.TextPrimary, modifier = Modifier.size(30.dp))
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
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(SpitifyColors.TextPrimary),
                )
            }
        }
    }
}
