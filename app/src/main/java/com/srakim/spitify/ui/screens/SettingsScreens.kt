package com.srakim.spitify.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.srakim.spitify.data.AppIcon
import com.srakim.spitify.data.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.srakim.spitify.BuildConfig
import com.srakim.spitify.data.AppUpdater
import com.srakim.spitify.data.AppSettings
import com.srakim.spitify.data.VolumeLevel
import com.srakim.spitify.data.DataSaverMode
import com.srakim.spitify.data.Quality
import com.srakim.spitify.data.ShuffleStyle
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApp
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalDownloads
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.data.LocalMusic
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.LocalSettings
import com.srakim.spitify.ui.LocalThemes
import com.srakim.spitify.ui.theme.Palettes
import com.srakim.spitify.ui.components.MyAvatar
import com.srakim.spitify.ui.theme.SpitifyColors
import com.srakim.spitify.ui.theme.SpitifyPalette

@Composable
private fun SettingsPage(title: String, content: LazyListScope.() -> Unit) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
        Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 48.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 24.dp), content = content)
    }
}

private fun LazyListScope.group(title: String) {
    item(key = "group:$title") {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpitifyColors.TextSecondary)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SpitifyColors.Accent,
                uncheckedThumbColor = SpitifyColors.TextSecondary,
                uncheckedTrackColor = SpitifyColors.Highlight,
                uncheckedBorderColor = SpitifyColors.Highlight,
            ),
        )
    }
}

@Composable
private fun spitifySliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = SpitifyColors.Accent,
    inactiveTrackColor = SpitifyColors.Highlight,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

/** One of the pages under Settings, in the order the hub lists them. */
enum class SettingsSection(val id: String, val title: String, val subtitle: String) {
    ACCOUNT("account", "Account", "Name, Spitify account, server address"),
    CONTENT("content", "Content and display", "App icon, theme, lyrics"),
    PRIVACY("privacy", "Privacy and social", "Private session, listening activity, history"),
    PLAYBACK("playback", "Playback", "Gapless playback, Automix, crossfade, shuffle, volume"),
    NOTIFICATIONS("notifications", "Notifications", "Messages, followers, shared playlists"),
    DEVICES("devices", "Apps and devices", "Spotify import, music sources, local files, Jams"),
    DATA("data", "Data-saving and offline", "Data saver, offline mode, downloads, storage"),
    QUALITY("quality", "Media quality", "Wi-Fi and cellular quality"),
    ABOUT("about", "About and support", "Version, updates, where the music comes from"),
    ;

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Settings: the hub with quick tiles and the section list, or one section when [section] is set. */
@Composable
fun SettingsScreen(section: String? = null) {
    val page = SettingsSection.of(section)
    if (page == null) SettingsHub() else SettingsSectionPage(page)
}

@Composable
private fun SettingsHub() {
    val store = LocalSettings.current
    val actions = LocalActions.current
    val app = LocalApp.current
    val settings by store.state.collectAsStateWithLifecycle()
    val acct by app.account.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    SettingsPage("Settings") {
        item {
            // Your profile first, like Spotify: picture, name, and a way to change them.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (acct.signedIn) actions.openProfile(acct.username.orEmpty())
                        else actions.showSheet(com.srakim.spitify.ui.AppSheet.EditProfile)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyAvatar(56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.profileName.ifBlank { acct.username ?: "Your profile" }, style = MaterialTheme.typography.titleMedium)
                    Text(if (acct.signedIn) "View profile" else "Add your name and photo", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpitifyColors.TextSecondary)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickTile(
                    "Data saver mode",
                    settings.dataSaverMode.label,
                    on = settings.dataSaverMode != DataSaverMode.OFF,
                    modifier = Modifier.weight(1f),
                ) {
                    val next = when (settings.dataSaverMode) {
                        DataSaverMode.OFF -> DataSaverMode.AUTO
                        DataSaverMode.AUTO -> DataSaverMode.ON
                        DataSaverMode.ON -> DataSaverMode.OFF
                    }
                    store.update { it.copy(dataSaverMode = next) }
                }
                QuickTile("Offline mode", if (settings.offlineMode) "On" else "Off", settings.offlineMode, Modifier.weight(1f)) {
                    store.update { it.copy(offlineMode = !it.offlineMode) }
                    actions.toast(if (!settings.offlineMode) "Offline mode on. Only downloads will play." else "You're back online")
                }
                QuickTile("Private session", if (settings.privateSession) "On" else "Off", settings.privateSession, Modifier.weight(1f)) {
                    store.update { it.copy(privateSession = !it.privateSession) }
                }
            }
        }
        SettingsSection.entries.forEach { s ->
            item(key = s.id) { SettingRow(s.title, s.subtitle, onClick = { actions.openSettingsSection(s.id) }) }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                if (acct.signedIn) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(SpitifyColors.TextPrimary)
                            .clickable { confirmLogout = true }
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                    ) { Text("Log out", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.Background) }
                } else {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(SpitifyColors.Accent)
                            .clickable { actions.openAccount() }
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                    ) { Text("Log in", style = MaterialTheme.typography.labelLarge, color = SpitifyColors.OnAccent) }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Log out?") },
            text = { Text("Your library stays on this phone and on your Spitify server. Friends, messages and Jams need you to log back in.") },
            confirmButton = {
                TextButton(onClick = {
                    app.account.signOut()
                    confirmLogout = false
                    actions.toast("Logged out")
                }) { Text("Log out", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }
}

@Composable
private fun QuickTile(title: String, value: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) SpitifyColors.Accent.copy(alpha = 0.18f) else SpitifyColors.Elevated)
            .clickable(onClick = onClick)
            .heightIn(min = 84.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = if (on) SpitifyColors.Accent else SpitifyColors.TextSecondary)
    }
}

@Composable
private fun <T> RadioRow(label: String, description: String?, value: T, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(selected = value == selected, onClick = { onSelect(value) }, colors = RadioButtonDefaults.colors(selectedColor = SpitifyColors.Accent))
    }
}

@Composable
private fun SettingsSectionPage(page: SettingsSection) {
    val app = LocalApp.current
    val store = LocalSettings.current
    val library = LocalLibrary.current
    val downloads = LocalDownloads.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val settings by store.state.collectAsStateWithLifecycle()
    val dl by downloads.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val lib by library.state.collectAsStateWithLifecycle()
    val acct by app.account.state.collectAsStateWithLifecycle()

    var editingServer by remember { mutableStateOf(false) }
    var editingWeb by remember { mutableStateOf(false) }
    var editingSpotifyId by remember { mutableStateOf(false) }
    var editingJamendo by remember { mutableStateOf(false) }
    var editingSoundCloud by remember { mutableStateOf(false) }
    val updater = app.updater
    val updateStatus by updater.status.collectAsStateWithLifecycle()
    val catalog = LocalCatalog.current
    val scope = rememberCoroutineScope()
    var serverStatus by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(settings.subsonicUrl, settings.subsonicUser, settings.subsonicPassword, settings.subsonicEnabled) {
        serverStatus = if (!catalog.server.enabled) null else {
            serverStatus = "Checking…"
            catalog.server.ping().fold({ "Connected" }, { "Not working: ${it.message}" })
        }
    }
    var webStatus by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(settings.webServerUrl, settings.webServerEnabled) {
        webStatus = if (!catalog.web.enabled) null else {
            webStatus = "Checking…"
            catalog.web.ping().fold({ "Connected" }, { "Not working: ${it.message}" })
        }
    }
    val localTracks by catalog.local.tracks.collectAsStateWithLifecycle()
    var localGranted by remember { mutableStateOf(catalog.local.hasPermission()) }
    val askLocal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        localGranted = ok
        if (ok) scope.launch { catalog.local.refresh() } else actions.toast("Spitify can't see your music without that permission")
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) actions.toast("Notifications are off for Spitify in Android settings")
    }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var confirmOfflineAccessOff by remember { mutableStateOf(false) }
    var storageTick by remember { mutableIntStateOf(0) }
    val downloadBytes = remember(dl.downloaded.size, storageTick) { downloads.bytesUsed() }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var disk by remember { mutableStateOf(0L to 0L) }
    androidx.compose.runtime.LaunchedEffect(storageTick) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            disk = runCatching {
                val fs = android.os.StatFs(context.filesDir.absolutePath)
                fs.totalBytes to fs.availableBytes
            }.getOrDefault(0L to 0L)
        }
    }

    fun set(block: (AppSettings) -> AppSettings) = store.update(block)

    fun needNotifyPermission(): Boolean = android.os.Build.VERSION.SDK_INT >= 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

    SettingsPage(page.title) {
        when (page) {
            SettingsSection.ACCOUNT -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.showSheet(com.srakim.spitify.ui.AppSheet.EditProfile) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyAvatar(56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(settings.profileName.ifBlank { acct.username ?: "Your profile" }, style = MaterialTheme.typography.titleMedium)
                            Text("Edit name and photo. Friends see them too.", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpitifyColors.TextSecondary)
                    }
                }
                item {
                    SettingRow(
                        if (acct.signedIn) "Signed in as ${acct.username}" else "Log in or sign up",
                        if (acct.signedIn) "Your playlists, likes, friends and messages live on your Spitify server" else "Keep your library on your Spitify server and unlock friends, messages, Blends and Jams",
                        onClick = actions::openAccount,
                    )
                }
                item {
                    SettingRow(
                        "Spitify server address",
                        settings.webServerUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL.ifBlank { "Not set" } } + (webStatus?.let { ". $it" } ?: ""),
                        onClick = { editingWeb = true },
                    )
                }
                if (acct.signedIn) {
                    item {
                        SettingRow("Share your profile", "Send friends a link that opens your profile in Spitify", onClick = {
                            val text = "Follow me on Spitify, I'm ${acct.username} there: spitify://user/${acct.username}"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share profile"))
                        })
                    }
                }
            }

            SettingsSection.CONTENT -> {
                item { AppIconPicker() }
                item {
                    SettingRow("Theme", SpitifyColors.palette.name, onClick = actions::openThemes) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(SpitifyColors.Accent))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpitifyColors.TextSecondary)
                        }
                    }
                }
                item {
                    ToggleRow("Show lyrics", "Show lyrics on the player when they're available.", settings.showLyrics) { v -> set { it.copy(showLyrics = v) } }
                }
            }

            SettingsSection.PRIVACY -> {
                item {
                    ToggleRow(
                        "Private session",
                        "Listen without adding to your history, stats or friend activity. Ends when you turn it off.",
                        settings.privateSession,
                    ) { v -> set { it.copy(privateSession = v) } }
                }
                item {
                    ToggleRow(
                        "Share my listening activity",
                        "People who follow you on Spitify can see what you're playing.",
                        settings.shareActivity,
                    ) { v ->
                        set { it.copy(shareActivity = v) }
                        if (acct.signedIn) scope.launch { runCatching { app.social.updateProfile(activity = v) } }
                    }
                }
                item {
                    SettingRow("Clear listening history", "${lib.recentTracks.size} songs in Recents", onClick = {
                        library.clearHistory()
                        actions.toast("Listening history cleared")
                    })
                }
                item {
                    SettingRow("Clear recent searches", onClick = {
                        library.clearRecentSearches()
                        actions.toast("Recent searches cleared")
                    })
                }
                item {
                    SettingRow("Reset listening stats", "Minutes, top songs and top artists kept on this phone", onClick = {
                        app.stats.clear()
                        actions.toast("Listening stats reset")
                    })
                }
                item {
                    SettingRow("Songs left out of your taste profile", "${lib.tasteExcluded.size} songs. They don't steer recommendations.")
                }
            }

            SettingsSection.PLAYBACK -> {
                item {
                    ToggleRow("Canvas", "Shows a looping visual made from the cover behind the song that's playing.", settings.canvas) { v -> set { it.copy(canvas = v) } }
                }
                item {
                    ToggleRow("Gapless playback", "Trims the silence between songs so one flows straight into the next.", settings.gapless) { v -> set { it.copy(gapless = v) } }
                }
                item {
                    ToggleRow("Automix", "Smooth transitions between songs on mixed playlists, genre mixes, radio and the DJ.", settings.automix) { v -> set { it.copy(automix = v) } }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Crossfade", style = MaterialTheme.typography.bodyLarge)
                        Text("Fades the end of each song into the next one.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("0s", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = settings.crossfadeSeconds.toFloat(),
                                onValueChange = { v -> set { it.copy(crossfadeSeconds = v.toInt()) } },
                                valueRange = 0f..12f,
                                steps = 11,
                                colors = spitifySliderColors(),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text("${settings.crossfadeSeconds}s", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item {
                    ToggleRow("Autoplay", "Keep listening to similar songs when your music ends.", settings.autoplay) { v -> set { it.copy(autoplay = v) } }
                }
                item {
                    ToggleRow("Mono audio", "Plays the same sound through both speakers or earbuds.", settings.monoAudio) { v -> set { it.copy(monoAudio = v) } }
                }
                item {
                    ToggleRow(
                        "Device broadcast status",
                        "Lets other apps on this phone, like scrobblers and widgets, see what you're listening to.",
                        settings.broadcastStatus,
                    ) { v -> set { it.copy(broadcastStatus = v) } }
                }
                item { SettingRow("Equalizer", if (settings.equalizerEnabled) "On" else "Off", onClick = actions::openEqualizer) }
                item {
                    ToggleRow("DJ voice", "The AI DJ says a line on air when it changes things up.", settings.djVoice) { v -> set { it.copy(djVoice = v) } }
                }
                group("Shuffle")
                item {
                    ToggleRow(
                        "Smart Shuffle",
                        "Adds Smart Shuffle to the shuffle button. It mixes in songs that suit what you're playing.",
                        settings.smartShuffle,
                    ) { v -> set { it.copy(smartShuffle = v) } }
                }
                ShuffleStyle.entries.forEach { style ->
                    item(key = "shuffle:${style.name}") {
                        RadioRow(style.label, style.description, style, settings.shuffleStyle) { v -> set { it.copy(shuffleStyle = v) } }
                    }
                }
                group("Volume")
                item {
                    ToggleRow(
                        "Volume normalization",
                        "Sets the same volume level for all songs and podcasts.",
                        settings.volumeNormalization,
                    ) { v -> set { it.copy(volumeNormalization = v) } }
                }
                VolumeLevel.entries.forEach { level ->
                    item(key = "volume:${level.name}") {
                        RadioRow(level.label, level.description, level, settings.volumeLevel) { v -> set { it.copy(volumeLevel = v) } }
                    }
                }
                group("Sleep timer")
                item {
                    SettingRow(
                        "Sleep timer",
                        when {
                            playback.sleepAtTrackEnd -> "Stops at the end of this song"
                            playback.sleepTimerEndsAt != null -> "On"
                            playback.current == null -> "Play something to set one"
                            else -> "Off"
                        },
                        onClick = if (playback.current != null) actions.showSleepTimer else null,
                    )
                }
            }

            SettingsSection.NOTIFICATIONS -> {
                item {
                    Text(
                        "Spitify checks in with your Spitify server while the app is open and lets you know about these.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                item {
                    ToggleRow("Messages", "When a friend sends you a song, a playlist or a message.", settings.notifyMessages) { v ->
                        set { it.copy(notifyMessages = v) }
                        if (v && needNotifyPermission()) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                item {
                    ToggleRow("New followers", "When someone follows you.", settings.notifyFollows) { v ->
                        set { it.copy(notifyFollows = v) }
                        if (v && needNotifyPermission()) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                item {
                    ToggleRow("Collaborative playlists", "When friends add songs to a playlist you share.", settings.notifyCollab) { v ->
                        set { it.copy(notifyCollab = v) }
                        if (v && needNotifyPermission()) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                item {
                    SettingRow("Android notification settings", "Sound, vibration and the lock screen", onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    })
                }
            }

            SettingsSection.DEVICES -> {
                item {
                    // Your Spotify account: Home, Search, Liked Songs and Library show its music.
                    val signedIn by app.spotifyImport.signedIn.collectAsStateWithLifecycle()
                    SettingRow(
                        if (signedIn) "Spotify account connected" else "Sign in with Spotify",
                        if (signedIn) "Your Spotify likes, playlists, albums, artists and recents show up across Spitify. Tap to sign out."
                        else "Show your Spotify music in Home, Search, Liked Songs and Your Library",
                        onClick = {
                            val importer = app.spotifyImport
                            when {
                                signedIn -> {
                                    importer.signOut()
                                    actions.toast("Signed out of Spotify")
                                }
                                importer.clientId.isBlank() -> editingSpotifyId = true
                                else -> runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, importer.authorizeUrl()))
                                }.onFailure { actions.toast("No browser found to sign in with") }
                            }
                        },
                    )
                }
                item {
                    SettingRow(
                        "Import from Spotify",
                        "Bring your Spotify playlists and Liked Songs into Spitify",
                        onClick = actions::openImport,
                    )
                }
                item {
                    val remote = app.spotifyRemote
                    val rs by remote.state.collectAsStateWithLifecycle()
                    SettingRow(
                        when {
                            rs.connected -> "Connected to Spotify"
                            rs.connecting -> "Connecting to Spotify…"
                            else -> "Connect to Spotify"
                        },
                        when {
                            !remote.included -> "This version was built without Spotify playback"
                            rs.connected && rs.canPlayOnDemand -> "Spotify songs play through the Spotify app. Tap to disconnect."
                            rs.connected -> "Your Spotify account doesn't have Premium, so Spotify songs play the closest match from Spitify's sources. Tap to disconnect."
                            rs.problem != null -> rs.problem
                            else -> "Play Spotify songs through the Spotify app on this phone. Needs Spotify Premium."
                        },
                        onClick = {
                            when {
                                !remote.included -> actions.toast("This version was built without Spotify playback")
                                rs.connected -> remote.disconnect()
                                else -> remote.connect(context, app.spotifyImport.clientId, interactive = true)
                            }
                        },
                    )
                }
                item {
                    ToggleRow(
                        "Play through Spotify",
                        "When it's off, Spotify songs always play the closest match from Spitify's own sources.",
                        settings.playThroughSpotify,
                    ) { v -> set { it.copy(playThroughSpotify = v) } }
                }
                item {
                    SettingRow(
                        "Spotify app id",
                        settings.spotifyClientId.ifBlank { BuildConfig.SPOTIFY_CLIENT_ID.ifBlank { "Not set. Needed to sign in to Spotify for imports." } }
                            .let { if (it.length > 24) it.take(8) + "…" else it },
                        onClick = { editingSpotifyId = true },
                    )
                }
                group("Connect")
                item {
                    ToggleRow(
                        "Auto-invite people nearby to a Jam",
                        "While you host a Jam, phones with Spitify open on the same Wi-Fi get an invite to join.",
                        settings.autoInviteNearby,
                    ) { v ->
                        set { it.copy(autoInviteNearby = v) }
                        app.nearby.setAutoInvite(v, app.social.myName)
                    }
                }
                item { SettingRow("Speakers and headphones", "Pick where your music plays", onClick = { com.srakim.spitify.playback.OutputDevice.openPicker(context) }) }
                group("Music sources")
                item { SettingRow("Audius", "Always on. The main catalogue.") }
                item {
                    ToggleRow("Internet Archive", "Live recordings, old radio and free albums, some in lossless FLAC.", settings.archiveEnabled) { v -> set { it.copy(archiveEnabled = v) } }
                }
                item {
                    ToggleRow("ccMixter", "Creative Commons remixes and instrumentals.", settings.ccMixterEnabled) { v -> set { it.copy(ccMixterEnabled = v) } }
                }
                item {
                    SettingRow(
                        "Jamendo",
                        if (settings.jamendoClientId.isBlank()) "Independent artists under Creative Commons. Add a free client id to turn it on."
                        else if (settings.jamendoEnabled) "On" else "Off",
                        onClick = { editingJamendo = true },
                    ) {
                        if (settings.jamendoClientId.isNotBlank()) {
                            Switch(
                                checked = settings.jamendoEnabled,
                                onCheckedChange = { v -> set { it.copy(jamendoEnabled = v) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpitifyColors.Accent, uncheckedThumbColor = SpitifyColors.TextSecondary, uncheckedTrackColor = SpitifyColors.Highlight, uncheckedBorderColor = SpitifyColors.Highlight),
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpitifyColors.TextSecondary)
                        }
                    }
                }
                item {
                    SettingRow(
                        "SoundCloud",
                        if (settings.soundCloudClientId.isBlank() || settings.soundCloudClientSecret.isBlank()) "Through SoundCloud's own API. Add your app's client id and secret to turn it on."
                        else "On, playable tracks only",
                        onClick = { editingSoundCloud = true },
                    )
                }
                item {
                    SettingRow(
                        "Your server",
                        when {
                            settings.subsonicUrl.isBlank() -> "Play your own collection from Navidrome or any Subsonic server."
                            else -> settings.subsonicUrl + (serverStatus?.let { ". $it" } ?: "")
                        },
                        onClick = { editingServer = true },
                    )
                }
                if (settings.subsonicUrl.isNotBlank()) {
                    item {
                        ToggleRow("Use your server", "Include it in Search, Home and Your Library.", settings.subsonicEnabled) { v -> set { it.copy(subsonicEnabled = v) } }
                    }
                }
                item {
                    ToggleRow(
                        "Spitify server results",
                        "Adds YouTube, Internet Archive, ccMixter and Jamendo to Search from your Spitify server.",
                        settings.webServerEnabled,
                    ) { v -> set { it.copy(webServerEnabled = v) } }
                }
                group("Local files")
                item {
                    ToggleRow(
                        "Show audio files from this device",
                        when {
                            !settings.showLocalFiles -> "Off"
                            !localGranted -> "Tap to let Spitify see the music on your phone."
                            else -> "${localTracks.size} songs found. They show up in Your Library and Search."
                        },
                        settings.showLocalFiles,
                    ) { v ->
                        set { it.copy(showLocalFiles = v) }
                        if (v && !catalog.local.hasPermission()) askLocal.launch(LocalMusic.PERMISSION)
                    }
                }
                if (settings.showLocalFiles && localGranted) {
                    item { SettingRow("Scan again", "Look for songs added since the last scan.", onClick = { scope.launch { catalog.local.refresh() } }) }
                }
            }

            SettingsSection.DATA -> {
                group("Data saver mode")
                DataSaverMode.entries.forEach { mode ->
                    item(key = "saver:${mode.name}") {
                        RadioRow(mode.label, mode.description, mode, settings.dataSaverMode) { v -> set { it.copy(dataSaverMode = v) } }
                    }
                }
                item {
                    Text(
                        "Data saver loads smaller artwork, streams at lower quality where the source offers it, and holds off on preloading.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                group("Offline")
                item {
                    ToggleRow(
                        "Offline mode",
                        "Only play music you've downloaded or saved on this phone. Nothing goes online.",
                        settings.offlineMode,
                    ) { v -> set { it.copy(offlineMode = v) } }
                }
                item {
                    ToggleRow(
                        "Offline access",
                        "Download songs and playlists to listen without a connection. Turning this off removes your downloads.",
                        settings.offlineAccess,
                    ) { v -> if (v) set { it.copy(offlineAccess = true) } else confirmOfflineAccessOff = true }
                }
                item {
                    ToggleRow(
                        "Download using cellular",
                        "Turn on to download over mobile data. Otherwise downloads wait for Wi-Fi.",
                        settings.downloadOnCellular,
                    ) { v -> set { it.copy(downloadOnCellular = v) } }
                }
                group("Storage")
                item { StorageBar(downloadBytes, cacheBytes, disk.first, disk.second) }
                item {
                    SettingRow("Downloads", "${dl.downloaded.size} songs • ${formatBytes(downloadBytes)}", onClick = actions::openDownloads)
                }
                item {
                    SettingRow("Remove all downloads", "Frees up the space used by downloaded songs.", onClick = { confirmClearDownloads = true })
                }
                item {
                    SettingRow("Clear cache", "Cached artwork and pages. ${formatBytes(cacheBytes)}", onClick = {
                        context.imageLoader.diskCache?.clear()
                        context.imageLoader.memoryCache?.clear()
                        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        storageTick++
                        actions.toast("Cache cleared")
                    })
                }
            }

            SettingsSection.QUALITY -> {
                group("Wi-Fi streaming")
                Quality.entries.forEach { q ->
                    item(key = "wifi:${q.name}") { RadioRow(q.label, q.description, q, settings.wifiQuality) { v -> set { it.copy(wifiQuality = v) } } }
                }
                group("Cellular streaming")
                Quality.entries.forEach { q ->
                    item(key = "cell:${q.name}") { RadioRow(q.label, q.description, q, settings.cellularQuality) { v -> set { it.copy(cellularQuality = v) } } }
                }
                item {
                    Text(
                        "Very high plays the best stream each source has: up to 320 kbps on Audius, YouTube's top audio stream, lossless FLAC from the Internet Archive where it exists, and your own server's original files. Low turns data saver on for that connection.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            SettingsSection.ABOUT -> {
                item { SettingRow("Version", BuildConfig.VERSION_NAME) }
                if (updater.supported) {
                    item {
                        SettingRow(
                            "Check for updates",
                            when (updateStatus) {
                                AppUpdater.Status.Checking -> "Checking…"
                                AppUpdater.Status.UpToDate -> "You're on the latest version"
                                is AppUpdater.Status.Downloading -> "Downloading…"
                                else -> "New builds install over this one and keep your library"
                            },
                            onClick = { updater.check() },
                        )
                    }
                }
                item {
                    SettingRow(
                        "Music catalogue",
                        "Songs come from Audius, an open music platform, plus anything saved on your phone and your own server if you add one.",
                    )
                }
                item { SettingRow("Lyrics", "Provided by LRCLIB, a free community lyrics database.") }
                item { SettingRow("Made by", "Malcolm (s-rakim)") }
                item {
                    Text(
                        "Spitify is an independent project. It isn't affiliated with or endorsed by Spotify.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpitifyColors.TextMuted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (editingJamendo) {
        var id by remember { mutableStateOf(settings.jamendoClientId) }
        AlertDialog(
            onDismissRequest = { editingJamendo = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Jamendo") },
            text = {
                Column {
                    Text("Make a free app at devportal.jamendo.com and paste its Client ID here.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    DialogField(id, { id = it.trim() }, "Client ID")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    set { it.copy(jamendoClientId = id, jamendoEnabled = id.isNotBlank()) }
                    editingJamendo = false
                }) { Text("Save", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editingJamendo = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (editingSoundCloud) {
        var id by remember { mutableStateOf(settings.soundCloudClientId) }
        var secret by remember { mutableStateOf(settings.soundCloudClientSecret) }
        AlertDialog(
            onDismissRequest = { editingSoundCloud = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("SoundCloud") },
            text = {
                Column {
                    Text(
                        "From an app registered at soundcloud.com/you/apps. Only tracks SoundCloud marks as playable show up, and they can't be downloaded. Without these, SoundCloud still comes through your Spitify server.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    DialogField(id, { id = it.trim() }, "Client ID")
                    Spacer(Modifier.height(8.dp))
                    DialogField(secret, { secret = it.trim() }, "Client secret", secret = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    set { it.copy(soundCloudClientId = id, soundCloudClientSecret = secret) }
                    editingSoundCloud = false
                    if (id.isNotBlank() && secret.isNotBlank()) scope.launch {
                        app.soundCloud.verify().fold(
                            { actions.toast("SoundCloud is connected") },
                            { actions.toast("SoundCloud didn't accept those: ${it.message}") },
                        )
                    }
                }) { Text("Save", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editingSoundCloud = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (editingSpotifyId) {
        var id by remember { mutableStateOf(settings.spotifyClientId) }
        AlertDialog(
            onDismissRequest = { editingSpotifyId = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Spotify app id") },
            text = {
                Column {
                    Text(
                        "The Client ID from your app on developer.spotify.com, with spitify://spotify-auth and spitify://spotify-remote added as Redirect URIs. Leave it blank to use the one built into this version.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    DialogField(id, { id = it.trim() }, "Client ID")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    set { it.copy(spotifyClientId = id) }
                    editingSpotifyId = false
                }) { Text("Save", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editingSpotifyId = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (editingServer) {
        var url by remember { mutableStateOf(settings.subsonicUrl) }
        var user by remember { mutableStateOf(settings.subsonicUser) }
        var password by remember { mutableStateOf(settings.subsonicPassword) }
        AlertDialog(
            onDismissRequest = { editingServer = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Your server") },
            text = {
                Column {
                    Text(
                        "The address of your Navidrome or Subsonic server, like http://192.168.1.20:4533 or your Tailscale name, plus your login.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    DialogField(url, { url = it.trim() }, "Server address")
                    Spacer(Modifier.height(8.dp))
                    DialogField(user, { user = it.trim() }, "Username")
                    Spacer(Modifier.height(8.dp))
                    DialogField(password, { password = it }, "Password", secret = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    set { it.copy(subsonicUrl = url, subsonicUser = user, subsonicPassword = password, subsonicEnabled = true) }
                    editingServer = false
                }) { Text("Save", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editingServer = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (editingWeb) {
        var url by remember { mutableStateOf(settings.webServerUrl) }
        AlertDialog(
            onDismissRequest = { editingWeb = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Spitify server") },
            text = {
                Column {
                    Text(
                        "Where your Spitify server runs. Your PC's Tailscale address like http://100.101.102.103:3000 never changes, or paste a trycloudflare.com link.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    DialogField(url, { url = it.trim() }, "Server address")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    set { it.copy(webServerUrl = url, webServerEnabled = true) }
                    editingWeb = false
                }) { Text("Save", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editingWeb = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Remove all downloads?") },
            text = { Text("You won't be able to play these songs offline until you download them again.") },
            confirmButton = {
                TextButton(onClick = {
                    downloads.removeAll()
                    storageTick++
                    confirmClearDownloads = false
                    actions.toast("Downloads removed")
                }) { Text("Remove", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmClearDownloads = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }

    if (confirmOfflineAccessOff) {
        AlertDialog(
            onDismissRequest = { confirmOfflineAccessOff = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Turn off offline access?") },
            text = { Text("This removes all ${dl.downloaded.size} downloaded songs and hides the download buttons.") },
            confirmButton = {
                TextButton(onClick = {
                    downloads.removeAll()
                    set { it.copy(offlineAccess = false, offlineMode = false) }
                    storageTick++
                    confirmOfflineAccessOff = false
                }) { Text("Turn off", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmOfflineAccessOff = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
        )
    }
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SpitifyColors.Accent,
            cursorColor = SpitifyColors.Accent,
            focusedLabelColor = SpitifyColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppIconPicker() {
    val context = LocalContext.current
    val actions = LocalActions.current
    var selected by remember { mutableStateOf(AppIcons.current(context)) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("App icon", style = MaterialTheme.typography.bodyLarge)
        Text("Pick the icon on your home screen. Your launcher may take a moment to update.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon.entries.forEach { icon ->
                val isSelected = icon == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!isSelected) {
                                AppIcons.select(context, icon)
                                selected = icon
                                actions.toast("App icon changed to ${icon.label}")
                            }
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFFFF4DC))
                            .then(if (isSelected) Modifier.border(3.dp, SpitifyColors.Accent, RoundedCornerShape(18.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(icon.preview),
                            contentDescription = "${icon.label} icon",
                            modifier = Modifier.size(60.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(icon.label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) SpitifyColors.Accent else SpitifyColors.TextPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageBar(downloadBytes: Long, cacheBytes: Long, totalBytes: Long = 0, freeBytes: Long = 0) {
    val other = (totalBytes - freeBytes - downloadBytes - cacheBytes).coerceAtLeast(0)
    val total = (if (totalBytes > 0) totalBytes else downloadBytes + cacheBytes).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(SpitifyColors.Highlight)) {
            if (other > 0) Box(Modifier.weight(other.toFloat() / total).height(8.dp).background(SpitifyColors.TextMuted))
            if (downloadBytes > 0) Box(Modifier.weight(downloadBytes.toFloat() / total).height(8.dp).background(SpitifyColors.Accent))
            if (cacheBytes > 0) Box(Modifier.weight(cacheBytes.toFloat() / total).height(8.dp).background(SpitifyColors.TextSecondary))
            val rest = (total - other - downloadBytes - cacheBytes).coerceAtLeast(0)
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat() / total))
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (totalBytes > 0) Legend(SpitifyColors.TextMuted, "Other apps ${formatBytes(other)}")
            Legend(SpitifyColors.Accent, "Downloads ${formatBytes(downloadBytes)}")
            Legend(SpitifyColors.TextSecondary, "Cache ${formatBytes(cacheBytes)}")
            if (totalBytes > 0) Legend(SpitifyColors.Highlight, "Free ${formatBytes(freeBytes)}")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen() {
    val themes = LocalThemes.current
    val current = SpitifyColors.palette
    var customAccent by remember { mutableStateOf(themes.customAccent) }
    var trueBlack by remember { mutableStateOf(themes.customTrueBlack) }

    SettingsPage("Theme") {
        item {
            Text(
                "Spitify is the default. The other presets come from the ui-ux-pro-max colour library. Custom lets you pick your own.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Palettes.presets.forEach { palette ->
            item(key = palette.id) {
                ThemeOption(palette, selected = current.id == palette.id) { themes.select(palette) }
            }
        }
        group("Custom")
        item {
            ThemeOption(
                Palettes.custom(customAccent, trueBlack),
                selected = current.id == Palettes.CUSTOM_ID,
            ) { themes.setCustom(customAccent, trueBlack) }
        }
        item {
            Text("Accent colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Palettes.customAccents.forEach { swatch ->
                    val picked = swatch == customAccent
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .then(if (picked) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable {
                                customAccent = swatch
                                themes.setCustom(swatch, trueBlack)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (picked) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = SpitifyPalette.readableOn(swatch))
                    }
                }
            }
        }
        item {
            HueSlider(customAccent) {
                customAccent = it
                themes.setCustom(it, trueBlack)
            }
        }
        item {
            ToggleRow("True black background", "Pure black, easiest on OLED screens.", trueBlack) {
                trueBlack = it
                themes.setCustom(customAccent, it)
            }
        }
    }
}

/** Fine-tune the custom accent around the colour wheel. */
@Composable
private fun HueSlider(color: Color, onPick: (Color) -> Unit) {
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), it) }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Fine-tune hue", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = hsv[0],
            onValueChange = { h ->
                val sat = hsv[1].coerceAtLeast(0.55f)
                val v = hsv[2].coerceAtLeast(0.75f)
                onPick(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v))))
            },
            valueRange = 0f..359f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = SpitifyColors.Highlight),
        )
    }
}

@Composable
private fun ThemeOption(palette: SpitifyPalette, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.background)
                .border(1.dp, palette.divider, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(palette.accent))
                Box(Modifier.size(width = 22.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(palette.highlight))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(palette.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = palette.accent))
    }
}

@Composable
fun EqualizerScreen() {
    val app = LocalApp.current
    val store = LocalSettings.current
    val context = LocalContext.current
    val settings by store.state.collectAsStateWithLifecycle()
    val effects = app.audioEffects
    val info = effects?.info

    SettingsPage("Equalizer") {
        item {
            ToggleRow("Equalizer", "Shape the sound to your taste.", settings.equalizerEnabled) { v ->
                store.update {
                    it.copy(
                        equalizerEnabled = v,
                        equalizerBands = if (it.equalizerBands.isEmpty()) effects?.bandLevels().orEmpty() else it.equalizerBands,
                    )
                }
            }
        }
        if (info == null) {
            item {
                Text(
                    "Start playing something first. The equaliser attaches to the player, and some phones don't support one at all.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            item {
                Text("Presets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        com.srakim.spitify.ui.components.Pill("Custom", settings.equalizerPreset == -1, onClick = {
                            store.update { it.copy(equalizerPreset = -1, equalizerEnabled = true) }
                        })
                    }
                    items(info.presets.size) { i ->
                        com.srakim.spitify.ui.components.Pill(info.presets[i], settings.equalizerPreset == i, onClick = {
                            store.update { it.copy(equalizerPreset = i, equalizerEnabled = true) }
                        })
                    }
                }
            }
            item {
                val levels = if (settings.equalizerPreset >= 0 || settings.equalizerBands.size != info.bandFrequenciesHz.size) {
                    effects?.bandLevels().orEmpty()
                } else {
                    settings.equalizerBands
                }
                Column(Modifier.padding(16.dp)) {
                    info.bandFrequenciesHz.forEachIndexed { band, hz ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                            Slider(
                                value = levels.getOrElse(band) { 0 }.toFloat(),
                                onValueChange = { v ->
                                    val next = levels.toMutableList().apply {
                                        while (size < info.bandFrequenciesHz.size) add(0)
                                        set(band, v.toInt())
                                    }
                                    store.update { it.copy(equalizerPreset = -1, equalizerBands = next, equalizerEnabled = true) }
                                },
                                valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                                colors = spitifySliderColors(),
                                modifier = Modifier.weight(1f),
                            )
                            Text("${levels.getOrElse(band) { 0 } / 100} dB", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        item {
            SettingRow("Device equaliser", "Open your phone's own audio settings, if it has them.", onClick = {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                runCatching { context.startActivity(intent) }
            })
        }
    }
}
