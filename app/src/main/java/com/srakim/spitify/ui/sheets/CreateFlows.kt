package com.srakim.spitify.ui.sheets

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.AiPlaylist
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.Pill
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.launch

enum class CreateOption(val title: String, val subtitle: String, val icon: ImageVector) {
    PLAYLIST("Playlist", "Build a playlist with songs", Icons.Rounded.LibraryMusic),
    COLLAB("Collaborative playlist", "Invite friends and create something together", Icons.Rounded.Groups),
    MIXED("Mixed playlist", "Songs blend into each other like a DJ set", Icons.Rounded.MergeType),
    BLEND("Blend", "Combine your tastes with a friend's in one playlist", Icons.Rounded.Insights),
    AI("AI Playlist", "Turn your ideas into playlists", Icons.Rounded.AutoAwesome),
    DJ("DJ", "A set picked for you that keeps going, with requests", Icons.Rounded.Headset),
    FOLDER("Folder", "Organise your playlists", Icons.Rounded.Folder),
    JAM("Jam", "Listen together, wherever you are", Icons.Rounded.Podcasts),
}

/** What the Create tab opens: every kind of thing you can make. */
@Composable
fun CreateSheet(onDismiss: () -> Unit, onPick: (CreateOption) -> Unit) {
    SpitifySheet(onDismiss, scrollable = true) {
        CreateOption.entries.forEach { o ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(o) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(SpitifyColors.Highlight), contentAlignment = Alignment.Center) {
                    Icon(o.icon, contentDescription = null, tint = SpitifyColors.TextSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(o.title, style = MaterialTheme.typography.titleMedium)
                    Text(o.subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val aiIdeas = listOf(
    "Late night drive through the city",
    "Afrobeats for a summer cookout",
    "Deep house for a rooftop sunset",
    "Focus music for coding, no vocals",
    "Gym warm-up into heavy lifting",
    "Rainy Sunday morning, jazz and lo-fi",
    "Songs like Burna Boy",
    "Pregame party that keeps building",
)

/** AI Playlist: type an idea, get a playlist that flows, save or play it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPlaylistSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiPlaylist.Result?>(null) }

    fun make() {
        if (prompt.isBlank() || busy) return
        busy = true
        result = null
        scope.launch {
            val skip = app.library.state.value.tasteExcluded.toSet()
            val r = runCatching { AiPlaylist.generate(prompt, app.catalog, app.account, skip, 30) }.getOrNull()
            busy = false
            if (r == null || r.tracks.isEmpty()) actions.toast("Couldn't find songs for that. Try other words.") else result = r
        }
    }

    SpitifySheet(onDismiss, scrollable = true) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SpitifyColors.Accent)
            Spacer(Modifier.width(10.dp))
            Text("AI Playlist", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(300) },
            placeholder = { Text("Describe a mood, a moment or an artist") },
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (result == null && !busy) {
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                aiIdeas.forEach { idea -> Pill(idea, selected = prompt == idea, onClick = { prompt = idea }) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = SpitifyColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Digging through the crates…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                WhiteButton(if (result == null) "Create" else "Try again", onClick = ::make)
            }
        }
        result?.let { r ->
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                MosaicArt(r.tracks.take(4).map { it.artwork }, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("${r.tracks.size} songs" + if (r.byAgent) " • picked by the DJ" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
            r.intro?.let {
                Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            if (r.understood.isNotEmpty()) {
                Text("Heard: ${r.understood.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            Column(Modifier.heightIn(max = 300.dp)) {
                r.tracks.take(6).forEach { t ->
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(t.artwork, Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
                if (r.tracks.size > 6) Text("and ${r.tracks.size - 6} more", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                OutlineButton("Play", onClick = {
                    app.player.play(r.tracks, 0, PlayContext("ai:${r.title}", r.title, "AI PLAYLIST"), shuffle = false)
                    onDismiss()
                    actions.openPlayer()
                })
                WhiteButton("Save playlist", onClick = {
                    val made = app.library.createPlaylist(r.title, r.tracks, mixed = true, description = r.description)
                    onDismiss()
                    actions.toast("Saved to Your Library")
                    actions.openMyPlaylist(made.id)
                })
            }
        }
    }
}

private val djRequests = listOf("Surprise me", "More energy", "Something chill", "Switch it up", "Throwbacks", "New discoveries", "Afrobeats", "Deep house")

/** AI DJ: start a set, or ask for something while it's playing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DjSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val dj by app.dj.state.collectAsStateWithLifecycle()
    val playback by app.player.state.collectAsStateWithLifecycle()
    val on = playback.context?.id == com.srakim.spitify.data.AiDj.CONTEXT_ID
    var request by remember { mutableStateOf("") }

    fun go(r: String?) {
        val ask = r?.takeUnless { it == "Surprise me" }
        if (on && ask != null) app.dj.ask(ask) else app.dj.play(ask)
        actions.toast(if (on) "The DJ heard you" else "Starting your DJ")
        onDismiss()
        if (!on) actions.openPlayer()
    }

    SpitifySheet(onDismiss, scrollable = true) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Headset, contentDescription = null, tint = SpitifyColors.Accent)
            Spacer(Modifier.width(10.dp))
            Text("DJ", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            if (on) "The DJ is on. Ask for something and the songs after this one change to match."
            else "Picks a set from what you've been playing, mixes it by tempo, key and energy, and keeps it going.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        dj.intro?.takeIf { on }?.let {
            Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            djRequests.forEach { r -> Pill(r, selected = false, onClick = { go(r) }) }
        }
        OutlinedTextField(
            value = request,
            onValueChange = { request = it.take(200) },
            singleLine = true,
            placeholder = { Text("Or ask for anything") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            if (dj.busy) CircularProgressIndicator(Modifier.size(22.dp), color = SpitifyColors.Accent, strokeWidth = 2.dp)
            else WhiteButton(if (on) "Ask the DJ" else "Start DJ", onClick = { go(request.ifBlank { null }) })
        }
    }
}

/** Move a playlist into a folder, out of one, or into a new one. */
@Composable
fun MoveToFolderSheet(playlistId: String, onDismiss: () -> Unit, onNewFolder: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val lib by app.library.state.collectAsStateWithLifecycle()
    val current = lib.playlists.firstOrNull { it.id == playlistId }?.folderId
    SpitifySheet(onDismiss) {
        Text("Move to folder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        SheetOption(Icons.Rounded.CreateNewFolder, "New folder", onClick = onNewFolder)
        if (current != null) {
            SheetOption(Icons.Rounded.FolderOff, "Take out of folder", onClick = {
                app.library.moveToFolder(playlistId, null)
                actions.toast("Moved out of the folder")
                onDismiss()
            })
        }
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(lib.folders, key = { it.id }) { f ->
                SheetOption(
                    Icons.Rounded.Folder,
                    f.name + if (f.id == current) " • here now" else "",
                    tint = if (f.id == current) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                    onClick = {
                        app.library.moveToFolder(playlistId, f.id)
                        actions.toast("Moved to ${f.name}")
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Suppress("unused")
private val queueIcon = Icons.AutoMirrored.Rounded.QueueMusic
