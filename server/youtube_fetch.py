import sys
import json
import yt_dlp
import os
import shutil

def fetch_song_stream(query):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # 1. Locate and copy cookies to a writable location
    if os.path.exists('/etc/secrets/cookies.txt'):
        original_cookie_path = '/etc/secrets/cookies.txt'
    else:
        original_cookie_path = os.path.join(script_dir, 'cookies.txt')

    writable_cookie_path = None
    if os.path.exists(original_cookie_path):
        writable_cookie_path = '/tmp/cookies_working.txt'
        shutil.copyfile(original_cookie_path, writable_cookie_path)

    # 2. Setup PO Token Provider URL
    pot_url = os.environ.get("YTDLP_POT_PROVIDER_URL")

    # 3. Build yt-dlp options with the correct player clients
    opts = {
        "quiet": True, 
        "no_warnings": True, 
        "noplaylist": True,
        "default_search": "ytsearch1",
        "cookiefile": writable_cookie_path,
        # Force the tv and mweb clients to bypass immediate LOGIN_REQUIRED errors
        "extractor_args": {
            "youtube": {
                "player_client": ["tv", "mweb"]
            }
        }
    }

    if pot_url:
        opts["extractor_args"]["youtubepot-bgutilhttp"] = {
            "base_url": [pot_url]
        }

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            result = ydl.extract_info(f"ytsearch1:{query}", download=False)
            
        entries = result.get("entries") or []
        if not entries:
            return {"error": "No results found"}

        video = entries[0]
        
        with yt_dlp.YoutubeDL({**opts, "format": "bestaudio/best"}) as ydl:
            info = ydl.extract_info(video["webpage_url"], download=False)
            
        if not info.get("url"):
            return {"error": "Could not extract audio URL"}

        return {
            "title": info.get("title"),
            "artist": info.get("uploader"),
            "image": info.get("thumbnail"),
            "audioUrl": info["url"],
            "source": "youtube",
            "id": f"yt-{info['id']}"
        }
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else ""
    result = fetch_song_stream(query)
    print(json.dumps(result))