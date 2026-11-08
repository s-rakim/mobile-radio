package com.srakim.spitify.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.srakim.spitify.MainActivity
import com.srakim.spitify.SpitifyApp
import com.srakim.spitify.data.AudiusApi
import com.srakim.spitify.data.DownloadStore
import com.srakim.spitify.data.SoundCloudApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer instance and the media session. Running as a foreground service keeps
 * music going with the screen off and gives us the notification and lock screen controls.
 * Also applies the audio settings: equaliser, volume level and crossfade.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var effects: AudioEffects? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val app = application as SpitifyApp

        // Local files for downloads, OkHttp for everything streamed.
        val network = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(app.httpClient))
        // SoundCloud stream addresses are signed and short-lived, so they're fetched right before playback.
        val dataSource = ResolvingDataSource.Factory(network) { spec ->
            if (spec.uri.scheme == SoundCloudApi.SCHEME) spec.withUri(app.soundCloud.resolveStreamBlocking(spec.uri)) else spec
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionId = Util.generateAudioSessionIdV21(this)
        player.audioSessionId = sessionId
        val fx = AudioEffects(sessionId)
        effects = fx
        app.audioEffects = fx

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback(app.downloads))
            .build()

        scope.launch {
            app.settings.state.collect { fx.apply(it) }
        }
        scope.launch { runFades(player, app) }
    }

    /**
     * Crossfade: ramps the volume down over the last N seconds of a song and back up at the
     * start of the next one. ExoPlayer has a single output, so songs fade rather than overlap.
     */
    private suspend fun runFades(player: ExoPlayer, app: SpitifyApp) {
        while (scope.isActive) {
            delay(100)
            val settings = app.settings.current
            val base = AudioEffects.baseVolume(settings.volumeLevel)
            val fadeMs = settings.crossfadeSeconds * 1000L
            if (fadeMs <= 0 || !player.isPlaying) {
                if (player.volume != base) player.volume = base
                continue
            }
            val duration = player.duration
            val position = player.currentPosition
            if (duration == C.TIME_UNSET || duration <= 0) continue
            val window = minOf(fadeMs, duration / 3)
            val remaining = duration - position
            val factor = when {
                position < window -> position.toFloat() / window
                remaining < window && player.hasNextMediaItem() -> remaining.toFloat() / window
                else -> 1f
            }.coerceIn(0.05f, 1f)
            player.volume = base * factor
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        (application as SpitifyApp).audioEffects = null
        effects?.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Items coming from a controller lose their URI on the way in, so rebuild it from the id. */
    private class Callback(private val downloads: DownloadStore) : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val local = downloads.fileFor(item.mediaId)
                val requested = item.requestMetadata.mediaUri
                when {
                    local != null -> item.buildUpon().setUri(Uri.fromFile(local)).build()
                    requested != null -> item.buildUpon().setUri(requested).build()
                    item.localConfiguration != null -> item
                    else -> item.buildUpon().setUri(AudiusApi.streamUrl(item.mediaId)).build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
