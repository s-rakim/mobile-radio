package com.srakim.spitify

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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

    private fun tapDesc(desc: String) {
        device.findObject(By.desc(desc))?.click()
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

            // The profile button has to open the side menu every time, also after visiting
            // Settings from it and coming back (it used to stop working after the first time).
            step {
                tapDesc("Profile and settings")
                shot("01b-drawer", 1500)
                tap("Settings and privacy")
                shot("01c-settings", 2500)
                device.pressBack()
                Thread.sleep(1500)
                tapDesc("Profile and settings")
                val opened = device.wait(Until.hasObject(By.text("Settings and privacy")), 4000) == true
                shot(if (opened) "01d-drawer-again-ok" else "01d-drawer-again-BROKEN", 600)
                device.pressBack()
            }
            // A profile picture, as if picked and cropped: it should show on the profile button,
            // at the top of Settings and in Edit profile.
            step {
                val square = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(square).apply {
                    drawColor(android.graphics.Color.rgb(232, 115, 40))
                    drawCircle(256f, 210f, 96f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(250, 220, 190) })
                    drawCircle(256f, 560f, 210f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(40, 40, 60) })
                }
                val (version, _) = com.srakim.spitify.data.ProfilePhoto.save(app, square)
                instrumentation.runOnMainSync { app.settings.update { it.copy(profilePhoto = version, profileName = "Malcolm") } }
                shot("01e-home-with-photo", 2500)
                tapDesc("Profile and settings")
                Thread.sleep(1200)
                tap("Settings and privacy")
                shot("01f-settings-with-photo", 2500)
                tap("Malcolm")
                shot("01g-edit-profile", 2500)
                tap("Change photo")
                shot("01h-change-photo", 1500)
                device.pressBack()
                Thread.sleep(800)
                device.pressBack()
                Thread.sleep(800)
                device.pressBack()
                Thread.sleep(1200)
                tap("Home")
            }

            step { tap("Search"); shot("03-search") }
            step { scrollDown(); shot("04-search-scrolled") }
            step { tap("Your Library"); shot("05-library") }
            step { tap("Liked Songs"); shot("05b-liked", 3000); device.pressBack() }

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
                    device.pressBack()
                }
                // A Spotify song with no Spotify app on the phone: Spitify should find it in
                // its own sources and play it, not stop with an error.
                step {
                    val t = tracks.first()
                    val fromSpotify = t.copy(id = "spotify:track:tour${t.id.takeLast(8)}")
                    instrumentation.runOnMainSync { app.player.play(listOf(fromSpotify), 0, PlayContext("tour", "Tour", "PLAYLIST")) }
                    var playing: String? = null
                    repeat(30) {
                        if (playing == null) {
                            Thread.sleep(500)
                            playing = app.player.state.value.current?.id?.takeUnless { it.startsWith("spotify:") }
                        }
                    }
                    shot(if (playing != null) "14-spotify-song-played-by-spitify" else "14-spotify-song-NOT-PLAYED", 3000)
                }
            }
        }
    }
}
