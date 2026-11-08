# Features

A screen-by-screen tour of what Spitify does today, plus the things a big streaming service does that Spitify can't, and why.

## Navigation

Four tabs along the bottom: Home, Search, Your Library and Create. Each tab keeps its own back stack, so you can dig into a playlist from Home, hop over to Search, and come back to exactly where you were. Tapping the tab you're already on takes you back to its top page.

Create opens a sheet for making a new playlist.

The mini player floats above the tabs whenever something is playing.

## Home

- Profile circle (opens Settings) and filter pills: All, Music, Playlists, Artists
- A time-of-day greeting, with your name if you've set one in Settings
- Up to eight shortcut tiles. Liked Songs comes first, then the playlists, mixes and artists you opened most recently. New installs get popular playlists here instead
- Shelves:
  - Recently played (with Show all)
  - Made for you: a mix for each genre
  - Trending this week
  - Popular playlists
  - Artists you might like
  - Top of the month
  - All-time greats
  - Your saved playlists

Tapping a song on a shelf plays that whole shelf from that song.

## Search

The Search tab shows a search box and a Browse all grid. Every genre tile has its own colour and a tilted cover from the top song in that genre.

Tapping the box opens the search page:

- Results update as you type
- Top shows an exact artist match if there is one, the first few songs, then artist and playlist shelves
- Songs, Artists and Playlists show the full lists
- Recent searches are saved when you open a result or press search on the keyboard. Remove them one by one or clear them all

## Your Library

- Liked Songs is pinned to the top
- Playlists you made, playlists you saved and artists you follow
- Filters: Playlists, Artists, Downloaded
- Sort: Recents, Recently added, Alphabetical
- List or grid view
- History button for everything you've played
- Plus button to make a playlist

## Collections

Playlists, mixes, Liked Songs, history and downloads all share one layout:

- Header washed in the artwork's colour, which slides away as you scroll and leaves a slim title bar
- Save to library (for playlists from the catalogue)
- Download the whole thing
- A menu with Add to queue and Share
- Shuffle toggle and a big play button that turns into pause when that collection is playing

### Your own playlists

- Make one from Create, the library's plus button, or "New playlist" when adding a song
- Rename and delete from the menu
- Remove songs from the song menu
- Covers become a 2x2 grid once there are four different covers
- A Recommended songs section underneath, based on the playlist's main genre, with one-tap add and a Refresh button

### Artist pages

- Large header image with the artist's name
- Follower count and Follow button
- Popular: top five songs by plays, expandable to ten, then all songs
- About card with picture, followers, track count and bio

## Song menu

Long-press any song or tap its three dots:

- Add to / remove from Liked Songs
- Add to playlist
- Remove from this playlist (in your own playlists)
- Play next
- Add to queue
- Download or remove the download
- Go to artist
- Go to the song's genre mix
- Share

## Player

**Mini player:** artwork, title, artist, like, play/pause and a thin progress line. The background takes its colour from the artwork. Tap it to open the full player or swipe it sideways to skip.

**Full player:**

- "Playing from" line naming the playlist, mix, artist or search you started from
- Large artwork. Swipe it left or right to change song
- Scrolling title for long names. Tap the artist name to open their page
- Like button
- Seek bar with elapsed and total time
- Shuffle, previous, play/pause, next, and repeat (off, all, one)
- Share and Queue buttons
- Lyrics card below the controls. With synced lyrics the current line lights up. Tap the card for full-screen lyrics, where tapping a line jumps there
- About the artist card
- Menu with Add to playlist, Sleep timer (5 to 60 minutes, shows time left) and Go to artist

**Queue:** the song playing now, then everything coming up in play order. Tap a song to jump to it, or the X to remove it.

**Background playback:** music keeps playing with the screen off. Notification, lock screen, Bluetooth and headset controls all work. Unplugging headphones pauses playback.

## Settings

Open it from the profile circle on any main tab.

**Profile**
- Your name, used in the Home greeting

**Content and display**
- Theme (see [DESIGN.md](DESIGN.md))
- Show lyrics on or off

**Playback**
- Crossfade, 0 to 12 seconds
- Gapless playback (always on)
- Autoplay: when your queue runs out, Spitify adds similar songs from the same genre
- Sleep timer status

**Audio quality**
- Volume level: Loud, Normal or Quiet
- Equaliser: on/off, the phone's built-in presets, or custom bands. There's also a shortcut to the phone's own audio settings where it has them
- Streaming quality (information only, see below)

**Data saving and offline**
- Data saver: loads smaller artwork
- Download using cellular: off by default, so downloads wait for Wi-Fi

**Privacy**
- Private session: nothing you play gets added to history
- Clear listening history
- Clear recent searches

**Storage**
- Bar showing space used by downloads and cache
- Downloads list
- Remove all downloads
- Clear cache

**About**
- Version, where the music and lyrics come from, and who made it

## Offline

Download a single song from its menu, or a whole playlist, mix, artist or Liked Songs with the download button. Downloaded songs play from the phone with no connection and show a filled download icon. Downloads run while the app is open.

## Music sources and Local Files

- Local Files: songs saved on the phone, pinned in Your Library and first in Search
- Your server: Navidrome or any Subsonic server, with its own page in Your Library and a Home shelf
- Internet Archive netlabel albums and live recordings, on Home and in Search
- ccMixter remixes on Home and in Search
- Jamendo and SoundCloud, each hidden until you add your own keys
- A credit line on the player saying where a song came from, with a link back

Details are in [SOURCES.md](SOURCES.md).

## What Spitify can't do

These depend on a big service's own servers, accounts or licensing deals, so there's no way to rebuild them on top of an open catalogue:

- **Connect / casting to other devices running the same app, Jam and group sessions, Blend, friend activity.** These need user accounts and real-time servers.
- **An AI DJ, daylists and personalised recommendations based on your taste across millions of listeners.** Spitify's mixes and recommendations are genre and trend based.
- **Podcasts and audiobooks.** Audius is a music catalogue.
- **Premium tiers, ads and account sync across devices.** Everything you save lives on your phone.
- **Choosing audio quality.** Audius serves one stream per song (MP3, up to 320 kbps), so there's nothing to choose between. The row in Settings says so rather than offering a fake picker.
- **Lyrics for every song.** LRCLIB is community run and mostly covers commercially released music, so plenty of independent tracks won't have lyrics. The lyrics card only appears when lyrics are found.
- **True overlapping crossfade.** Spitify uses one audio player, so songs fade out and in rather than overlapping.
- **Volume normalisation from loudness metadata.** Audius doesn't publish loudness values. The Volume level setting boosts or lowers output instead.
