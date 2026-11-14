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
import com.srakim.spitify.ui.sheets.MiniPlayer
import com.srakim.spitify.ui.sheets.TrackMenuSheet
import com.srakim.spitify.ui.sheets.QueueSheet
import com.srakim.spitify.ui.sheets.CreditsSheet
import com.srakim.spitify.ui.sheets.JamSheet
import com.srakim.spitify.data.Jam
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.ui.theme.SpitifyColors
import com.srakim.spitify.ui.components.Motion
import com.srakim.spitify.ui.components.rememberPop
import com.srakim.spitify.ui.components.springClick

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
    var queueOpen by remember { mutableStateOf(false) }
    var creditsFor by remember { mutableStateOf<Track?>(null) }
    var jamFor by remember { mutableStateOf<Track?>(null) }

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
            openPlayer = { playerOpen = true },
            closePlayer = { playerOpen = false },
        )
    }

    val settings by app.settings.state.collectAsStateWithLifecycle()
    val playback by app.player.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        app.player.messages.collect { actions.toast(it) }
    }
    // Someone opened a Jam invite: load the host's songs and jump to where they are now.
    val invite by Jam.incoming.collectAsStateWithLifecycle()
    LaunchedEffect(invite) {
        val inv = invite ?: return@LaunchedEffect
        Jam.incoming.value = null
        actions.toast("Joining the Jam…")
        val tracks = runCatching { app.api.tracksByIds(inv.ids) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) {
            actions.toast("Couldn't load that Jam. Check your connection and try again.")
            return@LaunchedEffect
        }
        val (index, offset) = Jam.catchUp(inv, tracks)
        app.player.play(tracks, index, PlayContext("jam", "Jam", "JAM"), shuffle = false)
        if (offset > 0) app.player.seekTo(offset)
        playerOpen = true
        actions.toast("You're in the Jam")
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
        LocalDataSaver provides settings.dataSaver,
        LocalBottomInset provides bottomInset,
        LocalContentWidth provides contentWidth,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(SpitifyColors.Background),
        ) {
            if (wide) NavRail(nav, onCreate = { createSheet = true })
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
                            .background(Brush.verticalGradient(listOf(Color.Transparent, SpitifyColors.Base.copy(alpha = 0.85f), SpitifyColors.Base))),
                    ) {
                        Column {
                            AnimatedVisibility(
                                visible = playback.current != null,
                                enter = slideInVertically(Motion.settle()) { it } + fadeIn(tween(Motion.MEDIUM)) + expandVertically(Motion.settle()),
                                exit = slideOutVertically(tween(Motion.MEDIUM)) { it } + fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.MEDIUM)),
                            ) {
                                MiniPlayer(onOpen = { playerOpen = true })
                            }
                            if (wide) Spacer(Modifier.navigationBarsPadding().height(8.dp))
                            else BottomBar(nav, onCreate = { createSheet = true })
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
        NavItem("Create", Icons.Rounded.Add, false, Modifier.weight(1f), onCreate)
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
        NavItem("Create", Icons.Rounded.Add, false, Modifier.fillMaxWidth(), onCreate)
    }
}

private fun tabIcon(tab: Tab, selected: Boolean): ImageVector = when (tab) {
    Tab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    Tab.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
    Tab.LIBRARY -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
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
                .size(26.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
        )
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
