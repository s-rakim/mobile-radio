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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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
                ProfileBadge(onClick = { actions.showSheet(com.srakim.spitify.ui.AppSheet.ProfileDrawer) })
                Spacer(Modifier.width(12.dp))
                Text("Search", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .springClick(pressed = 0.98f, onClick = actions::openSearchInput)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF121212), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("What do you want to listen to?", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = Color(0xFF535353))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Discover something new", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Tall cards like Spotify's: a cover filling the card, a hashtag along the bottom.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 16.dp),
                modifier = Modifier.padding(end = 0.dp),
            ) {
                items(Genres.all.take(8), key = { "disc:" + it.name }) { g -> DiscoverCard(g.name, Color(g.color)) { actions.openMix(g.name) } }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Browse all", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
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
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            color = Color.White,
            modifier = Modifier.padding(12.dp).fillMaxWidth(0.68f),
        )
        Artwork(
            url = (cover.state as? Load.Ready)?.data,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 4.dp)
                .rotate(25f)
                .size(72.dp)
                .shadow(8.dp, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

@Composable
private fun DiscoverCard(name: String, color: Color, onClick: () -> Unit) {
    val api = LocalApi.current
    val cover = rememberLoad("discover-cover", name) {
        api.trendingTracks(genre = name, limit = 3).getOrNull(1)?.let { it.artworkLarge ?: it.artwork }
    }
    Box(
        Modifier
            .width(118.dp)
            .height(196.dp)
            .springClick(pressed = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
    ) {
        Artwork((cover.state as? Load.Ready)?.data, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        Text(
            "#" + name.lowercase().replace("/", " ").replace("-", "").split(" ").first(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
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

private enum class ResultFilter(val label: String) {
    TOP("Top"), SONGS("Songs"), LYRICS("Lyrics"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists"),
    GENRES("Genres & moods"), RADIO("Radio"),
}

/** Spotify's search filters (artist:, album:, year: and friends) only mean something to Spotify. */
private val FIELD = Regex("\\b(artist|album|track|year|genre|isrc|upc|tag):(\"[^\"]*\"|\\S+)", RegexOption.IGNORE_CASE)

/** The query for every source that isn't Spotify: `artist:"Rihanna" year:2016` → `Rihanna`. */
private fun plainQuery(q: String): String = FIELD.replace(q) { m ->
    when (m.groupValues[1].lowercase()) {
        "artist", "album", "track", "genre" -> m.groupValues[2].trim('"')
        else -> ""
    }
}.replace(Regex("\\s+"), " ").trim()

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

    val plain = plainQuery(debounced)
    val results = rememberLoad("search", plain) { catalog.search(plain) }
    // Spotify's catalogue, when you've signed in to Spotify. Those songs play in the Spotify app
    // (Premium) or as the closest match from Spitify's sources.
    val spotifyImport = com.srakim.spitify.ui.LocalApp.current.spotifyImport
    val spotifyLoad = rememberLoad("search-spotify", debounced) {
        if (spotifyImport.hasToken) runCatching { spotifyImport.searchAll(debounced) }.getOrDefault(SearchResults.Empty)
            .also { spotifyImport.meta.remember(it.artists) }
        else SearchResults.Empty
    }
    val spotifyResults = (spotifyLoad.state as? Load.Ready)?.data ?: SearchResults.Empty
    // The Songs tab asks Spotify for three pages (it hands out 10 at a time now).
    val moreSpotify = rememberLoad("search-spotify-songs", debounced, filter == ResultFilter.SONGS) {
        if (filter == ResultFilter.SONGS && spotifyImport.hasToken) runCatching { spotifyImport.searchTracks(debounced, 30) }.getOrDefault(emptyList()) else emptyList()
    }
    val spotifyTracks = (moreSpotify.state as? Load.Ready)?.data?.takeIf { it.isNotEmpty() } ?: spotifyResults.tracks

    // Finding a song by its words: your own songs' lyrics first (instant, offline), then the internet.
    val app = com.srakim.spitify.ui.LocalApp.current
    val indexSize by app.lyricIndex.size.collectAsStateWithLifecycle()
    val lyricWords = com.srakim.spitify.data.LyricText.words(plain).size
    val wantLyrics = filter == ResultFilter.LYRICS || lyricWords >= 4
    val localLyrics = remember(plain, indexSize, wantLyrics) { if (wantLyrics) app.lyricIndex.search(plain) else emptyList() }
    val onlineLyricsLoad = rememberLoad("search-lyrics", plain, wantLyrics) { if (wantLyrics) app.lyrics.searchText(plain) else emptyList() }
    val foundByLyrics = run {
        val seen = HashSet<String>()
        (localLyrics + (onlineLyricsLoad.state as? Load.Ready)?.data.orEmpty())
            .filter { seen.add(com.srakim.spitify.data.LyricText.norm(it.title) + "|" + com.srakim.spitify.data.LyricText.norm(it.artist).substringBefore(" feat")) }
    }
    // Signed in to Spotify: each match becomes the real song (original artist, cover, playable).
    val canonLoad = rememberLoad("search-lyrics-spotify", foundByLyrics) { spotifyImport.meta.canonical(foundByLyrics) }
    val lyricMatches = (canonLoad.state as? Load.Ready)?.data ?: foundByLyrics
    val lyricsBusy = wantLyrics && onlineLyricsLoad.state is Load.Loading
    val scope = rememberCoroutineScope()
    fun playLyricMatch(m: com.srakim.spitify.data.LyricMatch) {
        library.addRecentSearch(query)
        val own = m.track
        if (own != null) {
            player.play(listOf(own), 0, PlayContext("lyrics:$plain", "Lyrics search", "SEARCH"))
            return
        }
        actions.toast("Finding ${m.title}…")
        scope.launch {
            val first = m.artist.split(",", "&", " feat", " ft.").first().trim()
            val found = runCatching {
                if (spotifyImport.hasToken) {
                    spotifyImport.searchTracks("track:\"${m.title}\" artist:\"$first\"").firstOrNull()
                        ?: spotifyImport.searchTracks("${m.title} $first").firstOrNull()
                } else {
                    val want = com.srakim.spitify.data.LyricText.norm(m.title)
                    catalog.searchForMatch("${m.title} $first").let { list -> list.firstOrNull { com.srakim.spitify.data.LyricText.norm(it.title).contains(want) } ?: list.firstOrNull() }
                }
            }.getOrNull()
            if (found != null) player.play(listOf(found), 0, PlayContext("lyrics:$plain", "Lyrics search", "SEARCH"))
            else actions.toast("Couldn't find ${m.title} to play")
        }
    }
    // The Spitify server's sources load on their own so a slow YouTube lookup never holds the rest up.
    val youtube = rememberLoad("search-youtube", plain) { catalog.searchYouTube(plain) }
    val openCatalogs = rememberLoad("search-open", plain) { catalog.searchOpenCatalogs(plain) }
    val soundcloud = rememberLoad("search-soundcloud", plain) { catalog.searchSoundCloud(plain) }
    val soundcloudTracks = (soundcloud.state as? Load.Ready)?.data.orEmpty()
    // The open catalogues come straight from the phone, one call each, so whichever answers
    // first shows first and a slow one never holds the rest up.
    val archiveLoad = rememberLoad("search-archive", plain) { catalog.searchArchive(plain) }
    val ccLoad = rememberLoad("search-ccmixter", plain) { catalog.searchCcMixter(plain) }
    val jamendoLoad = rememberLoad("search-jamendo", plain) { catalog.searchJamendo(plain) }
    val scDirectLoad = rememberLoad("search-sc-direct", plain) { catalog.searchSoundCloudDirect(plain) }
    val extrasLoad = rememberLoad("search-open-extras", plain) { catalog.searchOpenExtras(plain) }
    val directTracks = interleave(
        (jamendoLoad.state as? Load.Ready)?.data.orEmpty(),
        (ccLoad.state as? Load.Ready)?.data.orEmpty(),
        (archiveLoad.state as? Load.Ready)?.data.orEmpty(),
    )
    val scDirect = (scDirectLoad.state as? Load.Ready)?.data.orEmpty()
    val extras = (extrasLoad.state as? Load.Ready)?.data
    val radio = rememberLoad("search-radio", plain) { catalog.searchRadio(plain) }
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
                suggestions = { SearchSuggestions(onPick = { query = it }) },
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
                    val seen = HashSet<String>()
                    val merged = (interleave(spotifyTracks, youtubeTracks, soundcloudTracks, scDirect, it.tracks) + directTracks + openTracks)
                        .filter { t -> seen.add(t.title.lowercase().trim() + "|" + t.artist.lowercase().trim()) }
                    it.copy(
                        tracks = merged,
                        // Signed in to Spotify: its artists, albums and playlists come first, like the real thing.
                        artists = spotifyResults.artists + it.artists + extras?.artists.orEmpty(),
                        playlists = spotifyResults.playlists + it.playlists + extras?.playlists.orEmpty(),
                    )
                }
                val radioContext = PlayContext("radio-search:$debounced", "\"$debounced\" stations", "RADIO")
                if (filter == ResultFilter.LYRICS) {
                    LyricResults(lyricMatches, lyricsBusy, lyricWords, indexSize, ::playLyricMatch)
                    return@Column
                }
                if (filter == ResultFilter.GENRES) {
                    GenreResults(plain, onPick = { actions.openMix(it) }, onSpotifyGenre = if (spotifyImport.hasToken) ({ query = "genre:\"$plain\"" }) else null)
                    return@Column
                }
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
                    if ((youtubeBusy || soundcloudBusy) && (filter == ResultFilter.TOP || filter == ResultFilter.SONGS)) {
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
                            if (lyricMatches.isNotEmpty()) {
                                item {
                                    SectionHeader("Songs with these lyrics")
                                }
                                items(lyricMatches.take(3), key = { "ly:" + it.title + it.artist }) { m ->
                                    LyricRow(m) { playLyricMatch(m) }
                                }
                                if (lyricMatches.size > 3) {
                                    item {
                                        Text(
                                            "See all lyric matches",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = SpitifyColors.TextSecondary,
                                            modifier = Modifier.clickable { filter = ResultFilter.LYRICS }.padding(horizontal = 16.dp, vertical = 12.dp),
                                        )
                                    }
                                }
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
                            val albums = data.playlists.filter { it.label != "Playlist" }
                            val lists = data.playlists.filter { it.label == "Playlist" }
                            if (albums.isNotEmpty()) {
                                item {
                                    SectionHeader("Albums")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(albums, key = { "al:" + it.id }) { p ->
                                            MediaCard(p.name, listOfNotNull(p.year, p.owner.takeIf { it.isNotBlank() }).joinToString(" • "), onClick = { saveQuery(); actions.openPlaylist(p.id) }, image = p.artwork, width = 140.dp)
                                        }
                                    }
                                }
                            }
                            if (lists.isNotEmpty()) {
                                item {
                                    SectionHeader("Playlists")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(lists, key = { it.id }) { p ->
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
                        // Stations get their own list above, they never reach this one.
                        ResultFilter.RADIO, ResultFilter.LYRICS, ResultFilter.GENRES -> Unit
                        ResultFilter.ALBUMS, ResultFilter.PLAYLISTS -> items(
                            data.playlists.filter { (it.label == "Playlist") == (filter == ResultFilter.PLAYLISTS) },
                            key = { it.id },
                        ) { p ->
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
private fun RecentSearches(
    items: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    suggestions: @Composable () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
        item { suggestions() }
        if (items.isEmpty()) return@LazyColumn
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

/**
 * What the search page shows before you type: trending names to tap, your top Spotify artists,
 * and the ways you can search (a line of lyrics, artist:, album:, year:, genre:).
 */
@Composable
private fun SearchSuggestions(onPick: (String) -> Unit) {
    val api = LocalApi.current
    val app = com.srakim.spitify.ui.LocalApp.current
    val spotify by app.spotifyLibrary.state.collectAsStateWithLifecycle()
    val trending = rememberLoad("search-trending") {
        runCatching { api.trendingTracks(limit = 30) }.getOrDefault(emptyList())
            .flatMap { listOf(it.artistSearchName, it.title) }
            .filter { it.isNotBlank() && it.length <= 28 }
            .distinctBy { it.lowercase() }
            .take(12)
    }
    val trendingNames = (trending.state as? Load.Ready)?.data.orEmpty()
    val yourArtists = spotify.topArtists.map { it.name }.take(10)
    val signedIn = app.spotifyImport.hasToken
    Column(Modifier.padding(top = 8.dp)) {
        if (yourArtists.isNotEmpty()) SuggestionRow("Your top artists", yourArtists, onPick)
        if (trendingNames.isNotEmpty()) SuggestionRow("Trending searches", trendingNames, onPick)
        val ways = buildList {
            add("shine bright like a diamond")
            if (signedIn) {
                add("artist:\"Rihanna\" year:2016")
                add("album:\"Anti\"")
                add("genre:afrobeats")
                add("year:1990-1999")
                add("tag:new")
            }
        }
        SuggestionRow("Ways to search", ways, onPick)
        Text(
            if (signedIn) "Type a line you remember to find the song by its lyrics. Spotify filters like artist:, album:, year: and genre: work too."
            else "Type a line you remember to find the song by its lyrics.",
            style = MaterialTheme.typography.bodySmall,
            color = SpitifyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SuggestionRow(title: String, items: List<String>, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { title + it }) { q -> Pill(q, false, onClick = { onPick(q) }) }
    }
}

@Composable
private fun LyricResults(
    matches: List<com.srakim.spitify.data.LyricMatch>,
    busy: Boolean,
    words: Int,
    indexed: Int,
    onPlay: (com.srakim.spitify.data.LyricMatch) -> Unit,
) {
    when {
        words < 2 -> EmptyState("Search by lyrics", "Type at least a few words from the song, like a line from the chorus.")
        matches.isEmpty() && busy -> SkeletonTrackList()
        matches.isEmpty() -> EmptyState("No song with those words", "Try a different line, or fewer words. Lyrics of $indexed of your songs are on this phone.")
        else -> LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
            itemsIndexed(matches, key = { _, m -> "lyr:" + m.title + "|" + m.artist }) { i, m ->
                LyricRow(m, Modifier.appear(i)) { onPlay(m) }
            }
            item {
                Text(
                    (if (busy) "Still looking online… " else "") + "Lyrics of $indexed of your songs are searchable on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpitifyColors.TextMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** A song found by its words: cover, title, artist, and the matching line underneath. */
@Composable
private fun LyricRow(m: com.srakim.spitify.data.LyricMatch, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(m.track?.artwork ?: m.artwork, Modifier.size(52.dp), RoundedCornerShape(4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(m.artist, if (m.source == "Your library") "In your library" else m.source).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            m.line?.let {
                Text(
                    "\u201C${it.trim()}\u201D",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SpitifyColors.TextPrimary.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Genres and moods matching the search, as tiles that open a mix. */
@Composable
private fun GenreResults(query: String, onPick: (String) -> Unit, onSpotifyGenre: (() -> Unit)?) {
    val words = com.srakim.spitify.data.LyricText.words(query)
    val matching = Genres.all.filter { g -> words.any { w -> com.srakim.spitify.data.LyricText.norm(g.name).contains(w) } }
    val shown = matching.ifEmpty { Genres.all }
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns(LocalContentWidth.current, phone = 2, minCell = 200.dp)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomInset.current + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onSpotifyGenre != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Songs Spotify files under \u201C$query\u201D",
                    style = MaterialTheme.typography.labelLarge,
                    color = SpitifyColors.Accent,
                    modifier = Modifier.clickable(onClick = onSpotifyGenre).padding(vertical = 10.dp),
                )
            }
        }
        if (matching.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Browse all genres and moods", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        items(shown, key = { "g:" + it.name }) { g -> GenreTile(g.name, Color(g.color)) { onPick(g.name) } }
    }
}
