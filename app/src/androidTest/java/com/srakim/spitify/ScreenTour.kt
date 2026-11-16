package com.srakim.spitify

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.srakim.spitify.data.Links
import com.srakim.spitify.data.PlayContext
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the main screens and saves a screenshot of each, so the look can be checked
 * without a phone. CI runs it on an emulator and pulls the PNGs from the app's files folder.
 * Every step is best effort: a slow network shouldn't stop the rest of the tour.
 */
@RunWith(AndroidJUnit4::class)
class ScreenTour {

    @get:Rule
    val notifications: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val app get() = instrumentation.targetContext.applicationContext as SpitifyApp
    private val out by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "shots").apply { deleteRecursively(); mkdirs() }
    }

    private fun shot(name: String, settle: Long = 3500) {
        Thread.sleep(settle)
        device.waitForIdle(2000)
        device.takeScreenshot(File(out, "$name.png"))
    }

    private fun tap(text: String) {
        device.findObject(By.text(text))?.click()
    }

    private fun scrollDown() {
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
    }

    private fun step(block: () -> Unit) = runCatching(block).onFailure { it.printStackTrace() }

    @Test
    fun tour() {
        ActivityScenario.launch(MainActivity::class.java).use {
            out
            shot("01-home", 9000)
            step { scrollDown(); shot("02-home-scrolled") }

            step { tap("Search"); shot("03-search") }
            step { scrollDown(); shot("04-search-scrolled") }
            step { tap("Your Library"); shot("05-library") }

            val playlists = runCatching { runBlocking { app.api.trendingPlaylists(20) } }.getOrDefault(emptyList())
            val tracks = runCatching { runBlocking { app.api.trendingTracks(limit = 20) } }.getOrDefault(emptyList())

            playlists.firstOrNull { it.label == "Playlist" }?.let { p ->
                step {
                    tap("Home")
                    Links.incoming.value = Links.Link.Playlist(p.id)
                    shot("06-playlist", 6000)
                    scrollDown()
                    shot("07-playlist-scrolled")
                }
            }
            playlists.firstOrNull { it.label.contains("Album") }?.let { a ->
                step {
                    Links.incoming.value = Links.Link.Playlist(a.id)
                    shot("08-album", 6000)
                }
            }
            tracks.firstOrNull { it.artistId.isNotBlank() }?.let { t ->
                step {
                    Links.incoming.value = Links.Link.Artist(t.artistId)
                    shot("09-artist", 6000)
                    scrollDown()
                    shot("10-artist-scrolled")
                }
            }
            if (tracks.isNotEmpty()) {
                step {
                    instrumentation.runOnMainSync { app.player.play(tracks, 0, PlayContext("trending-week", "Trending now", "TRENDING")) }
                    shot("11-mini-player", 6000)
                    Links.incoming.value = Links.Link.Player
                    shot("12-now-playing", 5000)
                    scrollDown()
                    shot("13-now-playing-below")
                }
            }
        }
    }
}
