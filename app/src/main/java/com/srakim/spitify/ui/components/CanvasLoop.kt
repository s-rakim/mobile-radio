package com.srakim.spitify.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.srakim.spitify.data.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How long one loop of the Canvas lasts. Long enough that it never feels like it's repeating. */
private const val LOOP_MS = 12_000

/**
 * The looping visual behind the player, in the spirit of Spotify's Canvas. Spotify's own
 * Canvas clips are only shown inside the Spotify app, so this builds one from the song's cover:
 * the art fills the screen and slowly pushes in, drifts and breathes, with a soft band of light
 * passing over it. Every movement is a whole number of sine waves per loop, so the end meets
 * the start with no jump.
 *
 * It holds still while the song is paused, like a video would.
 */
@Composable
fun CanvasLoop(track: Track, playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val phase = remember(track.id) { Animatable(0f) }
    LaunchedEffect(track.id, playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            val left = ((1f - phase.value) * LOOP_MS).toInt().coerceAtLeast(1)
            phase.animateTo(1f, tween(left, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }

    Crossfade(targetState = track, label = "canvas", modifier = modifier) { shown ->
        Box(Modifier.fillMaxSize().background(tint)) {
            val art = shown.artworkLarge ?: shown.artwork
            // A blurred copy underneath, so the edges never show as the sharp layer drifts.
            Artwork(
                art,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.6f
                        scaleY = 1.6f
                    }
                    .blur(40.dp),
                RectangleShape,
            )
            Artwork(
                art,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val a = phase.value * 2f * PI.toFloat()
                        val zoom = 1.32f + 0.10f * sin(a)
                        scaleX = zoom
                        scaleY = zoom
                        translationX = size.width * 0.05f * cos(a)
                        translationY = size.height * 0.03f * sin(2f * a)
                        rotationZ = 1.2f * sin(a)
                    }
                    .drawWithContent {
                        drawContent()
                        // A wide, faint band of light sweeping corner to corner once per loop.
                        val sweep = phase.value * 2.4f - 0.7f
                        val w = size.width
                        val h = size.height
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.10f),
                                1f to Color.Transparent,
                                start = Offset(w * (sweep - 0.35f), h * (sweep - 0.35f)),
                                end = Offset(w * (sweep + 0.35f), h * (sweep + 0.35f)),
                            ),
                        )
                    },
                RectangleShape,
            )
            // Keeps white text readable at the top and over the controls at the bottom.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.22f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.78f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )
        }
    }
}
