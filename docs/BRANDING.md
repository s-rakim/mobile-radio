# Branding

Spitify ships with two launcher icons, both drawn from Malcolm's logo. Pick one in Settings → Content and display → App icon. The change applies straight away; some launchers take a few seconds to refresh.

| Name | Look | Resources |
| --- | --- | --- |
| Spitify (default) | The yellow face with headphones and a click-wheel player | `mipmap-*/ic_launcher*.png`, `ic_launcher_foreground` |
| Classic | The red-headphones version | `mipmap-*/ic_launcher_classic*.png`, `ic_launcher_classic_foreground` |

Both sit on a warm cream background, `#FFF4DC`, set in `res/values/ic_launcher_background.xml`. The splash screen uses the default icon's foreground.

## How switching works

Android has no API for changing an app's icon, so the manifest declares the launcher entry twice, as two `activity-alias` elements pointing at `MainActivity`:

- `.LauncherDefault`, enabled
- `.LauncherClassic`, disabled

Choosing an icon enables one alias and disables the other with `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)`. That's all in `data/AppIcons.kt`. The previews in Settings use `drawable-nodpi/spitify_logo.png` and `spitify_logo_classic.png`.

## Adding a third icon

1. Generate the mipmaps (Android Studio → New → Image Asset works) and name them `ic_launcher_<name>` and `ic_launcher_<name>_round`
2. Add an `activity-alias` named `.Launcher<Name>` to `AndroidManifest.xml`, copied from `.LauncherClassic`, with `android:enabled="false"`
3. Add an entry to the `AppIcon` enum in `AppIcons.kt` with the alias, a label and a preview drawable

The full-size logo files live in `assets/`.
