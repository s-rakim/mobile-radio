package com.srakim.spitify.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Music already saved on the phone, read from MediaStore. This is how songs that none of the
 * free catalogues carry still end up in Spitify, the same way Local Files works elsewhere.
 */
class LocalMusic(private val context: Context) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _scanned = MutableStateFlow(false)
    /** True once a scan has finished at least once, so screens can tell "empty" from "not looked yet". */
    val scanned: StateFlow<Boolean> = _scanned.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(): List<Track> = withContext(Dispatchers.IO) {
        val found = if (hasPermission()) runCatching { query() }.getOrDefault(emptyList()) else emptyList()
        _tracks.value = found
        _scanned.value = true
        found
    }

    fun search(query: String, limit: Int = 20): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return _tracks.value.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }.take(limit)
    }

    private fun query(): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val out = ArrayList<Track>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val art = ContentUris.withAppendedId(ALBUM_ART, c.getLong(albumCol)).toString()
                out += Track(
                    id = "$ID_PREFIX$id",
                    title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown song",
                    artist = c.getString(artistCol)?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: "Unknown artist",
                    artwork = art,
                    artworkLarge = art,
                    durationSec = (c.getLong(durationCol) / 1000).toInt(),
                    streamUrl = ContentUris.withAppendedId(collection, id).toString(),
                )
            }
        }
        return out
    }

    companion object {
        const val ID_PREFIX = "local:"
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

        val PERMISSION: String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
