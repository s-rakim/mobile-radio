# Branding

The current icon is a placeholder: a green circle with four sound bars. Swapping in the real Spitify logo takes a couple of minutes.

## Where the logo is used

| File | What it is |
| --- | --- |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The logo on the home screen icon and the splash screen |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | One-colour version for Android 13+ themed icons |
| `app/src/main/res/values/colors.xml` → `ic_launcher_background` | Colour behind the logo |

The launcher icon itself (`res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`) just points at those, so you don't need to touch it.

## Option 1: Android Studio (easiest)

1. Right-click `app/src/main/res` → New → Image Asset
2. Icon type: Launcher Icons (Adaptive and Legacy)
3. Name: `ic_launcher`
4. Foreground layer: pick your logo file (SVG or PNG). Use the resize slider to keep it inside the safe zone circle
5. Background layer: colour `#121212`, or whatever suits the logo
6. Finish. It overwrites the placeholder

## Option 2: by hand

1. Export the logo as a 432×432 px PNG with a transparent background, with the mark itself inside the middle 288×288 px (Android crops the edges into circles, squircles and so on)
2. Delete `app/src/main/res/drawable/ic_launcher_foreground.xml`
3. Save the PNG as `app/src/main/res/drawable/ic_launcher_foreground.png`
4. Optionally do the same for `ic_launcher_monochrome` with a white-only version
5. Change `ic_launcher_background` in `colors.xml` if you want a different backdrop

If the logo is an SVG, Android Studio's File → New → Vector Asset turns it into a drawable XML you can drop in as `ic_launcher_foreground.xml` instead.

Push to `main` and the next APK on the Releases page has the new icon.
