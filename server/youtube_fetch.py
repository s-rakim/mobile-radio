import sys
import json
import yt_dlp

def fetch_song_stream(query):
    opts = {
        "quiet": True, 
        "no_warnings": True, 
        "noplaylist": True,
        "default_search": "ytsearch1"
    }

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            result = ydl.extract_info(f"ytsearch1:{query}", download=False)
            
        entries = result.get("entries") or []
        if not entries:
            return {"error": "No results found"}

        video = entries[0]
        
        # Extract the audio url
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
    # Print raw JSON to stdout
    print(json.dumps(result))