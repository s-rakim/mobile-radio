package com.srakim.spitify.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.SpotifyImport
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.LocalSettings
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * True when Spotify songs in a list should come from your server: one is set up and switched on,
 * and Settings says songs come from there.
 */
@Composable
fun serverDecidesSpotifySongs(): Boolean {
    val app = LocalApp.current
    val settings by LocalSettings.current.state.collectAsStateWithLifecycle()
    return settings.alwaysFromServer && remember(settings) { app.serverCopies.enabled }
}

/** Whether [track] is a Spotify song your server doesn't have. Asks the server the first time. */
@Composable
fun missingFromServer(track: Track, active: Boolean): Boolean {
    if (!active || !SpotifyImport.isSpotify(track.id)) return false
    val copies = LocalApp.current.serverCopies
    val known by copies.onServer.collectAsStateWithLifecycle()
    LaunchedEffect(track.id) { copies.find(track) }
    return known[track.id] == false
}

/**
 * "41 of 50 songs are on your server" above a list with Spotify songs in it, with a button that
 * shares the missing ones as "Artist - Title" lines, ready to go looking for the files.
 */
@Composable
fun ServerCoverage(tracks: List<Track>) {
    val copies = LocalApp.current.serverCopies
    val context = LocalContext.current
    val spotify = remember(tracks) { tracks.filter { SpotifyImport.isSpotify(it.id) }.distinctBy { it.id } }
    if (spotify.isEmpty()) return
    val known by copies.onServer.collectAsStateWithLifecycle()
    LaunchedEffect(spotify) {
        coroutineScope { spotify.map { async { runCatching { copies.find(it) } } }.awaitAll() }
    }
    val have = spotify.count { known[it.id] == true }
    val missing = spotify.filter { known[it.id] == false }
    val checked = have + missing.size
    val text = when {
        checked < spotify.size -> "Checking your server… $checked of ${spotify.size}"
        missing.isEmpty() -> "All ${spotify.size} songs are on your server"
        else -> "$have of ${spotify.size} songs are on your server. ${missing.size} missing won't play until you add them."
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = SpitifyColors.TextSecondary)
        }
        if (missing.isNotEmpty() && checked == spotify.size) {
            TextButton(onClick = {
                val list = missing.joinToString("\n") { "${it.artist} - ${it.title}" }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Songs missing from my server")
                    putExtra(Intent.EXTRA_TEXT, list)
                }
                runCatching { context.startActivity(Intent.createChooser(send, "Share the missing songs")) }
            }) {
                Text("Share list", style = MaterialTheme.typography.labelMedium, color = SpitifyColors.TextPrimary)
            }
        }
    }
}
