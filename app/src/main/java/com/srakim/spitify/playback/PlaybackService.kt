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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.srakim.spitify.MainActivity
import com.srakim.spitify.SpitifyApp
import com.srakim.spitify.data.MixPlan
import com.srakim.spitify.data.AudiusApi
import com.srakim.spitify.data.DownloadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Owns the ExoPlayer instance and the media session. Running as a foreground service keeps
 * music going with the screen off and gives us the notification and lock screen controls.
 * Also applies the audio settings: equaliser, volume level and crossfade.
 */
class PlaybackService : MediaSessionService() {

    private companion object {
        /** How long Automix fades last when crossfade itself is off. */
        const val AUTOMIX_MS = 6_000L
    }

    private var session: MediaSession? = null
    private var effects: AudioEffects? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val app = application as SpitifyApp

        // Local files for downloads, OkHttp for everything streamed. The player gets its own,
        // quicker connect timeout: a dead storage node should fall back fast, not stall the song.
        // Longer read timeout: a YouTube song that wasn't prepared in advance makes the Spitify
        // server fetch its audio link first, which can take a while before the first byte.
        val playerHttp = app.httpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
        val network = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(playerHttp))
        // SoundCloud stream addresses are signed and short-lived, so they're fetched right before playback.
        val resolving = androidx.media3.datasource.ResolvingDataSource.Factory(network) { spec ->
            if (spec.uri.scheme == com.srakim.spitify.data.SoundCloudApi.SCHEME) spec.withUri(app.soundCloud.resolveStreamBlocking(spec.uri)) else spec
        }
        val dataSource = FallbackDataSource.Factory(resolving)
        // Only the renderers a music app needs: audio (through our own sink, so mono audio can be
        // mixed in) and metadata, which carries what a radio station says is on air.
        val mixer = ChannelMixingAudioProcessor()
        applyMono(mixer, app.settings.current.monoAudio)
        val renderers = RenderersFactory { handler, _, audioListener, _, metadataOutput ->
            val sink = DefaultAudioSink.Builder(this).setAudioProcessors(arrayOf<AudioProcessor>(mixer)).build()
            arrayOf(
                MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink),
                MetadataRenderer(metadataOutput, handler.looper),
            )
        }
        val player = ExoPlayer.Builder(this, renderers)
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
            app.settings.state.collect {
                fx.apply(it)
                // "Gapless playback" trims silence at the ends of songs (and long pauses inside them).
                if (player.skipSilenceEnabled != it.gapless) player.skipSilenceEnabled = it.gapless
                // Picked up by the audio sink from the next song on.
                applyMono(mixer, it.monoAudio)
            }
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
            // Automix: mixed playlists, genre mixes and radio fade into each other even with crossfade off.
            val ctx = app.player.state.value.context?.id.orEmpty()
            val mixedPlaylist = ctx.startsWith("mine:") &&
                app.library.state.value.playlists.any { it.id == ctx.removePrefix("mine:") && it.mixed }
            val djSet = ctx == "dj" || ctx.startsWith("ai:")
            val automix = settings.automix && (mixedPlaylist || djSet || ctx.startsWith("mix:") || ctx.startsWith("radio:"))
            val fadeMs = maxOf(settings.crossfadeSeconds * 1000L, if (automix) AUTOMIX_MS else 0L)
            if (fadeMs <= 0 || !player.isPlaying) {
                if (player.volume != base) player.volume = base
                continue
            }
            val duration = player.duration
            val position = player.currentPosition
            if (duration == C.TIME_UNSET || duration <= 0) continue
            // DJ sets carry a fade length per transition: long blends where tempo and key line up,
            // a near-cut on a big drop.
            val nextId = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex).mediaId else null
            val inMs = (if (automix && djSet) MixPlan.fadeMs(player.currentMediaItem?.mediaId) else null) ?: fadeMs
            val outMs = (if (automix && djSet) MixPlan.fadeMs(nextId) else null) ?: fadeMs
            val inWindow = minOf(inMs, duration / 3).coerceAtLeast(1)
            val outWindow = minOf(outMs, duration / 3).coerceAtLeast(1)
            val remaining = duration - position
            val factor = when {
                position < inWindow -> position.toFloat() / inWindow
                remaining < outWindow && player.hasNextMediaItem() -> remaining.toFloat() / outWindow
                else -> 1f
            }.coerceIn(0.05f, 1f)
            player.volume = base * factor
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyMono(mixer: ChannelMixingAudioProcessor, mono: Boolean) {
        if (mono) {
            mixer.putChannelMixingMatrix(ChannelMixingMatrix(1, 2, floatArrayOf(1f, 1f)))
            mixer.putChannelMixingMatrix(ChannelMixingMatrix(2, 2, floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)))
        } else {
            mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2))
            mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))
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
                val mime = item.requestMetadata.extras?.getString(EXTRA_MIME)
                when {
                    local != null -> item.buildUpon().setUri(Uri.fromFile(local)).build()
                    requested != null -> item.buildUpon().setUri(requested).setMimeType(mime).build()
                    item.localConfiguration != null -> item
                    else -> item.buildUpon().setUri(AudiusApi.streamUrl(item.mediaId)).build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
