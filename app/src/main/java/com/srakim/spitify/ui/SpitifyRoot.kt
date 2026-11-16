package com.srakim.spitify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srakim.spitify.SpitifyApp
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.screens.ArtistScreen
import com.srakim.spitify.ui.screens.AccountScreen
import com.srakim.spitify.ui.screens.DownloadsScreen
import com.srakim.spitify.ui.sheets.UpdateDialog
import com.srakim.spitify.ui.screens.EqualizerScreen
import com.srakim.spitify.ui.screens.HistoryScreen
import com.srakim.spitify.ui.screens.HomeScreen
import com.srakim.spitify.ui.screens.LibraryScreen
import com.srakim.spitify.ui.screens.LikedSongsScreen
import com.srakim.spitify.ui.screens.LocalFilesScreen
import com.srakim.spitify.ui.screens.MixScreen
import com.srakim.spitify.ui.screens.MyPlaylistScreen
import com.srakim.spitify.ui.screens.NowPlayingScreen
import com.srakim.spitify.ui.screens.PlaylistScreen
import com.srakim.spitify.ui.screens.SearchInputScreen
import com.srakim.spitify.ui.screens.SearchScreen
import com.srakim.spitify.ui.screens.ServerScreen
import com.srakim.spitify.ui.screens.SettingsScreen
import com.srakim.spitify.ui.screens.ThemeScreen
import com.srakim.spitify.ui.sheets.AddToPlaylistSheet
import com.srakim.spitify.ui.sheets.CreatePlaylistDialog
import com.srakim.spitify.ui.sheets.CreateSheet
import com.srakim.spitify.ui.sheets.CreateOption
import com.srakim.spitify.ui.sheets.AiPlaylistSheet
import com.srakim.spitify.ui.sheets.DjSheet
import com.srakim.spitify.ui.sheets.MoveToFolderSheet
import com.srakim.spitify.ui.sheets.EditProfileDialog
import com.srakim.spitify.ui.sheets.ProfileDrawer
import com.srakim.spitify.ui.sheets.ConnectSheet
import com.srakim.spitify.ui.sheets.SendToSheet
import com.srakim.spitify.ui.sheets.BlendPickerSheet
import com.srakim.spitify.ui.sheets.joinFriendJam
import com.srakim.spitify.ui.screens.BlendScreen
import com.srakim.spitify.ui.screens.ChatScreen
import com.srakim.spitify.ui.screens.CollabScreen
import com.srakim.spitify.ui.screens.FolderScreen
import com.srakim.spitify.ui.screens.FriendsScreen
import com.srakim.spitify.ui.screens.ImportScreen
import com.srakim.spitify.ui.screens.MessagesScreen
import com.srakim.spitify.ui.screens.ProfileScreen
import com.srakim.spitify.ui.screens.StatsScreen
import com.srakim.spitify.ui.screens.UpdatesScreen
import com.srakim.spitify.data.CollabPreview
import com.srakim.spitify.data.Links
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import com.srakim.spitify.ui.sheets.MiniPlayer
import com.srakim.spitify.ui.sheets.TrackMenuSheet
import com.srakim.spitify.ui.sheets.QueueSheet
import com.srakim.spitify.ui.sheets.CreditsSheet
import com.srakim.spitify.ui.sheets.JamSheet
import com.srakim.spitify.ui.sheets.SleepTimerSheet
import com.srakim.spitify.ui.sheets.SongDnaSheet
import com.srakim.spitify.data.Jam
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.ui.theme.SpitifyColors
import com.srakim.spitify.ui.components.Motion
import com.srakim.spitify.ui.components.rememberPop
import com.srakim.spitify.ui.components.springClick
import com.srakim.spitify.ui.components.NavIcons

private data class TrackMenuRequest(val track: Track, val playlistId: String?)

@Composable
fun SpitifyRoot(app: SpitifyApp) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var trackMenu by remember { mutableStateOf<TrackMenuRequest?>(null) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    var createWithTrack by remember { mutableStateOf<Track?>(null) }
    var sheet by remember { mutableStateOf<AppSheet?>(null) }
    // Bumped on every open, so asking for a sheet always shows it fresh, even if the last copy
    // of the same sheet never reported that it closed.
    var sheetOpens by remember { mutableIntStateOf(0) }
    var pendingFolderMove by remember { mutableStateOf<String?>(null) }
    var collabInvite by remember { mutableStateOf<CollabPreview?>(null) }
    var hiddenJams by remember { mutableStateOf(setOf<String>()) }
    var queueOpen by remember { mutableStateOf(false) }
    var creditsFor by remember { mutableStateOf<Track?>(null) }
    var jamFor by remember { mutableStateOf<Track?>(null) }
    var sleepTimerOpen by remember { mutableStateOf(false) }
    var dnaFor by remember { mutableStateOf<Track?>(null) }

    val actions = remember(nav) {
        AppActions(
            nav = nav,
            snackbar = snackbar,
            scope = scope,
            app = app,
            showTrackMenu = { track, playlistId -> trackMenu = TrackMenuRequest(track, playlistId) },
            showAddToPlaylist = { addToPlaylist = it },
            showCreatePlaylist = { track ->
                createWithTrack = track
                createPlaylist = true
            },
            showQueue = { queueOpen = true },
            showCredits = { creditsFor = it },
            showJam = { jamFor = it },
            showSleepTimer = { sleepTimerOpen = true },
            showSongDna = { dnaFor = it },
            showSheet = {
                sheet = it
                sheetOpens++
            },
            openPlayer = { playerOpen = true },
            closePlayer = { playerOpen = false },
        )
    }

    val settings by app.settings.state.collectAsStateWithLifecycle()
    val playback by app.player.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        app.player.messages.collect { actions.toast(it) }
    }
    LaunchedEffect(Unit) {
        app.dj.messages.collect { actions.toast(it) }
    }
    val account by app.account.state.collectAsStateWithLifecycle()
    // Jams on the same Wi-Fi show up as a banner while signed in.
    androidx.compose.runtime.DisposableEffect(account.username) {
        if (account.signedIn) app.nearby.startLooking(account.username)
        onDispose { app.nearby.stopLooking() }
    }
    val nearbyJams by app.nearby.nearby.collectAsStateWithLifecycle()
    val hostingJam by app.nearby.hosting.collectAsStateWithLifecycle()

    // Collab invites, profile links and the Spotify sign-in coming back from the browser.
    // Collected once for the life of the app screen. Keying an effect on the link and then
    // clearing it restarted the effect and cancelled whatever it was doing, which is how the
    // Spotify sign-in got cut off with "The coroutine scope left the composition".
    LaunchedEffect(Unit) {
        Links.incoming.filterNotNull().collect { l ->
        Links.incoming.value = null
        when (l) {
            is Links.Link.CollabInvite -> {
                if (!app.account.state.value.signedIn) {
                    actions.toast("Log in to Spitify to join shared playlists")
                    actions.openAccount()
                } else {
                    runCatching { app.social.previewCollab(l.id) }
                        .onSuccess { collabInvite = it }
                        .onFailure { actions.toast(it.message ?: "Couldn't open that invite") }
                }
            }
            is Links.Link.User -> actions.openProfile(l.name)
            is Links.Link.Playlist -> actions.openPlaylist(l.id)
            is Links.Link.Artist -> actions.openArtist(l.id)
            Links.Link.Player -> if (app.player.state.value.current != null) playerOpen = true
            is Links.Link.SpotifyAuth -> app.spotifyImport.completeSignIn(l.code, l.state, l.error)
        }
        }
    }
    // How the Spotify sign-in went, once it's done.
    LaunchedEffect(Unit) {
        app.spotifyImport.signIn.collect { s ->
            when (s) {
                com.srakim.spitify.data.SpotifyImport.SignIn.Working -> actions.toast("Signing in to Spotify…")
                com.srakim.spitify.data.SpotifyImport.SignIn.Done -> actions.toast("Signed in to Spotify. Your music is loading.")
                is com.srakim.spitify.data.SpotifyImport.SignIn.Failed -> actions.toast(s.message)
                com.srakim.spitify.data.SpotifyImport.SignIn.Idle -> Unit
            }
        }
    }
    // Someone opened a Jam invite: load the host's songs and jump to where they are now.
    LaunchedEffect(Unit) {
        Jam.incoming.filterNotNull().collect { inv ->
            Jam.incoming.value = null
            actions.toast("Joining the Jam…")
            val tracks = runCatching { app.api.tracksByIds(inv.ids) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) {
                actions.toast("Couldn't load that Jam. Check your connection and try again.")
                return@collect
            }
            val (index, offset) = Jam.catchUp(inv, tracks)
            app.player.play(tracks, index, PlayContext("jam", "Jam", "JAM"), shuffle = false)
            if (offset > 0) app.player.seekTo(offset)
            playerOpen = true
            actions.toast("You're in the Jam")
        }
    }
    // Tablets and wide windows swap the bottom bar for a rail down the left side.
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val wide = screenWidth >= WIDE_SCREEN_DP
    val contentWidth = if (wide) (screenWidth - RAIL_WIDTH).dp else screenWidth.dp
    val bottomInset by animateDpAsState(
        when {
            wide -> if (playback.current != null) 88.dp else 16.dp
            playback.current != null -> 150.dp
            else -> 86.dp
        },
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "bottomInset",
    )
    LaunchedEffect(settings.showLocalFiles) {
        if (settings.showLocalFiles) app.localMusic.refresh()
    }

    CompositionLocalProvider(
        LocalApp provides app,
        LocalApi provides app.api,
        LocalLibrary provides app.library,
        LocalPlayer provides app.player,
        LocalSettings provides app.settings,
        LocalDownloads provides app.downloads,
        LocalThemes provides app.themes,
        LocalLyrics provides app.lyrics,
        LocalCatalog provides app.catalog,
        LocalActions provides actions,
        LocalDataSaver provides com.srakim.spitify.ui.components.rememberDataSaver(settings),
        LocalBottomInset provides bottomInset,
        LocalContentWidth provides contentWidth,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(SpitifyColors.Background),
        ) {
            if (wide) NavRail(nav, onCreate = { sheet = AppSheet.Create })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AppNavHost(nav)

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    SnackbarHost(snackbar) { data ->
                        Snackbar(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            containerColor = SpitifyColors.Toast,
                            contentColor = SpitifyColors.OnToast,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(data.visuals.message, style = MaterialTheme.typography.titleSmall, color = SpitifyColors.OnToast)
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to SpitifyColors.Base.copy(alpha = 0.82f), 1f to SpitifyColors.Base)),
                    ) {
                        Column {
                            val jamHost = nearbyJams.firstOrNull { it !in hiddenJams }
                            AnimatedVisibility(
                                visible = jamHost != null && !hostingJam && playback.context?.id != "jam:$jamHost",
                                enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(Motion.settle()),
                                exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.MEDIUM)),
                            ) {
                                val host = jamHost ?: return@AnimatedVisibility
                                NearbyJamBanner(
                                    host = host,
                                    onJoin = {
                                        scope.launch {
                                            if (joinFriendJam(app, host)) actions.toast("You're in $host's Jam") else actions.toast("That Jam has ended")
                                        }
                                        hiddenJams = hiddenJams + host
                                    },
                                    onDismiss = { hiddenJams = hiddenJams + host },
                                )
                            }
                            AnimatedVisibility(
                                visible = playback.current != null,
                                enter = slideInVertically(Motion.settle()) { it } + fadeIn(tween(Motion.MEDIUM)) + expandVertically(Motion.settle()),
                                exit = slideOutVertically(tween(Motion.MEDIUM)) { it } + fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.MEDIUM)),
                            ) {
                                MiniPlayer(onOpen = { playerOpen = true })
                            }
                            if (wide) Spacer(Modifier.navigationBarsPadding().height(8.dp))
                            else BottomBar(nav, onCreate = { sheet = AppSheet.Create })
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            UpdateDialog()

            AnimatedVisibility(
                visible = playerOpen && playback.current != null,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } +
                    fadeIn(tween(Motion.FAST)) +
                    scaleIn(tween(Motion.SLOW, easing = FastOutSlowInEasing), initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 1f)),
                exit = slideOutVertically(tween(Motion.MEDIUM, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(Motion.MEDIUM, delayMillis = 60)),
            ) {
                NowPlayingScreen(onClose = { playerOpen = false })
            }
        }

        BackHandler(enabled = playerOpen) { playerOpen = false }

        trackMenu?.let { req ->
            TrackMenuSheet(
                track = req.track,
                playlistId = req.playlistId,
                onDismiss = { trackMenu = null },
            )
        }
        if (queueOpen) QueueSheet(onDismiss = { queueOpen = false })
        creditsFor?.let { track ->
            CreditsSheet(track = track, onDismiss = { creditsFor = null })
        }
        jamFor?.let { track ->
            JamSheet(track = track, onDismiss = { jamFor = null })
        }
        dnaFor?.let { track ->
            SongDnaSheet(track = track, onDismiss = { dnaFor = null })
        }
        if (sleepTimerOpen) SleepTimerSheet(onDismiss = { sleepTimerOpen = false })
        addToPlaylist?.let { track ->
            AddToPlaylistSheet(track = track, onDismiss = { addToPlaylist = null })
        }
        val closeSheet = { sheet = null }
        key(sheetOpens) { when (val s = sheet) {
            null -> Unit
            AppSheet.ProfileDrawer -> ProfileDrawer(onDismiss = closeSheet)
            AppSheet.EditProfile -> EditProfileDialog(onDismiss = closeSheet)
            AppSheet.Create -> CreateSheet(onDismiss = closeSheet, onPick = { option ->
                sheet = null
                when (option) {
                    CreateOption.PLAYLIST -> {
                        createWithTrack = null
                        createPlaylist = true
                    }
                    CreateOption.COLLAB -> sheet = AppSheet.NewCollab()
                    CreateOption.MIXED -> sheet = AppSheet.NewMixed
                    CreateOption.BLEND -> sheet = AppSheet.BlendPicker
                    CreateOption.AI -> sheet = AppSheet.AiPlaylist
                    CreateOption.DJ -> sheet = AppSheet.Dj
                    CreateOption.FOLDER -> sheet = AppSheet.NewFolder
                    CreateOption.JAM -> {
                        val track = app.player.state.value.current
                        if (track == null) {
                            actions.toast("Play something first, then start a Jam")
                        } else {
                            if (app.account.state.value.signedIn) app.nearby.startHosting(app.player, app.social, app.settings)
                            jamFor = track
                        }
                    }
                }
            })
            AppSheet.Connect -> ConnectSheet(onDismiss = closeSheet)
            is AppSheet.SendTo -> SendToSheet(track = s.track, playlist = s.playlist, onDismiss = closeSheet)
            AppSheet.AiPlaylist -> AiPlaylistSheet(onDismiss = closeSheet)
            AppSheet.Dj -> DjSheet(onDismiss = closeSheet)
            AppSheet.BlendPicker -> BlendPickerSheet(onDismiss = closeSheet)
            is AppSheet.NewCollab -> {
                if (!account.signedIn) {
                    LaunchedEffect(s) {
                        sheet = null
                        actions.toast("Log in to Spitify to make playlists with friends")
                        actions.openAccount()
                    }
                } else {
                    CreatePlaylistDialog(
                        onDismiss = closeSheet,
                        title = "Name your collaborative playlist",
                        initial = "Our playlist",
                        onCreate = { name ->
                            sheet = null
                            scope.launch {
                                runCatching { app.social.createCollab(name, listOfNotNull(s.withTrack)) }
                                    .onSuccess { actions.openCollab(it.id) }
                                    .onFailure { actions.toast(it.message ?: "Couldn't make that playlist") }
                            }
                        },
                    )
                }
            }
            AppSheet.NewMixed -> CreatePlaylistDialog(
                onDismiss = closeSheet,
                title = "Name your mixed playlist",
                initial = "My mix",
                onCreate = { name ->
                    sheet = null
                    val made = app.library.createPlaylist(name, mixed = true)
                    actions.openMyPlaylist(made.id)
                },
            )
            AppSheet.NewFolder -> CreatePlaylistDialog(
                onDismiss = { sheet = null; pendingFolderMove = null },
                title = "Name your folder",
                initial = "New folder",
                onCreate = { name ->
                    sheet = null
                    val folder = app.library.createFolder(name)
                    val moving = pendingFolderMove
                    pendingFolderMove = null
                    if (moving != null) {
                        app.library.moveToFolder(moving, folder.id)
                        actions.toast("Moved to ${folder.name}")
                    } else actions.openFolder(folder.id)
                },
            )
            is AppSheet.MoveToFolder -> MoveToFolderSheet(
                playlistId = s.playlistId,
                onDismiss = closeSheet,
                onNewFolder = {
                    pendingFolderMove = s.playlistId
                    sheet = AppSheet.NewFolder
                },
            )
        } }
        collabInvite?.let { inv ->
            AlertDialog(
                onDismissRequest = { collabInvite = null },
                containerColor = SpitifyColors.Elevated,
                title = { Text("Join ${inv.name}?") },
                text = {
                    Text(
                        "${inv.owner.displayName} invited you to add songs together. ${inv.tracks} songs, ${inv.members} ${if (inv.members == 1) "person" else "people"} so far.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        collabInvite = null
                        scope.launch {
                            runCatching { app.social.joinCollab(inv.id) }
                                .onSuccess { actions.toast("You joined ${it.name}"); actions.openCollab(it.id) }
                                .onFailure { actions.toast(it.message ?: "Couldn't join") }
                        }
                    }) { Text("Join", color = SpitifyColors.Accent) }
                },
                dismissButton = { TextButton(onClick = { collabInvite = null }) { Text("Not now", color = SpitifyColors.TextSecondary) } },
            )
        }
        if (createPlaylist) {
            CreatePlaylistDialog(
                onDismiss = { createPlaylist = false },
                onCreate = { name ->
                    createPlaylist = false
                    val created = app.library.createPlaylist(name)
                    createWithTrack?.let {
                        app.library.addToPlaylist(created.id, it)
                        actions.toast("Added to ${created.name}")
                    }
                    createWithTrack = null
                    actions.openMyPlaylist(created.id)
                },
            )
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(
        navController = nav,
        startDestination = Tab.HOME.route,
        // Switching tabs is a quick cross-fade. Opening a page slides it in from the side
        // over a fading parent, and back reverses it, so you always know which way you went.
        enterTransition = {
            if (isTabSwitch()) fadeIn(tween(Motion.MEDIUM))
            else slideIntoContainer(SlideDirection.Start, tween(Motion.SLOW, easing = EaseOutQuart)) { it / 4 } + fadeIn(tween(Motion.MEDIUM))
        },
        exitTransition = {
            if (isTabSwitch()) fadeOut(tween(Motion.FAST))
            else slideOutOfContainer(SlideDirection.Start, tween(Motion.SLOW, easing = EaseOutQuart)) { it / 10 } + fadeOut(tween(Motion.MEDIUM))
        },
        popEnterTransition = {
            if (isTabSwitch()) fadeIn(tween(Motion.MEDIUM))
            else slideIntoContainer(SlideDirection.End, tween(Motion.SLOW, easing = EaseOutQuart)) { it / 10 } + fadeIn(tween(Motion.MEDIUM))
        },
        popExitTransition = {
            if (isTabSwitch()) fadeOut(tween(Motion.FAST))
            else slideOutOfContainer(SlideDirection.End, tween(Motion.MEDIUM, easing = EaseOutQuart)) { it / 4 } + fadeOut(tween(Motion.MEDIUM))
        },
    ) {
        composable(Tab.HOME.route) { HomeScreen() }
        composable(Tab.SEARCH.route) { SearchScreen() }
        composable(Tab.LIBRARY.route) { LibraryScreen() }
        composable(Dest.SEARCH_INPUT) { SearchInputScreen() }
        composable("${Dest.SEARCH_INPUT}/{q}") { SearchInputScreen(it.arguments?.getString("q").orEmpty()) }

        Tab.entries.forEach { tab ->
            val p = tab.route
            composable("$p/${Dest.PLAYLIST}") { PlaylistScreen(it.arguments?.getString("id").orEmpty()) }
            composable("$p/${Dest.MY_PLAYLIST}") { MyPlaylistScreen(it.arguments?.getString("id").orEmpty()) }
            composable("$p/${Dest.LIKED}") { LikedSongsScreen() }
            composable("$p/${Dest.MIX}") { MixScreen(it.arguments?.getString("genre").orEmpty()) }
            composable("$p/${Dest.ARTIST}") { ArtistScreen(it.arguments?.getString("id").orEmpty()) }
            composable("$p/${Dest.SETTINGS}") { SettingsScreen() }
            composable("$p/${Dest.EQUALIZER}") { EqualizerScreen() }
            composable("$p/${Dest.THEMES}") { ThemeScreen() }
            composable("$p/${Dest.HISTORY}") { HistoryScreen() }
            composable("$p/${Dest.DOWNLOADS}") { DownloadsScreen() }
            composable("$p/${Dest.LOCAL_FILES}") { LocalFilesScreen() }
            composable("$p/${Dest.SERVER}") { ServerScreen() }
            composable("$p/${Dest.ACCOUNT}") { AccountScreen() }
            composable("$p/${Dest.SETTINGS_SECTION}") { SettingsScreen(it.arguments?.getString("id")) }
            composable("$p/${Dest.IMPORT}") { ImportScreen() }
            composable("$p/${Dest.STATS}") { StatsScreen() }
            composable("$p/${Dest.UPDATES}") { UpdatesScreen() }
            composable("$p/${Dest.MESSAGES}") { MessagesScreen() }
            composable("$p/${Dest.CHAT}") { ChatScreen(it.arguments?.getString("name").orEmpty()) }
            composable("$p/${Dest.FRIENDS}") { FriendsScreen() }
            composable("$p/${Dest.PROFILE}") { ProfileScreen(it.arguments?.getString("name").orEmpty()) }
            composable("$p/${Dest.COLLAB}") { CollabScreen(it.arguments?.getString("id").orEmpty()) }
            composable("$p/${Dest.BLEND}") { BlendScreen(it.arguments?.getString("name").orEmpty()) }
            composable("$p/${Dest.FOLDER}") { FolderScreen(it.arguments?.getString("id").orEmpty()) }
        }
    }
}

/** True when both ends of a navigation are top-level tabs (no "/" in the route). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route.orEmpty()
    val to = targetState.destination.route.orEmpty()
    return !from.contains('/') && !to.contains('/')
}

@Composable
private fun BottomBar(nav: NavHostController, onCreate: () -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination?.route?.substringBefore('/') ?: Tab.HOME.route

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = currentTab == tab.route
            NavItem(tab.label, tabIcon(tab, selected), selected, Modifier.weight(1f)) { openTab(nav, tab, selected) }
        }
        NavItem("Create", NavIcons.Create, false, Modifier.weight(1f), onCreate)
    }
}

private const val RAIL_WIDTH = 88

/** Same tabs as the bottom bar, stacked down the left edge for tablets and wide windows. */
@Composable
private fun NavRail(nav: NavHostController, onCreate: () -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination?.route?.substringBefore('/') ?: Tab.HOME.route

    Column(
        Modifier
            .width(RAIL_WIDTH.dp)
            .fillMaxHeight()
            .background(SpitifyColors.Base)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tab.entries.forEach { tab ->
            val selected = currentTab == tab.route
            NavItem(tab.label, tabIcon(tab, selected), selected, Modifier.fillMaxWidth()) { openTab(nav, tab, selected) }
        }
        NavItem("Create", NavIcons.Create, false, Modifier.fillMaxWidth(), onCreate)
    }
}

private fun tabIcon(tab: Tab, selected: Boolean): ImageVector = when (tab) {
    Tab.HOME -> if (selected) NavIcons.HomeSelected else NavIcons.Home
    Tab.SEARCH -> if (selected) NavIcons.SearchSelected else NavIcons.Search
    Tab.LIBRARY -> if (selected) NavIcons.LibrarySelected else NavIcons.Library
}

private fun openTab(nav: NavHostController, tab: Tab, selected: Boolean) {
    if (selected) {
        nav.popBackStack(tab.route, inclusive = false)
    } else {
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (selected) SpitifyColors.TextPrimary else SpitifyColors.TextSecondary,
        tween(Motion.MEDIUM),
        label = "navTint",
    )
    val pop by rememberPop(selected)
    Column(
        modifier = modifier
            .springClick(pressed = 0.86f, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "alex is hosting a Jam nearby" with Join, shown when another Spitify phone on this Wi-Fi is hosting. */
@Composable
private fun NearbyJamBanner(host: String, onJoin: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpitifyColors.AccentDeep)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$host is hosting a Jam nearby", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Listen along on this phone", style = MaterialTheme.typography.bodySmall, color = SpitifyColors.TextSecondary)
        }
        TextButton(onClick = onDismiss) { Text("Not now", color = SpitifyColors.TextSecondary) }
        TextButton(onClick = onJoin) { Text("Join", color = SpitifyColors.Accent) }
    }
}
