package com.srakim.spitify.playback

import android.media.audiofx.Equalizer
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
    }

    companion object {
        /** Base player volume for each level. Quiet simply turns things down. */
        fun baseVolume(level: VolumeLevel) = if (level == VolumeLevel.QUIET) 0.65f else 1f
    }
}
