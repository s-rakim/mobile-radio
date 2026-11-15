package com.srakim.spitify.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.srakim.spitify.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Jams between Spitify accounts. The host's phone keeps its queue and playhead on the Spitify
 * server while the Jam runs, so friends can join from anywhere. With "Auto-invite people
 * nearby" on, it also announces itself on the local Wi-Fi, and other phones with Spitify open
 * on the same network see an invite to join.
 */
class NearbyJam(context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hosting = MutableStateFlow(false)
    val hosting: StateFlow<Boolean> = _hosting.asStateFlow()

    /** Usernames of Spitify listeners hosting a Jam on this Wi-Fi. */
    private val _nearby = MutableStateFlow<List<String>>(emptyList())
    val nearby: StateFlow<List<String>> = _nearby.asStateFlow()

    private var hostJob: Job? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    fun startHosting(player: PlayerController, social: SocialStore, settings: SettingsStore) {
        if (_hosting.value) return
        _hosting.value = true
        hostJob = io.launch {
            var lastKey: String? = null
            var lastAt = 0L
            while (isActive) {
                val s = player.state.value
                val current = s.current
                val key = current?.id
                val now = System.currentTimeMillis()
                if (current != null && (key != lastKey || now - lastAt > 30_000)) {
                    lastKey = key
                    lastAt = now
                    val queue = listOf(current) + s.upNext.map { it.track }
                    runCatching { social.hostJam(queue, 0, s.positionMs) }
                }
                delay(3_000)
            }
        }
        val me = social.myName
        if (settings.current.autoInviteNearby && me != null) advertise(me)
    }

    fun stopHosting(social: SocialStore) {
        _hosting.value = false
        hostJob?.cancel()
        hostJob = null
        stopAdvertising()
        io.launch { runCatching { social.endJam() } }
    }

    private fun advertise(username: String) {
        val manager = nsd ?: return
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "Spitify Jam $username"
            serviceType = SERVICE_TYPE
            port = PORT
            setAttribute("user", username)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun stopAdvertising() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        registration = null
    }

    /** Look for Jams on this Wi-Fi while the app is on screen. */
    fun startLooking(myName: String?) {
        val manager = nsd ?: return
        if (discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // The service name carries the username, so there's no need to resolve it.
                val name = serviceInfo.serviceName.removePrefix("Spitify Jam ").trim().lowercase()
                if (name.isNotEmpty() && name != myName) _nearby.update { (it + name).distinct() }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName.removePrefix("Spitify Jam ").trim().lowercase()
                _nearby.update { it - name }
            }
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { discovery = null }
    }

    fun stopLooking() {
        discovery?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        discovery = null
        _nearby.value = emptyList()
    }

    /** Called when the setting changes while already hosting. */
    fun setAutoInvite(on: Boolean, myName: String?) {
        if (!_hosting.value) return
        if (on && myName != null) advertise(myName) else stopAdvertising()
    }

    private companion object {
        const val SERVICE_TYPE = "_spitifyjam._tcp."
        const val PORT = 39017
    }
}
