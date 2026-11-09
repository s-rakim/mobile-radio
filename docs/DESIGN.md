# Design

## Where the look comes from

The visual rules come from the [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) design library. Its entry for music streaming apps asks for:

- **Style:** dark mode (OLED) with vibrant, block-based sections
- **Colour:** a near-black `#121212` base with vibrant accents, letting album artwork carry most of the colour
- **Type:** modern and bold
- **Must have:** a strong audio player. **Avoid:** cluttered layouts and a weak player

Its general checklist shaped the details too: vector icons only (no emoji), touch targets of at least 48dp, at least 4.5:1 contrast for text, skeleton loading states, and motion that means something (sheets slide, the player rises from the bottom, colours cross-fade between songs) rather than decoration.

## Colour tokens

Screens never use raw hex. They use the tokens in `SpitifyColors`, which are backed by Compose state, so changing theme recolours everything at once.

| Token | Spitify default | Used for |
| --- | --- | --- |
| Background | `#121212` | Screen background |
| Base | `#000000` | Under the tab bar |
| Elevated | 6% lighter than background | Sheets |
| Highlight | 11% lighter | Tiles, placeholders, pills |
| Pressed | 17% lighter | Pressed states |
| Divider | 12% lighter | Lines |
| Accent | `#2AD46B` | Play buttons, likes, active states, current song |
| OnAccent | black or white, whichever reads better | Text and icons on the accent |
| TextPrimary | `#FFFFFF` | Titles |
| TextSecondary | `#B3B3B3` | Subtitles, about 9:1 on the background |
| TextMuted | `#8C8C8C` | Footnotes, about 5.5:1 |

Surface steps are worked out from the background, so a theme only has to pick a background and an accent.

The accent is used sparingly. Most colour on screen comes from artwork: playlist and artist headers, the mini player and the full player all take a dark, saturated colour from the current cover.

## Themes

Settings → Theme offers:

| Theme | Background | Accent | Source |
| --- | --- | --- | --- |
| Spitify (default) | `#121212` | `#2AD46B` green | Black and green house style |
| Midnight | `#0F0F23` | `#22C55E` | ui-ux-pro-max Music Streaming palette |
| Sunset | `#0F0F23` | `#F97316` | ui-ux-pro-max Podcast Platform palette |
| Cinema | `#000000` | `#E11D48` | ui-ux-pro-max Video Streaming palette |
| Spotlight | `#0F0F23` | `#CA8A04` | ui-ux-pro-max Theater/Cinema palette |
| Studio | `#0F172A` | `#8B5CF6` | ui-ux-pro-max Music Creation palette |
| Neon | `#0F0F23` | `#F43F5E` | ui-ux-pro-max Gaming palette |
| Lagoon | `#0F172A` | `#14B8A6` | ui-ux-pro-max Productivity palette |
| Ember | `#1C1917` | `#F59E0B` | ui-ux-pro-max Fitness palette |
| Mono | `#000000` | `#F8FAFC` | ui-ux-pro-max Photography Studio palette |
| Custom | `#121212` or true black | any | You |

Custom gives you sixteen accent swatches, a hue slider for anything in between, and a true black switch. Text on the accent automatically flips between black and white for contrast.

The choice is saved and applied before the first frame on the next launch.

## Typography

[Figtree](https://github.com/erikdkennedy/figtree), a geometric sans with a friendly, bold feel, bundled as a variable font (SIL Open Font License). Headlines run ExtraBold with slightly tight tracking. Body text is 14 to 16sp, and nothing important drops below 11sp.

## Genre colours

Genre tiles and mix covers use full-strength colours pulled from the ui-ux-pro-max product palettes: orange `#EA580C` for hip-hop, cyan `#0891B2` for electronic, pink `#DB2777` for pop, teal `#0D9488` for lo-fi, red `#DC2626` for rock, gold `#A16207` for jazz and so on. Each tile is a short diagonal gradient (a touch lighter at the top left, deeper at the bottom right) so it reads as lit rather than flat. All of them keep white bold text at 3:1 or better. They live in `Genres.all` in `Models.kt`.

## Colour from artwork

Every cover gets measured once with AndroidX Palette and cached (`ArtworkColors` in `Artwork.kt`). It gives two colours: a vibrant one, clamped so white text stays readable, and a deeper one for the bottom of gradients. Grey covers stay grey instead of picking up a random tint.

Where it shows up:

- The full player background runs from the cover's main colour into its deep colour, then into the theme background.
- The mini player is a sideways gradient of the same two colours.
- Playlist and album headers use both colours, and the slim top bar that fades in takes a darker shade of the main one.
- Home's top glow follows whatever is playing, and falls back to the theme accent when nothing is.
- Lyrics cards sit between the two colours.

Colours cross-fade over about 600ms when the song changes, so the screen shifts mood rather than flashing.

## Motion

All motion settings live in `Motion.kt`, so the app moves with one feel.

- **Press feedback.** Cards, tiles, pills and round buttons shrink slightly while held and spring back on release (`springClick`). Track rows keep their ripple and add a very small shrink. The play button, skip buttons and bottom tabs push in further because they're the things you hit most.
- **Play and pause** swap with a quick scale and fade instead of a hard icon cut.
- **Likes** pop the icon when you save a song.
- **Pages** slide in from the right over a fading parent and slide back out on back. Switching tabs is a plain cross-fade.
- **Player.** It rises from the bottom on a soft spring with a slight scale. Pull down from the top of the page to tuck it away; it shrinks and rounds its corners as you drag and closes past a threshold or on a quick flick. The cover sits back a little while paused, tilts and follows your finger when you swipe to skip, and the next cover scales into place.
- **Mini player** slides up when something starts, follows a sideways swipe, and its title slides between songs.
- **Progress** glides between the four position updates a second, so the bar never steps. The seek thumb and track swell while you hold them.
- **Lists.** Rows, cards and tiles fade and lift in the first time they appear, with a short stagger capped at ten items. Scrolling back over them doesn't replay it.
- **Headers.** Playlist headers drift up slower than the list and fade out; artist photos scroll at half speed for depth.
- **Loading.** Skeletons have a soft highlight sweeping across them instead of blinking.

Nearly everything animates scale, position and opacity on the graphics layer, which keeps it smooth on cheaper phones.

## Layout notes

- 16dp side margins everywhere
- Cards on shelves are 148dp wide with 14dp gaps
- Song rows are at least 60dp tall with 48dp artwork
- The tab bar sits on a gradient from transparent to black so content fades out underneath it
- The mini player is 56dp tall with a 2dp progress line, inset 8dp from the edges
