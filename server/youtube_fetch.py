import sys
import json
import os
import shutil
import traceback

import yt_dlp

PROXY = os.environ.get("YT_PROXY", "socks5h://127.0.0.1:1080")
ALLOW_DIRECT_FALLBACK = os.environ.get("YT_DIRECT_FALLBACK") == "1"

def get_cookie_file():
    here = os.path.dirname(os.path.abspath(__file__))
    for p in ("/etc/secrets/cookies.txt", os.path.join(here, "cookies.txt")):
        if os.path.exists(p):
            dst = "/tmp/cookies_working.txt"
            if not os.path.exists(dst):  # keep whatever yt-dlp refreshed
                shutil.copyfile(p, dst)
            return dst
    return None

def build_opts(proxy):
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": "bestaudio/best",
        "cookiefile": get_cookie_file(),
        "socket_timeout": 15,
        "extractor_args": {"youtube": {"player_client": ["mweb", "tv"]}},
    }
    if proxy:
        opts["proxy"] = proxy

    pot = os.environ.get("YTDLP_POT_PROVIDER_URL")
    if pot:
        opts["extractor_args"]["youtubepot-bgutilhttp"] = {"base_url": [pot]}
    return opts

def extract(query, proxy):
    print(f"Extracting via {proxy or 'direct'}", file=sys.stderr)
    with yt_dlp.YoutubeDL(build_opts(proxy)) as ydl:
        result = ydl.extract_info(f"ytsearch1:{query}", download=False)

    entries = result.get("entries") or []
    if not entries or not entries[0].get("url"):
        raise Exception("No playable result")
    return entries[0]

def fetch_song_stream(query):
    try:
        try:
            info = extract(query, PROXY)
        except Exception as e:
            if not ALLOW_DIRECT_FALLBACK:
                raise
            print(f"Proxy failed: {e}, trying direct", file=sys.stderr)
            info = extract(query, None)

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