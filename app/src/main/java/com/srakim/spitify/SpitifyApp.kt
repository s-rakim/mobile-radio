package com.srakim.spitify

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.srakim.spitify.data.ArchiveApi
import com.srakim.spitify.data.AudiusApi
import com.srakim.spitify.data.Catalog
import com.srakim.spitify.data.CcMixterApi
import com.srakim.spitify.data.DownloadStore
import com.srakim.spitify.data.JamendoApi
import com.srakim.spitify.data.LibraryStore
import com.srakim.spitify.data.LocalMusic
import com.srakim.spitify.data.LyricsApi
import com.srakim.spitify.data.SettingsStore
import com.srakim.spitify.data.SoundCloudApi
import com.srakim.spitify.data.SubsonicApi
import com.srakim.spitify.data.ThemeStore
import com.srakim.spitify.playback.AudioEffects
import com.srakim.spitify.playback.PlayerController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SpitifyApp : Application(), ImageLoaderFactory {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val api: AudiusApi by lazy { AudiusApi(httpClient) }
    val lyrics: LyricsApi by lazy { LyricsApi(httpClient) }
    val jamendo: JamendoApi by lazy { JamendoApi(httpClient, settings) }
    val archive: ArchiveApi by lazy { ArchiveApi(httpClient, settings) }
    val ccMixter: CcMixterApi by lazy { CcMixterApi(httpClient, settings) }
    val soundCloud: SoundCloudApi by lazy { SoundCloudApi(httpClient, settings) }
    val server: SubsonicApi by lazy { SubsonicApi(httpClient, settings) }
    val localMusic: LocalMusic by lazy { LocalMusic(this) }
    val catalog: Catalog by lazy { Catalog(api, jamendo, archive, ccMixter, soundCloud, server, localMusic, settings) }
    val library: LibraryStore by lazy { LibraryStore(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val themes: ThemeStore by lazy { ThemeStore(this) }
    val downloads: DownloadStore by lazy { DownloadStore(this, httpClient, settings) }
    val player: PlayerController by lazy { PlayerController(this, api, library, settings, downloads) }

    /** Set by the playback service while it's alive, so Settings can read equaliser bands. */
    @Volatile
    var audioEffects: AudioEffects? = null

    override fun onCreate() {
        super.onCreate()
        themes.restore()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(httpClient)
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
