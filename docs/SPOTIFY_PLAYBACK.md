# Playing Spotify songs in Spitify

Spitify can search Spotify's catalogue and hand songs to the Spotify app on the same phone,
using Spotify's official App Remote SDK. The music plays inside the Spotify app; Spitify picks
the songs, shows them, and controls play, pause, skip and seek.

## What it needs

- The **Spotify app** installed and logged in on the phone.
- **Spotify Premium.** Spotify only lets Premium accounts choose a specific song. This is
  checked by Spotify's own servers and Spitify doesn't try to get around it.
- A **Spotify developer app** (free) with Spitify registered on it.

When any of those is missing, Spotify songs still play: Spitify finds the closest match in its
own sources (Audius, Jamendo, Internet Archive and the rest) and plays that instead, with a
short note saying why. The screens look and work the same either way.

## One-time setup

1. Go to developer.spotify.com/dashboard and create an app (or open the one you already use for
   playlist import).
2. Under **Redirect URIs** add both:
   - `spitify://spotify-auth` (sign-in for search and import)
   - `spitify://spotify-remote` (connecting to the Spotify app)
3. Under **Android packages** add:
   - Package name `com.srakim.spitify`
   - The SHA-1 fingerprint of the key the APK is signed with. For the release key:
     `keytool -list -v -keystore spitify-release.jks -alias <alias>` and copy the SHA1 line.
4. Tick **Android** and **Web API** under APIs used, and save.
5. Save the Client ID as the `SPOTIFY_CLIENT_ID` repository secret (Settings → Secrets and
   variables → Actions → Secrets → New repository secret) so every build has it, or type it
   into the app under Settings → Devices → Spotify app id. A `SPOTIFY_CLIENT_ID` variable
   still works if no secret is set.

While the developer app is in development mode, only accounts you add under **User Management**
on the dashboard can use it.

## In the app

- Settings → Devices → **Connect to Spotify** asks the Spotify app for permission once.
  After that Spitify reconnects by itself whenever it opens.
- Settings → Devices → **Play through Spotify** turns the whole thing off if you'd rather
  always hear Spitify's own sources.
- Sign in to Spotify (Settings → Devices → Import from Spotify → Sign in) and Spotify songs
  show up in Search next to everything else. They say "On Spotify" under the title in the player,
  with a link back to the song on Spotify.

## When the Spotify app can't play

Spitify never waits on the Spotify app. If it isn't connected, the account isn't Premium, or
the app refuses a song, the song plays on Spitify's own player from the first of its sources
that has it: songs on the phone, your own server, Audius, Jamendo, Internet Archive, ccMixter
and SoundCloud. A short note shows when it's a close match rather than the same song, or
another song by the same artist because nobody had that one. Spotify's own audio only ever
plays inside the Spotify app.

If Spotify won't connect, the reason shows under **Connect to Spotify**. The usual ones:

- *Spotify needs you to allow Spitify once*: Spitify now opens that screen by itself the first
  time; you can also tap Connect to Spotify.
- *Spotify doesn't recognise this app*: the dashboard needs the Android package
  `com.srakim.spitify` with the release key's SHA-1, and your account under User Management.
- *Redirect URI*: `spitify://spotify-remote` has to be listed next to `spitify://spotify-auth`.

## How the build gets the SDK

Spotify publishes the App Remote SDK as an `.aar` on its GitHub releases page rather than on
Maven, so it isn't committed here. The Build APK workflow runs `scripts/fetch-spotify-sdk.sh`,
which downloads it into `app/libs/`. Gradle then compiles `app/src/spotify/` (the real remote
control). If the download fails, Gradle compiles `app/src/nospotify/` instead, a stand-in that
reports "built without Spotify playback", and the APK still builds.

To build locally with it: install the GitHub CLI, then run `scripts/fetch-spotify-sdk.sh`.

## Rules worth knowing

Spotify's developer terms apply to anything that comes from Spotify: don't cache or download
Spotify audio, keep the link back to Spotify, don't use Spotify content to train models, and
don't use Spotify's name or logo as if Spitify were theirs. Spitify keeps its own name, logo
and artwork.
