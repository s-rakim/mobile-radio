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
| Mono | `#000000` | `#F8FAFC` | ui-ux-pro-max Photography Studio palette |
| Custom | `#121212` or true black | any | You |

Custom gives you sixteen accent swatches, a hue slider for anything in between, and a true black switch. Text on the accent automatically flips between black and white for contrast.

The choice is saved and applied before the first frame on the next launch.

## Typography

[Figtree](https://github.com/erikdkennedy/figtree), a geometric sans with a friendly, bold feel, bundled as a variable font (SIL Open Font License). Headlines run ExtraBold with slightly tight tracking. Body text is 14 to 16sp, and nothing important drops below 11sp.

## Genre colours

The genre tiles and mix covers use a hand-mixed set of earthy, slightly muted colours (rust for hip-hop, deep blue for electronic, rose for pop, sage for lo-fi and so on), so the tiles look like a set and don't fight the artwork. They live in `Genres.all` in `Models.kt`.

## Layout notes

- 16dp side margins everywhere
- Cards on shelves are 148dp wide with 14dp gaps
- Song rows are at least 60dp tall with 48dp artwork
- The tab bar sits on a gradient from transparent to black so content fades out underneath it
- The mini player is 56dp tall with a 2dp progress line, inset 8dp from the edges
