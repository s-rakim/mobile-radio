package com.srakim.spitify.ui.sheets

import android.content.Intent
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.SpitifyApp
import com.srakim.spitify.data.Friend
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Profile
import com.srakim.spitify.data.SharedPlaylist
import com.srakim.spitify.data.Track
import com.srakim.spitify.playback.OutputDevice
import com.srakim.spitify.playback.rememberOutputDevice
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.launch

/** A friend's colour from the server, or the accent if it doesn't parse. */
fun profileColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(SpitifyColors.Accent)

@Composable
fun Avatar(name: String, color: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(profileColor(color)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = Color.Black.copy(alpha = 0.8f),
        )
    }
}

/** Joins a friend's Jam: their queue, at the spot they're at now. */
suspend fun joinFriendJam(app: SpitifyApp, host: String): Boolean {
    val jam = runCatching { app.social.jamOf(host) }.getOrNull() ?: return false
    if (jam.tracks.isEmpty()) return false
    val behind = (System.currentTimeMillis() - jam.at).coerceAtLeast(0)
    app.player.play(jam.tracks, jam.index.coerceIn(0, jam.tracks.lastIndex), PlayContext("jam:$host", "${jam.host.displayName}'s Jam", "JAM"), shuffle = false)
    app.player.seekTo(jam.positionMs + behind)
    return true
}

private fun inviteFriends(context: android.content.Context, me: String?) {
    val text = buildString {
        append("Come listen with me on Spitify")
        if (me != null) append(". Add me, I'm $me: spitify://user/$me")
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite friends"))
}

// ---------------------------------------------------------------------------------------------
// Profile drawer

/** Slides in from the left when you tap your profile picture, like Spotify's side menu. */
@Composable
fun ProfileDrawer(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }

    fun close(then: () -> Unit = {}) {
        closing = true
        visible.targetState = false
        then()
    }
    LaunchedEffect(visible.currentState, closing) {
        if (closing && !visible.currentState && visible.isIdle) onDismiss()
    }
    LaunchedEffect(Unit) { if (acct.signedIn) app.social.refreshNow() }
    BackHandler { close() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { close() },
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInHorizontally(tween(260)) { -it },
            exit = slideOutHorizontally(tween(220)) { -it },
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(SpitifyColors.Elevated)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                val me = social.me
                val name = me?.displayName ?: settings.profileName.ifBlank { acct.username ?: "You" }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            close {
                                if (acct.signedIn) actions.openProfile(acct.username.orEmpty()) else actions.openAccount()
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(name, me?.color, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (acct.signedIn) "View profile" else "Log in", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = SpitifyColors.Highlight)
                DrawerRow(Icons.Rounded.Insights, "Listening stats") { close { actions.openStats() } }
                DrawerRow(Icons.Rounded.History, "Recents") { close { actions.openHistory() } }
                DrawerRow(Icons.Rounded.NotificationsNone, "Your Updates", badge = social.unread.takeIf { it > 0 }) { close { actions.openUpdates() } }
                DrawerRow(Icons.Rounded.Settings, "Settings and privacy") { close { actions.openSettings() } }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SpitifyColors.Highlight)
                if (!acct.signedIn) {
                    Text(
                        "Log in to follow friends, see what they're playing and message each other.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    DrawerRow(Icons.Rounded.PersonAdd, "Log in or sign up") { close { actions.openAccount() } }
                    return@Column
                }

                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Friend activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (settings.shareActivity) SpitifyColors.Accent.copy(alpha = 0.18f) else SpitifyColors.Highlight)
                            .clickable {
                                val on = !settings.shareActivity
                                app.settings.update { it.copy(shareActivity = on) }
                                scope.launch { runCatching { app.social.updateProfile(activity = on) } }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (settings.shareActivity) "Activity On" else "Activity Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (settings.shareActivity) SpitifyColors.Accent else SpitifyColors.TextSecondary,
                        )
                    }
                }
                val live = social.following.filter { it.nowPlaying?.track != null }.take(5)
                if (live.isEmpty()) {
                    Text(
                        if (social.following.isEmpty()) "Follow friends to see what they're listening to." else "None of your friends are playing anything right now.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                live.forEach { f -> FriendActivityRow(f) { close { actions.openProfile(f.username) } } }
                DrawerRow(Icons.Rounded.PersonAdd, "Invite friends") { inviteFriends(context, acct.username) }
                DrawerRow(Icons.Rounded.PersonAdd, "Find friends") { close { actions.openFriends() } }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SpitifyColors.Highlight)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Messages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "See all",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpitifyColors.TextSecondary,
                        modifier = Modifier.clip(CircleShape).clickable { close { actions.openMessages() } }.padding(8.dp),
                    )
                }
                social.conversations.take(4).forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { close { actions.openChat(c.with.username) } }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.with.displayName, c.with.color, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.with.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(previewOf(c.last), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (c.unread > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(SpitifyColors.Accent))
                    }
                }
                DrawerRow(Icons.AutoMirrored.Rounded.Chat, "New message") { close { actions.openFriends() } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

fun previewOf(m: com.srakim.spitify.data.Message): String = when (m.kind) {
    "track" -> "Sent ${m.track?.title ?: "a song"}"
    "playlist" -> "Shared ${m.playlist?.name ?: "a playlist"}"
    else -> m.text.orEmpty()
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, badge: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SpitifyColors.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(Modifier.clip(CircleShape).background(SpitifyColors.Accent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = SpitifyColors.OnAccent)
            }
        }
    }
}

@Composable
fun FriendActivityRow(f: Friend, onClick: () -> Unit) {
    val np = f.nowPlaying
    val track = np?.track
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(f.displayName, f.color, 40.dp)
            if (np?.live == true) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(SpitifyColors.Elevated).padding(2.dp)
                        .clip(CircleShape).background(SpitifyColors.Accent),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (track != null) {
                Text("${track.title} • ${track.artist}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (np.live) "Listening now" else agoText(np.at), style = MaterialTheme.typography.labelSmall, color = SpitifyColors.TextMuted)
            }
        }
        if (track != null) Artwork(track.artwork, Modifier.size(40.dp))
    }
}

fun agoText(at: Long): String {
    val mins = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        else -> "${mins / (24 * 60)}d ago"
    }
}

// ---------------------------------------------------------------------------------------------
// Connect

/** Spotify's Connect sheet: where music is playing, volume, Jams and nearby invites. */
@Composable
fun ConnectSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback by app.player.state.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val hosting by app.nearby.hosting.collectAsStateWithLifecycle()
    val nearby by app.nearby.nearby.collectAsStateWithLifecycle()
    val output = rememberOutputDevice()
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1 }
    var volume by remember { mutableFloatStateOf((audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVol) }

    SpitifySheet(onDismiss, scrollable = true) {
        Text("Current device", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(SpitifyColors.Accent.copy(alpha = 0.14f)).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (output.external) Icons.Rounded.Headphones else Icons.Rounded.Smartphone, contentDescription = null, tint = SpitifyColors.Accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(output.name, style = MaterialTheme.typography.titleMedium, color = SpitifyColors.Accent)
                    Text(if (output.external) "Connected" else "This phone", style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.clip(CircleShape).background(SpitifyColors.Highlight).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
            playback.current?.let { t ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(t.artwork, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
            Slider(
                value = volume,
                onValueChange = { v ->
                    volume = v
                    audio?.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).toInt(), 0)
                },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = SpitifyColors.Accent, inactiveTrackColor = SpitifyColors.Highlight),
            )
        }

        Row(Modifier.fillMaxWidth().clickable {
            val on = !settings.autoInviteNearby
            app.settings.update { it.copy(autoInviteNearby = on) }
            app.nearby.setAutoInvite(on, app.social.myName)
        }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = SpitifyColors.TextSecondary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-invite people nearby to a Jam", style = MaterialTheme.typography.bodyLarge)
                Text("Phones with Spitify on the same Wi-Fi get an invite while you host.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = settings.autoInviteNearby,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpitifyColors.Accent, uncheckedTrackColor = SpitifyColors.Highlight, uncheckedBorderColor = SpitifyColors.Highlight),
            )
        }

        if (nearby.isNotEmpty()) {
            Text("Jams near you", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            nearby.forEach { host ->
                SheetOption(Icons.Rounded.Wifi, "Join $host's Jam", tint = SpitifyColors.Accent, onClick = {
                    scope.launch {
                        if (joinFriendJam(app, host)) {
                            actions.toast("You're in $host's Jam")
                            onDismiss()
                        } else actions.toast("That Jam has ended")
                    }
                })
            }
        }

        Text("Select a device", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        SheetOption(Icons.Rounded.Smartphone, "This phone", tint = if (!output.external) SpitifyColors.Accent else SpitifyColors.TextSecondary, onClick = { OutputDevice.openPicker(context) })
        if (output.external) SheetOption(Icons.Rounded.Headphones, output.name, tint = SpitifyColors.Accent, onClick = { OutputDevice.openPicker(context) })
        SheetOption(Icons.Rounded.Cast, "Speakers, TVs and other devices", onClick = { OutputDevice.openPicker(context) })
        SheetOption(Icons.Rounded.Bluetooth, "Bluetooth", onClick = {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
        })

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (hosting) SpitifyColors.Highlight else SpitifyColors.TextPrimary)
                    .clickable {
                        if (hosting) {
                            app.nearby.stopHosting(app.social)
                            actions.toast("Jam ended")
                            return@clickable
                        }
                        val track = playback.current
                        if (track == null) {
                            actions.toast("Play something first, then start a Jam")
                            return@clickable
                        }
                        if (app.account.state.value.signedIn) app.nearby.startHosting(app.player, app.social, app.settings)
                        onDismiss()
                        actions.showJam(track)
                    }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    if (hosting) "End Jam" else "Start a Jam",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hosting) SpitifyColors.TextPrimary else SpitifyColors.Background,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Send to a friend

@Composable
fun SendToSheet(track: Track?, playlist: SharedPlaylist?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (acct.signedIn) app.social.refreshNow() }

    SpitifySheet(onDismiss) {
        Text(
            "Send ${track?.title ?: playlist?.name ?: ""}",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (!acct.signedIn) {
            Text("Log in to send songs to friends on Spitify.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
            SheetOption(Icons.Rounded.PersonAdd, "Log in", onClick = { onDismiss(); actions.openAccount() })
            return@SpitifySheet
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            singleLine = true,
            placeholder = { Text("Add a note (optional)") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search friends") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val people = remember(social.friends, social.conversations, query) {
            val fromChats = social.conversations.map { it.with }
            val all = (fromChats + social.friends.map { it.profile }).distinctBy { it.username }
            all.filter { query.isBlank() || it.displayName.contains(query, true) || it.username.contains(query, true) }
        }
        if (people.isEmpty()) {
            Text("Follow friends first, then you can send them music.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
            SheetOption(Icons.Rounded.PersonAdd, "Find friends", onClick = { onDismiss(); actions.openFriends() })
        }
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(people, key = { it.username }) { p ->
                PersonRow(p) {
                    scope.launch {
                        val sent = runCatching {
                            if (track != null) app.social.sendTrack(p.username, track, note.ifBlank { null })
                            else if (playlist != null) app.social.sendPlaylist(p.username, playlist, note.ifBlank { null })
                            else Unit
                        }
                        actions.toast(if (sent.isSuccess) "Sent to ${p.displayName}" else sent.exceptionOrNull()?.message ?: "Couldn't send that")
                        if (sent.isSuccess) onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
fun PersonRow(p: Profile, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.displayName, p.color, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(subtitle ?: "@${p.username}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
// Blend

@Composable
fun BlendPickerSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (acct.signedIn) app.social.refreshNow() }
    SpitifySheet(onDismiss) {
        Text("Blend", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Text(
            "Pick a friend. Spitify mixes your listening with theirs into one playlist and shows how well your tastes match.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (!acct.signedIn) {
            SheetOption(Icons.Rounded.PersonAdd, "Log in to make a Blend", onClick = { onDismiss(); actions.openAccount() })
            return@SpitifySheet
        }
        if (social.following.isEmpty()) {
            SheetOption(Icons.Rounded.PersonAdd, "Find friends to blend with", onClick = { onDismiss(); actions.openFriends() })
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(social.following, key = { it.username }) { f ->
                PersonRow(f.profile) {
                    onDismiss()
                    actions.openBlend(f.username)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}
