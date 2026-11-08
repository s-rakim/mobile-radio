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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.Artist
import com.srakim.spitify.data.Genres
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.Playlist
import com.srakim.spitify.data.SearchResults
import com.srakim.spitify.data.Track
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.LocalBottomInset
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
        columns = GridCells.Fixed(2),
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable(onClick = actions::openSearchInput)
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
            GenreTile(genre.name, Color(genre.color)) { actions.openMix(genre.name) }
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
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick),
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

private enum class ResultFilter(val label: String) { TOP("Top"), SONGS("Songs"), ARTISTS("Artists"), PLAYLISTS("Albums & playlists") }

/** The focused search page with live results, filters and recent searches. */
@Composable
fun SearchInputScreen() {
    val catalog = LocalCatalog.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ResultFilter.TOP) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    LaunchedEffect(query) {
        delay(300)
        debounced = query.trim()
    }

    val results = rememberLoad("search", debounced) { catalog.search(debounced) }

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

        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultFilter.entries.forEach { f -> Pill(f.label, filter == f, onClick = { filter = f }) }
        }

        when (val state = results.state) {
            is Load.Loading -> SkeletonTrackList()
            is Load.Failed -> ErrorState(state.message, results.retry)
            is Load.Ready -> {
                val data = state.data
                if (data.isEmpty) {
                    EmptyState("Couldn't find \"$debounced\"", "Try searching again using a different spelling or keyword.")
                    return@Column
                }
                val context = PlayContext("search:$debounced", "\"$debounced\" in Search", "SEARCH")
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
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
                                )
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
