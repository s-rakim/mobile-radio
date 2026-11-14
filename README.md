# Spitify

Spitify is an Android music streaming app built to feel like the streaming apps you already know: a home feed full of shelves, search with genre tiles, a library for everything you've saved, and a full-screen player that takes its colour from the album art.

Music comes from [Audius](https://audius.co), an open catalogue of independent artists, so there's real music to play without an account or API key. You can also play songs saved on your phone and your own [Navidrome](https://www.navidrome.org) or Subsonic server. See [docs/SOURCES.md](docs/SOURCES.md). Lyrics come from [LRCLIB](https://lrclib.net) when a song is listed there.

## Download

Each build started from Actions → Build APK on `main` makes a signed APK and publishes it on the [Releases page](../../releases/latest). Grab `Spitify-x.y.z.apk` on your phone and open it. Android will ask you to allow installs from your browser or file manager the first time.

Needs Android 8.0 (API 26) or newer. Works on phones and tablets: on a tablet or a wide window you get a side rail instead of the bottom bar, bigger grids, and the player puts the cover next to the controls in landscape.

## Windows

Spitify also runs as a web app you install from Edge or Chrome. Open **https://s-rakim.github.io/mobile-radio/**, then click **Install app** in the top bar (or the install icon at the right of the address bar). It gets its own window, Start menu entry and taskbar icon, and the media keys on your keyboard work with it. See [webapp/README.md](webapp/README.md).

## What's in it

**Home**
- Greeting, profile button and All / Music / Playlists / Artists filters
- Eight shortcut tiles for Liked Songs and whatever you opened recently
- Shelves for Recently played, Made for you (genre mixes), Trending this week, Popular playlists, Artists you might like, Top of the month and All-time greats

**Search**
- Browse all grid with a colour tile for every genre
- Live search across songs, artists and playlists with Top / Songs / Artists / Playlists filters
- Recent searches you can remove one by one or clear

**Your Library**
- Liked Songs pinned at the top, your playlists, saved playlists and followed artists
- Playlists / Artists / Downloaded filters, Recents / Recently added / Alphabetical sort, list or grid view
- Listening history

**Playlists, mixes and artists**
- Colour-washed headers, save to library, download, shuffle and play
- Make your own playlists, rename or delete them, add songs from anywhere, and get recommended songs underneath
- Artist pages with Popular tracks, follow button and an About card

**Player**
- Mini player that picks up the artwork colour. Tap to open, swipe to skip
- Full-screen player with seek bar, shuffle, repeat one / all, like, share and queue
- Swipe the artwork to change song
- Lyrics card that follows along with synced lyrics, and a full-screen lyrics view where tapping a line jumps to it
- Queue with "Next from" and remove buttons
- Sleep timer
- Background playback with notification and lock-screen controls

**Settings** (tap the profile circle)
- Theme: black and green by default, six more presets, or build your own accent
- Crossfade, autoplay, volume level, equaliser with presets and custom bands
- Data saver, download over cellular, private session
- Storage breakdown, remove downloads, clear cache, clear history

See [docs/FEATURES.md](docs/FEATURES.md) for the full list, including what isn't possible and why.

## Building it yourself

```bash
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. More detail, including signing, is in [docs/BUILDING.md](docs/BUILDING.md).

## Docs

- [Features](docs/FEATURES.md): what Spitify does, screen by screen
- [Architecture](docs/ARCHITECTURE.md): how the code is laid out
- [Design](docs/DESIGN.md): colours, type and the theme system
- [Building](docs/BUILDING.md): local builds, CI and signing
- [Sources](docs/SOURCES.md): Audius, Local Files and your own server
- [Branding](docs/BRANDING.md): the two app icons and how the switcher works
- [Web server](server/README.md): the optional Node backend and test page

## Credits

Built by Malcolm ([@s-rakim](https://github.com/s-rakim)).

Spitify is an independent project and isn't affiliated with or endorsed by Spotify. All music, artwork and artist information belongs to the artists and is served by the sources listed in docs/SOURCES.md.
