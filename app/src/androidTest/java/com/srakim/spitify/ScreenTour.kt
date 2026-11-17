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
        // The emulator's launcher sometimes pops "isn't responding" over everything.
        device.findObject(By.text("Wait"))?.click()
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

    private fun step(block: () -> Unit) = runCatching { ensureApp(); block() }.onFailure { it.printStackTrace() }

    /**
     * Brings the app back if a missed tap let the Back presses walk out of it, so one slow frame
     * doesn't leave the rest of the tour photographing the launcher.
     */
    private fun ensureApp() {
        val context = instrumentation.targetContext
        if (device.currentPackageName == context.packageName) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
        Thread.sleep(2500)
    }

    /** Taps a search filter, scrolling the row of filters along until it shows. */
    private fun tapPill(text: String) {
        repeat(4) {
            device.findObject(By.text(text))?.let { it.click(); return }
            val top = device.findObject(By.text("Top")) ?: device.findObject(By.text("Songs")) ?: return
            val y = top.visibleBounds.centerY()
            device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 20)
            Thread.sleep(500)
        }
    }

    /** Finding songs by a line of their lyrics: from this phone's lyric index and online. */
    private fun lyricSearch() {

        // One song's lyrics fetched the normal way, which also puts them in the index.
        step {
            runBlocking { app.lyrics.find(com.srakim.spitify.data.Track("tour:diamonds", "Diamonds", "Rihanna", durationSec = 225)) }
            Thread.sleep(500)
        }
        step {
            Links.incoming.value = Links.Link.Search("")
            shot("30-search-suggestions", 5000)
        }
        step {
            Links.incoming.value = Links.Link.Search("shine bright like a diamond")
            shot("31-lyrics-in-top-results", 9000)
            tapPill("Lyrics")
            shot("32-lyrics-tab", 5000)
        }
        step {
            Links.incoming.value = Links.Link.Search("we found love in a hopeless place")
            Thread.sleep(2000)
            tapPill("Lyrics")
            shot("33-lyrics-online", 9000)
        }
        step {
            Links.incoming.value = Links.Link.Search("hip hop")
            Thread.sleep(2500)
            tapPill("Genres & moods")
            shot("34-genres-and-moods", 3000)
        }
    }

    /**
     * Artist pages and song details from real Spotify. CI passes the Spotify app's client id and
     * secret; without them these steps are skipped and a marker file says so.
     */
    /** POSTs JSON to the tour's own Spitify server and returns the reply. */
    private fun post(url: String, body: org.json.JSONObject, token: String? = null): org.json.JSONObject {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val text = (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return org.json.JSONObject(text.ifBlank { "{}" })
    }

    private fun get(url: String, token: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        return c.inputStream.bufferedReader().readText()
    }

    /**
     * Chat against a real Spitify server that CI starts next to the emulator: the bar has to sit
     * above the mini player and tab bar, and above the keyboard while typing; a friend's Spotify
     * song arrives as a playable card; and a song can be sent from the chat itself.
     */
    private fun chatTour() {
        val server = InstrumentationRegistry.getArguments().getString("chatServer").orEmpty()
        if (server.isBlank()) {
            File(out, "70-chat-SKIPPED-no-server.txt").writeText("CI starts a Spitify server for the chat steps; none was given.")
            return
        }
        val report = StringBuilder()
        // This phone signs in as "malcolm"; "alex" is the friend on the other end.
        instrumentation.runOnMainSync { app.settings.update { it.copy(webServerUrl = server) } }
        Thread.sleep(500)
        val signedIn = java.util.concurrent.CountDownLatch(1)
        var signInResult: Result<Unit>? = null
        app.account.signIn("malcolm", "tour-pass-1", create = true) { signInResult = it; signedIn.countDown() }
        signedIn.await(30, java.util.concurrent.TimeUnit.SECONDS)
        report.appendLine("sign in: ${signInResult?.exceptionOrNull()?.message ?: "ok"}")
        val alex = post("$server/api/auth/register", org.json.JSONObject().put("username", "alex").put("password", "tour-pass-1")).optString("token")
        report.appendLine("alex token: ${alex.isNotBlank()}")

        // Alex sends a Spotify song, the way the app would after a Spotify import.
        val song = runCatching { runBlocking { app.spotifyImport.searchTracks("track:\"Diamonds\" artist:\"Rihanna\"") }.first() }
            .getOrElse { app.player.state.value.current?.copy(id = "spotify:track:tourdiamonds") }
        song?.let { t ->
            val track = org.json.JSONObject()
                .put("id", t.id).put("title", t.title).put("artist", t.artist).put("durationSec", t.durationSec)
                .put("artwork", t.artwork ?: org.json.JSONObject.NULL).put("artworkLarge", t.artworkLarge ?: org.json.JSONObject.NULL)
                .put("album", t.album ?: org.json.JSONObject.NULL)
            val sent = post("$server/api/messages/malcolm", org.json.JSONObject().put("kind", "track").put("text", "this one").put("track", track), alex)
            report.appendLine("alex sent ${t.id}: ${sent.optString("id").ifBlank { sent.toString() }}")
        }

        step {
            Links.incoming.value = Links.Link.User("alex")
            Thread.sleep(4000)
            tap("Message")
            shot("70-chat-above-mini-player", 5000)
        }
        step {
            device.findObject(By.text("Message"))?.let { it.click(); Thread.sleep(800); it.text = "Sending you one back" }
            shot("71-chat-keyboard-open", 2500)
            device.pressBack() // keyboard down
            Thread.sleep(800)
        }
        step {
            tapDesc("Send a song")
            shot("72-song-picker", 3000)
            device.findObject(By.descStartsWith("Send song:"))?.click()
            shot("73-chat-song-sent", 5000)
        }
        // What alex sees: the song from this phone, as a playable card with title, artist and cover.
        step {
            val chat = org.json.JSONObject(get("$server/api/messages/malcolm", alex)).getJSONArray("messages")
            for (i in 0 until chat.length()) {
                val m = chat.getJSONObject(i)
                val t = m.optJSONObject("track")
                report.appendLine("${m.optString("from")}: ${m.optString("kind")} ${m.optString("text")} ${t?.optString("title")} / ${t?.optString("artist")} / art=${t?.has("artwork")} / stream=${t?.optString("streamUrl")}")
            }
        }
        // The friend's Spotify song plays when tapped.
        step {
            song?.let { tap(it.title) }
            var playing: String? = null
            repeat(30) {
                if (playing == null) {
                    Thread.sleep(500)
                    playing = app.player.state.value.current?.takeIf { it.id == song?.id }?.title
                }
            }
            report.appendLine("received song plays: ${playing != null}")
            shot("74-received-song-playing", 3000)
            device.pressBack()
        }
        File(out, "75-chat-report.txt").writeText(report.toString())
    }

    private fun spotifyPages() {
        val args = InstrumentationRegistry.getArguments()
        val id = args.getString("spotifyClientId").orEmpty()
        val secret = args.getString("spotifyClientSecret").orEmpty()
        val ok = id.isNotBlank() && secret.isNotBlank() &&
            runCatching { app.spotifyImport.useAppToken(id, secret) }.onFailure { it.printStackTrace() }.isSuccess
        if (!ok) {
            File(out, "40-spotify-SKIPPED-no-client-secret.txt").writeText("Set the SPOTIFY_CLIENT_SECRET repo secret to tour Spotify pages.")
            return
        }
        // The bug report: Rihanna from search came up blank.
        step {
            Links.incoming.value = Links.Link.Search("rihanna")
            shot("40-search-rihanna", 8000)
            tapPill("Artists")
            Thread.sleep(2500)
            device.findObjects(By.text("Rihanna")).firstOrNull { it.visibleBounds.top > device.displayHeight / 6 }?.click()
            shot("41-rihanna-from-search", 9000)
            scrollDown()
            shot("42-rihanna-popular", 2500)
            scrollDown()
            shot("43-rihanna-discography", 2500)
            scrollDown(); scrollDown()
            shot("44-rihanna-appears-on-about", 2500)
        }
        listOf(
            "drake" to "3TVXtAsR1Inumwj472S9r4",
            "taylor-swift" to "06HL4z0CvFAxyc27GXpf02",
            "burna-boy" to "3wcj11K77LjEY1PkEazffa",
            "the-weeknd" to "1Xyo4u8uXC1ZmMpatF05PJ",
        ).forEachIndexed { i, (name, artist) ->
            step {
                Links.incoming.value = Links.Link.Artist("spotify:artist:$artist")
                shot("5$i-$name", 9000)
                scrollDown()
                shot("5$i-$name-scrolled", 2500)
            }
        }
        // An artist known only by name (a song from your server without an artist id).
        step {
            Links.incoming.value = Links.Link.Artist("name:Sauti Sol")
            shot("56-by-name-sauti-sol", 10000)
        }
        // Song details straight from Spotify.
        step {
            val song = runBlocking { app.spotifyImport.searchTracks("track:\"Diamonds\" artist:\"Rihanna\"") }.first()
            Links.incoming.value = Links.Link.Credits(song)
            shot("60-song-details-spotify", 6000)
            device.pressBack()
        }
        // Spotify's own search filters.
        step {
            Links.incoming.value = Links.Link.Search("artist:\"Rihanna\" year:2016")
            shot("61-search-artist-year-filter", 8000)
        }
    }

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
            instrumentation.runOnMainSync { if (app.player.state.value.isPlaying) app.player.togglePlayPause() }

            lyricSearch()
            spotifyPages()
            chatTour()
        }
    }
}
