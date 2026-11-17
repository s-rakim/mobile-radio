package com.srakim.spitify

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.srakim.spitify.data.AccountSync
import com.srakim.spitify.data.AppUpdater
import com.srakim.spitify.data.AudiusApi
import com.srakim.spitify.data.Catalog
import com.srakim.spitify.data.DownloadStore
import com.srakim.spitify.data.LibraryStore
import com.srakim.spitify.data.LocalMusic
import com.srakim.spitify.data.LyricsApi
import com.srakim.spitify.data.SettingsStore
import com.srakim.spitify.data.RadioBrowserApi
import com.srakim.spitify.data.SpitifyServerApi
import com.srakim.spitify.data.SubsonicApi
import com.srakim.spitify.data.ThemeStore
import com.srakim.spitify.playback.AudioEffects
import com.srakim.spitify.playback.PlayerController
import com.srakim.spitify.data.ListeningStats
import com.srakim.spitify.data.NearbyJam
import com.srakim.spitify.data.SocialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SpitifyApp : Application(), ImageLoaderFactory {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // Offline mode: nothing leaves the phone. Downloads and local files still play.
            .addInterceptor { chain ->
                if (settings.current.offlineMode) throw java.io.IOException("Offline mode is on")
                chain.proceed(chain.request())
            }
            .build()
    }

    val api: AudiusApi by lazy { AudiusApi(httpClient) }
    val lyrics: LyricsApi by lazy { LyricsApi(httpClient).also { api -> api.onFound = { t, l -> lyricIndex.add(t, l) } } }
    /** Lyrics of your own songs, kept on the phone so Search can find a song from a line. */
    val lyricIndex: com.srakim.spitify.data.LyricIndex by lazy { com.srakim.spitify.data.LyricIndex(this, lyrics) }
    val server: SubsonicApi by lazy { SubsonicApi(httpClient, settings) }
    val localMusic: LocalMusic by lazy { LocalMusic(this) }
    val webServer: SpitifyServerApi by lazy { SpitifyServerApi(httpClient, settings) }
    val archive: com.srakim.spitify.data.ArchiveApi by lazy { com.srakim.spitify.data.ArchiveApi(httpClient, settings) }
    val ccMixter: com.srakim.spitify.data.CcMixterApi by lazy { com.srakim.spitify.data.CcMixterApi(httpClient, settings) }
    val jamendo: com.srakim.spitify.data.JamendoApi by lazy { com.srakim.spitify.data.JamendoApi(httpClient, settings) }
    val soundCloud: com.srakim.spitify.data.SoundCloudApi by lazy { com.srakim.spitify.data.SoundCloudApi(httpClient, settings) }
    val catalog: Catalog by lazy { Catalog(api, server, webServer, radio, localMusic, settings, archive, ccMixter, jamendo, soundCloud).also { it.spotify = spotifyImport } }
    val library: LibraryStore by lazy { LibraryStore(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val themes: ThemeStore by lazy { ThemeStore(this) }
    val downloads: DownloadStore by lazy { DownloadStore(this, httpClient, settings) }
    val updater: AppUpdater by lazy { AppUpdater(this, httpClient) }
    val account: AccountSync by lazy { AccountSync(this, httpClient, settings, library) }
    val radio: RadioBrowserApi by lazy { RadioBrowserApi(this, httpClient) }
    val player: PlayerController by lazy {
        PlayerController(this, api, library, settings, downloads, radio).also { p ->
            p.spotify = com.srakim.spitify.playback.SpotifyBridge(
                spotifyRemote,
                settings,
                hasClientId = { spotifyImport.clientId.isNotBlank() },
            ) { t -> spotifyImport.standInFor(t, catalog) }
        }
    }
    /** Remote control for the Spotify app (Premium accounts). See playback/SpotifyRemote.kt. */
    val spotifyRemote: com.srakim.spitify.playback.SpotifyRemote by lazy { com.srakim.spitify.playback.SpotifyRemoteImpl(this) }
    val social: SocialStore by lazy { SocialStore(this, account, settings) }
    val stats: ListeningStats by lazy { ListeningStats(this, settings) }
    val nearby: NearbyJam by lazy { NearbyJam(this) }
    val spotifyImport: com.srakim.spitify.data.SpotifyImport by lazy { com.srakim.spitify.data.SpotifyImport(this, httpClient, settings) }
    val spotifyLibrary: com.srakim.spitify.data.SpotifyLibrary by lazy { com.srakim.spitify.data.SpotifyLibrary(spotifyImport) }
    val dj: com.srakim.spitify.data.AiDj by lazy { com.srakim.spitify.data.AiDj(this) }
    /** Lives as long as the app, for work that must finish even if the screen that started it closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Set by the playback service while it's alive, so Settings can read equaliser bands. */
    @Volatile
    var audioEffects: AudioEffects? = null

    override fun onCreate() {
        super.onCreate()
        themes.restore()
        webServer // created early so saved server links can be resolved during playback
        account.start()
        social.start()
        stats.start(player, social)
        dj.start()
        spotifyLibrary.start()
        // Fill the lyric index with your songs in the background, after start-up has settled.
        appScope.launch {
            kotlinx.coroutines.delay(20_000)
            kotlinx.coroutines.flow.combine(library.state, spotifyLibrary.state) { mine, sp ->
                mine.liked + mine.recentTracks + mine.playlists.flatMap { it.tracks } + sp.liked + sp.topTracks + sp.recent
            }.collect { songs ->
                if (settings.current.showLyrics && !settings.current.offlineMode) lyricIndex.fill(songs)
                kotlinx.coroutines.delay(60_000)
            }
        }
        // Friends see what you're playing: tell the server when the song or play state changes,
        // and every couple of minutes while it keeps playing so it still shows as live.
        appScope.launch {
            var last: Pair<String?, Boolean>? = null
            var lastAt = 0L
            player.state.collect { s ->
                val key = s.current?.id to s.isPlaying
                val now = System.currentTimeMillis()
                if (key != last || (s.isPlaying && now - lastAt > 120_000)) {
                    last = key
                    lastAt = now
                    social.reportPlaying(s.current, s.isPlaying)
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(httpClient)
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
