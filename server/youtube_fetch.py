import sys
import json
import os
import shutil
import traceback

import yt_dlp

# Empty by default: at home yt-dlp talks to YouTube directly.
# Set YT_PROXY if you ever want to route through something.
PROXY = os.environ.get("YT_PROXY", "")


def get_cookie_file():
    here = os.path.dirname(os.path.abspath(__file__))
    src = os.path.join(here, "cookies.txt")
    if os.path.isfile(src) and os.path.getsize(src) > 0:
        dst = "/tmp/cookies_working.txt"
        if not os.path.exists(dst):  # keep whatever yt-dlp refreshed
            shutil.copyfile(src, dst)
        return dst
    return None


def build_opts():
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": "bestaudio/best",
        "socket_timeout": 15,
        "extractor_args": {},
    }

    cookies = get_cookie_file()
    if cookies:
        opts["cookiefile"] = cookies

    if PROXY:
        opts["proxy"] = PROXY

    pot = os.environ.get("YTDLP_POT_PROVIDER_URL")
    if pot:
        opts["extractor_args"]["youtubepot-bgutilhttp"] = {"base_url": [pot]}
    return opts


def fetch_song_stream(query):
    try:
        with yt_dlp.YoutubeDL(build_opts()) as ydl:
            result = ydl.extract_info(f"ytsearch1:{query}", download=False)

        entries = result.get("entries") or []
        if not entries or not entries[0].get("url"):
            raise Exception("No playable result")
        info = entries[0]

        return {
            "title": info.get("title"),
            "artist": info.get("artist") or info.get("uploader"),
            "image": info.get("thumbnail"),
            "audioUrl": info["url"],
            "headers": info.get("http_headers", {}),
            "source": "youtube",
            "id": f"yt-{info['id']}",
        }
    except Exception as e:
        print(traceback.format_exc(), file=sys.stderr)
        return {"error": str(e)}


if __name__ == "__main__":
    q = sys.argv[1] if len(sys.argv) > 1 else ""
    print(json.dumps(fetch_song_stream(q)))
