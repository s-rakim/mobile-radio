const { execFile } = require('child_process');
const path = require('path');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');
const PYTHON = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
const TTL = 5 * 60 * 60 * 1000; // googlevideo URLs die around 6h

const tracks = new Map();   // id -> { audioUrl, headers, expires }
const queries = new Map();  // query -> { result, expires }
const inflight = new Map(); // job key -> Promise

function runPython(arg) {
  return new Promise((resolve) => {
    execFile(PYTHON, ['-u', SCRIPT_PATH, arg], { timeout: 90000 }, (error, stdout, stderr) => {
      if (error) {
        console.warn('[youtube] Python failed:', error.message, '\n', stderr);
        return resolve({ error: 'Extraction failed' });
      }
      const last = stdout.trim().split('\n').pop();
      try {
        resolve(JSON.parse(last));
      } catch {
        console.warn('[youtube] Bad JSON:', stdout, '\n', stderr);
        resolve({ error: 'Extraction failed' });
      }
    });
  });
}

// Two requests for the same thing at once share one Python run.
function once(key, arg) {
  if (!inflight.has(key)) {
    inflight.set(key, runPython(arg).finally(() => inflight.delete(key)));
  }
  return inflight.get(key);
}

function remember(data) {
  tracks.set(data.id, {
    audioUrl: data.audioUrl,
    headers: data.headers || {},
    expires: Date.now() + TTL,
  });
}

// Search. Returns stream paths like /stream/yt-abc123; index.js adds the host.
async function fetchTrack(query) {
  const clean = query.replace(/^id:/i, '').trim();
  const key = clean.toLowerCase();

  const cached = queries.get(key);
  if (cached && cached.expires > Date.now()) return [cached.result];

  const data = await once(`q:${key}`, clean);
  if (data.error) {
    console.warn('[youtube] Scraper error:', data.error);
    return [{ error: data.error }];
  }

  remember(data);
  const result = {
    title: data.title,
    artist: data.artist,
    image: data.image,
    duration: data.duration || 0,
    streamPath: `/stream/${data.id}`,
    source: data.source,
    id: data.id,
  };
  queries.set(key, { result, expires: Date.now() + TTL });
  return [result];
}

function getTrack(id) {
  const t = tracks.get(id);
  if (!t) return null;
  if (t.expires < Date.now()) {
    tracks.delete(id);
    return null;
  }
  return t;
}

// Fetches a fresh stream for a known video, so old links (liked songs,
// downloads, a server restart) keep working instead of expiring.
async function refetch(id) {
  const videoId = id.replace(/^yt-/, '');
  if (!/^[\w-]{11}$/.test(videoId)) return null;

  const data = await once(`id:${videoId}`, `id:${videoId}`);
  if (data.error || data.id !== id) {
    console.warn('[youtube] Refetch failed for', id, data.error || '');
    return null;
  }
  remember(data);
  return getTrack(id);
}

function invalidate(id) {
  tracks.delete(id);
  for (const [k, v] of queries) if (v.result.id === id) queries.delete(k);
}

module.exports = fetchTrack;
module.exports.getTrack = getTrack;
module.exports.refetch = refetch;
module.exports.invalidate = invalidate;
