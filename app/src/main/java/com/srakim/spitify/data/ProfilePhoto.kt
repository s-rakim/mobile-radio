package com.srakim.spitify.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Your profile picture on this phone: a square JPEG in the app's files, named after the moment it
 * was set so image caches never show an old one. Settings keeps that moment in profilePhoto.
 */
object ProfilePhoto {

    /** Side of the saved square. Plenty for a 140dp circle on a sharp screen, small enough to upload. */
    private const val SIZE = 512

    fun file(context: Context, version: Long): File? =
        if (version <= 0) null else File(context.filesDir, "avatar-$version.jpg").takeIf { it.exists() }

    /** Writes [square] and returns its version and the JPEG bytes. Any older picture is deleted. */
    fun save(context: Context, square: Bitmap): Pair<Long, ByteArray> {
        val version = System.currentTimeMillis()
        val bytes = ByteArrayOutputStream().use {
            square.compress(Bitmap.CompressFormat.JPEG, 88, it)
            it.toByteArray()
        }
        File(context.filesDir, "avatar-$version.jpg").writeBytes(bytes)
        clear(context, keep = version)
        return version to bytes
    }

    fun clear(context: Context, keep: Long = -1) {
        context.filesDir.listFiles { f -> f.name.startsWith("avatar-") && f.name != "avatar-$keep.jpg" }?.forEach { it.delete() }
    }

    /**
     * Opens a picked photo the right way up (phones often store camera shots sideways and flag
     * it in the EXIF data) and no bigger than [max] on its long side, so a 50MP photo can't run
     * the app out of memory.
     */
    fun decode(context: Context, uri: Uri, max: Int = 2048): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder turns the picture upright by itself.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleFor(maxOf(info.size.width, info.size.height), max))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(maxOf(bounds.outWidth, bounds.outHeight), max) }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Couldn't open that photo")
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun sampleFor(longSide: Int, max: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= max) sample *= 2
        return sample
    }

    /** Cuts the square at [left], [top] (in [source] pixels) of side [side] and scales it to the saved size. */
    fun crop(source: Bitmap, left: Float, top: Float, side: Float): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        val from = Rect(left.roundToInt(), top.roundToInt(), (left + side).roundToInt(), (top + side).roundToInt())
        canvas.drawBitmap(source, from, RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }
}
