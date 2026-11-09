package com.srakim.spitify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Shared motion settings so every press, sheet and list moves with the same feel.
 * Springs for anything the finger touches, short tweens for colour and fades.
 */
object Motion {
    const val FAST = 160
    const val MEDIUM = 280
    const val SLOW = 420

    /** Settles quickly with a hint of bounce. Used for press feedback and toggles. */
    fun <T> press() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** No overshoot, fast. Used for sheets and things that follow a drag. */
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** A small pop for confirmations such as liking a song. */
    fun <T> pop() = spring<T>(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
}

/**
 * Shrinks a little while pressed and springs back on release, the way Spotify's cards and
 * buttons do. Pass the same [interactionSource] the clickable uses.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.press(), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A clickable with no ripple that shrinks on press. For artwork cards, tiles and round
 * buttons where a ripple looks cheap.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.springClick(
    pressed: Float = 0.96f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source, pressed)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * Fades and lifts an item in the first time it shows up. The flag is saved per lazy item, so
 * scrolling back over a row doesn't replay it. Later items wait a little longer, capped so a
 * long list never feels slow.
 */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay((index.coerceIn(0, 10) * 32).toLong())
            progress.animateTo(1f, tween(Motion.MEDIUM + 60, easing = FastOutSlowInEasing))
            shown = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

/** A one-shot bounce, played whenever [trigger] flips to true (for example, when a song gets liked). */
@Composable
fun rememberPop(trigger: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (trigger) {
            scale.snapTo(0.7f)
            scale.animateTo(1f, Motion.pop())
        } else {
            scale.snapTo(0.9f)
            scale.animateTo(1f, Motion.press())
        }
    }
    return scale.asState()
}

/**
 * Playback position arrives four times a second. This glides between those ticks so bars and
 * sliders move continuously instead of stepping, and jumps straight there after a seek.
 */
@Composable
fun rememberSmoothProgress(target: Float): State<Float> {
    val value = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (abs(target - value.value) > 0.03f) {
            value.snapTo(target)
        } else {
            value.animateTo(target, tween(260, easing = LinearEasing))
        }
    }
    return value.asState()
}

/** A soft highlight that sweeps across loading placeholders. */
fun Modifier.shimmer(base: Color, highlight: Color): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX",
    )
    drawWithCache {
        val width = size.width.coerceAtLeast(1f)
        onDrawBehind {
            drawRect(base)
            val start = width * x
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(start - width * 0.6f, 0f),
                    end = Offset(start, size.height),
                ),
            )
        }
    }
}
