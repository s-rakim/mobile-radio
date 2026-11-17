package com.srakim.spitify.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.ListeningStats
import com.srakim.spitify.data.PersonStats
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.SpotifyImport
import com.srakim.spitify.data.UserPlaylist
import com.srakim.spitify.data.WeekListen
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.EmptyState
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.sheets.Avatar
import com.srakim.spitify.ui.sheets.CreatePlaylistDialog
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Listening stats

@Composable
fun StatsScreen() {
    val app = LocalApp.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val weeks by app.stats.weeks.collectAsStateWithLifecycle()
    val acct by app.account.state.collectAsStateWithLifecycle()
    val thisWeek = ListeningStats.thisWeek()
    val choices = (weeks.keys + thisWeek).distinct().sortedDescending()
    var picked by remember { mutableStateOf(thisWeek) }
    val week = weeks[picked] ?: WeekListen(picked)
    val previous = weeks[java.time.LocalDate.parse(picked).minusWeeks(1).toString()]
    val friends = rememberLoad(picked, acct.signedIn) {
        if (acct.signedIn) app.social.friendStats(picked) else null
    }

    val board: List<PersonStats> = run {
        val people = (friends.state as? Load.Ready)?.data?.people.orEmpty()
        val me = acct.username
        val withMe = if (me != null && people.none { it.username == me }) people + PersonStats(me, me, minutes = week.minutes) else people
        withMe.sortedByDescending { it.minutes }
    }
    val rank = acct.username?.let { me -> board.indexOfFirst { it.username == me } }?.takeIf { it >= 0 }?.plus(1)

    SimplePage("Listening stats", trailing = {
        IconButton(onClick = {
            val text = buildString {
                append("My week on Spitify (${week.range}): ${week.minutes} minutes")
                week.topArtists.firstOrNull()?.let { append(", mostly ${it.name}") }
                week.topTracks.firstOrNull()?.let { append(". On repeat: ${it.track.title} by ${it.track.artist}") }
                append(".")
            }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your stats"))
        }) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
    }) {
        if (choices.size > 1) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(choices) { w ->
                        val selected = w == picked
                        Box(
                            Modifier.clip(CircleShape)
                                .background(if (selected) SpitifyColors.Accent else SpitifyColors.Elevated)
                                .clickable { picked = w }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (w == thisWeek) "This week" else (weeks[w] ?: WeekListen(w)).range,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) SpitifyColors.OnAccent else SpitifyColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(SpitifyColors.AccentDeep).padding(20.dp),
            ) {
                Text(week.range, style = MaterialTheme.typography.labelLarge, color = SpitifyColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text("${week.minutes}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("minutes listened", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                val change = previous?.takeIf { it.minutes > 0 }?.let { ((week.minutes - it.minutes) * 100f / it.minutes).roundToInt() }
                Text(
                    when {
                        change == null -> "Your first week of stats. Keep listening."
                        change > 0 -> "Up $change% on the week before"
                        change < 0 -> "Down ${-change}% on the week before"
                        else -> "Same as the week before"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpitifyColors.TextSecondary,
                )
                if (rank != null && board.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("#$rank of ${board.size} among you and your friends", style = MaterialTheme.typography.bodySmall, color = SpitifyColors.TextPrimary)
                }
            }
        }

        if (week.tracks.isEmpty()) {
            item { EmptyState("Nothing played yet", "A song counts after 30 seconds. Private sessions aren't counted.") }
        } else {
            val topArtist = week.topArtists.firstOrNull()
            val topTrack = week.topTracks.firstOrNull()
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (topArtist != null) HighlightTile("Top artist", topArtist.name, "${topArtist.ms / 60_000} min", topArtist.image, round = true, modifier = Modifier.weight(1f))
                    if (topTrack != null) HighlightTile("Top song", topTrack.track.title, "${topTrack.plays} plays", topTrack.track.artwork, modifier = Modifier.weight(1f)) {
                        app.player.play(listOf(topTrack.track), 0, PlayContext("stats-top", "Top song", "SONG"))
                    }
                }
            }
            item { SectionTitle("Top artists") }
            itemsIndexed(week.topArtists.take(5), key = { _, a -> "a:" + a.name }) { i, a ->
                RankRow(i + 1, a.image, a.name, "${a.ms / 60_000} min • ${a.plays} plays", round = true)
            }
            item { SectionTitle("Top songs") }
            val ctx = PlayContext("stats:$picked", "Your top songs", "PLAYLIST")
            val songs = week.topTracks.take(5)
            itemsIndexed(songs, key = { _, t -> "t:" + t.track.id }) { i, t ->
                RankRow(i + 1, t.track.artwork, t.track.title, "${t.track.artist} • ${t.plays} plays") {
                    app.player.play(songs.map { it.track }, i, ctx)
                }
            }
        }

        if (!acct.signedIn) {
            item { SectionTitle("Friends") }
            item {
                EmptyState("Compare with friends", "Log in to Spitify to see how your week stacks up.", action = "Log in", onAction = actions::openAccount)
            }
        } else if (board.size > 1) {
            item { SectionTitle("Friends this week") }
            itemsIndexed(board, key = { _, p -> "p:" + p.username }) { i, p ->
                Row(
                    Modifier.fillMaxWidth().clickable { if (p.username != acct.username) actions.openProfile(p.username) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
                    Avatar(p.displayName, p.color, 40.dp, image = p.avatar)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p.username == acct.username) "You" else p.displayName, style = MaterialTheme.typography.bodyLarge)
                        p.topArtists.firstOrNull()?.let { Text("Mostly ${it.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                    Text("${p.minutes} min", style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary)
                }
            }
        }

        if (previous != null && previous.minutes > 0 && picked == thisWeek) {
            item { SectionTitle("Last week") }
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SpitifyColors.Elevated).padding(16.dp)) {
                    Text("${previous.range} • ${previous.minutes} minutes", style = MaterialTheme.typography.bodyLarge)
                    previous.topArtists.firstOrNull()?.let { Text("Top artist: ${it.name}", style = MaterialTheme.typography.bodySmall) }
                    previous.topTracks.firstOrNull()?.let { Text("Top song: ${it.track.title} by ${it.track.artist}", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    OutlineButton("See last week", onClick = { picked = previous.week })
                }
            }
        }
    }
}

@Composable
private fun HighlightTile(label: String, title: String, detail: String, image: String?, modifier: Modifier = Modifier, round: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(SpitifyColors.Elevated)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SpitifyColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Artwork(image, Modifier.size(72.dp), if (round) CircleShape else RoundedCornerShape(4.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = SpitifyColors.TextSecondary)
    }
}

@Composable
private fun RankRow(rank: Int, image: String?, title: String, subtitle: String, round: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
        Artwork(image, Modifier.size(48.dp), if (round) CircleShape else RoundedCornerShape(4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Spotify import

private sealed interface ImportStep {
    data object Start : ImportStep
    data class Pick(val playlists: List<SpotifyImport.SourcePlaylist>, val fromSpotify: Boolean) : ImportStep
    data class Matching(val name: String, val done: Int, val total: Int, val listIndex: Int, val lists: Int) : ImportStep
    data class Done(val results: List<ImportResult>) : ImportStep
}

private data class ImportResult(val playlist: UserPlaylist?, val name: String, val found: Int, val missing: List<SpotifyImport.SourceTrack>)

@Composable
fun ImportScreen() {
    val app = LocalApp.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = app.spotifyImport
    var step by remember { mutableStateOf<ImportStep>(ImportStep.Start) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var askId by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    fun showPlaylists(lists: List<SpotifyImport.SourcePlaylist>, fromSpotify: Boolean) {
        if (lists.isEmpty()) { error = "No songs found in that."; return }
        selected.clear()
        if (!fromSpotify || lists.size == 1) selected.addAll(lists.map { it.id })
        step = ImportStep.Pick(lists, fromSpotify)
    }

    fun loadSpotify() {
        busy = "Loading your Spotify playlists…"
        error = null
        scope.launch {
            try {
                showPlaylists(importer.myPlaylists(), true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Spotify didn't answer"
            } finally {
                busy = null
            }
        }
    }

    // Load your playlists as soon as you're signed in, including when the sign-in finishes
    // while this screen is open.
    val spotifySignedIn by importer.signedIn.collectAsStateWithLifecycle()
    val signInState by importer.signIn.collectAsStateWithLifecycle()
    LaunchedEffect(spotifySignedIn) { if (spotifySignedIn && step == ImportStep.Start) loadSpotify() }
    LaunchedEffect(signInState) {
        when (val s = signInState) {
            com.srakim.spitify.data.SpotifyImport.SignIn.Working -> busy = "Signing in to Spotify…"
            is com.srakim.spitify.data.SpotifyImport.SignIn.Failed -> { busy = null; error = s.message }
            else -> Unit
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Reading the file…"
        error = null
        scope.launch {
            runCatching { importer.readFile(uri) }
                .onSuccess { showPlaylists(it, false) }
                .onFailure { error = it.message ?: "Couldn't read that file" }
            busy = null
        }
    }

    fun runImport(pick: ImportStep.Pick) {
        val chosen = pick.playlists.filter { it.id in selected }
        if (chosen.isEmpty()) return
        scope.launch {
            val results = mutableListOf<ImportResult>()
            chosen.forEachIndexed { i, source ->
                step = ImportStep.Matching(source.name, 0, source.total, i + 1, chosen.size)
                val tracks = runCatching {
                    if (source.tracks.isEmpty() && pick.fromSpotify) importer.tracksOf(source.id) else source.tracks
                }.getOrElse {
                    results += ImportResult(null, source.name, 0, emptyList())
                    error = it.message
                    return@forEachIndexed
                }
                val matches = importer.match(tracks, app.catalog) { done, total ->
                    step = ImportStep.Matching(source.name, done, total, i + 1, chosen.size)
                }
                val found = matches.mapNotNull { it.track }
                // Songs nobody has yet stay in the playlist as Spotify songs, greyed out, and play
                // from your server as soon as you add them there.
                val keep = matches.mapNotNull { m -> m.track ?: m.source.uri?.let { m.source.asTrack() } }
                val made = if (keep.isNotEmpty()) {
                    app.library.createPlaylist(source.name, keep, description = "Imported from Spotify")
                } else null
                results += ImportResult(made, source.name, found.size, matches.filter { it.track == null }.map { it.source })
            }
            step = ImportStep.Done(results)
        }
    }

    SimplePage("Import from Spotify") {
        error?.let { msg ->
            item {
                Text(msg, color = SpitifyColors.Error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        busy?.let { msg ->
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpitifyColors.Accent, trackColor = SpitifyColors.Elevated)
                }
            }
        }

        when (val s = step) {
            ImportStep.Start -> {
                item {
                    Text(
                        if (app.serverCopies.enabled) "Bring your playlists over. Each song is looked up on your server and only the exact song counts. Songs it doesn't have stay in the playlist, greyed out, until you add them."
                        else "Bring your playlists over. Each song is looked up in Spitify's sources and only the exact song counts. Set up your server in Settings to play them from there.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpitifyColors.TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                item {
                    ImportOption(
                        Icons.Rounded.CheckCircle,
                        if (spotifySignedIn) "Signed in to Spotify" else "Sign in with Spotify",
                        if (spotifySignedIn) "Tap to load your playlists and Liked Songs" else "Pick from your playlists and Liked Songs",
                    ) {
                        if (spotifySignedIn) loadSpotify()
                        else if (importer.clientId.isBlank()) askId = true
                        else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, importer.authorizeUrl())) }
                            .onFailure { error = "No browser found to sign in with" }
                    }
                }
                item {
                    ImportOption(Icons.Rounded.FileOpen, "Open a file", "Spotify's data download (Playlist1.json), a CSV export, or a text file") {
                        filePicker.launch(arrayOf("application/json", "text/*", "text/csv", "application/octet-stream"))
                    }
                }
                item {
                    ImportOption(Icons.Rounded.ContentPaste, "Paste a list", "One song per line, as Artist - Title") { pasting = true }
                }
            }

            is ImportStep.Pick -> {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selected.size} of ${s.playlists.size} picked", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (selected.size == s.playlists.size) selected.clear() else { selected.clear(); selected.addAll(s.playlists.map { it.id }) }
                        }) { Text(if (selected.size == s.playlists.size) "Clear" else "Pick all", color = SpitifyColors.Accent) }
                    }
                }
                items(s.playlists, key = { it.id }) { p ->
                    val on = p.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) selected.remove(p.id) else selected.add(p.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.total} songs", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(
                            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (on) SpitifyColors.Accent else SpitifyColors.TextMuted,
                        )
                    }
                }
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WhiteButton(if (selected.size == 1) "Import playlist" else "Import ${selected.size} playlists", onClick = { runImport(s) })
                        OutlineButton("Back", onClick = { step = ImportStep.Start })
                    }
                }
            }

            is ImportStep.Matching -> item {
                Column(Modifier.padding(16.dp)) {
                    Text(if (s.lists > 1) "Playlist ${s.listIndex} of ${s.lists}" else "Importing", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.TextSecondary)
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (s.total == 0) 0f else s.done / s.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = SpitifyColors.Accent,
                        trackColor = SpitifyColors.Elevated,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Matched ${s.done} of ${s.total} songs", style = MaterialTheme.typography.bodySmall)
                }
            }

            is ImportStep.Done -> {
                item {
                    val total = s.results.sumOf { it.found }
                    Text(
                        "Done. $total songs ready to play across ${s.results.count { it.playlist != null }} playlists.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(s.results, key = { it.name + it.found }) { r ->
                    var open by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(SpitifyColors.Elevated).padding(14.dp)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (r.playlist == null) "Nothing matched"
                            else "${r.found} ready to play" + if (r.missing.isNotEmpty()) ", ${r.missing.size} ${if (app.serverCopies.enabled) "not on your server yet" else "not found"}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.playlist?.let { pl -> OutlineButton("Open", onClick = { actions.openMyPlaylist(pl.id) }) }
                            if (r.missing.isNotEmpty()) OutlineButton(if (open) "Hide missing" else "Show missing", onClick = { open = !open })
                        }
                        if (open) {
                            Spacer(Modifier.height(6.dp))
                            r.missing.forEach { m ->
                                Text(
                                    listOf(m.artist, m.title).filter { it.isNotBlank() }.joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpitifyColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(16.dp)) { OutlineButton("Import more", onClick = { step = ImportStep.Start; error = null }) }
                }
            }
        }
    }

    if (askId) {
        var id by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askId = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Spotify app id needed") },
            text = {
                Column {
                    Text(
                        "Spotify only lets registered apps read playlists. Make a free app on developer.spotify.com, add spitify://spotify-auth as its Redirect URI, add yourself under Users, then paste its Client ID here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    ImportField(id, { id = it.trim() }, "Client ID")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.settings.update { it.copy(spotifyClientId = id) }
                    askId = false
                    if (id.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, importer.authorizeUrl())) }
                }) { Text("Save and sign in", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { askId = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (pasting) {
        var name by remember { mutableStateOf("Imported") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Paste a list") },
            text = {
                Column {
                    ImportField(name, { name = it }, "Playlist name")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Burna Boy - Last Last\nTems - Free Mind") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        colors = fieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pasting = false
                    showPlaylists(importer.parseText(text, name.ifBlank { "Imported" }), false)
                }) { Text("Next", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }
}

@Composable
private fun ImportOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp))
            .background(SpitifyColors.Elevated).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SpitifyColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = SpitifyColors.TextSecondary)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SpitifyColors.Accent,
    cursorColor = SpitifyColors.Accent,
    focusedLabelColor = SpitifyColors.Accent,
)

@Composable
private fun ImportField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------------------------
// Playlist folders

@Composable
fun FolderScreen(id: String) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val lib by app.library.state.collectAsStateWithLifecycle()
    val folder = lib.folders.firstOrNull { it.id == id }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    if (folder == null) {
        SimplePage("Folder") { item { EmptyState("This folder is gone", "It was deleted, and its playlists moved back to Your Library.") } }
        return
    }
    val inside = lib.playlists.filter { it.folderId == id }

    SimplePage(folder.name, trailing = {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Folder options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = SpitifyColors.Elevated) {
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("Add playlists") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; adding = true })
                DropdownMenuItem(text = { Text("Delete folder") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; deleting = true })
            }
        }
    }) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(SpitifyColors.Highlight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = SpitifyColors.TextSecondary, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(if (inside.size == 1) "1 playlist" else "${inside.size} playlists", style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary)
            }
        }
        if (inside.isEmpty()) {
            item { EmptyState("Empty folder", "Group playlists here to keep Your Library tidy.", action = "Add playlists", onAction = { adding = true }) }
        }
        items(inside, key = { it.id }) { p ->
            var rowMenu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { actions.openMyPlaylist(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MosaicArt(p.tracks.map { it.artwork }.take(4), Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Playlist • ${p.tracks.size} songs", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    IconButton(onClick = { rowMenu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }, containerColor = SpitifyColors.Elevated) {
                        DropdownMenuItem(text = { Text("Remove from folder") }, onClick = {
                            rowMenu = false
                            app.library.moveToFolder(p.id, null)
                            actions.toast("Moved ${p.name} out of ${folder.name}")
                        })
                    }
                }
            }
        }
    }

    if (renaming) {
        CreatePlaylistDialog(
            onDismiss = { renaming = false },
            onCreate = { app.library.renameFolder(id, it); renaming = false },
            title = "Rename folder",
            initial = folder.name,
            confirm = "Save",
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Delete ${folder.name}?") },
            text = { Text("The playlists inside stay in Your Library.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    app.library.deleteFolder(id)
                    actions.toast("Folder deleted")
                    actions.back()
                }) { Text("Delete", color = SpitifyColors.Error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }
    if (adding) {
        val others = lib.playlists.filter { it.folderId != id }
        val picks = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Add to ${folder.name}") },
            text = {
                if (others.isEmpty()) Text("Every playlist is already in here.", style = MaterialTheme.typography.bodyMedium)
                else androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(others, key = { it.id }) { p ->
                        val on = p.id in picks
                        Row(
                            Modifier.fillMaxWidth().clickable { if (on) picks.remove(p.id) else picks.add(p.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(
                                if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (on) SpitifyColors.Accent else SpitifyColors.TextMuted,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    picks.forEach { app.library.moveToFolder(it, id) }
                    adding = false
                }) { Text("Add", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }
}
