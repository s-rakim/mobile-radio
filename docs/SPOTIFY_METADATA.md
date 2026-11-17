# Spotify info in Spitify

Once you sign in with Spotify (Settings → Apps and devices), artist pages, album pages and
song details come from Spotify. The music itself still plays from your own server first
(see SPOTIFY_PLAYBACK.md).

## What comes from where

| Part | Source |
| --- | --- |
| Artist photo, name, genres | Spotify `GET /artists/{id}` |
| Popular songs | Spotify search for `artist:"Name"`, keeping only songs the artist is credited on, in Spotify's order |
| Albums, singles, compilations, Appears on | Spotify `GET /artists/{id}/albums`, 10 per page; album search if that fails |
| Works with | Other artists on their songs and on the records they appear on |
| About | Opening paragraph of the artist's Wikipedia article, when there is one about a musician |
| Song details | Spotify `GET /tracks/{id}`: every credited artist, album, release date, track and disc number, explicit, ISRC. Songs from your server are matched on Spotify by title and artist first |
| Lyrics on Now Playing | LRCLIB |
| Find a song by its lyrics | Lyrics of your own songs kept on the phone (works offline), plus Genius and LRCLIB online |

If an artist from your server, Audius or Jamendo has a Spotify artist with the same name, their
page opens as the Spotify page.

## What Spotify doesn't allow

Spotify cut the Web API for apps in development mode in February 2026. Spitify's Spotify app is
in development mode, so these are closed to it:

- Artist top tracks (`/artists/{id}/top-tracks`). Popular songs are rebuilt from search instead.
- Related artists ("Fans also like"), recommendations, audio features and audio analysis
  (tempo, key, energy, danceability).
- Follower counts and popularity on artists, songs and albums. Record labels on albums.
- New releases, browse categories and Spotify's editorial and made-for-you playlists.
- Lyrics: Spotify's API has never offered them.
- More than 10 search results per call. Spitify asks for several pages instead.
- Other users' profiles and playlists.

## Search

Search looks in every source at once. The filters are Top, Songs, Artists, Albums, Playlists,
Lyrics, Genres & moods and Radio. With Spotify signed in, Spotify's own search syntax works too,
for example `artist:"Rihanna" year:2016`, `album:"Anti"`, `genre:afrobeats`, `year:1990-1999`
and `tag:new`. Other sources get the plain words.

Typing four or more words also looks the words up as lyrics and shows "Songs with these lyrics"
under the top songs.
