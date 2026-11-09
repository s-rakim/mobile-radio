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


# YouTube's Android VR client hands out audio links without the web player's JS challenge,
# which is the slow part of extraction. Tried first; the full method is the fallback.
FAST_CLIENTS = ["android_vr"]


def pack(info):
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


def plays(ydl, info):
    """Asks YouTube for the first two bytes. A link can look fine and still be refused."""
    from yt_dlp.networking import Request

    headers = dict(info.get("http_headers") or {})
    headers["Range"] = "bytes=0-1"
    with ydl.urlopen(Request(info["url"], headers=headers)) as response:
        response.read(2)
    return True


def fetch_stream(video_id, fast=True):
    """
    The playable audio link for one video. Tries the fast client first, but only keeps its link
    if YouTube really streams it; otherwise does the full extraction.
    """
    url = f"https://www.youtube.com/watch?v={video_id}"
    last_error = None
    ways = (FAST_CLIENTS, None) if fast and os.environ.get("YT_FAST", "1") != "0" else (None,)

    for clients in ways:
        opts = build_opts()
        if clients:
            opts["extractor_args"]["youtube"] = {"player_client": clients}
            opts.pop("cookiefile", None)  # this client doesn't take cookies
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if info and info.get("url") and (not clients or plays(ydl, info)):
                    return pack(info)
        except Exception as e:  # noqa: BLE001 - any failure just means "try the next way"
            last_error = e
            print(f"[{'fast' if clients else 'full'}] {video_id}: {e}", file=sys.stderr)

    raise last_error or Exception("No playable audio")


def main(arg):
    try:
        # "id:<videoId>" gets a stream link; anything else is a search
        if arg.startswith("id:"):
            return fetch_stream(arg[3:])
        if arg.startswith("full:"):
            return fetch_stream(arg[5:], fast=False)
        return search(arg)
    except Exception as e:
        print(traceback.format_exc(), file=sys.stderr)
        return {"error": str(e)}


def serve():
    """
    Long-running mode: one JSON request per line on stdin, one JSON reply per line on stdout.
    yt-dlp stays loaded between songs, and a few run side by side.
    """
    from concurrent.futures import ThreadPoolExecutor
    import threading

    out = threading.Lock()
    pool = ThreadPoolExecutor(max_workers=int(os.environ.get("YT_WORKERS", "3")))

    def handle(req):
        result = main(req.get("arg", ""))
        with out:
            sys.stdout.write(json.dumps({"id": req.get("id"), "result": result}) + "\n")
            sys.stdout.flush()

    sys.stdout.write(json.dumps({"ready": True}) + "\n")
    sys.stdout.flush()
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            pool.submit(handle, json.loads(line))
        except ValueError:
            continue


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--serve":
        serve()
    else:
        q = sys.argv[1] if len(sys.argv) > 1 else ""
        print(json.dumps(main(q)))
