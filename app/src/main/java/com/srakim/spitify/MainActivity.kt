package com.srakim.spitify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.srakim.spitify.data.Jam
import com.srakim.spitify.data.Links
import com.srakim.spitify.ui.SpitifyRoot
import com.srakim.spitify.ui.theme.SpitifyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        var askedSpotifyApproval = false
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val app = application as SpitifyApp
        app.player.connect()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleLink(intent)

        setContent {
            SpitifyTheme {
                SpitifyRoot(app)
            }
        }

        // The Spotify app asks you once to let Spitify pick songs. The quiet connect below
        // can't show that screen, so when Spotify says it's needed, connect again with it
        // allowed. Once per launch, so saying no doesn't bring it straight back.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.spotifyRemote.state.collect { rs ->
                    if (rs.needsApproval && !askedSpotifyApproval && app.settings.current.playThroughSpotify) {
                        askedSpotifyApproval = true
                        app.spotifyRemote.connect(this@MainActivity, app.spotifyImport.clientId, interactive = true)
                    }
                }
            }
        }
    }

    // Quietly reconnect to the Spotify app each time Spitify comes to the front. If Spotify
    // wants your OK first, the watcher in onCreate asks for it.
    override fun onStart() {
        super.onStart()
        val app = application as SpitifyApp
        val id = app.spotifyImport.clientId
        if (app.settings.current.playThroughSpotify && id.isNotBlank() && app.spotifyRemote.isSpotifyInstalled(this)) {
            app.spotifyRemote.connect(this, id, interactive = false)
        }
    }

    // The activity is singleTask, so a Jam, collab, profile or Spotify sign-in link tapped while Spitify is open lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        Jam.parse(uri)?.let { Jam.incoming.value = it; return }
        Links.parse(uri)?.let { Links.incoming.value = it }
    }
}
