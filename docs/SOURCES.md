# Where the music comes from

Search asks every source at once and merges the answers. Your own music comes first (songs on the phone, then your server), and the open catalogues take turns after that so none of them buries the others. If one source is slow or down, the rest still show.

| Source | Needs | What it has |
| --- | --- | --- |
| Local Files | Permission to read music on the phone | Anything you've copied or downloaded to the device |
| Your server | Navidrome or any Subsonic server, plus your login | Your own collection |
| Audius | Nothing | Independent artists. The biggest catalogue here |
| Internet Archive | Nothing | Netlabel albums (Creative Commons) and live shows from the etree collection |
| ccMixter | Nothing | Creative Commons remixes and originals |
| Jamendo | Your own client ID | Creative Commons music. Hidden until an ID is entered |
| SoundCloud | Your own app keys | Tracks SoundCloud marks as playable. Hidden until keys are entered |

Everything above is free and legal to stream. Each source can be switched off in Settings → Music sources.

## Why mainstream hits don't show up

None of the open catalogues license major-label music. Searching for a chart song usually finds covers, remixes or nothing at all. To play songs you already own, put the files on your phone (they appear under Local Files) or on your own server.

## Local Files

Settings → Local files → "Show audio files from this device". Spitify reads Android's media library (MediaStore), so it sees the same songs your phone's music app does. The first time, Android asks for the Music and audio permission. Local songs show up in Your Library as a pinned "Local Files" playlist and at the top of Search results. They play, queue, shuffle and go into playlists like any other song. They can't be downloaded because they're already on the phone.

## Your server (Navidrome / Subsonic)

Settings → Music sources → Your server. Enter the address (for example `http://192.168.1.20:4533` or a Tailscale name), username and password. Spitify checks the login straight away and shows "Connected" when it works.

Under the hood it uses the Subsonic API with token auth, so the password itself is never sent: each request carries `t = md5(password + salt)` and a fresh random salt. Endpoints used: `ping`, `search3` (Search), `getAlbumList2`, `getPlaylists`, `getPlaylist`, `getAlbum`, `getArtist`, `getStarred2` (the "Your server" page in Your Library), `stream` and `getCoverArt`.

Plain HTTP is allowed in the network security config because a home server usually isn't on HTTPS. The public catalogues are all HTTPS regardless.

## Internet Archive

Only two collections are searched: `netlabels`, where labels release under Creative Commons, and `etree`, live recordings from bands that allow taping and trading. Each archive item is an album. Its MP3 files are the tracks, streamed from `archive.org/download/…`. Search with `advancedsearch.php`, album details with `/metadata/{identifier}`, covers from `/services/img/{identifier}`. No key.

## ccMixter

`ccmixter.org/api/query` returns uploads with direct MP3 links. Spitify picks the main mix and skips stems and a cappellas. No key.

## Jamendo

Needs a client ID from [devportal.jamendo.com](https://devportal.jamendo.com). The old shared ID stopped working, so Jamendo now stays hidden until you add your own under Settings → Music sources → Jamendo client ID.

## SoundCloud

Uses the official API only, with an app's client ID and secret (OAuth client credentials). Registering an app at soundcloud.com/you/apps currently needs an Artist Pro account, so this stays off unless you have keys. When it's on:

- only tracks marked playable are listed
- the uploader is credited and the player links back to the track on SoundCloud
- stream addresses are fetched from `/tracks/{id}/streams` right before playback, never stored
- nothing from SoundCloud can be downloaded

## Sources that were looked at and left out

From fmhy.net/audio and elsewhere:

- **Free Music Archive.** Its public API was shut down, so there's nothing to call.
- **Tubidy, JioSaavn mirrors, YouTube rippers.** They serve copies of commercial music without a license.
- **Bandcamp.** No public API. The only way in is scraping, which their terms don't allow.
- **Pixabay Music, Incompetech, Musopen.** No open API for music.
