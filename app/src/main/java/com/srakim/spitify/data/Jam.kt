package com.srakim.spitify.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Jams without a server. The host's queue and playhead go into a spitify://jam link (shown as a
 * QR code and shareable as text). Opening it on another phone loads the same songs from Audius
 * and jumps to where the host is now, using the time the invite was made to catch up.
 * Only Audius songs travel, since anything on the host's phone or server can't be fetched.
 */
object Jam {
    private const val MAX_SONGS = 40

    data class Invite(val ids: List<String>, val positionMs: Long, val sentAt: Long)

    /** Invites opened from outside the app wait here until the UI picks them up. */
    val incoming = MutableStateFlow<Invite?>(null)

    /** Null when nothing in [queue] can be shared. [positionMs] belongs to the first song. */
    fun link(queue: List<Track>, positionMs: Long, now: Long = System.currentTimeMillis()): String? {
        val shareable = queue.filter { it.isAudius }.distinctBy { it.id }.take(MAX_SONGS)
        if (shareable.isEmpty()) return null
        val position = if (shareable.first().id == queue.firstOrNull()?.id) positionMs else 0L
        return Uri.Builder()
            .scheme("spitify")
            .authority("jam")
            .appendQueryParameter("t", shareable.joinToString(",") { it.id })
            .appendQueryParameter("p", position.coerceAtLeast(0).toString())
            .appendQueryParameter("s", now.toString())
            .build()
            .toString()
    }

    fun parse(uri: Uri?): Invite? {
        if (uri == null || uri.scheme != "spitify" || uri.host != "jam") return null
        val ids = uri.getQueryParameter("t").orEmpty().split(',').map { it.trim() }
            .filter { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
            .take(MAX_SONGS)
        if (ids.isEmpty()) return null
        return Invite(
            ids = ids,
            positionMs = uri.getQueryParameter("p")?.toLongOrNull() ?: 0L,
            sentAt = uri.getQueryParameter("s")?.toLongOrNull() ?: System.currentTimeMillis(),
        )
    }

    /**
     * Where the host should be by now: which song and how far in. Walks forward through the
     * queue if the invite sat unopened for a while. Gives up after the last song.
     */
    fun catchUp(invite: Invite, tracks: List<Track>, now: Long = System.currentTimeMillis()): Pair<Int, Long> {
        val late = (now - invite.sentAt).coerceIn(0L, 6 * 60 * 60 * 1000L)
        var offset = invite.positionMs + late
        tracks.forEachIndexed { i, t ->
            val length = t.durationSec * 1000L
            if (length <= 0 || offset < length - 3_000) return i to offset.coerceAtLeast(0)
            offset -= length
        }
        return 0 to 0L
    }
}
