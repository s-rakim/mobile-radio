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


def search(query, limit=5):
    """Quick list lookup: titles, ids and thumbnails, no stream links."""
    opts = build_opts()
    opts["extract_flat"] = "in_playlist"
    opts.pop("format", None)

    with yt_dlp.YoutubeDL(opts) as ydl:
        result = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)

    tracks = []
    for e in result.get("entries") or []:
        vid = e.get("id")
        if not vid or e.get("live_status") == "is_live":
            continue
        tracks.append({
            "title": e.get("title"),
            "artist": e.get("channel") or e.get("uploader"),
            "image": f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
            "duration": int(e.get("duration") or 0),
            "source": "youtube",
            "id": f"yt-{vid}",
        })
    return {"results": tracks}


def fetch_stream(video_id):
    """Full extraction for one video: the playable audio link."""
    with yt_dlp.YoutubeDL(build_opts()) as ydl:
        info = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)

    if not info or not info.get("url"):
        raise Exception("No playable audio")

    return {
        "title": info.get("title"),
        "artist": info.get("artist") or info.get("uploader"),
        "image": info.get("thumbnail"),
        "duration": int(info.get("duration") or 0),
        "audioUrl": info["url"],
        "headers": info.get("http_headers", {}),
        "source": "youtube",
        "id": f"yt-{info['id']}",
    }


def main(arg):
    try:
        # "id:<videoId>" gets a stream link; anything else is a search
        if arg.startswith("id:"):
            return fetch_stream(arg[3:])
        return search(arg)
    except Exception as e:
        print(traceback.format_exc(), file=sys.stderr)
        return {"error": str(e)}


if __name__ == "__main__":
    q = sys.argv[1] if len(sys.argv) > 1 else ""
    print(json.dumps(main(q)))
