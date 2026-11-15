package com.srakim.spitify.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.srakim.spitify.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a few lines of lyrics into a card image and hands it to the share sheet, the way
 * Spotify does: cover and song at the top, the lines in big bold type, card in the song's colour.
 * The words go along as text too, for apps that only take text.
 */
object LyricsShare {

    suspend fun share(context: Context, track: Track, lines: List<String>, color: Color) {
        val picked = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (picked.isEmpty()) return
        val cover = loadCover(context, track.artworkLarge ?: track.artwork)
        val file = withContext(Dispatchers.Default) {
            // White type needs a dark enough card, so pale covers get their colour deepened.
            val card = if (color.luminance() > 0.4f) lerp(color, Color.Black, 0.4f) else color
            val bitmap = draw(track, picked, card, cover)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "lyrics.png").also { out ->
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val text = buildString {
            append(picked.joinToString("\n"))
            append("\n\n").append("${track.title} · ${track.artist}")
            track.link?.let { append("\n").append(it) }
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share lyrics"))
    }

    private suspend fun loadCover(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(256).build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull() as? SuccessResult
        return (result?.drawable as? BitmapDrawable)?.bitmap
    }

    private fun draw(track: Track, lines: List<String>, color: Color, cover: Bitmap?): Bitmap {
        val width = 1080
        val pad = 84f
        val textWidth = (width - pad * 2).toInt()

        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lyricLayout = StaticLayout.Builder
            .obtain(lines.joinToString("\n"), 0, lines.joinToString("\n").length, lyricPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1f)
            .build()

        val coverSize = 132f
        val headerHeight = coverSize + 64f
        val footerHeight = 120f
        val height = (pad + headerHeight + lyricLayout.height + footerHeight + pad).toInt().coerceAtLeast(width)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color.toArgb())

        // Song header: cover, title, artist.
        if (cover != null) {
            val dst = RectF(pad, pad, pad + coverSize, pad + coverSize)
            canvas.save()
            val clip = android.graphics.Path().apply { addRoundRect(dst, 14f, 14f, android.graphics.Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(cover, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
        val textLeft = if (cover != null) pad + coverSize + 32f else pad
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.argb(200, 255, 255, 255)
            textSize = 38f
        }
        val room = width - pad - textLeft
        canvas.drawText(ellipsize(track.title, titlePaint, room), textLeft, pad + 56f, titlePaint)
        canvas.drawText(ellipsize(track.artist, artistPaint, room), textLeft, pad + 108f, artistPaint)

        canvas.save()
        canvas.translate(pad, pad + headerHeight)
        lyricLayout.draw(canvas)
        canvas.restore()

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.argb(220, 255, 255, 255)
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Spitify", pad, height - pad, brandPaint)
        return bitmap
    }

    private fun ellipsize(text: String, paint: TextPaint, room: Float): String =
        android.text.TextUtils.ellipsize(text, paint, room, android.text.TextUtils.TruncateAt.END).toString()
}
