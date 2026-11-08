# Your own music server (Navidrome)

Spitify can play your own music alongside Audius. The songs live on your computer, [Navidrome](https://www.navidrome.org) serves them, and the app talks to it over the Subsonic API.

Navidrome only plays files that are already in your music folder. It doesn't download anything from SoundCloud, YouTube or anywhere else.

## What you need

- Docker Desktop (Windows / macOS) or Docker Engine (Linux)
- A folder of music files: MP3, FLAC, M4A, OGG, OPUS or WAV

## Start it

```sh
cd server/navidrome
cp .env.example .env      # on Windows: copy .env.example .env
```

Open `.env` and point `MUSIC_DIR` at your music folder. Then:

```sh
docker compose up -d
```

Open http://localhost:4533 in a browser. The first visit asks you to create an admin account. That username and password is what you'll type into Spitify later.

Navidrome scans the folder on start and again every hour. To pick up new songs straight away, open the web page, go to the activity icon in the top right and hit **Quick scan**.

Your database, play counts and settings live in `server/navidrome/data`, so they survive restarts and image updates.

## Connect Spitify

1. Find your computer's local IP. Windows: `ipconfig`, look for IPv4 Address. macOS / Linux: `ip addr` or System Settings → Network. It looks like `192.168.1.20`.
2. In Spitify, open **Settings → Your server**.
3. Server address: `http://192.168.1.20:4533`
4. Enter the username and password you made, then tap **Test connection**.

Your phone has to be on the same Wi-Fi as the computer for this address to work.

If it won't connect, check that the computer's firewall allows port 4533. On Windows, the first time Docker opens the port you'll usually get a prompt; allow it on private networks.

## Listening away from home (Tailscale)

Tailscale puts your phone and computer on a private network without opening ports on your router.

1. Install [Tailscale](https://tailscale.com/download) on the computer running Navidrome and on your phone, and sign in with the same account on both.
2. On the computer, run `tailscale ip -4`. You'll get an address like `100.101.102.103`.
3. In Spitify, set the server address to `http://100.101.102.103:4533`.

That address works on Wi-Fi and mobile data, as long as Tailscale is switched on on the phone.

Don't forward port 4533 on your router to expose Navidrome to the open internet. If you really want a public address, put it behind a reverse proxy with HTTPS (Caddy is the easiest) and use a strong password.

## Getting music into the folder

Anything you own works: songs you bought, CD rips, old MP3s. Some places to get files legitimately:

- **Bandcamp**: buying a track or album gives you MP3 or FLAC downloads. Plenty of artists also offer name-your-price releases.
- **SoundCloud**: when an artist has turned on the **Download** button under a track, you can save the file and drop it in.
- **Free Music Archive** and **Jamendo**: tracks released under Creative Commons with downloads allowed.

Keep files tidy, roughly `Artist/Album/01 - Title.mp3`, and make sure the tags (title, artist, album, cover art) are filled in. Navidrome goes by the tags, not the file names. [MusicBrainz Picard](https://picard.musicbrainz.org) fixes messy tags in bulk.

## Updating

```sh
docker compose pull
docker compose up -d
```

## Stopping

```sh
docker compose down
```

This keeps your data folder. Delete `server/navidrome/data` only if you want to start over.
