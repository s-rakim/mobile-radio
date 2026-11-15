package com.srakim.spitify.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.media.audiofx.LoudnessEnhancer
import com.srakim.spitify.data.AppSettings
import com.srakim.spitify.data.VolumeLevel

data class EqualizerInfo(
    val presets: List<String>,
    val bandFrequenciesHz: List<Int>,
    val minLevel: Int,
    val maxLevel: Int,
)

/**
 * Wraps the platform Equalizer and LoudnessEnhancer for the player's audio session.
 * Some devices don't expose these effects; everything here fails quietly in that case.
 */
class AudioEffects(sessionId: Int) {

    private val equalizer: Equalizer? = runCatching { Equalizer(0, sessionId) }.getOrNull()
    private val loudness: LoudnessEnhancer? = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

    /**
     * Volume normalization. Songs don't carry loudness tags here, so instead of ReplayGain this
     * runs a gentle compressor and a limiter on the output: quiet songs come up, loud ones come
     * down, and nothing clips. Android 9 and newer.
     */
    private val leveller: DynamicsProcessing? = if (Build.VERSION.SDK_INT >= 28) {
        runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                true, 1,
                false, 0,
                true,
            ).build()
            val dp = DynamicsProcessing(0, sessionId, config)
            val band = DynamicsProcessing.MbcBand(
                true,
                20_000f, // one band covering everything
                30f, // attack ms
                400f, // release ms
                3f, // ratio
                -26f, // threshold dB
                6f, // knee dB
                -90f, // noise gate threshold
                1f, // expander ratio
                0f, // pre gain
                6f, // post gain: make up for the compression
            )
            dp.setMbcBandAllChannelsTo(0, band)
            dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
            dp
        }.getOrNull()
    } else {
        null
    }

    val info: EqualizerInfo? = equalizer?.let { eq ->
        runCatching {
            val range = eq.bandLevelRange
            EqualizerInfo(
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                bandFrequenciesHz = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
            )
        }.getOrNull()
    }

    fun apply(settings: AppSettings) {
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = settings.equalizerEnabled
                if (settings.equalizerEnabled) {
                    val preset = settings.equalizerPreset
                    if (preset in 0 until eq.numberOfPresets) {
                        eq.usePreset(preset.toShort())
                    } else {
                        settings.equalizerBands.forEachIndexed { band, level ->
                            if (band < eq.numberOfBands) eq.setBandLevel(band.toShort(), level.toShort())
                        }
                    }
                }
            }
        }
        leveller?.let { runCatching { it.enabled = settings.volumeNormalization } }
        loudness?.let { le ->
            runCatching {
                val gain = if (settings.volumeLevel == VolumeLevel.LOUD) 600 else 0
                le.setTargetGain(gain)
                le.enabled = gain > 0
            }
        }
    }

    /** Current band levels, for drawing the sliders after a preset is picked. */
    fun bandLevels(): List<Int> = equalizer?.let { eq ->
        runCatching { (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() } }.getOrNull()
    } ?: emptyList()

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        runCatching { leveller?.release() }
    }

    companion object {
        /** Base player volume for each level. Quiet simply turns things down. */
        fun baseVolume(level: VolumeLevel) = if (level == VolumeLevel.QUIET) 0.65f else 1f
    }
}
