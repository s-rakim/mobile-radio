package com.srakim.spitify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srakim.spitify.SpitifyApp
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.screens.ArtistScreen
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
import com.srakim.spitify.ui.sheets.MiniPlayer
import com.srakim.spitify.ui.sheets.TrackMenuSheet
import com.srakim.spitify.ui.theme.SpitifyColors

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
    var createSheet by remember { mutableStateOf(false) }

    val actions = remember(nav) {
        AppActions(
            nav = nav,
            snackbar = snackbar,
            scope = scope,
            showTrackMenu = { track, playlistId -> trackMenu = TrackMenuRequest(track, playlistId) },
            showAddToPlaylist = { addToPlaylist = it },
            showCreatePlaylist = { track ->
                createWithTrack = track
                createPlaylist = true
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
        LocalDataSaver provides settings.dataSaver,
        LocalBottomInset provides if (playback.current != null) 150.dp else 86.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SpitifyColors.Background),
        ) {
            AppNavHost(nav)
            UpdateDialog()

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
                        .background(Brush.verticalGradient(listOf(Color.Transparent, SpitifyColors.Base.copy(alpha = 0.85f), SpitifyColors.Base))),
                ) {
                    Column {
                        if (playback.current != null) {
                            MiniPlayer(onOpen = { playerOpen = true })
                        }
                        BottomBar(nav, onCreate = { createSheet = true })
                    }
                }
            }

            AnimatedVisibility(
                visible = playerOpen && playback.current != null,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
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
        addToPlaylist?.let { track ->
            AddToPlaylistSheet(track = track, onDismiss = { addToPlaylist = null })
        }
        if (createSheet) {
            CreateSheet(
                onDismiss = { createSheet = false },
                onPlaylist = {
                    createSheet = false
                    createWithTrack = null
                    createPlaylist = true
                },
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
    NavHost(navController = nav, startDestination = Tab.HOME.route) {
        composable(Tab.HOME.route) { HomeScreen() }
        composable(Tab.SEARCH.route) { SearchScreen() }
        composable(Tab.LIBRARY.route) { LibraryScreen() }
        composable(Dest.SEARCH_INPUT) { SearchInputScreen() }

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
        }
    }
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
            val icon = when (tab) {
                Tab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
                Tab.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
                Tab.LIBRARY -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
            }
            NavItem(tab.label, icon, selected, Modifier.weight(1f)) {
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
        }
        NavItem("Create", Icons.Rounded.Add, false, Modifier.weight(1f), onCreate)
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) SpitifyColors.TextPrimary else SpitifyColors.TextSecondary
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
