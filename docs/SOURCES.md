# Where the music comes from

| Source | Needs | What it has |
| --- | --- | --- |
| Audius | Nothing | Independent artists. Always on |
| Local Files | Permission to read music on the phone | Anything you've copied or downloaded to the device |
| Your server | Navidrome or any Subsonic server, plus your login | Your own collection |

Search waits on Audius only. Local songs are already in memory, so matching them costs nothing. Your server is asked only if you've set one up, and only for three seconds, so a server you can't reach from where you are doesn't hold results up. Your own music is listed first.

## Why mainstream hits don't show up

Audius doesn't license major-label music, so a search for a chart song usually finds covers, remixes or nothing. To play songs you already own, put the files on your phone (they show up under Local Files) or on your own server.

## How Audius songs start quickly

Every Audius track in an API response comes with a direct link to the file on a storage node. Spitify starts playback from that link. The regular `/v1/tracks/{id}/stream` address goes through a redirect first, and in testing that took about twice as long before audio arrived (around 2 to 3 seconds against about 1). Direct links can go stale, so the regular address rides along in the URI fragment and `FallbackDataSource` switches to it if the direct link fails. The player also uses a 6 second connect timeout, so a dead node gives up quickly instead of stalling the song.

## Local Files

Settings → Local files → "Show audio files from this device". Spitify reads Android's media library (MediaStore), so it sees the same songs your phone's music app does. The first time, Android asks for the Music and audio permission. Local songs appear in Your Library as a pinned "Local Files" playlist and at the top of Search. They play, queue, shuffle and go into playlists like any other song.

## Your server (Navidrome / Subsonic)

Settings → Music sources → Your server. Enter the address (for example `http://192.168.1.20:4533` or a Tailscale name), username and password. Spitify checks the login and shows "Connected" when it works.

It uses the Subsonic API with token auth, so the password itself is never sent. Each request carries `t = md5(password + salt)` with a fresh random salt. The endpoints it calls:

- `ping` checks the login
- `search3` feeds Search
- `getAlbumList2`, `getPlaylists`, `getPlaylist`, `getAlbum`, `getArtist` and `getStarred2` feed the "Your server" page in Your Library
- `stream` plays songs and `getCoverArt` loads cover art

Plain HTTP is allowed in the network security config because a home server usually isn't on HTTPS.

## Sources that were tried and taken out

Version 1.0.29 also searched the Internet Archive and ccMixter, and had optional Jamendo and SoundCloud. Searching took noticeably longer, because results waited on the slowest source; the Internet Archive alone needs four requests per search. Those catalogues mostly add obscure music anyway, so they were removed in the next version.
