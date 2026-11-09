// YouTube search straight from YouTube's own web API, the one youtube.com calls
// when you type in its search box. No Python, no yt-dlp, usually well under a second.
// If YouTube changes the response shape this returns nothing and the caller falls
// back to yt-dlp, so search keeps working either way.

const ENDPOINT = 'https://www.youtube.com/youtubei/v1/search?prettyPrint=false';
const CLIENT = { clientName: 'WEB', clientVersion: '2.20250101.00.00', hl: 'en', gl: 'KE' };
const VIDEOS_ONLY = 'EgIQAQ%3D%3D'; // the "Videos" filter, so no channels or playlists

function seconds(text) {
  if (!text) return 0;
  return text.split(':').reduce((total, part) => total * 60 + (parseInt(part, 10) || 0), 0);
}

function runsText(node) {
  if (!node) return '';
  if (node.simpleText) return node.simpleText;
  return (node.runs || []).map((r) => r.text).join('');
}

// Walks the response for videoRenderer entries wherever YouTube nests them.
function collectVideos(node, out) {
  if (!node || typeof node !== 'object' || out.length >= 20) return;
  if (Array.isArray(node)) {
    for (const child of node) collectVideos(child, out);
    return;
  }
  if (node.videoRenderer) {
    out.push(node.videoRenderer);
    return;
  }
  for (const value of Object.values(node)) collectVideos(value, out);
}

async function searchYouTube(query, limit = 5) {
  const res = await fetch(ENDPOINT, {
    method: 'POST',
    signal: AbortSignal.timeout(8000),
    headers: {
      'Content-Type': 'application/json',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
    },
    body: JSON.stringify({ context: { client: CLIENT }, query, params: VIDEOS_ONLY }),
  });
  if (!res.ok) throw new Error(`YouTube search answered ${res.status}`);

  const videos = [];
  collectVideos((await res.json()).contents, videos);

  return videos
    // live streams have no length and can't be played as a song
    .filter((v) => v.videoId && v.lengthText)
    .slice(0, limit)
    .map((v) => ({
      title: runsText(v.title),
      artist: runsText(v.ownerText) || runsText(v.longBylineText),
      image: `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`,
      duration: seconds(runsText(v.lengthText)),
      source: 'youtube',
      id: `yt-${v.videoId}`,
    }));
}

module.exports = searchYouTube;
