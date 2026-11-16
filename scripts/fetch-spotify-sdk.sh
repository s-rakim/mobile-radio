#!/usr/bin/env bash
# Downloads Spotify's App Remote SDK (.aar) into app/libs so the build includes
# "Play through Spotify". Needs the GitHub CLI (gh). Safe to run more than once.
set -euo pipefail
TAG="${SPOTIFY_SDK_TAG:-v0.8.0-appremote_v2.1.0-auth}"
cd "$(dirname "$0")/.."
mkdir -p app/libs
if ls app/libs/*app-remote*.aar >/dev/null 2>&1; then
  echo "Spotify SDK already in app/libs"
  exit 0
fi
tmp="$(mktemp -d)"
gh release download "$TAG" --repo spotify/android-sdk --dir "$tmp" --pattern '*' || true
# The release has carried the .aar directly in some versions and inside a zip in others.
for z in "$tmp"/*.zip; do [ -e "$z" ] && unzip -o -q "$z" -d "$tmp/unzipped" || true; done
aar="$(find "$tmp" -name '*app-remote*.aar' | head -n 1)"
if [ -z "$aar" ]; then
  echo "Couldn't find the App Remote .aar in release $TAG. Building without Spotify playback."
  exit 0
fi
cp "$aar" app/libs/
echo "Added $(basename "$aar")"
