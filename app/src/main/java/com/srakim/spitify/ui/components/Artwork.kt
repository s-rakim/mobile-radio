package com.srakim.spitify.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.srakim.spitify.ui.LocalDataSaver
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4),
    fallback: ImageVector = Icons.Rounded.MusicNote,
    contentDescription: String? = null,
) {
    val dataSaver = LocalDataSaver.current
    val source = if (dataSaver) url?.replace("1000x1000", "150x150")?.replace("480x480", "150x150") else url
    Box(
        modifier = modifier.clip(shape).background(SpitifyColors.Highlight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fallback,
            contentDescription = null,
            tint = SpitifyColors.TextMuted,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (!source.isNullOrBlank()) {
            AsyncImage(
                model = source,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The Liked Songs cover: a warm gradient with a heart. */
/** Generated tile for built-in collections such as Local Files: a gradient with an icon. */
@Composable
fun IconArt(icon: ImageVector, top: Color, bottom: Color, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4)) {
    Box(
        modifier = modifier.clip(shape).background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

@Composable
fun LikedSongsArt(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4)) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(SpitifyColors.Accent, lerp(SpitifyColors.Accent, Color(0xFF2A0F3A), 0.55f), Color(0xFF1A0E24)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}

/** Cover for a playlist you made: one image, or a 2x2 grid once there are four songs. */
@Composable
fun MosaicArt(images: List<String?>, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4)) {
    val distinct = images.filterNotNull().distinct()
    if (distinct.size < 4) {
        Artwork(distinct.firstOrNull(), modifier, shape)
        return
    }
    Column(modifier.clip(shape)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0..1) {
                    Artwork(distinct[row * 2 + col], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0))
                }
            }
        }
    }
}

/**
 * Pulls a dark, saturated colour out of a cover so headers, the player and the mini player
 * can wear the artwork's colour. Animates between tracks.
 */
@Composable
fun rememberArtworkColor(url: String?, fallback: Color = SpitifyColors.Highlight): State<Color> {
    val context = LocalContext.current
    val target = produceState(fallback, url) {
        if (url.isNullOrBlank()) {
            value = fallback
            return@produceState
        }
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(160)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@produceState
        val swatch = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        }
        if (swatch != null) value = tame(Color(swatch.rgb))
    }
    return animateColorAsState(target.value, tween(durationMillis = 700), label = "artworkColor")
}

/** Keeps extracted colours dark enough for white text (4.5:1 or better). */
private fun tame(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(0.8f)
    hsv[2] = hsv[2].coerceIn(0.22f, 0.5f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
