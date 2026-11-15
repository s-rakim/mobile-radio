package com.srakim.spitify.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.srakim.spitify.data.AppSettings
import com.srakim.spitify.data.DataSaverMode
import com.srakim.spitify.data.Quality

/** True on mobile data, a metered hotspot, or with Android's own Data Saver on. */
private fun isMetered(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val androidSaver = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    return cm.isActiveNetworkMetered || androidSaver
}

/**
 * Whether to save data right now: always, never, or (Automatic) on mobile data and with
 * Android's Data Saver. Picking Low quality for the current connection turns it on too.
 */
@Composable
fun rememberDataSaver(settings: AppSettings): Boolean {
    val context = LocalContext.current
    var metered by remember { mutableStateOf(isMetered(context)) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                metered = isMetered(context)
            }
            override fun onLost(network: Network) {
                metered = isMetered(context)
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    val quality = if (metered) settings.cellularQuality else settings.wifiQuality
    return when (settings.dataSaverMode) {
        DataSaverMode.ON -> true
        DataSaverMode.AUTO -> metered || quality == Quality.LOW
        DataSaverMode.OFF -> quality == Quality.LOW
    }
}
