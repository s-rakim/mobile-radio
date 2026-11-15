package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Track
import com.srakim.spitify.data.TrackDetails
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.MenuIcons
import com.srakim.spitify.ui.components.PlayPauseIcon
import com.srakim.spitify.ui.components.springClick
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class Dna(
    val details: TrackDetails?,
    val soundsLike: List<Track>,
    val sharesTag: Pair<String, List<Track>>?,
    val fromArtist: List<Track>,
) {
    val all: List<Track> get() = soundsLike + sharesTag?.second.orEmpty() + fromArtist
}

/**
 * Our take on Spotify's SongDNA. Spotify's version maps samples and covers, which no free
 * catalogue has. What Audius does have is the song's genre, mood, key, tempo and tags, so this
 * shows those as its building blocks and finds songs that share them: same genre, same tag,
 * same artist. Tap a block to explore it, or play the lot as a mix.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SongDnaSheet(track: Track, onDismiss: () -> Unit) {
    val api = LocalApi.current
    val catalog = LocalCatalog.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current

    val dna by produceState<Dna?>(null, track.id) {
        val skip = library.state.value.tasteExcluded.toSet() + track.id
        val keep = { t: Track -> t.id !in skip }
        value = coroutineScope {
            val details = async { if (track.isAudius) runCatching { api.trackDetails(track.id) }.getOrNull() else null }
            val genre = track.genre?.takeIf { it.isNotBlank() }
            val sounds = async {
                runCatching { api.trendingTracks(genre = genre, time = "month", limit = 40) }.getOrDefault(emptyList())
                    .filter { keep(it) && it.artist != track.artist }
                    .shuffled()
                    .take(6)
            }
            val artist = async {
                runCatching {
                    when {
                        track.artistId.isNotBlank() -> catalog.artist(track.artistId).second
                        track.artistSearchName.isNotBlank() -> api.searchTracks(track.artistSearchName)
                        else -> emptyList()
                    }
                }.getOrDefault(emptyList()).filter(keep).take(5)
            }
            val d = details.await()
            val tag = d?.tags?.firstOrNull { !it.equals(genre, ignoreCase = true) }
            val tagged = tag?.let { t ->
                val found = runCatching { api.searchTracks(t) }.getOrDefault(emptyList()).filter(keep).take(5)
                if (found.isEmpty()) null else t to found
            }
            Dna(d, sounds.await(), tagged, artist.await())
        }
    }

    fun playFrom(list: List<Track>, start: Int) {
        player.play(list, start, PlayContext("dna:${track.id}", "${track.title} song DNA", "SONG DNA"))
        onDismiss()
    }

    SpitifySheet(onDismiss, scrollable = true) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MenuIcons.Dna, contentDescription = null, tint = SpitifyColors.Accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Song DNA", style = MaterialTheme.typography.titleLarge)
                Text("${track.title} · ${track.artist}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val ready = dna?.all.orEmpty()
            if (ready.isNotEmpty()) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(48.dp)
                        .springClick(pressed = 0.85f) { playFrom(listOf(track) + ready, 0) }
                        .clip(CircleShape)
                        .background(SpitifyColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(false, tint = SpitifyColors.OnAccent, modifier = Modifier.size(26.dp))
                }
            }
        }

        val d = dna
        if (d == null) {
            Text(
                "Reading this song's DNA…",
                style = MaterialTheme.typography.bodyMedium,
                color = SpitifyColors.TextSecondary,
                modifier = Modifier.padding(20.dp),
            )
            return@SpitifySheet
        }

        // The building blocks. Each one opens something to explore.
        val genre = d.details?.genre ?: track.genre
        val blocks = buildList<Pair<String, () -> Unit>> {
            genre?.takeIf { it.isNotBlank() }?.let { g -> add(g to { onDismiss(); actions.openMix(g) }) }
            d.details?.mood?.let { m -> add(m to { onDismiss(); actions.searchFor(m) }) }
            d.details?.bpm?.let { b -> add("$b BPM" to { onDismiss(); actions.searchFor("$b bpm") }) }
            d.details?.key?.let { k -> add(k to { onDismiss(); actions.searchFor(k) }) }
            d.details?.tags.orEmpty().take(6).forEach { t -> add("#$t" to { onDismiss(); actions.searchFor(t) }) }
        }
        if (blocks.isNotEmpty()) {
            Heading("Made of")
            FlowRow(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                blocks.forEach { (label, open) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SpitifyColors.Highlight)
                            .clickable(onClick = open)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (d.soundsLike.isNotEmpty()) {
            Heading(genre?.let { "Same $it sound" } ?: "Same sound")
            d.soundsLike.forEachIndexed { i, t -> DnaRow(t) { playFrom(d.soundsLike, i) } }
        }
        d.sharesTag?.let { (tag, list) ->
            Heading("Also tagged #$tag")
            list.forEachIndexed { i, t -> DnaRow(t) { playFrom(list, i) } }
        }
        if (d.fromArtist.isNotEmpty()) {
            Heading("More from ${track.artist}")
            d.fromArtist.forEachIndexed { i, t -> DnaRow(t) { playFrom(d.fromArtist, i) } }
        }
        if (blocks.isEmpty() && d.all.isEmpty()) {
            Text(
                "There isn't enough about this song to go on. Try song radio instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpitifyColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun DnaRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artwork, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
