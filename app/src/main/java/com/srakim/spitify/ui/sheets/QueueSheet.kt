package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.theme.SpitifyColors

/** Now playing plus everything lined up after it. Opens from the player and from any song menu. */
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsStateWithLifecycle()
    SpitifySheet(onDismiss) {
        Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val now = state.current
        if (now == null) {
            Text(
                "Nothing's playing. Add songs to the queue from any song's menu.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpitifyColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            Text("Now playing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(now.artwork, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(now.title, style = MaterialTheme.typography.bodyLarge, color = SpitifyColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(now.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        if (state.upNext.isNotEmpty()) {
            Text(
                state.context?.let { "Next from: ${it.title}" } ?: "Next up",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            itemsIndexed(state.upNext, key = { pos, e -> "${e.index}:${e.track.id}:$pos" }) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { player.skipTo(entry.index) }
                        .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(entry.track.artwork, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    IconButton(onClick = { player.removeFromQueue(entry.index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue", tint = SpitifyColors.TextSecondary)
                    }
                }
            }
        }
    }
}
