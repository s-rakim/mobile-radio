package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srakim.spitify.data.TrackDetails
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.components.formatCount
import com.srakim.spitify.ui.components.formatDuration
import com.srakim.spitify.ui.theme.SpitifyColors

/**
 * Who made the song and what's known about it. Audius songs show whatever the uploader filled
 * in (writers, producers, label lines, mood, key, BPM). Songs from the phone or a home server
 * show the tags they carry.
 */
@Composable
fun CreditsSheet(track: Track, onDismiss: () -> Unit) {
    val api = LocalApi.current
    val actions = LocalActions.current
    val details by produceState<TrackDetails?>(null, track.id) {
        if (track.isAudius) value = runCatching { api.trackDetails(track.id) }.getOrNull()
    }
    val d = details

    SpitifySheet(onDismiss, scrollable = true) {
        Text(
            "Credits",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        val toArtist: (() -> Unit)? = if (!track.hasArtistLink) null else fun() {
            onDismiss()
            actions.openArtistOf(track)
        }
        Credit("Performed by", listOf(track.artist), onClick = toArtist)
        d?.credits?.forEach { (role, people) -> Credit(role, people) }

        HorizontalDivider(color = SpitifyColors.Divider, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

        val album = track.album ?: d?.album
        if (album != null) {
            Credit("Album", listOf(album), onClick = {
                onDismiss()
                actions.openAlbumOf(track)
            })
        }
        Fact("Source", if (track.isAudius) "Audius" + d?.artistHandle?.let { " · @$it" }.orEmpty() else track.source)
        Fact("Genre", d?.genre ?: track.genre)
        Fact("Mood", d?.mood)
        Fact("Released", d?.released)
        if (track.durationSec > 0) Fact("Length", formatDuration(track.durationSec.toLong()))
        Fact("Tempo", d?.bpm?.let { "$it BPM" })
        Fact("Key", d?.key)
        if (d != null && d.plays > 0) Fact("Plays", formatCount(d.plays))
        if (d != null && d.likes > 0) Fact("Likes on Audius", formatCount(d.likes))
        Fact("ISRC", d?.isrc)
        Fact("License", d?.license)
        if (d != null && d.tags.isNotEmpty()) Fact("Tags", d.tags.joinToString(", "))

        d?.description?.let {
            Spacer(Modifier.height(8.dp))
            Text("About", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp))
        }
        listOfNotNull(d?.copyright, d?.phonographic).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = SpitifyColors.TextMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        }

        if (track.isAudius && d == null) {
            Text(
                "Getting the rest from Audius…",
                style = MaterialTheme.typography.bodySmall,
                color = SpitifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Credit(role: String, people: List<String>, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(people.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        Text(role, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextPrimary)
    }
}
