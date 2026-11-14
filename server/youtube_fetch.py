import sys
import json
import os
import base64
from urllib.parse import urlparse
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
        # Highest-bitrate audio-only stream: Opus (~160 kbps) first, then AAC.
        # Never a video file with sound baked in unless nothing else exists.
        "format": "bestaudio[acodec=opus]/bestaudio[ext=m4a]/bestaudio/best",
        "format_sort": ["abr", "asr"],
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


# ---- SoundCloud ----
# Same idea as YouTube: yt-dlp finds the track and its audio link. A track's id carries its
# soundcloud.com address (base64), so a saved song can be looked up again after a restart.

SC_FORMAT = "bestaudio[protocol^=http]/bestaudio"


def sc_token(page_url):
    path = urlparse(page_url).path.strip("/")
    return base64.urlsafe_b64encode(path.encode()).decode().rstrip("=")


def sc_page(token):
    padded = token + "=" * (-len(token) % 4)
    return "https://soundcloud.com/" + base64.urlsafe_b64decode(padded.encode()).decode()


def sc_pack(info):
    page = info.get("webpage_url") or info.get("original_url")
    url = info.get("url")
    if not page or not url:
        return None
    # SoundCloud Go+ songs only give a 30-second preview to everyone else; leave those out.
    if "/preview/" in url or (info.get("format_note") or "").lower() == "preview":
        return None
    thumbs = info.get("thumbnails") or []
    best = max(thumbs, key=lambda t: (t.get("width") or 0) * (t.get("height") or 0), default=None)
    return {
        "title": info.get("title"),
        "artist": info.get("artist") or info.get("uploader"),
        "image": (best or {}).get("url") or info.get("thumbnail"),
        "duration": int(info.get("duration") or 0),
        "audioUrl": url,
        "headers": info.get("http_headers", {}),
        "hls": (info.get("protocol") or "").startswith("m3u8"),
        "source": "soundcloud",
        "id": "sc-" + sc_token(page),
    }


def sc_options():
    opts = build_opts()
    opts["format"] = SC_FORMAT
    opts["ignoreerrors"] = True  # one blocked track shouldn't sink the whole search
    opts.pop("cookiefile", None)  # those are YouTube's
    return opts


def sc_search(query, limit=6):
    with yt_dlp.YoutubeDL(sc_options()) as ydl:
        result = ydl.extract_info(f"scsearch{limit}:{query}", download=False) or {}
    tracks = [sc_pack(e) for e in (result.get("entries") or []) if e]
    return {"results": [t for t in tracks if t]}


def sc_fetch(token):
    with yt_dlp.YoutubeDL(sc_options()) as ydl:
        info = ydl.extract_info(sc_page(token), download=False)
    packed = sc_pack(info) if info else None
    if not packed:
        raise Exception("No playable audio")
    return packed


def main(arg):
    try:
        # "id:<videoId>" gets a YouTube stream link, "sc:<query>" searches SoundCloud,
        # "scid:<token>" gets a SoundCloud stream link; anything else is a YouTube search
        if arg.startswith("id:"):
            return fetch_stream(arg[3:])
        if arg.startswith("scid:"):
            return sc_fetch(arg[5:])
        if arg.startswith("sc:"):
            return sc_search(arg[3:])
        return search(arg)
    except Exception as e:
        print(traceback.format_exc(), file=sys.stderr)
        return {"error": str(e)}


if __name__ == "__main__":
    q = sys.argv[1] if len(sys.argv) > 1 else ""
    print(json.dumps(main(q)))
