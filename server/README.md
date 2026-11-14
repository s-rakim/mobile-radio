# Spitify web server

An Express server and a small test page that searches several catalogues at once, plus YouTube. The Android app works without it, but if you add the server's address under Settings → Music sources → Spitify server, the app gets YouTube, Internet Archive, ccMixter and Jamendo results too.

It runs on my own machine so YouTube sees a normal home IP, and a Cloudflare Tunnel gives it a public HTTPS URL without opening router ports.

## Running it (Docker)

```bash
cd server
docker compose up -d --build
docker compose logs tunnel
```

The tunnel logs print a `https://some-random-words.trycloudflare.com` address. Open it and the search page loads from there.

That address changes whenever the tunnel container restarts. For a fixed one, create a named tunnel on a Cloudflare account with your own domain and change the `tunnel` service command to `tunnel run --token <your-token>`.

To turn Jamendo on, put `JAMENDO_CLIENT_ID=...` in a `.env` file next to `docker-compose.yml`.

## Running it without Docker (Windows)

Needs Node 20+, Python 3 and Deno.

```powershell
cd server
pip install -U yt-dlp yt-dlp-ejs
npm install
npm start                                   # http://localhost:3000
cloudflared tunnel --url http://localhost:3000   # second window, for a public URL
```

Install cloudflared with `winget install Cloudflare.cloudflared` if you don't have it.

## Routes

All search routes take `?q=` and answer 400 without it. The catalogue routes give up after 10 seconds and return an empty result instead of failing.

| Route | Source |
| --- | --- |
| `/api/youtube` | YouTube through yt-dlp, full tracks streamed via `/stream/:id` |
| `/api/soundcloud` | SoundCloud through yt-dlp, played via `/stream/:id` like YouTube |
| `/api/audius` | Audius search (api.audius.co) |
| `/api/archive` | Internet Archive netlabels and live shows |
| `/api/ccmixter` | ccMixter Creative Commons tracks |
| `/api/deezer` | Deezer's official API, 30 second previews only |
| `/api/jamendo` | Jamendo, only with `JAMENDO_CLIENT_ID` set |
| `/stream/:id` | Plays a YouTube result, with range support for seeking. `410` means search again. |
| `/health` | Returns `ok` |

Each source shows up on the page as soon as it answers. Searches are cached for 10 minutes (YouTube for a day), two identical searches at once share one request, and a source that keeps failing, like ccMixter when it's down, is skipped for 5 minutes instead of slowing every search.

## Reaching it from the phone

Two options for the address you give the app:

- Your PC's Tailscale address, e.g. `http://100.101.102.103:3000` (`tailscale ip -4` shows it). It never changes, but Tailscale has to be on on the phone.
- The `trycloudflare.com` link from the tunnel logs. Works without Tailscale, but changes whenever the tunnel restarts.

YouTube songs saved in the app (likes, downloads) keep working after the address changes. The app only stores the song's id and asks whatever server address is set today, and the server fetches a fresh stream when an old one has expired.

## Accounts

The app's Settings → Account page logs in to this server, and your playlists, liked songs, followed artists and saved playlists are kept here. Log in on a new phone or after a reinstall and they come back. Play history stays on each phone.

Accounts and libraries are plain JSON files in the `spitify-data` Docker volume (passwords are scrypt hashes). Anyone who can reach the server can create an account, so once yours exists you can close sign-ups by putting `ALLOW_SIGNUPS=false` in `server/.env` and running `docker compose up -d`.

| Route | What it does |
| --- | --- |
| `POST /api/auth/register` | `{username, password}`, returns a token |
| `POST /api/auth/login` | same, for an existing account |
| `POST /api/auth/logout` | signs this device out |
| `GET /api/library` | your saved library (send `Authorization: Bearer <token>`) |
| `PUT /api/library` | saves it |

## Why YouTube audio goes through the server

YouTube's stream URLs only work from the IP that requested them. The server fetched it, so the server has to serve it.

## Cookies

Optional. From a home IP YouTube usually doesn't ask. If searches start failing with "sign in to confirm you're not a bot", export cookies from a throwaway account into `cookies.txt` next to `youtube_fetch.py`. It's in `.gitignore`.

## Environment variables

| Name | What it does |
| --- | --- |
| `PORT` | Defaults to `3000` |
| `JAMENDO_CLIENT_ID` | Turns the Jamendo route on |
| `PUBLIC_URL` | Forces the base URL for YouTube stream links. Normally worked out per request. |
| `YTDLP_POT_PROVIDER_URL` | bgutil PO token server. Set by docker-compose. |
| `YT_PROXY` | Routes yt-dlp through a proxy. Empty means direct. |
| `PYTHON` | Python command. Defaults to `python` on Windows, `python3` elsewhere. |
