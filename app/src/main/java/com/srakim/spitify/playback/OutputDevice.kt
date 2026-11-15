package com.srakim.spitify.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Where the sound is coming out: the phone itself, or headphones and speakers plugged in or
 * paired. Spotify shows this under the controls and in the queue; tapping it opens Android's
 * own output picker, since that's what actually moves the audio.
 */
object OutputDevice {

    data class Output(val name: String, val external: Boolean)

    private val externalTypes = buildList {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
        add(AudioDeviceInfo.TYPE_HDMI)
        if (Build.VERSION.SDK_INT >= 31) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    fun current(context: Context): Output {
        val audio = context.getSystemService(AudioManager::class.java)
        val outputs = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        // Android sends media to the most recently connected headset or speaker, which is
        // normally the only one connected, so the first external device is a fair answer.
        val external = externalTypes.firstNotNullOfOrNull { type -> outputs.firstOrNull { it.type == type } }
        if (external != null) {
            val name = external.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL }
                ?: when (external.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphones"
                    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                    else -> "Bluetooth"
                }
            return Output(name, external = true)
        }
        return Output(Build.MODEL.ifBlank { "This phone" }, external = false)
    }

    /** Android's output switcher where there is one, Bluetooth settings otherwise. */
    fun openPicker(context: Context) {
        if (Build.VERSION.SDK_INT >= 34 && runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)) {
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val sent = runCatching {
                context.sendBroadcast(
                    Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                        .setPackage("com.android.systemui")
                        .putExtra("package_name", context.packageName),
                )
            }.isSuccess
            if (sent) return
        }
        runCatching {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** The current output, kept up to date as headphones come and go. */
@Composable
fun rememberOutputDevice(): OutputDevice.Output {
    val context = LocalContext.current
    var output by remember { mutableStateOf(OutputDevice.current(context)) }
    DisposableEffect(context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                output = OutputDevice.current(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                output = OutputDevice.current(context)
            }
        }
        audio?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio?.unregisterAudioDeviceCallback(callback) }
    }
    return output
}
