package com.srakim.spitify.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.BlendResult
import com.srakim.spitify.data.Chat
import com.srakim.spitify.data.Collab
import com.srakim.spitify.data.Friend
import com.srakim.spitify.data.Message
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Profile
import com.srakim.spitify.data.SharedPlaylist
import com.srakim.spitify.ui.AppSheet
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.EmptyState
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.MosaicArt
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.components.PlayCircle
import com.srakim.spitify.ui.components.SkeletonTrackList
import com.srakim.spitify.ui.components.WhiteButton
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.sheets.Avatar
import com.srakim.spitify.ui.sheets.CreatePlaylistDialog
import com.srakim.spitify.ui.sheets.FriendActivityRow
import com.srakim.spitify.ui.sheets.PersonRow
import com.srakim.spitify.ui.sheets.agoText
import com.srakim.spitify.ui.sheets.previewOf
import com.srakim.spitify.ui.sheets.profileColor
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A plain page with a back arrow and a title, for the social screens. */
@Composable
fun SimplePage(title: String, trailing: (@Composable () -> Unit)? = null, content: LazyListScope.() -> Unit) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
        Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 24.dp), content = content)
    }
}

/** Shown on social screens when nobody's logged in. */
internal fun LazyListScope.needsAccount() {
    item {
        val actions = LocalActions.current
        EmptyState(
            "Log in to Spitify",
            "Friends, messages, Blends and shared playlists live on your Spitify server.",
            action = "Log in",
            onAction = actions::openAccount,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Messages

@Composable
fun MessagesScreen() {
    val app = LocalApp.current
    val actions = LocalActions.current
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.social.refreshNow() }
    SimplePage("Messages", trailing = {
        IconButton(onClick = actions::openFriends) { Icon(Icons.Rounded.Edit, contentDescription = "New message") }
    }) {
        if (!acct.signedIn) return@SimplePage needsAccount()
        if (social.conversations.isEmpty()) {
            item {
                EmptyState("No messages yet", "Send a friend a song from any song's menu, or start a chat from their profile.", action = "New message", onAction = actions::openFriends)
            }
        }
        items(social.conversations, key = { it.with.username }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable { actions.openChat(c.with.username) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.with.displayName, c.with.color, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.with.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        previewOf(c.last) + " • " + agoText(c.last.at),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.unread > 0) SpitifyColors.TextPrimary else SpitifyColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (c.unread > 0) Box(Modifier.size(10.dp).clip(CircleShape).background(SpitifyColors.Accent))
            }
        }
    }
}

private val reactions = listOf("❤️", "🔥", "😂", "👏", "😮", "🙌")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(name: String) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val me = app.account.state.collectAsStateWithLifecycle().value.username
    var chat by remember { mutableStateOf<Chat?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var reactingTo by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(name) {
        while (true) {
            runCatching { app.social.chat(name) }.fold({ chat = it; error = null }, { error = it.message })
            delay(5_000)
        }
    }
    LaunchedEffect(chat?.messages?.size) { chat?.messages?.size?.let { if (it > 0) list.animateScrollToItem(it - 1) } }

    Column(Modifier.fillMaxSize().background(SpitifyColors.Background).imePadding()) {
        Row(Modifier.statusBarsPadding().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            val with = chat?.with
            Row(Modifier.weight(1f).clickable { actions.openProfile(name) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(with?.displayName ?: name, with?.color, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(with?.displayName ?: name, style = MaterialTheme.typography.titleMedium)
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                chat == null && error != null -> ErrorState(error!!, onRetry = { error = null })
                chat == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpitifyColors.Accent) }
                else -> LazyColumn(state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (chat!!.messages.isEmpty()) {
                        item { Text("Say hi, or send a song from any song's menu.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                    }
                    items(chat!!.messages, key = { it.id }) { m ->
                        val mine = m.from == me
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Box(
                                Modifier
                                    .widthIn(max = 290.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (mine) SpitifyColors.Accent.copy(alpha = 0.22f) else SpitifyColors.Elevated)
                                    .combinedClickable(onClick = { open(m, app, actions) }, onLongClick = { reactingTo = m.id })
                                    .padding(10.dp),
                            ) { MessageBody(m) }
                            if (m.reactions.isNotEmpty()) {
                                Text(m.reactions.values.joinToString(" "), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            if (reactingTo == m.id) {
                                Row(
                                    Modifier.clip(CircleShape).background(SpitifyColors.Elevated).padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    reactions.forEach { e ->
                                        Text(e, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(CircleShape).clickable {
                                            reactingTo = null
                                            scope.launch { runCatching { app.social.react(name, m.id, if (m.reactions[me] == e) null else e) }; chat = runCatching { app.social.chat(name) }.getOrNull() ?: chat }
                                        }.padding(6.dp))
                                    }
                                    IconButton(onClick = { reactingTo = null }) { Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(1000) },
                placeholder = { Text("Message") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val text = draft.trim()
                if (text.isEmpty()) return@IconButton
                draft = ""
                scope.launch {
                    runCatching { app.social.sendText(name, text) }
                        .onSuccess { chat = runCatching { app.social.chat(name) }.getOrNull() ?: chat }
                        .onFailure { actions.toast(it.message ?: "Couldn't send that") }
                }
            }) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = SpitifyColors.Accent) }
        }
    }
}

private fun open(m: Message, app: com.srakim.spitify.SpitifyApp, actions: com.srakim.spitify.ui.AppActions) {
    m.track?.let {
        app.player.play(listOf(it), 0, PlayContext("chat:${m.id}", it.title, "SONG"))
        actions.openPlayer()
    }
    m.playlist?.let { p ->
        if (p.tracks.isNotEmpty()) {
            app.player.play(p.tracks, 0, PlayContext("shared:${p.id}", p.name, "PLAYLIST"))
            actions.openPlayer()
        }
    }
}

@Composable
private fun MessageBody(m: Message) {
    when (m.kind) {
        "track" -> m.track?.let { t ->
            Column {
                m.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(6.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(t.artwork, Modifier.size(48.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = SpitifyColors.Accent)
                }
            }
        }
        "playlist" -> m.playlist?.let { p ->
            Column {
                m.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(6.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MosaicArt(p.tracks.take(4).map { it.artwork }, Modifier.size(48.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Playlist • ${p.tracks.size} songs", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = SpitifyColors.Accent)
                }
            }
        }
        else -> Text(m.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Friends

@Composable
fun FriendsScreen() {
    val app = LocalApp.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Friend>>(emptyList()) }
    LaunchedEffect(Unit) { app.social.refreshNow() }
    LaunchedEffect(query) {
        delay(300)
        results = if (query.isBlank()) emptyList() else runCatching { app.social.searchUsers(query.trim()) }.getOrDefault(emptyList())
    }

    fun toggleFollow(f: Friend) = scope.launch {
        runCatching { if (f.following) app.social.unfollow(f.username) else app.social.follow(f.username) }
            .onSuccess { actions.toast(if (f.following) "Unfollowed ${f.displayName}" else "Following ${f.displayName}") }
            .onFailure { actions.toast(it.message ?: "Couldn't do that") }
        results = results.map { if (it.username == f.username) it.copy(following = !f.following) else it }
    }

    SimplePage("Friends") {
        if (!acct.signedIn) return@SimplePage needsAccount()
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Find people on Spitify") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable {
                    val text = "Come listen with me on Spitify. Add me, I'm ${acct.username}: spitify://user/${acct.username}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite friends"))
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(SpitifyColors.Highlight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Text("Invite friends", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) item { Text("Nobody by that name yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
            items(results, key = { "r:" + it.username }) { f -> FriendRow(f, onFollow = { toggleFollow(f) }) { actions.openProfile(f.username) } }
            return@SimplePage
        }
        val following = social.friends.filter { it.following }
        val followers = social.friends.filter { it.followsYou && !it.following }
        if (followers.isNotEmpty()) {
            item { SectionTitle("Follow back") }
            items(followers, key = { "fb:" + it.username }) { f -> FriendRow(f, onFollow = { toggleFollow(f) }) { actions.openProfile(f.username) } }
        }
        item { SectionTitle("Following") }
        if (following.isEmpty()) item { Text("Search for friends by their Spitify username.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
        items(following, key = { "f:" + it.username }) { f -> FriendRow(f, onFollow = { toggleFollow(f) }) { actions.openProfile(f.username) } }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun FriendRow(f: Friend, onFollow: () -> Unit, onClick: () -> Unit) {
    PersonRow(
        f.profile,
        subtitle = f.nowPlaying?.track?.let { "${it.title} • ${it.artist}" } ?: if (f.followsYou) "Follows you" else "@${f.username}",
        trailing = {
            Box(
                Modifier.clip(CircleShape)
                    .background(if (f.following) Color.Transparent else SpitifyColors.TextPrimary)
                    .then(if (f.following) Modifier.background(SpitifyColors.Highlight) else Modifier)
                    .clickable(onClick = onFollow)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(if (f.following) "Following" else "Follow", style = MaterialTheme.typography.labelMedium, color = if (f.following) SpitifyColors.TextPrimary else SpitifyColors.Background)
            }
        },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------------------------------------
// Profiles

@Composable
fun ProfileScreen(name: String) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    val isMe = acct.username == name
    val profile = rememberLoad("profile", name) { app.social.profile(name) }
    val stats = rememberLoad("profile-stats", name) {
        runCatching { app.social.friendStats(com.srakim.spitify.data.ListeningStats.thisWeek()) }.getOrNull()?.people?.firstOrNull { it.username == name }
    }
    val friend = social.friends.firstOrNull { it.username == name }
    val p: Profile? = (profile.state as? Load.Ready)?.data ?: friend?.profile

    SimplePage(p?.displayName ?: name) {
        if (!acct.signedIn) return@SimplePage needsAccount()
        if (profile.state is Load.Failed && p == null) {
            item { ErrorState((profile.state as Load.Failed).message, profile.retry) }
            return@SimplePage
        }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p?.displayName ?: name, p?.color, 112.dp)
                Spacer(Modifier.height(12.dp))
                Text(p?.displayName ?: name, style = MaterialTheme.typography.headlineSmall)
                Text("@$name" + if (friend?.followsYou == true) " • Follows you" else "", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                if (!isMe) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (friend?.following == true) {
                            OutlineButton("Following", onClick = {
                                scope.launch { runCatching { app.social.unfollow(name) }.onSuccess { actions.toast("Unfollowed") } }
                            })
                        } else {
                            WhiteButton("Follow", onClick = {
                                scope.launch { runCatching { app.social.follow(name) }.onSuccess { actions.toast("Following ${p?.displayName ?: name}") }.onFailure { actions.toast(it.message ?: "Couldn't follow") } }
                            })
                        }
                        OutlineButton("Message", onClick = { actions.openChat(name) })
                        OutlineButton("Blend", onClick = { actions.openBlend(name) })
                    }
                } else {
                    OutlineButton("Edit profile", onClick = { actions.openSettingsSection("account") })
                }
            }
        }
        friend?.takeIf { it.nowPlaying?.track != null }?.let { f ->
            item { SectionTitle(if (f.nowPlaying?.live == true) "Listening now" else "Last played") }
            item { FriendActivityRow(f) { f.nowPlaying?.track?.let { t -> app.player.play(listOf(t), 0, PlayContext("friend:$name", t.title, "SONG")); actions.openPlayer() } } }
        }
        val week = (stats.state as? Load.Ready)?.data
        if (week != null && (week.topTracks.isNotEmpty() || week.topArtists.isNotEmpty())) {
            item { SectionTitle("This week • ${week.minutes} minutes") }
            items(week.topArtists.take(3), key = { "a:" + it.name }) { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(a.image, Modifier.size(44.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${a.plays} plays", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(week.topTracks.take(5), key = { "t:" + it.track.id }) { tp ->
                Row(
                    Modifier.fillMaxWidth().clickable { app.player.play(week.topTracks.map { it.track }, week.topTracks.indexOf(tp), PlayContext("friend-top:$name", "${p?.displayName ?: name}'s top songs", "PLAYLIST")) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(tp.track.artwork, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tp.track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tp.track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
        val shared = social.collabs.filter { c -> c.members.any { it.username == name } }
        if (shared.isNotEmpty()) {
            item { SectionTitle(if (isMe) "Your collaborative playlists" else "Playlists you share") }
            items(shared, key = { "c:" + it.id }) { c -> CollabRow(c) { actions.openCollab(c.id) } }
        }
    }
}

@Composable
fun CollabRow(c: Collab, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MosaicArt(c.tracks.take(4).map { it.track.artwork }, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Collaborative • ${c.tracks.size} songs • ${c.members.size} people", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Your Updates

@Composable
fun UpdatesScreen() {
    val app = LocalApp.current
    val actions = LocalActions.current
    val acct by app.account.state.collectAsStateWithLifecycle()
    val social by app.social.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.social.refreshNow() }
    val me = acct.username
    SimplePage("Your Updates") {
        if (!acct.signedIn) return@SimplePage needsAccount()
        val unread = social.conversations.filter { it.unread > 0 }
        val newFollowers = social.friends.filter { it.followsYou && !it.following }
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val collabNews = social.collabs.mapNotNull { c ->
            val added = c.tracks.filter { it.addedBy != me && it.at > weekAgo }
            if (added.isEmpty()) null else c to added
        }
        if (unread.isEmpty() && newFollowers.isEmpty() && collabNews.isEmpty()) {
            item { EmptyState("You're all caught up", "New messages, followers and songs friends add to shared playlists show up here.") }
        }
        items(unread, key = { "m:" + it.with.username }) { c ->
            PersonRow(c.with, subtitle = "${c.unread} new • ${previewOf(c.last)}") { actions.openChat(c.with.username) }
        }
        items(newFollowers, key = { "f:" + it.username }) { f ->
            PersonRow(f.profile, subtitle = "Followed you. Follow back to see what each other plays.") { actions.openProfile(f.username) }
        }
        items(collabNews, key = { "c:" + it.first.id }) { (c, added) ->
            Row(Modifier.fillMaxWidth().clickable { actions.openCollab(c.id) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MosaicArt(added.take(4).map { it.track.artwork }, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    val who = added.map { it.addedBy }.distinct().joinToString(", ")
                    Text("$who added ${added.size} song${if (added.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collaborative playlists

@Composable
fun CollabScreen(id: String) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = app.account.state.collectAsStateWithLifecycle().value.username
    val playback by app.player.state.collectAsStateWithLifecycle()
    var tick by remember { mutableStateOf(0) }
    val load = rememberLoad("collab", id, tick) { app.social.collab(id) }
    var renaming by remember { mutableStateOf(false) }

    when (val s = load.state) {
        is Load.Loading -> Box(Modifier.fillMaxSize().background(SpitifyColors.Background).statusBarsPadding()) { SkeletonTrackList() }
        is Load.Failed -> Box(Modifier.fillMaxSize().background(SpitifyColors.Background).statusBarsPadding()) { ErrorState(s.message, load.retry) }
        is Load.Ready -> {
            val c = s.data
            val tracks = c.tracks.map { it.track }
            val ctx = PlayContext("collab:${c.id}", c.name, "PLAYLIST")
            val color = profileColor(c.members.firstOrNull { it.username == c.owner }?.color)
            CollectionScaffold(title = c.name, color = color.copy(alpha = 0.7f), header = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MosaicArt(tracks.take(4).map { it.artwork }, Modifier.size(200.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(c.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        c.members.take(5).forEachIndexed { i, m -> Avatar(m.displayName, m.color, 26.dp, Modifier.offset(x = (-8 * i).dp)) }
                        Text(
                            c.members.joinToString(", ") { it.displayName }.let { if (it.length > 40) it.take(40) + "…" else it },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text("Collaborative playlist • ${tracks.size} songs", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val text = "Add songs to \"${c.name}\" with me on Spitify: ${c.link}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite to playlist"))
                    }) { Icon(Icons.Rounded.PersonAdd, contentDescription = "Invite friends") }
                    IconButton(onClick = { actions.showSheet(AppSheet.SendTo(playlist = SharedPlaylist(c.id, c.name, tracks.firstOrNull()?.artwork, tracks))) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Send to a friend")
                    }
                    if (c.owner == me) IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Rename") }
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { app.social.leaveCollab(c.id) }.onSuccess {
                                actions.toast(if (c.owner == me) "Playlist deleted" else "You left ${c.name}")
                                actions.back()
                            }
                        }
                    }) { Icon(Icons.Rounded.Logout, contentDescription = "Leave") }
                    Spacer(Modifier.weight(1f))
                    PlayCircle(playing = playback.isPlayingFrom(ctx.id) && playback.isPlaying, onClick = {
                        if (playback.isPlayingFrom(ctx.id)) app.player.togglePlayPause() else if (tracks.isNotEmpty()) app.player.play(tracks, 0, ctx)
                    })
                }
            }) {
                if (tracks.isEmpty()) {
                    item {
                        EmptyState("Nothing here yet", "Use Add to playlist on any song and pick ${c.name}. Everyone in it can add songs.", action = "Find songs", onAction = actions::openSearchInput)
                    }
                }
                items(c.tracks.size, key = { "${c.id}:${c.tracks[it].track.id}:$it" }) { i ->
                    val e = c.tracks[i]
                    com.srakim.spitify.ui.components.TrackRow(
                        track = e.track,
                        isCurrent = playback.current?.id == e.track.id,
                        isPlaying = playback.isPlaying,
                        liked = false,
                        onClick = { app.player.play(tracks, i, ctx) },
                        onMore = { actions.showTrackMenu(e.track, null) },
                        trailing = if (e.addedBy == me || c.owner == me) {
                            {
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { app.social.removeFromCollab(c.id, e.track.id) }.onSuccess { tick++ }
                                    }
                                }) { Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = SpitifyColors.TextSecondary) }
                            }
                        } else null,
                    )
                    Text(
                        "Added by ${c.members.firstOrNull { it.username == e.addedBy }?.displayName ?: e.addedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpitifyColors.TextMuted,
                        modifier = Modifier.padding(start = 76.dp, bottom = 4.dp),
                    )
                }
            }
            if (renaming) {
                CreatePlaylistDialog(
                    onDismiss = { renaming = false },
                    onCreate = { n ->
                        renaming = false
                        scope.launch { runCatching { app.social.renameCollab(c.id, n) }.onSuccess { tick++ } }
                    },
                    title = "Rename playlist",
                    initial = c.name,
                    confirm = "Save",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Blend

@Composable
fun BlendScreen(name: String) {
    val app = LocalApp.current
    val actions = LocalActions.current
    val playback by app.player.state.collectAsStateWithLifecycle()
    val load = rememberLoad("blend", name) { app.social.blend(name) }
    when (val s = load.state) {
        is Load.Loading -> Box(Modifier.fillMaxSize().background(SpitifyColors.Background).statusBarsPadding()) { SkeletonTrackList() }
        is Load.Failed -> Box(Modifier.fillMaxSize().background(SpitifyColors.Background).statusBarsPadding()) { ErrorState(s.message, load.retry) }
        is Load.Ready -> BlendBody(s.data, playback, app, actions)
    }
}

@Composable
private fun BlendBody(b: BlendResult, playback: com.srakim.spitify.playback.PlaybackState, app: com.srakim.spitify.SpitifyApp, actions: com.srakim.spitify.ui.AppActions) {
    val ctx = PlayContext("blend:${b.with.username}", b.name, "BLEND")
    val me = app.social.state.collectAsStateWithLifecycle().value.me
    CollectionScaffold(title = b.name, color = profileColor(b.with.color).copy(alpha = 0.75f), header = {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Avatar(me?.displayName ?: "You", me?.color, 96.dp)
                Avatar(b.with.displayName, b.with.color, 96.dp, Modifier.offset(x = (-20).dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(b.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("${b.match}% taste match", style = MaterialTheme.typography.titleMedium, color = SpitifyColors.Accent)
            if (b.sharedArtists.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("You both play ${b.sharedArtists.take(3).joinToString(", ")}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlineButton("Save as playlist", onClick = {
                    val made = app.library.createPlaylist(b.name, b.tracks, description = "A Blend of you and ${b.with.displayName}")
                    actions.toast("Saved to Your Library")
                    actions.openMyPlaylist(made.id)
                })
                PlayCircle(playing = playback.isPlayingFrom(ctx.id) && playback.isPlaying, onClick = {
                    if (playback.isPlayingFrom(ctx.id)) app.player.togglePlayPause() else if (b.tracks.isNotEmpty()) app.player.play(b.tracks, 0, ctx)
                })
            }
        }
    }) {
        if (b.tracks.isEmpty()) item { EmptyState("Not enough to blend yet", "Play a bit more music, both of you, and check back.") }
        collectionTracks(b.tracks, ctx, playback.current?.id, playback.isPlaying, emptySet(), onPlay = { app.player.play(b.tracks, it, ctx) }, onMore = { actions.showTrackMenu(it, null) })
    }
}

@Suppress("unused")
private val groupsIcon = Icons.Rounded.Groups
