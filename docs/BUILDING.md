# Building

## Requirements

- JDK 17
- Android SDK with platform 35 (Android Studio installs this for you)

## Local builds

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

The debug build installs next to the release build (it uses the `.debug` package suffix), so you can keep both.

Release builds are shrunk with R8, which makes them much smaller and smoother than debug builds. Use release when judging how the app feels.

## CI

`.github/workflows/build.yml` runs on every push to `main`, on pull requests and on demand from the Actions tab. It:

1. sets up JDK 17 and the Android SDK
2. builds the release APK
3. uploads it as a workflow artifact
4. on pushes to `main`, publishes a GitHub release tagged `v1.0.<run number>` with the APK attached

Pushes that only touch Markdown or `docs/` don't trigger a build.

The version code is the run number, so every release can install over the previous one.

## Signing

No signing key is kept in the repo. `keystore/` and `*.jks` are in `.gitignore`, so a key can't be committed by accident.

### CI

CI signs release builds with a key stored in the repo's Actions secrets. Set it up once:

1. Make a keystore on your own machine and keep it somewhere safe, outside the repo:
   ```bash
   keytool -genkeypair -v -keystore spitify-release.jks -alias spitify \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In the repo's Settings → Secrets and variables → Actions, add:
   - `SPITIFY_KEYSTORE_BASE64`: output of `base64 -w0 spitify-release.jks` (on macOS, `base64 -i spitify-release.jks`)
   - `SPITIFY_KEYSTORE_PASSWORD`
   - `SPITIFY_KEY_ALIAS` (`spitify` if you used the command above)
   - `SPITIFY_KEY_PASSWORD`

Without these secrets the build still runs, but it signs with a key made for that one run and leaves a warning on the run. That APK installs fine on a phone that doesn't have Spitify yet, but it can't update an existing install.

Back up the keystore and its passwords. If you lose them, every phone has to uninstall before it can take a new build.

### Local builds

`assembleRelease` signs with the debug key unless you point it at your keystore:

```bash
export SPITIFY_KEYSTORE=/path/to/spitify-release.jks
export SPITIFY_KEYSTORE_PASSWORD=...
export SPITIFY_KEY_ALIAS=spitify
export SPITIFY_KEY_PASSWORD=...
./gradlew assembleRelease
```

Switching keys means phones with the old build have to uninstall before installing the new one, since Android won't let a different key update an app. Builds up to v1.0.31 used the old shared dev key, so moving to your own key needs that one uninstall.
