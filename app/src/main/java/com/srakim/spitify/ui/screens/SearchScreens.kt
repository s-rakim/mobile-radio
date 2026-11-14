package com.srakim.spitify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.Artist
import com.srakim.spitify.data.Genres
import com.srakim.spitify.ui.components.springClick
import com.srakim.spitify.ui.components.appear
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Playlist
import com.srakim.spitify.data.SearchResults
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalContentWidth
import com.srakim.spitify.ui.gridColumns
import com.srakim.spitify.ui.LocalCatalog
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.EmptyState
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.MediaCard
import com.srakim.spitify.ui.components.Pill
import com.srakim.spitify.ui.components.ProfileBadge
import com.srakim.spitify.ui.components.SectionHeader
import com.srakim.spitify.ui.components.SkeletonTrackList
import com.srakim.spitify.ui.components.TrackRow
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.delay

/** The Search tab: a search box that opens the real search, and genre tiles to browse. */
@Composable
fun SearchScreen() {
    val actions = LocalActions.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns(LocalContentWidth.current, phone = 2, minCell = 200.dp)),
        modifier = Modifier.fillMaxSize().background(SpitifyColors.Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomInset.current + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileBadge(onClick = actions::openSettings)
                Spacer(Modifier.width(12.dp))
                Text("Search", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .springClick(pressed = 0.98f, onClick = actions::openSearchInput)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF121212), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("What do you want to listen to?", style = MaterialTheme.typography.titleSmall, color = Color(0xFF404040))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Browse all", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
        }
        items(Genres.all, key = { it.name }) { genre ->
            Box(Modifier.appear(Genres.all.indexOf(genre))) {
                GenreTile(genre.name, Color(genre.color)) { actions.openMix(genre.name) }
            }
        }
    }
}

@Composable
private fun GenreTile(name: String, color: Color, onClick: () -> Unit) {
    val api = LocalApi.current
    val cover = rememberLoad("genre-cover", name) {
        api.trendingTracks(genre = name, limit = 1).firstOrNull()?.artwork
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.75f)
            .springClick(pressed = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.08f), color, lerp(color, Color.Black, 0.35f)))),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(12.dp).fillMaxWidth(0.7f),
        )
        Artwork(
            url = (cover.state as? Load.Ready)?.data,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 6.dp)
                .size(64.dp)
                .rotate(25f),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

/** Takes one from each list in turn: a, b, c, a, b, c… so every source gets a turn near the top. */
private fun <T> interleave(vararg lists: List<T>): List<T> {
    val out = ArrayList<T>(lists.sumOf { it.size })
    var i = 0
    while (out.size < lists.sumOf { it.size }) {
        lists.forEach { list -> if (i < list.size) out += list[i] }
        i++
    }
    return out
}

private enum class ResultFilter(val label: String) { TOP("Top"), SONGS("Songs"), RADIO("Radio"), ARTISTS("Artists"), PLAYLISTS("Albums & playlists") }

/** The focused search page with live results, filters and recent searches. */
@Composable
fun SearchInputScreen(initialQuery: String = "") {
    val catalog = LocalCatalog.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var debounced by rememberSaveable { mutableStateOf(initialQuery.trim()) }
    var filter by rememberSaveable { mutableStateOf(ResultFilter.TOP) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    LaunchedEffect(query) {
        delay(300)
        debounced = query.trim()
    }

    val results = rememberLoad("search", debounced) { catalog.search(debounced) }
    // The Spitify server's sources load on their own so a slow YouTube lookup never holds the rest up.
    val youtube = rememberLoad("search-youtube", debounced) { catalog.searchYouTube(debounced) }
    val openCatalogs = rememberLoad("search-open", debounced) { catalog.searchOpenCatalogs(debounced) }
    val soundcloud = rememberLoad("search-soundcloud", debounced) { catalog.searchSoundCloud(debounced) }
    val soundcloudTracks = (soundcloud.state as? Load.Ready)?.data.orEmpty()
    val radio = rememberLoad("search-radio", debounced) { catalog.searchRadio(debounced) }
    val stations = (radio.state as? Load.Ready)?.data.orEmpty()
    val radioBusy = radio.state is Load.Loading
    val youtubeTracks = (youtube.state as? Load.Ready)?.data.orEmpty()
    val openTracks = (openCatalogs.state as? Load.Ready)?.data.orEmpty()
    val serverBusy = catalog.web.enabled &&
        (youtube.state is Load.Loading || openCatalogs.state is Load.Loading || soundcloud.state is Load.Loading)
    val youtubeBusy = catalog.web.enabled && youtube.state is Load.Loading
    val soundcloudBusy = catalog.web.enabled && soundcloud.state is Load.Loading

    fun saveQuery() = library.addRecentSearch(query)

    Column(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .background(SpitifyColors.Highlight)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { actions.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("What do you want to listen to?", style = MaterialTheme.typography.bodyLarge, color = SpitifyColors.TextSecondary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = SpitifyColors.TextPrimary),
                    cursorBrush = SolidColor(SpitifyColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        saveQuery()
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        }

        if (debounced.isBlank()) {
            RecentSearches(
                items = lib.recentSearches,
                onPick = { query = it },
                onRemove = library::removeRecentSearch,
                onClear = library::clearRecentSearches,
            )
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ResultFilter.entries.toList()) { f -> Pill(f.label, filter == f, onClick = { filter = f }) }
        }

        when (val state = results.state) {
            is Load.Loading -> SkeletonTrackList()
            is Load.Failed -> ErrorState(state.message, results.retry)
            is Load.Ready -> {
                // Top songs from each big source first, so one source can't push the others off the top.
                val data = state.data.let {
                    it.copy(tracks = interleave(youtubeTracks, soundcloudTracks, it.tracks) + openTracks)
                }
                val radioContext = PlayContext("radio-search:$debounced", "\"$debounced\" stations", "RADIO")
                if (filter == ResultFilter.RADIO) {
                    when {
                        stations.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
                            itemsIndexed(stations, key = { _, t -> "r" + t.id }) { i, t ->
                                TrackRow(
                                    track = t,
                                    isCurrent = playback.current?.id == t.id,
                                    isPlaying = playback.isPlaying,
                                    liked = lib.liked.any { it.id == t.id },
                                    onClick = { saveQuery(); player.play(stations, i, radioContext) },
                                    onMore = { actions.showTrackMenu(t, null) },
                                    modifier = Modifier.appear(i),
                                )
                            }
                        }
                        radioBusy -> SkeletonTrackList()
                        else -> EmptyState("No stations for \"$debounced\"", "Try a station name, a city, or a genre like gospel, reggae or news.")
                    }
                    return@Column
                }
                if (data.isEmpty && stations.isEmpty() && (serverBusy || radioBusy)) {
                    SkeletonTrackList()
                    return@Column
                }
                if (data.isEmpty && stations.isEmpty()) {
                    EmptyState("Couldn't find \"$debounced\"", "Try searching again using a different spelling or keyword.")
                    return@Column
                }
                val context = PlayContext("search:$debounced", "\"$debounced\" in Search", "SEARCH")
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
                    if ((youtubeBusy || soundcloudBusy) && filter != ResultFilter.ARTISTS && filter != ResultFilter.PLAYLISTS) {
                        item {
                            Text(
                                when {
                                    youtubeBusy && soundcloudBusy -> "Still checking YouTube and SoundCloud…"
                                    youtubeBusy -> "Still checking YouTube…"
                                    else -> "Still checking SoundCloud…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SpitifyColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    when (filter) {
                        ResultFilter.TOP -> {
                            val topArtist = data.artists.firstOrNull { it.name.equals(debounced, ignoreCase = true) }
                            if (topArtist != null) {
                                item { ArtistRow(topArtist) { saveQuery(); actions.openArtist(topArtist.id) } }
                            }
                            itemsIndexed(data.tracks.take(6), key = { _, t -> "t" + t.id }) { i, t ->
                                TrackRow(
                                    track = t,
                                    isCurrent = playback.current?.id == t.id,
                                    isPlaying = playback.isPlaying,
                                    liked = lib.liked.any { it.id == t.id },
                                    onClick = { saveQuery(); player.play(data.tracks, i, context) },
                                    onMore = { actions.showTrackMenu(t, null) },
                                    modifier = Modifier.appear(i),
                                )
                            }
                            if (stations.isNotEmpty()) {
                                item {
                                    SectionHeader("Radio stations")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        itemsIndexed(stations.take(10), key = { _, t -> "rs" + t.id }) { i, t ->
                                            MediaCard(
                                                t.title,
                                                "Live • " + (t.genre?.replaceFirstChar { it.titlecase() } ?: "Radio"),
                                                onClick = { saveQuery(); player.play(stations, i, radioContext) },
                                                image = t.artwork,
                                                width = 120.dp,
                                            )
                                        }
                                    }
                                }
                            }
                            if (data.artists.isNotEmpty()) {
                                item {
                                    SectionHeader("Artists")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(data.artists, key = { it.id }) { a ->
                                            MediaCard(a.name, "Artist", onClick = { saveQuery(); actions.openArtist(a.id) }, image = a.picture, round = true, width = 120.dp)
                                        }
                                    }
                                }
                            }
                            if (data.playlists.isNotEmpty()) {
                                item {
                                    SectionHeader("Playlists")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(data.playlists, key = { it.id }) { p ->
                                            MediaCard(p.name, "${p.label} • ${p.owner}", onClick = { saveQuery(); actions.openPlaylist(p.id) }, image = p.artwork, width = 140.dp)
                                        }
                                    }
                                }
                            }
                        }
                        ResultFilter.SONGS -> itemsIndexed(data.tracks, key = { _, t -> t.id }) { i, t ->
                            TrackRow(
                                track = t,
                                isCurrent = playback.current?.id == t.id,
                                isPlaying = playback.isPlaying,
                                liked = lib.liked.any { it.id == t.id },
                                onClick = { saveQuery(); player.play(data.tracks, i, context) },
                                onMore = { actions.showTrackMenu(t, null) },
                                modifier = Modifier.appear(i),
                            )
                        }
                        ResultFilter.ARTISTS -> items(data.artists, key = { it.id }) { a ->
                            ArtistRow(a) { saveQuery(); actions.openArtist(a.id) }
                        }
                        ResultFilter.PLAYLISTS -> items(data.playlists, key = { it.id }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { saveQuery(); actions.openPlaylist(p.id) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(p.artwork, Modifier.size(56.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${p.label} • ${p.owner}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artist.picture, Modifier.size(56.dp), CircleShape, fallback = Icons.Rounded.Person)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artist", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecentSearches(items: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    if (items.isEmpty()) {
        EmptyState("Play what you love", "Search for artists, songs and playlists.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
        item {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
        items(items, key = { it }) { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onPick(q) }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = SpitifyColors.TextSecondary)
                Spacer(Modifier.width(14.dp))
                Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(q) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove $q", tint = SpitifyColors.TextSecondary)
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .clickable(onClick = onClear)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Clear recent searches", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
