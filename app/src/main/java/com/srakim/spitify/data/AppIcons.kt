package com.srakim.spitify.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.srakim.spitify.R

/** The home screen icons Spitify can wear. Each one is an activity-alias in the manifest. */
enum class AppIcon(val alias: String, val label: String, @DrawableRes val preview: Int) {
    DEFAULT(".LauncherDefault", "Spitify", R.drawable.spitify_logo),
    CLASSIC(".LauncherClassic", "Classic", R.drawable.spitify_logo_classic),
}

object AppIcons {

    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DEFAULT
                else -> false
            }
        } ?: AppIcon.DEFAULT
    }

    /** Switches the launcher icon. Some launchers take a few seconds to pick up the change. */
    fun select(context: Context, choice: AppIcon) {
        val pm = context.packageManager
        // Enable the new one first so there's never a moment with no launcher entry.
        pm.setComponentEnabledSetting(component(context, choice), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != choice }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private fun component(context: Context, icon: AppIcon) =
        ComponentName(context.packageName, "com.srakim.spitify${icon.alias}")
}
