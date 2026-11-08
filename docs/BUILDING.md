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

Out of the box, release builds are signed with the shared dev key in `keystore/spitify-dev.jks`. That key is public on purpose: it lets anyone build an APK that updates the one from CI without uninstalling first. It isn't suitable for a Play Store release.

To sign with your own private key instead:

1. Make a keystore:
   ```bash
   keytool -genkeypair -v -keystore spitify-release.jks -alias spitify \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In the repo's Settings → Secrets and variables → Actions, add:
   - `SPITIFY_KEYSTORE_BASE64`: output of `base64 -w0 spitify-release.jks`
   - `SPITIFY_KEYSTORE_PASSWORD`
   - `SPITIFY_KEY_ALIAS`
   - `SPITIFY_KEY_PASSWORD`

CI picks the private key up automatically. Locally, point the `SPITIFY_KEYSTORE` environment variable at the file and set the same three password variables.

Switching keys means phones with the old build have to uninstall before installing the new one, since Android won't let a different key update an app.
