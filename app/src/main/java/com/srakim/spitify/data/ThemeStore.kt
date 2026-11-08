package com.srakim.spitify.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.srakim.spitify.ui.theme.Palettes
import com.srakim.spitify.ui.theme.SpitifyColors
import com.srakim.spitify.ui.theme.SpitifyPalette

/** Remembers which theme the listener picked, including their custom accent. */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    var customAccent: Color
        get() = Color(prefs.getInt(KEY_ACCENT, Palettes.Spitify.accent.toArgb()))
        private set(value) = prefs.edit().putInt(KEY_ACCENT, value.toArgb()).apply()

    var customTrueBlack: Boolean
        get() = prefs.getBoolean(KEY_TRUE_BLACK, false)
        private set(value) = prefs.edit().putBoolean(KEY_TRUE_BLACK, value).apply()

    val selectedId: String get() = prefs.getString(KEY_SELECTED, Palettes.Spitify.id) ?: Palettes.Spitify.id

    /** Applies the saved theme. Call once at startup. */
    fun restore() {
        SpitifyColors.palette = resolve(selectedId)
    }

    fun select(palette: SpitifyPalette) {
        prefs.edit().putString(KEY_SELECTED, palette.id).apply()
        SpitifyColors.palette = palette
    }

    fun setCustom(accent: Color = customAccent, trueBlack: Boolean = customTrueBlack) {
        customAccent = accent
        customTrueBlack = trueBlack
        select(Palettes.custom(accent, trueBlack))
    }

    private fun resolve(id: String): SpitifyPalette =
        if (id == Palettes.CUSTOM_ID) Palettes.custom(customAccent, customTrueBlack)
        else Palettes.presets.firstOrNull { it.id == id } ?: Palettes.Spitify

    private companion object {
        const val KEY_SELECTED = "selected"
        const val KEY_ACCENT = "custom_accent"
        const val KEY_TRUE_BLACK = "custom_true_black"
    }
}
