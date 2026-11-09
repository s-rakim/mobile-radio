import sys
import json
import yt_dlp
import os
import shutil
import traceback

def fetch_song_stream(query):
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        
        # 1. Locate and copy cookies
        if os.path.exists('/etc/secrets/cookies.txt'):
            original_cookie_path = '/etc/secrets/cookies.txt'
        else:
            original_cookie_path = os.path.join(script_dir, 'cookies.txt')

        writable_cookie_path = None
        if os.path.exists(original_cookie_path):
            writable_cookie_path = '/tmp/cookies_working.txt'
            shutil.copyfile(original_cookie_path, writable_cookie_path)
            print("Cookies copied to /tmp", file=sys.stderr)

        pot_url = os.environ.get("YTDLP_POT_PROVIDER_URL")
        
        # Primary (SOCKS5) and Backup (HTTP) proxies
        primary_proxy = "socks5h://127.0.0.1:1080"
        backup_proxy = "http://127.0.0.1:8080"

        opts = {
            "quiet": True, 
            "no_warnings": True, 
            "noplaylist": True,
            "default_search": "ytsearch1",
            "cookiefile": writable_cookie_path,
            "socket_timeout": 15,
            "extractor_args": {
                "youtube": {
                    "player_client": ["mweb", "tv"]
                }
            }
        }

        if pot_url:
            opts["extractor_args"]["youtubepot-bgutilhttp"] = {
                "base_url": [pot_url]
            }

        def attempt_extraction(proxy):
            opts["proxy"] = proxy
            print(f"Attempting extraction using proxy: {proxy}", file=sys.stderr)
            
            with yt_dlp.YoutubeDL(opts) as ydl:
                result = ydl.extract_info(f"ytsearch1:{query}", download=False)
                
            entries = result.get("entries") or []
            if not entries:
                raise Exception("No results found")

            video = entries[0]
            
            with yt_dlp.YoutubeDL({**opts, "format": "bestaudio/best"}) as ydl:
                info = ydl.extract_info(video["webpage_url"], download=False)
                
            if not info.get("url"):
                raise Exception("Could not extract audio URL")
                
            return info

        # Try primary proxy, fallback to HTTP proxy if it fails
        try:
            info = attempt_extraction(primary_proxy)
        except Exception as e:
            print(f"Primary proxy ({primary_proxy}) failed: {e}", file=sys.stderr)
            print("Switching to backup HTTP proxy...", file=sys.stderr)
            info = attempt_extraction(backup_proxy)

        return {
            "title": info.get("title"),
            "artist": info.get("uploader"),
            "image": info.get("thumbnail"),
            "audioUrl": info["url"],
            "source": "youtube",
            "id": f"yt-{info['id']}"
        }
    except Exception as e:
        print(traceback.format_exc(), file=sys.stderr)
        return {"error": str(e)}

if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else ""
    result = fetch_song_stream(query)
    print(json.dumps(result))