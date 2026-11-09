package com.srakim.spitify.ui.sheets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalDownloads
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.theme.SpitifyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpitifySheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SpitifyColors.Elevated,
        contentColor = SpitifyColors.TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

@Composable
fun SheetOption(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = SpitifyColors.TextSecondary) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Long-press or "more" on any song. */
@Composable
fun TrackMenuSheet(track: Track, playlistId: String?, onDismiss: () -> Unit) {
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val downloads = LocalDownloads.current
    val context = LocalContext.current
    val lib by library.state.collectAsStateWithLifecycle()
    val dl by downloads.state.collectAsStateWithLifecycle()
    val liked = lib.liked.any { it.id == track.id }
    val downloaded = dl.has(track.id)

    SpitifySheet(onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        HorizontalDivider(color = SpitifyColors.Divider, modifier = Modifier.padding(vertical = 8.dp))
        SheetOption(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
            tint = if (liked) SpitifyColors.Accent else SpitifyColors.TextSecondary,
            onClick = {
                val now = library.toggleLike(track)
                actions.toast(if (now) "Added to Liked Songs" else "Removed from Liked Songs")
                onDismiss()
            },
        )
        SheetOption(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", onClick = {
            onDismiss()
            actions.showAddToPlaylist(track)
        })
        if (playlistId != null) {
            SheetOption(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist", onClick = {
                library.removeFromPlaylist(playlistId, track.id)
                actions.toast("Removed from playlist")
                onDismiss()
            })
        }
        SheetOption(Icons.Rounded.SkipNext, "Play next", onClick = {
            player.playNext(track)
            actions.toast("Playing next")
            onDismiss()
        })
        SheetOption(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", onClick = {
            player.addToQueue(track)
            actions.toast("Added to queue")
            onDismiss()
        })
        if (track.canDownload) {
            SheetOption(
                if (downloaded) Icons.Rounded.DownloadForOffline else Icons.Rounded.Download,
                if (downloaded) "Remove download" else "Download",
                tint = if (downloaded) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                onClick = {
                    if (downloaded) {
                        downloads.remove(listOf(track.id))
                        actions.toast("Download removed")
                    } else if (downloads.download(listOf(track))) {
                        actions.toast("Downloading")
                    } else {
                        actions.toast("Downloads are set to Wi-Fi only")
                    }
                    onDismiss()
                },
            )
        }
        if (track.canDownload) {
            SheetOption(Icons.Rounded.SaveAlt, "Save to phone", onClick = {
                actions.toast("Saving to your phone…")
                downloads.saveToPhone(track) { result ->
                    result.fold(
                        { folder -> actions.toast("Saved to $folder") },
                        { actions.toast(it.message ?: "Couldn't save the song") },
                    )
                }
                onDismiss()
            })
        }
        if (track.artistId.isNotBlank()) {
            SheetOption(Icons.Rounded.Person, "Go to artist", onClick = {
                onDismiss()
                actions.openArtist(track.artistId)
            })
        }
        if (!track.genre.isNullOrBlank()) {
            SheetOption(Icons.Rounded.Album, "Go to ${track.genre} mix", onClick = {
                onDismiss()
                actions.openMix(track.genre)
            })
        }
        SheetOption(Icons.Rounded.Share, "Share", onClick = {
            val text = buildString {
                append("${track.title} by ${track.artist}")
                track.link?.let { append("\n").append(it) }
            }
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, "Share song"))
            onDismiss()
        })
    }
}

@Composable
fun AddToPlaylistSheet(track: Track, onDismiss: () -> Unit) {
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()

    SpitifySheet(onDismiss) {
        Text(
            "Add to playlist",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            WhiteButton("New playlist", onClick = {
                onDismiss()
                actions.showCreatePlaylist(track)
            })
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(lib.playlists, key = { it.id }) { pl ->
                val already = pl.tracks.any { it.id == track.id }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (library.addToPlaylist(pl.id, track)) actions.toast("Added to ${pl.name}")
                            else actions.toast("Already in ${pl.name}")
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MosaicArt(pl.tracks.map { it.artwork }, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pl.tracks.size} songs", style = MaterialTheme.typography.bodySmall)
                    }
                    if (already) Icon(Icons.Rounded.CheckCircle, contentDescription = "Already added", tint = SpitifyColors.Accent)
                }
            }
        }
        if (lib.playlists.isEmpty()) {
            Text(
                "You haven't made any playlists yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpitifyColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/** What the Create tab opens. */
@Composable
fun CreateSheet(onDismiss: () -> Unit, onPlaylist: () -> Unit) {
    SpitifySheet(onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlaylist)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(SpitifyColors.Highlight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = SpitifyColors.TextSecondary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Playlist", style = MaterialTheme.typography.titleMedium)
                Text("Build a playlist with songs", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Full-screen "Give your playlist a name" prompt. Also used for renaming. */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    title: String = "Give your playlist a name",
    initial: String = "My playlist",
    confirm: String = "Create",
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(SpitifyColors.Highlight, SpitifyColors.Background)))
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center, color = SpitifyColors.TextPrimary),
                cursorBrush = SolidColor(SpitifyColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(SpitifyColors.TextMuted))
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlineButton("Cancel", onClick = onDismiss)
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(SpitifyColors.Accent)
                        .clickable { onCreate(name) }
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                ) {
                    Text(confirm, style = MaterialTheme.typography.labelLarge, color = SpitifyColors.OnAccent)
                }
            }
        }
    }
}
