# Spitify on Windows (web app)

The desktop version of Spitify. It's plain HTML, CSS and JavaScript with no build step, and it installs as an app from Edge or Chrome.

## Install it

1. Open https://s-rakim.github.io/mobile-radio/ in Edge or Chrome.
2. Click **Install app** in the top bar. In Edge you can also use the menu: Apps, then Install Spitify.
3. Spitify opens in its own window and shows up in the Start menu. Pin it to the taskbar like any other app.

Updates arrive by themselves: every push to `main` that touches `webapp/` redeploys the site, and the installed app picks it up the next time it starts.

## What's in it

- Home with your recent stuff, trending songs, genre mixes, popular playlists, artists, top of the month and all-time greats
- Search as you type, with a top result, songs, artists and playlists, plus a Browse all grid of genres
- Playlist, mix and artist pages with a header coloured from the artwork
- Your Library down the left: Liked Songs, your own playlists, saved playlists, followed artists
- Player bar with shuffle, repeat, seek and volume, and a right panel for Now playing, synced lyrics (from LRCLIB) or the queue
- Right-click any song to queue it, like it, add it to a playlist or jump to the artist. Double-click to play
- The same ten themes as the phone app
- Keyboard: Space play/pause, Ctrl+Left/Right previous/next, Ctrl+Up/Down volume, Ctrl+S shuffle, Ctrl+R repeat, Ctrl+K search, Alt+Left/Right back/forward
- Windows media keys and the volume flyout control it, because it uses the browser's media session

Music comes from Audius only. Likes, playlists and history are stored in the browser on that computer, so they don't sync with the phone.

## Run it locally

Any static server works:

```bash
cd webapp
python -m http.server 8000
```

Then open http://localhost:8000. Installing needs HTTPS, so try that part on the deployed site.

## Publishing

`.github/workflows/webapp.yml` deploys to GitHub Pages. Pages has to be set to **GitHub Actions** once, under Settings, Pages, Build and deployment, Source.
