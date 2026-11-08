package com.srakim.spitify.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srakim.spitify.data.PlayContext
import com.srakim.spitify.data.RecentItem
import com.srakim.spitify.ui.Load
import com.srakim.spitify.ui.LocalActions
import com.srakim.spitify.ui.LocalApi
import com.srakim.spitify.ui.LocalBottomInset
import com.srakim.spitify.ui.LocalJamendo
import com.srakim.spitify.data.JamendoApi
import com.srakim.spitify.ui.LocalLibrary
import com.srakim.spitify.ui.LocalPlayer
import com.srakim.spitify.ui.components.Artwork
import com.srakim.spitify.ui.components.ErrorState
import com.srakim.spitify.ui.components.OutlineButton
import com.srakim.spitify.ui.components.SectionHeader
import com.srakim.spitify.ui.components.SkeletonTrackList
import com.srakim.spitify.ui.components.formatCount
import com.srakim.spitify.ui.components.rememberArtworkColor
import com.srakim.spitify.ui.rememberLoad
import com.srakim.spitify.ui.sheets.SheetOption
import com.srakim.spitify.ui.theme.SpitifyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun ArtistScreen(id: String) {
    val api = LocalApi.current
    val jamendo = LocalJamendo.current
    val library = LocalLibrary.current
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val lib by library.state.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val likedIds = rememberLikedIds()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val load = rememberLoad("artist", id) {
        coroutineScope {
            val fromJamendo = JamendoApi.isJamendo(id)
            val info = async { if (fromJamendo) jamendo.artist(id) else api.artist(id) }
            val tracks = async { if (fromJamendo) jamendo.artistTracks(id) else api.artistTracks(id) }
            info.await() to tracks.await()
        }
    }
    val artist = (load.state as? Load.Ready)?.data?.first
    val tracks = (load.state as? Load.Ready)?.data?.second.orEmpty()
    val heroImage = artist?.cover ?: artist?.pictureLarge
    val color by rememberArtworkColor(artist?.picture)
    val following = lib.followedArtists.any { it.id == id }
    val ctx = PlayContext("artist:$id", artist?.name ?: "Artist", "ARTIST")

    LaunchedEffect(artist?.id) {
        artist?.let { library.recordVisit(RecentItem(RecentItem.Kind.ARTIST, it.id, it.name, "Artist", it.picture)) }
    }

    val barAlpha by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f) }
    }
    val alpha by animateFloatAsState(barAlpha, label = "artistBar")

    Box(Modifier.fillMaxSize().background(SpitifyColors.Background)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(340.dp).background(color)) {
                    Artwork(heroImage, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), fallback = Icons.Rounded.Person)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, SpitifyColors.Background.copy(alpha = 0.9f)))),
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            artist?.name.orEmpty(),
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (artist?.verified == true) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.Verified, contentDescription = "Verified artist", tint = SpitifyColors.Accent)
                        }
                    }
                }
            }

            when (val state = load.state) {
                is Load.Loading -> item { SkeletonTrackList(6) }
                is Load.Failed -> item { ErrorState(state.message, load.retry) }
                is Load.Ready -> {
                    item {
                        Text(
                            if (JamendoApi.isJamendo(id)) "Jamendo artist" else "${formatCount(artist?.followers ?: 0)} followers",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(artist?.picture, Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(12.dp))
                            OutlineButton(if (following) "Following" else "Follow", onClick = {
                                artist?.let {
                                    val now = library.toggleFollow(it)
                                    actions.toast(if (now) "Following ${it.name}" else "Unfollowed ${it.name}")
                                }
                            })
                            CollectionActions(
                                tracks = tracks,
                                context = ctx,
                                modifier = Modifier.weight(1f),
                                extraMenu = { close ->
                                    SheetOption(Icons.Rounded.Share, "Share", onClick = {
                                        shareText(context, "${artist?.name} on Spitify\nhttps://audius.co/${artist?.handle}")
                                        close()
                                    })
                                },
                            )
                        }
                    }
                    item { SectionHeader("Popular") }
                    val shown = if (showAll) tracks.take(10) else tracks.take(5)
                    collectionTracks(
                        shown, ctx, playback.current?.id, playback.isPlaying, likedIds,
                        onPlay = { player.play(tracks, it, ctx) },
                        onMore = { actions.showTrackMenu(it, null) },
                        numbered = true,
                    )
                    if (tracks.size > 5) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                                OutlineButton(if (showAll) "Show less" else "See more", onClick = { showAll = !showAll })
                            }
                        }
                    }
                    if (tracks.size > 10) {
                        item { SectionHeader("All songs") }
                        collectionTracks(
                            tracks.drop(10), PlayContext("artist-all:$id", artist?.name ?: "Artist", "ARTIST"),
                            playback.current?.id, playback.isPlaying, likedIds,
                            onPlay = { player.play(tracks, it + 10, ctx) },
                            onMore = { actions.showTrackMenu(it, null) },
                        )
                    }
                    if (artist != null) {
                        item {
                            SectionHeader("About")
                            Column(
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpitifyColors.Highlight),
                            ) {
                                Artwork(artist.pictureLarge, Modifier.fillMaxWidth().height(220.dp), RoundedCornerShape(0.dp), fallback = Icons.Rounded.Person)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${formatCount(artist.followers)} followers", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (JamendoApi.isJamendo(artist.id)) "On Jamendo" else "${artist.trackCount} tracks on Audius",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    artist.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SpitifyColors.TextSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = alpha))
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f * (1 - alpha))).clickable { actions.back() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text(artist?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(alpha), maxLines = 1)
        }
    }
}
