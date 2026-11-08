# Architecture

Spitify is a single-module Android app written in Kotlin with Jetpack Compose for the UI and Media3 (ExoPlayer) for playback. There's no backend of its own. It talks straight to the public Audius API and keeps everything personal on the device.

```
app/src/main/java/com/srakim/spitify/
├── SpitifyApp.kt          Application: builds the shared objects once
├── MainActivity.kt        Splash, edge-to-edge, notification permission, sets content
├── data/
│   ├── Models.kt          Track, Playlist, Artist, UserPlaylist, RecentItem, genres
│   ├── AudiusApi.kt       Catalogue client with a short in-memory cache
│   ├── LyricsApi.kt       LRCLIB lookup and LRC parsing
│   ├── LibraryStore.kt    Likes, playlists, follows, history (library.json)
│   ├── DownloadStore.kt   Offline files and their index
│   ├── SettingsStore.kt   App and playback settings
│   └── ThemeStore.kt      Selected theme and custom accent
├── playback/
│   ├── PlaybackService.kt MediaSessionService that owns ExoPlayer
│   ├── PlayerController.kt UI-side MediaController wrapper, one StateFlow
│   └── AudioEffects.kt    Equaliser and loudness for the player's session
└── ui/
    ├── SpitifyRoot.kt     Nav host, bottom bar, mini player, player overlay, sheets
    ├── AppState.kt        CompositionLocals, routes, AppActions, rememberLoad
    ├── theme/Theme.kt     Palettes, colour tokens, typography
    ├── components/        Artwork, rows, cards, pills, skeletons, buttons
    ├── sheets/            Mini player, song menu, add to playlist, create
    └── screens/           Home, Search, Library, collections, artist, player, settings
```

## Data

**AudiusApi** wraps the handful of endpoints Spitify needs: trending tracks (by week, month, all time, optionally by genre), trending playlists, search for tracks, users and playlists, playlist details and tracks, and user details and tracks. Every request adds `app_name=spitify`. Responses are cached in memory for five minutes, which is what makes moving back and forth between screens instant.

Items are decoded one at a time, so a single odd record in a response gets dropped instead of breaking the whole shelf.

Streaming URLs are `https://api.audius.co/v1/tracks/{id}/stream?app_name=spitify`, which redirect to a content node. OkHttp follows the redirect.

**LibraryStore** holds everything the listener owns in one `StateFlow<LibraryState>` and writes it to `files/library.json` in the background after every change. Writes go through a temp file and a mutex so a crash mid-write can't corrupt it.

**DownloadStore** saves streams to `files/downloads/{id}.mp3`, two at a time, and keeps an index next to them. It checks the "download using cellular" setting before starting.

**SettingsStore** and **ThemeStore** are small SharedPreferences wrappers.

## Playback

`PlaybackService` is a Media3 `MediaSessionService`. It builds ExoPlayer with:

- a `DefaultDataSource` so local download files and OkHttp streams both work
- music audio attributes with audio focus handling
- pause on headphone unplug
- a network wake lock
- a fixed audio session id so the equaliser can attach

Media items sent from a controller lose their URI on the way in, so the session callback rebuilds it: a downloaded file if one exists, otherwise the stream URL.

The service also runs the crossfade loop. Every 100 ms it looks at position and duration and ramps the volume down near the end of a song and up at the start of the next.

`PlayerController` lives in the app process and connects to the service with a `MediaController`. It exposes one `StateFlow<PlaybackState>` with the current track, play state, position, shuffle, repeat, the up-next list in real play order (shuffle aware), where playback started from, and the sleep timer. Calls made before the controller connects are queued and run once it does.

It also handles:

- recording plays to history, unless private session is on
- autoplay, topping up the queue with similar songs when it's about to run out
- skipping past songs that fail to load
- the sleep timer

## UI

Compose all the way through, Material 3 underneath with custom components on top.

Shared objects reach screens through `CompositionLocal`s (`LocalApi`, `LocalLibrary`, `LocalPlayer` and so on), provided once in `SpitifyRoot`.

`AppActions` is the one place screens go to navigate, open sheets or show a toast. Detail routes are registered once per tab (`home/playlist/{id}`, `search/playlist/{id}`, ...) and navigation always goes to the current tab's copy, which is how each tab keeps its own back stack.

Loading uses a small `rememberLoad(keys) { ... }` helper that gives back Loading, Ready or Failed plus a retry function. Screens show skeletons while loading and a retry button on errors.

The full player is an overlay above the nav host rather than a destination, so it slides up over whatever you were looking at and drops back down to the same place.

`rememberArtworkColor` pulls a dark vibrant colour out of a cover with the Palette library, clamps it so white text stays readable, and animates between songs. Headers, the full player and the mini player all use it.

## Libraries

| Library | Used for |
| --- | --- |
| Jetpack Compose + Material 3 | UI |
| Navigation Compose | Screens and back stacks |
| Media3 ExoPlayer + Session | Playback, notification, lock screen |
| OkHttp | Networking for API, images and audio |
| kotlinx.serialization | JSON |
| Coil | Images |
| AndroidX Palette | Artwork colours |
| Core SplashScreen | Launch screen |
