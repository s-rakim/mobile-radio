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
import com.srakim.spitify.data.AppSettings
import com.srakim.spitify.data.VolumeLevel
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

@Composable
fun SettingsScreen() {
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

    var editingName by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf(false) }
    val catalog = LocalCatalog.current
    val scope = rememberCoroutineScope()
    var serverStatus by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(settings.subsonicUrl, settings.subsonicUser, settings.subsonicPassword, settings.subsonicEnabled) {
        serverStatus = if (!catalog.server.enabled) null else {
            serverStatus = "Checking…"
            catalog.server.ping().fold({ "Connected" }, { "Not working: ${it.message}" })
        }
    }
    val localTracks by catalog.local.tracks.collectAsStateWithLifecycle()
    var localGranted by remember { mutableStateOf(catalog.local.hasPermission()) }
    val askLocal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        localGranted = ok
        if (ok) scope.launch { catalog.local.refresh() } else actions.toast("Spitify can't see your music without that permission")
    }
    var volumePicker by remember { mutableStateOf(false) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var storageTick by remember { mutableIntStateOf(0) }
    val downloadBytes = remember(dl.downloaded.size, storageTick) { downloads.bytesUsed() }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(storageTick) {
        cacheBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    fun set(block: (AppSettings) -> AppSettings) = store.update(block)

    SettingsPage("Settings") {
        item {
            Row(
                Modifier.fillMaxWidth().clickable { editingName = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(SpitifyColors.Accent), contentAlignment = Alignment.Center) {
                    Text(
                        settings.profileName.trim().firstOrNull()?.uppercase() ?: "S",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpitifyColors.OnAccent,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.profileName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleMedium)
                    Text("Edit name", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpitifyColors.TextSecondary)
            }
        }

        group("Content and display")
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

        group("Playback")
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Crossfade", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Fades the end of each song into the next one.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
            SettingRow("Gapless playback", "Songs flow into each other with no silence. Always on.")
        }
        item {
            ToggleRow(
                "Autoplay",
                "Keep listening to similar songs when your music ends.",
                settings.autoplay,
            ) { v -> set { it.copy(autoplay = v) } }
        }
        item {
            SettingRow("Sleep timer", playback.sleepTimerEndsAt?.let { "On" } ?: "Set it from the player's menu")
        }

        group("Music sources")
        item { SettingRow("Audius", "Always on. The main catalogue.") }
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

        group("Audio quality")
        item {
            SettingRow("Volume level", "${settings.volumeLevel.label}. ${settings.volumeLevel.description}", onClick = { volumePicker = true })
        }
        item {
            SettingRow(
                "Equalizer",
                if (settings.equalizerEnabled) "On" else "Off",
                onClick = actions::openEqualizer,
            )
        }
        item {
            SettingRow("Streaming quality", "High (up to 320 kbps). Set by the source, so there's nothing to pick.")
        }

        group("Data saving and offline")
        item {
            ToggleRow("Data saver", "Loads smaller artwork to use less data.", settings.dataSaver) { v -> set { it.copy(dataSaver = v) } }
        }
        item {
            ToggleRow(
                "Download using cellular",
                "Turn on to download over mobile data. Otherwise downloads wait for Wi-Fi.",
                settings.downloadOnCellular,
            ) { v -> set { it.copy(downloadOnCellular = v) } }
        }

        group("Privacy and social")
        item {
            ToggleRow(
                "Private session",
                "Pause your listening history. Nothing you play gets added to Recently played.",
                settings.privateSession,
            ) { v -> set { it.copy(privateSession = v) } }
        }
        item {
            SettingRow("Clear listening history", "${lib.recentTracks.size} songs", onClick = {
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

        group("Storage")
        item {
            StorageBar(downloadBytes, cacheBytes)
        }
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

        group("About")
        item { SettingRow("Version", BuildConfig.VERSION_NAME) }
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

    if (editingName) {
        var name by remember { mutableStateOf(settings.profileName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Your name") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("Shown in your greeting") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpitifyColors.Accent, cursorColor = SpitifyColors.Accent),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    set { it.copy(profileName = name.trim()) }
                    editingName = false
                }) { Text("Save", color = SpitifyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel", color = SpitifyColors.TextSecondary) } },
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

    if (volumePicker) {
        AlertDialog(
            onDismissRequest = { volumePicker = false },
            containerColor = SpitifyColors.Elevated,
            title = { Text("Volume level") },
            text = {
                Column {
                    VolumeLevel.entries.forEach { level ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                set { it.copy(volumeLevel = level) }
                                volumePicker = false
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.volumeLevel == level,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = SpitifyColors.Accent),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(level.label, style = MaterialTheme.typography.bodyLarge)
                                Text(level.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { volumePicker = false }) { Text("Done", color = SpitifyColors.Accent) } },
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

@Composable
private fun StorageBar(downloadBytes: Long, cacheBytes: Long) {
    val total = (downloadBytes + cacheBytes).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(SpitifyColors.Highlight)) {
            if (downloadBytes > 0) Box(Modifier.weight(downloadBytes.toFloat() / total).height(8.dp).background(SpitifyColors.Accent))
            if (cacheBytes > 0) Box(Modifier.weight(cacheBytes.toFloat() / total).height(8.dp).background(SpitifyColors.TextSecondary))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(SpitifyColors.Accent, "Downloads ${formatBytes(downloadBytes)}")
            Legend(SpitifyColors.TextSecondary, "Cache ${formatBytes(cacheBytes)}")
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
