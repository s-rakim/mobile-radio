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
    val server: SubsonicApi by lazy { SubsonicApi(httpClient, settings) }
    val localMusic: LocalMusic by lazy { LocalMusic(this) }
    val webServer: SpitifyServerApi by lazy { SpitifyServerApi(httpClient, settings) }
    val catalog: Catalog by lazy { Catalog(api, server, webServer, radio, localMusic, settings) }
    val library: LibraryStore by lazy { LibraryStore(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val themes: ThemeStore by lazy { ThemeStore(this) }
    val downloads: DownloadStore by lazy { DownloadStore(this, httpClient, settings) }
    val updater: AppUpdater by lazy { AppUpdater(this, httpClient) }
    val account: AccountSync by lazy { AccountSync(this, httpClient, settings, library) }
    val radio: RadioBrowserApi by lazy { RadioBrowserApi(this, httpClient) }
    val player: PlayerController by lazy { PlayerController(this, api, library, settings, downloads, radio) }

    /** Set by the playback service while it's alive, so Settings can read equaliser bands. */
    @Volatile
    var audioEffects: AudioEffects? = null

    override fun onCreate() {
        super.onCreate()
        themes.restore()
        webServer // created early so saved server links can be resolved during playback
        account.start()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(httpClient)
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
