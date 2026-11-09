package com.srakim.spitify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.srakim.spitify.R

/** One complete set of colour tokens. Every screen reads these through [SpitifyColors]. */
data class SpitifyPalette(
    val id: String,
    val name: String,
    val background: Color,
    val base: Color,
    val elevated: Color,
    val highlight: Color,
    val pressed: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        /** Derives the surface steps from a background and picks readable text for the accent. */
        fun of(id: String, name: String, accent: Color, background: Color, base: Color = Color.Black): SpitifyPalette =
            SpitifyPalette(
                id = id,
                name = name,
                background = background,
                base = base,
                elevated = lerp(background, Color.White, 0.06f),
                highlight = lerp(background, Color.White, 0.11f),
                pressed = lerp(background, Color.White, 0.17f),
                divider = lerp(background, Color.White, 0.12f),
                accent = accent,
                onAccent = readableOn(accent),
            )

        fun readableOn(color: Color): Color {
            val l = color.luminance()
            val blackContrast = (l + 0.05f) / 0.05f
            val whiteContrast = 1.05f / (l + 0.05f)
            return if (blackContrast >= whiteContrast) Color.Black else Color.White
        }
    }
}

/**
 * The built-in themes. Spitify (black and green) is the default. The rest are lifted from the
 * product palettes in ui-ux-pro-max: Music Streaming, Podcast Platform, Video Streaming/OTT,
 * Theater/Cinema, Music Creation, Photography Studio, Gaming, Productivity and Fitness. Custom is whatever the listener builds.
 */
object Palettes {
    val Spitify = SpitifyPalette.of("spitify", "Spitify", accent = Color(0xFF2AD46B), background = Color(0xFF121212))
    val Midnight = SpitifyPalette.of("midnight", "Midnight", accent = Color(0xFF22C55E), background = Color(0xFF0F0F23), base = Color(0xFF08081A))
    val Sunset = SpitifyPalette.of("sunset", "Sunset", accent = Color(0xFFF97316), background = Color(0xFF0F0F23), base = Color(0xFF08081A))
    val Cinema = SpitifyPalette.of("cinema", "Cinema", accent = Color(0xFFE11D48), background = Color(0xFF000000))
    val Spotlight = SpitifyPalette.of("spotlight", "Spotlight", accent = Color(0xFFCA8A04), background = Color(0xFF0F0F23), base = Color(0xFF08081A))
    val Studio = SpitifyPalette.of("studio", "Studio", accent = Color(0xFF8B5CF6), background = Color(0xFF0F172A), base = Color(0xFF080D1A))
    val Mono = SpitifyPalette.of("mono", "Mono", accent = Color(0xFFF8FAFC), background = Color(0xFF000000))
    // Gaming palette's rose action colour on its deep navy.
    val Neon = SpitifyPalette.of("neon", "Neon", accent = Color(0xFFF43F5E), background = Color(0xFF0F0F23), base = Color(0xFF08081A))
    // Productivity teal on the slate used by the IoT and beat-maker palettes.
    val Lagoon = SpitifyPalette.of("lagoon", "Lagoon", accent = Color(0xFF14B8A6), background = Color(0xFF0F172A), base = Color(0xFF080D1A))
    // Fitness orange, warm and loud, on near-black stone.
    val Ember = SpitifyPalette.of("ember", "Ember", accent = Color(0xFFF59E0B), background = Color(0xFF1C1917), base = Color(0xFF0C0A09))

    val presets = listOf(Spitify, Midnight, Sunset, Cinema, Spotlight, Studio, Neon, Lagoon, Ember, Mono)

    const val CUSTOM_ID = "custom"

    fun custom(accent: Color, trueBlack: Boolean): SpitifyPalette =
        SpitifyPalette.of(CUSTOM_ID, "Custom", accent, if (trueBlack) Color.Black else Color(0xFF121212))

    /** Accent swatches offered when building a custom theme. */
    val customAccents = listOf(
        0xFF2AD46B, 0xFF1FB8A6, 0xFF2E9BE6, 0xFF5B7CFA, 0xFF8B5CF6, 0xFFC04BD8,
        0xFFE84A8A, 0xFFE11D48, 0xFFF25C3C, 0xFFF97316, 0xFFE8A93B, 0xFFCDDC39,
        0xFF9ED36A, 0xFFB8C4D6, 0xFFF8FAFC, 0xFFD4A373,
    ).map { Color(it) }
}

/**
 * Colour tokens backed by Compose state, so switching theme recolours the whole app at once.
 * Screens use these names, never raw hex.
 */
object SpitifyColors {
    var palette by mutableStateOf(Palettes.Spitify)

    val Background get() = palette.background
    val Base get() = palette.base
    val Elevated get() = palette.elevated
    val Highlight get() = palette.highlight
    val Pressed get() = palette.pressed
    val Divider get() = palette.divider
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    val AccentDeep get() = lerp(palette.accent, Color.Black, 0.6f)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF8C8C8C)
    val Error = Color(0xFFE5534B)
    val Toast = Color(0xFFF2F2F2)
    val OnToast = Color(0xFF121212)
}

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    resId = R.font.figtree,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Figtree = FontFamily(
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
    figtree(FontWeight.Bold),
    figtree(FontWeight.ExtraBold),
)

private val base = TextStyle(fontFamily = Figtree, color = Color.White)

val SpitifyTypography = Typography(
    displaySmall = base.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    headlineLarge = base.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    headlineMedium = base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineSmall = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    titleLarge = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = Color(0xFFB3B3B3)),
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
)

@Composable
fun SpitifyTheme(content: @Composable () -> Unit) {
    val palette = SpitifyColors.palette
    val scheme = remember(palette) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = lerp(palette.accent, Color.Black, 0.6f),
            onPrimaryContainer = Color.White,
            secondary = Color.White,
            onSecondary = palette.background,
            background = palette.background,
            onBackground = Color.White,
            surface = palette.background,
            onSurface = Color.White,
            surfaceVariant = palette.highlight,
            onSurfaceVariant = SpitifyColors.TextSecondary,
            surfaceContainer = palette.elevated,
            surfaceContainerLow = palette.elevated,
            surfaceContainerHigh = palette.highlight,
            surfaceContainerHighest = palette.pressed,
            outline = SpitifyColors.TextMuted,
            outlineVariant = palette.divider,
            error = SpitifyColors.Error,
            inverseSurface = SpitifyColors.Toast,
            inverseOnSurface = SpitifyColors.OnToast,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = SpitifyTypography, content = content)
}
